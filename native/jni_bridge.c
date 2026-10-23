/* Pont entre l'application Android (Kotlin, classe Native) et le frontal libretro de la version web.
 *
 * Le frontal (amiga1200/wasm/frontend.c) fait déjà tourner le cœur Amiberry dans son propre fil, au rythme de
 * l'Amiga, et expose des fonctions simples « fe_* ». Ce fichier y ajoute ce que la page web faisait elle-même :
 * l'affichage (un fil copie la dernière image dans la surface Android), le son (flux AAudio qui lit le tampon
 * circulaire du frontal) et le passage des appels Kotlin. */
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <aaudio/AAudio.h>
#include <stdarg.h>
#include <errno.h>
#include <fcntl.h>
#include "libretro.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "amiga1200", __VA_ARGS__)

/* ---------- frontal (frontend.c) ---------- */
void fe_set_option(const char *k, const char *v);
void fe_set_dirs(const char *sys, const char *save);
uint8_t *fe_video_ptr(void);
unsigned fe_video_w(void);
unsigned fe_video_h(void);
unsigned long fe_frame_count(void);
unsigned fe_audio_read(float *dst, unsigned max);
unsigned fe_audio_available(void);
void fe_mouse_move(int dx, int dy);
void fe_mouse_buttons(int mask);
void fe_joy(int port, unsigned mask);
void fe_key_push(int down, unsigned retrok);
void fe_key(int down, unsigned retrok);
int fe_start(const char *path);
void fe_run(void);
int fe_led(int i);
void fe_midi_enable(int out, int in);
unsigned fe_midi_out_read(uint8_t *dst, unsigned max);
void fe_midi_in_write(const uint8_t *src, unsigned len);
void amb_set_floppy_sound(int on);
void fe_reset(void);
double fe_fps(void);
double fe_sample_rate(void);
void fe_drive_insert(int num, const char *path);
void fe_drive_eject(int num);
void fe_flush(void);
unsigned fe_state_size(void);
int fe_state_save(void *dst, unsigned size);
int fe_state_load(const void *src, unsigned size);
int fe_thread_start(const char *path);
int fe_thread_state(void);
void fe_thread_pause(int p);
void fe_thread_warp(int w);
void fe_thread_stop(void);
unsigned fe_thread_frames(void);
double fe_thread_busy(void);
void fe_lock(void);
void fe_unlock(void);

#define JNI(name) Java_io_github_laurent67370_amiga1200_Native_##name

static char *jstr(JNIEnv *env, jstring s) {
  if (!s) return NULL;
  const char *c = (*env)->GetStringUTFChars(env, s, NULL);
  char *r = strdup(c);
  (*env)->ReleaseStringUTFChars(env, s, c);
  return r;
}

/* ---------- journal ----------
 * Le frontal ne garde que les avertissements du cœur. Ici, tout ce que le cœur écrit va dans le journal d'Android
 * et dans un fichier que l'application affiche (menu → Journal) : on intercepte retro_set_environment
 * (édition des liens avec --wrap) pour lui donner notre propre fonction de journal. */
static pthread_mutex_t log_mtx = PTHREAD_MUTEX_INITIALIZER;
static FILE *log_file;
static long log_bytes;
#define LOG_MAX (4L << 20)

static void log_line(int level, const char *msg) {
  __android_log_write(level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR : level >= RETRO_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO, "amiga1200", msg);
  pthread_mutex_lock(&log_mtx);
  if (log_file && log_bytes < LOG_MAX) {
    size_t n = strlen(msg);
    fputs(msg, log_file);
    if (!n || msg[n - 1] != '\n') { fputc('\n', log_file); n++; }
    fflush(log_file);
    log_bytes += (long)n;
  }
  pthread_mutex_unlock(&log_mtx);
}
static void core_log(enum retro_log_level level, const char *fmt, ...) {
  char buf[2048];
  va_list a; va_start(a, fmt); vsnprintf(buf, sizeof buf, fmt, a); va_end(a);
  log_line(level, buf);
}
static retro_environment_t real_env;
static bool env_wrap(unsigned cmd, void *data) {
  if (cmd == RETRO_ENVIRONMENT_GET_LOG_INTERFACE) { ((struct retro_log_callback *)data)->log = core_log; return true; }
  return real_env(cmd, data);
}
void __real_retro_set_environment(retro_environment_t cb);
void __wrap_retro_set_environment(retro_environment_t cb) {
  real_env = cb;
  __real_retro_set_environment(env_wrap);
}
JNIEXPORT void JNICALL JNI(setLogFile)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  pthread_mutex_lock(&log_mtx);
  if (log_file) fclose(log_file);
  log_file = fopen(p, "w");
  log_bytes = 0;
  pthread_mutex_unlock(&log_mtx);
  free(p);
}
JNIEXPORT void JNICALL JNI(log)(JNIEnv *env, jobject self, jstring msg) {
  (void)self;
  char *m = jstr(env, msg);
  if (m) { log_line(RETRO_LOG_INFO, m); free(m); }
}

/* ---------- disques choisis par l'utilisateur ----------
 * L'application ouvre chaque document (Storage Access Framework) et pose un lien symbolique, à son vrai nom,
 * vers son descripteur (/proc/self/fd/N). Rouvrir ce chemin passe par les droits d'accès au fichier d'origine,
 * que l'application n'a pas (dossier Téléchargements…) : si fopen échoue sur un tel lien, on repart du descripteur
 * déjà ouvert (copie par dup). fopen est détourné à l'édition des liens (--wrap=fopen). */
FILE *__real_fopen(const char *path, const char *mode);
FILE *__wrap_fopen(const char *path, const char *mode) {
  FILE *f = __real_fopen(path, mode);
  if (f || !path || !mode) return f;
  int err = errno, fd;
  char tgt[64];
  ssize_t n = readlink(path, tgt, sizeof tgt - 1);
  if (n > 0) {
    tgt[n] = 0;
    if (sscanf(tgt, "/proc/self/fd/%d", &fd) == 1) {
      int acc = fcntl(fd, F_GETFL) & O_ACCMODE;
      int wants_write = strchr(mode, '+') || mode[0] == 'w' || mode[0] == 'a';
      if (acc >= 0 && (!wants_write || acc == O_RDWR)) {
        int d = dup(fd);
        if (d >= 0) {
          f = fdopen(d, mode);
          if (f) fseek(f, 0, mode[0] == 'a' ? SEEK_END : SEEK_SET);
          else close(d);
        }
      }
      core_log(f ? RETRO_LOG_INFO : RETRO_LOG_WARN, "fopen(%s, %s) : %s par le descripteur %d (accès direct refusé : %s)",
               path, mode, f ? "ouvert" : "échec", fd, strerror(err));
    }
  }
  if (!f) errno = err;
  return f;
}

/* ---------- affichage ---------- */
static pthread_mutex_t win_mtx = PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *win;
static unsigned win_w, win_h;
static pthread_t render_thr;
static volatile int render_on;

/* Filtres (réglages de l'image) : 0 lisse (l'image est copiée telle quelle, Android l'agrandit en la lissant),
 * 1 nette et 2 écran cathodique : l'image est agrandie ici à la taille de la vue, pixel le plus proche, sans flou ;
 * l'écran cathodique assombrit une ligne sur deux de l'Amiga (lignes de balayage). */
static volatile int flt_mode;
static volatile unsigned view_w, view_h;
#define OUT_MAX 4096
static uint16_t xmap[OUT_MAX];
static unsigned xmap_w, xmap_bw;

static inline uint32_t dim(uint32_t p) { return 0xff000000u | (((p >> 1) & 0x007f7f7fu) + ((p >> 3) & 0x001f1f1fu)); }

static void scale(const uint32_t *src, unsigned w, unsigned h, uint32_t *dst, unsigned stride, unsigned bw, unsigned bh, int crt) {
  if (xmap_w != w || xmap_bw != bw) {
    for (unsigned x = 0; x < bw; x++) xmap[x] = (uint16_t)((x * 2 + 1) * w / (bw * 2));
    xmap_w = w; xmap_bw = bw;
  }
  /* Lignes de l'Amiga : une image de plus de 400 lignes a ses lignes doublées (basse résolution) ou entrelacées */
  unsigned per = h >= 400 ? 2 : 1;
  const uint32_t *prev = NULL; unsigned prev_key = ~0u;
  for (unsigned y = 0; y < bh; y++) {
    unsigned sy = (unsigned)(((unsigned long)y * 2 + 1) * h / (bh * 2));
    int d = 0;
    if (crt) {
      unsigned long ph = (((unsigned long)y * 2 + 1) * h * 2 / (bh * 2 * per)) & 1; /* moitié basse de chaque ligne */
      d = (int)ph;
    }
    uint32_t *o = dst + (size_t)y * stride;
    unsigned key = sy * 2 + (unsigned)d;
    if (key == prev_key) { memcpy(o, prev, (size_t)bw * 4); continue; }
    const uint32_t *s = src + (size_t)sy * w;
    if (d) for (unsigned x = 0; x < bw; x++) o[x] = dim(s[xmap[x]]);
    else for (unsigned x = 0; x < bw; x++) o[x] = s[xmap[x]];
    prev = o; prev_key = key;
  }
}

/* Une image nouvelle : copiée dans la surface (RGBA, comme pour la page), ou agrandie selon le filtre */
static void draw(void) {
  const uint8_t *src = fe_video_ptr();
  unsigned w = fe_video_w(), h = fe_video_h();
  if (!src || !w || !h) return;
  int mode = flt_mode;
  unsigned bw = w, bh = h;
  if (mode && view_w >= w && view_h >= h) {
    bw = view_w < OUT_MAX ? view_w : OUT_MAX; bh = view_h < OUT_MAX ? view_h : OUT_MAX;
  } else mode = 0;
  pthread_mutex_lock(&win_mtx);
  if (win) {
    if (bw != win_w || bh != win_h) {
      ANativeWindow_setBuffersGeometry(win, (int32_t)bw, (int32_t)bh, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
      win_w = bw; win_h = bh;
    }
    ANativeWindow_Buffer b;
    if (ANativeWindow_lock(win, &b, NULL) == 0) {
      if (mode && (unsigned)b.width == bw && (unsigned)b.height == bh)
        scale((const uint32_t *)src, w, h, b.bits, (unsigned)b.stride, bw, bh, mode == 2);
      else {
        unsigned cw = (unsigned)b.width < w ? (unsigned)b.width : w, ch = (unsigned)b.height < h ? (unsigned)b.height : h;
        for (unsigned y = 0; y < ch; y++) memcpy((uint8_t *)b.bits + (size_t)y * b.stride * 4, src + (size_t)y * w * 4, (size_t)cw * 4);
      }
      ANativeWindow_unlockAndPost(win);
    }
  }
  pthread_mutex_unlock(&win_mtx);
}
static void *render_main(void *arg) {
  (void)arg;
  unsigned long last = 0;
  while (render_on) {
    unsigned long fc = fe_frame_count();
    if (fc == last) { usleep(1500); continue; }
    last = fc;
    draw();
  }
  return NULL;
}
JNIEXPORT void JNICALL JNI(setSurface)(JNIEnv *env, jobject self, jobject surface) {
  (void)self;
  pthread_mutex_lock(&win_mtx);
  if (win) ANativeWindow_release(win);
  win = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
  win_w = win_h = 0;
  pthread_mutex_unlock(&win_mtx);
}

/* Filtre de l'image et taille de la vue (pour les filtres net et cathodique) */
JNIEXPORT void JNICALL JNI(setFilter)(JNIEnv *env, jobject self, jint mode, jint w, jint h) {
  (void)env; (void)self;
  view_w = w > 0 ? (unsigned)w : 0; view_h = h > 0 ? (unsigned)h : 0;
  flt_mode = mode;
}
/* Image affichée, en pixels ARGB d'Android (captures d'écran, miniatures des sauvegardes) ; null sans image */
JNIEXPORT jintArray JNICALL JNI(frame)(JNIEnv *env, jobject self) {
  (void)self;
  const uint32_t *src = (const uint32_t *)fe_video_ptr();
  unsigned w = fe_video_w(), h = fe_video_h();
  if (!src || !w || !h) return NULL;
  size_t n = (size_t)w * h;
  uint32_t *px = malloc(n * 4);
  if (!px) return NULL;
  for (size_t i = 0; i < n; i++) { uint32_t p = src[i]; px[i] = 0xff000000u | ((p & 0xffu) << 16) | (p & 0xff00u) | ((p >> 16) & 0xffu); }
  jintArray a = (*env)->NewIntArray(env, (jsize)n);
  if (a) (*env)->SetIntArrayRegion(env, a, 0, (jsize)n, (const jint *)px);
  free(px);
  return a;
}

/* ---------- son ---------- */
static AAudioStream *astream;
static volatile int audio_mute;
static int32_t audio_rate;

/* Lecture sans grésillement : le cœur livre le son par paquets d'une image (20 ms), irrégulièrement (temps de calcul
 * variable, fil de l'émulation endormi par Android), alors qu'AAudio en demande de petits morceaux à intervalles
 * réguliers. Comme dans la version web, une file d'attente garde une réserve visée (50 ms au départ) : la lecture
 * attend que la réserve soit pleine, puis lit un peu plus vite ou plus lentement (au plus 0,5 %, inaudible) pour la
 * garder à sa cible. Un manque relève la cible de 10 ms (jusqu'à 150 ms), une minute sans manque la redescend ;
 * au-delà du triple de la cible (reprise après une pause), les plus vieux échantillons sont jetés. La lecture
 * interpole entre deux échantillons, ce qui convertit aussi la fréquence du cœur vers celle de l'appareil. */
#define AQ_FRAMES 65536
static float aq[AQ_FRAMES * 2];
static unsigned aq_n;            /* trames en réserve */
static double aq_pos;            /* position de lecture (fraction de trame) dans la réserve */
static int aq_playing;
static double aq_target_ms = 50, aq_core_rate = 44100;
static long aq_calm;             /* trames lues depuis le dernier manque */
static volatile unsigned long aq_pulls; /* appels du rappel AAudio : le son avance-t-il ? */

static void aq_drop(unsigned n) {
  if (n >= aq_n) { aq_n = 0; aq_pos = 0; return; }
  memmove(aq, aq + n * 2, (size_t)(aq_n - n) * 2 * sizeof(float));
  aq_n -= n;
  aq_pos -= n;
  if (aq_pos < 0) aq_pos = 0;
}

static aaudio_data_callback_result_t audio_cb(AAudioStream *s, void *user, void *data, int32_t frames) {
  (void)s; (void)user;
  float *d = data;
  aq_pulls++;
  /* Tout ce que le cœur a produit rejoint la réserve */
  if (aq_n < AQ_FRAMES) aq_n += fe_audio_read(aq + aq_n * 2, AQ_FRAMES - aq_n);
  unsigned target = (unsigned)(aq_core_rate * aq_target_ms / 1000);
  if (aq_n > target * 3) aq_drop(aq_n - target);
  if (audio_mute || !aq_playing) {
    memset(d, 0, (size_t)frames * 2 * sizeof(float));
    if (!audio_mute && aq_n >= target) aq_playing = 1;
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
  }
  /* Vitesse de lecture : fréquence du cœur / fréquence de l'appareil, corrigée selon l'écart à la cible */
  double err = ((double)aq_n - target) / (target ? target : 1);
  if (err > 1) err = 1; else if (err < -1) err = -1;
  double step = aq_core_rate / audio_rate * (1 + 0.005 * err);
  int32_t i = 0;
  for (; i < frames; i++) {
    unsigned k = (unsigned)aq_pos;
    if (k + 1 >= aq_n) break;
    float f = (float)(aq_pos - k);
    d[i * 2] = aq[k * 2] + (aq[k * 2 + 2] - aq[k * 2]) * f;
    d[i * 2 + 1] = aq[k * 2 + 1] + (aq[k * 2 + 3] - aq[k * 2 + 1]) * f;
    aq_pos += step;
  }
  if (i < frames) {
    /* Manque : silence, réserve plus grande, on attend qu'elle se remplisse */
    memset(d + i * 2, 0, (size_t)(frames - i) * 2 * sizeof(float));
    aq_playing = 0;
    if (aq_target_ms < 150) aq_target_ms += 10;
    aq_calm = 0;
  } else if ((aq_calm += frames) > audio_rate * 60L && aq_target_ms > 50) {
    aq_target_ms -= 10;
    aq_calm = 0;
  }
  aq_drop((unsigned)aq_pos);
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void audio_open(void) {
  if (astream) return;
  AAudioStreamBuilder *b;
  if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return;
  audio_rate = (int32_t)(fe_sample_rate() > 0 ? fe_sample_rate() : 44100);
  aq_core_rate = audio_rate;
  AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
  AAudioStreamBuilder_setChannelCount(b, 2);
  AAudioStreamBuilder_setSampleRate(b, audio_rate);
  AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_GAME);
  AAudioStreamBuilder_setDataCallback(b, audio_cb, NULL);
  if (AAudioStreamBuilder_openStream(b, &astream) == AAUDIO_OK) {
    audio_rate = AAudioStream_getSampleRate(astream);
    AAudioStream_requestStart(astream);
    LOG("son : %d Hz (cœur %.0f Hz)", audio_rate, aq_core_rate);
  } else astream = NULL;
  AAudioStreamBuilder_delete(b);
}
static void audio_close(void) {
  if (!astream) return;
  AAudioStream_requestStop(astream);
  AAudioStream_close(astream);
  astream = NULL;
}

/* ---------- démarrage, arrêt ---------- */
JNIEXPORT void JNICALL JNI(setDirs)(JNIEnv *env, jobject self, jstring sys, jstring save) {
  (void)self;
  char *a = jstr(env, sys), *b = jstr(env, save);
  fe_set_dirs(a, b);
  free(a); free(b);
}
JNIEXPORT void JNICALL JNI(setOption)(JNIEnv *env, jobject self, jstring k, jstring v) {
  (void)self;
  char *a = jstr(env, k), *b = jstr(env, v);
  fe_set_option(a, b);
  free(a); free(b);
}
/* ---------- fil de l'émulation, cadencé par le son ----------
 * Le frontal sait faire tourner le cœur dans son propre fil, à une horloge fixe (50 images/s). Mais l'Amiga change de
 * fréquence d'image en route (démo en NTSC 60 Hz, écran RTG…) : à 50 images/s, il produit alors moins de son qu'il
 * n'en faut et la musique se coupe. Ce fil-ci calcule une image dès que la réserve de son descend sous sa cible :
 * l'émulation suit exactement l'horloge du son (comme la synchronisation audio de RetroArch), à la vraie cadence de
 * l'Amiga. Sans son (pas encore ouvert, ou rappel AAudio arrêté), il revient à l'horloge fixe du frontal.
 * Ce qui touche à l'état du cœur depuis l'interface (sauvegardes d'état) s'exécute dans ce fil, entre deux images. */
static pthread_t emu_thr;
static pthread_mutex_t emu_mtx = PTHREAD_MUTEX_INITIALIZER;
static volatile int emu_state, emu_stop, emu_paused, emu_turbo;
static char emu_path[1024];
static volatile unsigned stat_frames;
static volatile double stat_busy_us;
#define KQ 256
static unsigned kq[KQ];
static volatile unsigned kq_w, kq_r;
static pthread_mutex_t cmd_mtx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cmd_cv = PTHREAD_COND_INITIALIZER;
static int cmd_code, cmd_res, cmd_done;   /* 1 sauvegarder, 2 reprendre */
static void *cmd_buf;
static unsigned cmd_len;

/* MIDI du port série : le cœur lit et écrit les files du frontal dans ce fil-ci, entre deux images ; l'application
 * (android.media.midi) échange avec deux autres files, à un seul lecteur et un seul écrivain chacune */
#define MQ 16384
static uint8_t mo[MQ], mi[MQ];
static unsigned mo_w, mo_r, mi_w, mi_r;
static void midi_pump(void) {
  uint8_t b[512];
  for (unsigned n; (n = fe_midi_out_read(b, sizeof b));)
    for (unsigned i = 0; i < n; i++) {
      unsigned w = __atomic_load_n(&mo_w, __ATOMIC_RELAXED), nw = (w + 1) % MQ;
      if (nw == __atomic_load_n(&mo_r, __ATOMIC_ACQUIRE)) break;  /* l'application ne lit plus : octets perdus */
      mo[w] = b[i]; __atomic_store_n(&mo_w, nw, __ATOMIC_RELEASE);
    }
  unsigned n = 0, r = __atomic_load_n(&mi_r, __ATOMIC_RELAXED), w = __atomic_load_n(&mi_w, __ATOMIC_ACQUIRE);
  while (r != w && n < sizeof b) { b[n++] = mi[r]; r = (r + 1) % MQ; }
  __atomic_store_n(&mi_r, r, __ATOMIC_RELEASE);
  if (n) fe_midi_in_write(b, n);
}

static double now_ms(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec * 1000.0 + t.tv_nsec / 1e6; }

static void emu_frame(void) {
  double t0 = now_ms();
  pthread_mutex_lock(&emu_mtx);
  /* Une touche par image, dans l'ordre (l'Amiga voit chaque appui et chaque relâchement) */
  if (kq_r != kq_w) { unsigned k = kq[kq_r]; kq_r = (kq_r + 1) % KQ; fe_key((k & 0x80000000u) != 0, k & 0x7fffffffu); }
  fe_run();
  midi_pump();
  pthread_mutex_unlock(&emu_mtx);
  stat_busy_us += (now_ms() - t0) * 1000.0;
  stat_frames++;
}
static void emu_cmd_run(void) {
  pthread_mutex_lock(&cmd_mtx);
  if (cmd_code && !cmd_done) {
    pthread_mutex_lock(&emu_mtx);
    cmd_res = cmd_code == 1 ? fe_state_save(cmd_buf, cmd_len) : fe_state_load(cmd_buf, cmd_len);
    pthread_mutex_unlock(&emu_mtx);
    cmd_done = 1;
    pthread_cond_broadcast(&cmd_cv);
  }
  pthread_mutex_unlock(&cmd_mtx);
}
static int emu_cmd(int code, void *buf, unsigned len) {
  if (emu_state != 2) return 0;
  pthread_mutex_lock(&cmd_mtx);
  cmd_code = code; cmd_buf = buf; cmd_len = len; cmd_done = 0; cmd_res = 0;
  while (!cmd_done && emu_state == 2 && !emu_stop) {
    struct timespec t; clock_gettime(CLOCK_REALTIME, &t); t.tv_nsec += 100000000; if (t.tv_nsec >= 1000000000) { t.tv_sec++; t.tv_nsec -= 1000000000; }
    pthread_cond_timedwait(&cmd_cv, &cmd_mtx, &t);
  }
  int r = cmd_done ? cmd_res : 0;
  cmd_code = 0;
  pthread_mutex_unlock(&cmd_mtx);
  return r;
}

static void *emu_main(void *arg) {
  (void)arg;
  pthread_mutex_lock(&emu_mtx);
  int ok = fe_start(emu_path[0] ? emu_path : NULL);
  pthread_mutex_unlock(&emu_mtx);
  emu_state = ok ? 2 : -1;
  if (!ok) return NULL;
  double period = 1000.0 / (fe_fps() > 0 ? fe_fps() : 50.0), next = now_ms();
  unsigned long last_pulls = aq_pulls;
  double last_pull_t = now_ms();
  while (!emu_stop) {
    emu_cmd_run();
    if (emu_paused) { usleep(4000); next = now_ms(); continue; }
    double now = now_ms();
    if (emu_turbo) { emu_frame(); next = now_ms(); continue; }   /* turbo : sans attendre */
    /* Le son avance-t-il ? (rappel AAudio appelé dans les 200 dernières millisecondes) */
    if (aq_pulls != last_pulls) { last_pulls = aq_pulls; last_pull_t = now; }
    if (astream && now - last_pull_t < 200) {
      /* Cadence du son : une image dès que la réserve (tampon du frontal + file de lecture) passe sous la cible */
      unsigned queued = fe_audio_available() + aq_n;
      unsigned want = (unsigned)(aq_core_rate * (aq_target_ms + 25) / 1000);
      /* Au plus deux fois la cadence normale : un Amiga muet (son coupé dans le cœur) ne s'emballe pas */
      if (queued < want && now >= next) { emu_frame(); next = now + period / 2; } else usleep(1000);
      continue;
    }
    /* Horloge fixe, comme le frontal */
    if (now < next) { double d = next - now; usleep(d > 2.0 ? (useconds_t)((d - 1.0) * 1000) : 200); continue; }
    if (now - next > 250) next = now;
    emu_frame();
    next += period;
  }
  return NULL;
}

/* Démarre le fil de l'émulation sur le contenu (fichier .uae, disquette…) ; l'affichage suit */
JNIEXPORT jboolean JNICALL JNI(start)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  snprintf(emu_path, sizeof emu_path, "%s", p ? p : "");
  free(p);
  emu_state = 1;
  pthread_attr_t a; pthread_attr_init(&a); pthread_attr_setstacksize(&a, 8u << 20);
  if (pthread_create(&emu_thr, &a, emu_main, NULL)) { emu_state = 0; return JNI_FALSE; }
  if (!render_on) {
    render_on = 1;
    pthread_create(&render_thr, NULL, render_main, NULL);
  }
  return JNI_TRUE;
}
/* 1 en démarrage, 2 en marche (le son s'ouvre alors : le cœur a donné sa fréquence), -1 refus du cœur, 0 échec */
JNIEXPORT jint JNICALL JNI(state)(JNIEnv *env, jobject self) {
  (void)env; (void)self;
  int s = emu_state;
  if (s == 2) audio_open();
  return s;
}
JNIEXPORT void JNICALL JNI(pause)(JNIEnv *env, jobject self, jboolean p) {
  (void)env; (void)self;
  emu_paused = p;
  audio_mute = p;
  if (p) fe_flush();
}
/* Turbo : le frontal jette alors le son (fe_thread_warp ne règle que ce drapeau, son propre fil ne tourne pas) */
JNIEXPORT void JNICALL JNI(warp)(JNIEnv *env, jobject self, jint w) {
  (void)env; (void)self;
  emu_turbo = w > 1;
  fe_thread_warp(w);
}
JNIEXPORT void JNICALL JNI(reset)(JNIEnv *env, jobject self) { (void)env; (void)self; }
/* Arrêt : le cœur garde un état global, l'application redémarre son processus pour rallumer */
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *env, jobject self) {
  (void)env; (void)self;
  audio_close();
  emu_stop = 1;
  render_on = 0;
  fe_flush();
}

/* ---------- entrées ---------- */
JNIEXPORT void JNICALL JNI(mouseMove)(JNIEnv *env, jobject self, jint dx, jint dy) { (void)env; (void)self; fe_mouse_move(dx, dy); }
JNIEXPORT void JNICALL JNI(mouseButtons)(JNIEnv *env, jobject self, jint m) { (void)env; (void)self; fe_mouse_buttons(m); }
JNIEXPORT void JNICALL JNI(joy)(JNIEnv *env, jobject self, jint port, jint m) { (void)env; (void)self; fe_joy(port, (unsigned)m); }
JNIEXPORT void JNICALL JNI(key)(JNIEnv *env, jobject self, jboolean down, jint retrok) {
  (void)env; (void)self;
  unsigned n = (kq_w + 1) % KQ;
  if (n == kq_r) return;
  kq[kq_w] = (unsigned)retrok | (down ? 0x80000000u : 0);
  __atomic_store_n(&kq_w, n, __ATOMIC_RELEASE);
}

JNIEXPORT void JNICALL JNI(portDevice)(JNIEnv *env, jobject self, jint port, jint device) {
  (void)env; (void)self;
  pthread_mutex_lock(&emu_mtx);
  retro_set_controller_port_device((unsigned)port, (unsigned)device);
  pthread_mutex_unlock(&emu_mtx);
}

/* ---------- disquettes ---------- */
JNIEXPORT void JNICALL JNI(insertDisk)(JNIEnv *env, jobject self, jint drive, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  pthread_mutex_lock(&emu_mtx);
  if (p && *p) fe_drive_insert(drive, p); else fe_drive_eject(drive);
  pthread_mutex_unlock(&emu_mtx);
  free(p);
}

/* Bruits des lecteurs, à chaud (échantillons dans data/floppy_sounds, sous le dossier courant) */
JNIEXPORT void JNICALL JNI(floppySound)(JNIEnv *env, jobject self, jboolean on) {
  (void)env; (void)self;
  pthread_mutex_lock(&emu_mtx);
  amb_set_floppy_sound(on ? 1 : 0);
  pthread_mutex_unlock(&emu_mtx);
}
/* Dossier courant : Amiberry cherche ses fichiers de données (bruits des lecteurs) dans « data/ », chemin relatif
 * sur Android quand le frontal ne donne pas de stockage externe */
JNIEXPORT jboolean JNICALL JNI(setHome)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  int ok = p && chdir(p) == 0;
  free(p);
  return ok ? JNI_TRUE : JNI_FALSE;
}

/* ---------- MIDI ---------- */
JNIEXPORT void JNICALL JNI(midiEnable)(JNIEnv *env, jobject self, jboolean out, jboolean in) {
  (void)env; (void)self;
  fe_midi_enable(out ? 1 : 0, in ? 1 : 0);
}
/* Octets envoyés par l'Amiga depuis le dernier appel */
JNIEXPORT jint JNICALL JNI(midiRead)(JNIEnv *env, jobject self, jbyteArray dst) {
  (void)self;
  jsize max = (*env)->GetArrayLength(env, dst);
  uint8_t b[4096];
  unsigned n = 0, r = __atomic_load_n(&mo_r, __ATOMIC_RELAXED), w = __atomic_load_n(&mo_w, __ATOMIC_ACQUIRE);
  while (r != w && n < sizeof b && n < (unsigned)max) { b[n++] = mo[r]; r = (r + 1) % MQ; }
  __atomic_store_n(&mo_r, r, __ATOMIC_RELEASE);
  if (n) (*env)->SetByteArrayRegion(env, dst, 0, (jsize)n, (const jbyte *)b);
  return (jint)n;
}
/* Octets reçus d'un clavier MIDI, remis à l'Amiga à l'image suivante */
JNIEXPORT void JNICALL JNI(midiWrite)(JNIEnv *env, jobject self, jbyteArray src, jint off, jint len) {
  (void)self;
  if (len <= 0) return;
  jbyte *b = (*env)->GetByteArrayElements(env, src, NULL);
  if (!b) return;
  for (jint i = 0; i < len; i++) {
    unsigned w = __atomic_load_n(&mi_w, __ATOMIC_RELAXED), nw = (w + 1) % MQ;
    if (nw == __atomic_load_n(&mi_r, __ATOMIC_ACQUIRE)) break;
    mi[w] = (uint8_t)b[off + i]; __atomic_store_n(&mi_w, nw, __ATOMIC_RELEASE);
  }
  (*env)->ReleaseByteArrayElements(env, src, b, JNI_ABORT);
}

/* ---------- sauvegardes d'état ---------- */
JNIEXPORT jboolean JNICALL JNI(saveState)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  int ok = 0;
  pthread_mutex_lock(&emu_mtx);
  unsigned n = fe_state_size();
  pthread_mutex_unlock(&emu_mtx);
  void *buf = n ? malloc(n) : NULL;
  if (buf && emu_cmd(1, buf, n)) {
    FILE *f = fopen(p, "wb");
    if (f) { ok = fwrite(buf, 1, n, f) == n; fclose(f); }
  }
  free(buf); free(p);
  return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL JNI(loadState)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  int ok = 0;
  FILE *f = fopen(p, "rb");
  free(p);
  if (!f) return JNI_FALSE;
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  void *buf = n > 0 ? malloc((size_t)n) : NULL;
  if (buf && fread(buf, 1, (size_t)n, f) == (size_t)n) ok = emu_cmd(2, buf, (unsigned)n); /* exécuté entre deux images */
  fclose(f); free(buf);
  return ok ? JNI_TRUE : JNI_FALSE;
}

/* ---------- mesures ---------- */
/* Images calculées et temps de calcul (µs) depuis le dernier appel : la vitesse réelle et la charge */
JNIEXPORT jint JNICALL JNI(frames)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)__atomic_exchange_n(&stat_frames, 0, __ATOMIC_RELAXED); }
JNIEXPORT jdouble JNICALL JNI(busyUs)(JNIEnv *env, jobject self) { (void)env; (void)self; double b = stat_busy_us; stat_busy_us = 0; return b; }
JNIEXPORT jdouble JNICALL JNI(fps)(JNIEnv *env, jobject self) { (void)env; (void)self; return fe_fps(); }
JNIEXPORT jint JNICALL JNI(videoW)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)fe_video_w(); }
JNIEXPORT jint JNICALL JNI(videoH)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)fe_video_h(); }
JNIEXPORT jint JNICALL JNI(led)(JNIEnv *env, jobject self, jint i) { (void)env; (void)self; return fe_led(i); }
