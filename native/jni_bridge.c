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
int fe_led(int i);
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

/* ---------- affichage ---------- */
static pthread_mutex_t win_mtx = PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *win;
static unsigned win_w, win_h;
static pthread_t render_thr;
static volatile int render_on;

/* Une image nouvelle : copiée telle quelle dans la surface (RGBA, comme pour la page) ; Android la met à l'échelle */
static void draw(void) {
  const uint8_t *src = fe_video_ptr();
  unsigned w = fe_video_w(), h = fe_video_h();
  if (!src || !w || !h) return;
  pthread_mutex_lock(&win_mtx);
  if (win) {
    if (w != win_w || h != win_h) {
      ANativeWindow_setBuffersGeometry(win, (int32_t)w, (int32_t)h, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
      win_w = w; win_h = h;
    }
    ANativeWindow_Buffer b;
    if (ANativeWindow_lock(win, &b, NULL) == 0) {
      unsigned cw = (unsigned)b.width < w ? (unsigned)b.width : w, ch = (unsigned)b.height < h ? (unsigned)b.height : h;
      for (unsigned y = 0; y < ch; y++) memcpy((uint8_t *)b.bits + (size_t)y * b.stride * 4, src + (size_t)y * w * 4, (size_t)cw * 4);
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

/* ---------- son ---------- */
static AAudioStream *astream;
static volatile int audio_mute;
static int32_t audio_rate;

static aaudio_data_callback_result_t audio_cb(AAudioStream *s, void *user, void *data, int32_t frames) {
  (void)s; (void)user;
  float *d = data;
  /* Retard borné : au-delà de 120 ms en attente (à-coup, reprise après une pause), les plus vieux échantillons
     sont jetés pour revenir vers 40 ms */
  unsigned av = fe_audio_available(), hi = (unsigned)audio_rate * 12 / 100, target = (unsigned)audio_rate * 4 / 100;
  if (av > hi + (unsigned)frames) fe_audio_read(NULL, av - target - (unsigned)frames);
  unsigned got = audio_mute ? 0 : fe_audio_read(d, (unsigned)frames);
  if (got < (unsigned)frames) memset(d + got * 2, 0, (size_t)(frames - got) * 2 * sizeof(float));
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void audio_open(void) {
  if (astream) return;
  AAudioStreamBuilder *b;
  if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return;
  audio_rate = (int32_t)(fe_sample_rate() > 0 ? fe_sample_rate() : 44100);
  AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
  AAudioStreamBuilder_setChannelCount(b, 2);
  AAudioStreamBuilder_setSampleRate(b, audio_rate);
  AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_GAME);
  AAudioStreamBuilder_setDataCallback(b, audio_cb, NULL);
  if (AAudioStreamBuilder_openStream(b, &astream) == AAUDIO_OK) {
    audio_rate = AAudioStream_getSampleRate(astream);
    AAudioStream_requestStart(astream);
    LOG("son : %d Hz", audio_rate);
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
/* Démarre le fil de l'émulation sur le contenu (fichier .uae, disquette…) ; l'affichage suit */
JNIEXPORT jboolean JNICALL JNI(start)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  int ok = fe_thread_start(p);
  free(p);
  if (ok && !render_on) {
    render_on = 1;
    pthread_create(&render_thr, NULL, render_main, NULL);
  }
  return ok ? JNI_TRUE : JNI_FALSE;
}
/* 1 en démarrage, 2 en marche (le son s'ouvre alors : le cœur a donné sa fréquence), -1 refus du cœur, 0 échec */
JNIEXPORT jint JNICALL JNI(state)(JNIEnv *env, jobject self) {
  (void)env; (void)self;
  int s = fe_thread_state();
  if (s == 2) audio_open();
  return s;
}
JNIEXPORT void JNICALL JNI(pause)(JNIEnv *env, jobject self, jboolean p) {
  (void)env; (void)self;
  fe_thread_pause(p);
  audio_mute = p;
  if (p) fe_flush();
}
JNIEXPORT void JNICALL JNI(warp)(JNIEnv *env, jobject self, jint w) { (void)env; (void)self; fe_thread_warp(w); }
JNIEXPORT void JNICALL JNI(reset)(JNIEnv *env, jobject self) { (void)env; (void)self; fe_reset(); }
/* Arrêt : le cœur garde un état global, l'application redémarre son processus pour rallumer */
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *env, jobject self) {
  (void)env; (void)self;
  audio_close();
  fe_thread_stop();
  render_on = 0;
  fe_flush();
}

/* ---------- entrées ---------- */
JNIEXPORT void JNICALL JNI(mouseMove)(JNIEnv *env, jobject self, jint dx, jint dy) { (void)env; (void)self; fe_mouse_move(dx, dy); }
JNIEXPORT void JNICALL JNI(mouseButtons)(JNIEnv *env, jobject self, jint m) { (void)env; (void)self; fe_mouse_buttons(m); }
JNIEXPORT void JNICALL JNI(joy)(JNIEnv *env, jobject self, jint port, jint m) { (void)env; (void)self; fe_joy(port, (unsigned)m); }
JNIEXPORT void JNICALL JNI(key)(JNIEnv *env, jobject self, jboolean down, jint retrok) { (void)env; (void)self; fe_key_push(down, (unsigned)retrok); }

/* ---------- disquettes ---------- */
JNIEXPORT void JNICALL JNI(insertDisk)(JNIEnv *env, jobject self, jint drive, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  fe_lock();
  if (p && *p) fe_drive_insert(drive, p); else fe_drive_eject(drive);
  fe_unlock();
  free(p);
}

/* ---------- sauvegardes d'état ---------- */
JNIEXPORT jboolean JNICALL JNI(saveState)(JNIEnv *env, jobject self, jstring path) {
  (void)self;
  char *p = jstr(env, path);
  int ok = 0;
  fe_lock();
  unsigned n = fe_state_size();
  void *buf = n ? malloc(n) : NULL;
  if (buf && fe_state_save(buf, n)) {
    FILE *f = fopen(p, "wb");
    if (f) { ok = fwrite(buf, 1, n, f) == n; fclose(f); }
  }
  fe_unlock();
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
  if (buf && fread(buf, 1, (size_t)n, f) == (size_t)n) ok = fe_state_load(buf, (unsigned)n); /* exécuté entre deux images */
  fclose(f); free(buf);
  return ok ? JNI_TRUE : JNI_FALSE;
}

/* ---------- mesures ---------- */
/* Images calculées et temps de calcul (µs) depuis le dernier appel : la vitesse réelle et la charge */
JNIEXPORT jint JNICALL JNI(frames)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)fe_thread_frames(); }
JNIEXPORT jdouble JNICALL JNI(busyUs)(JNIEnv *env, jobject self) { (void)env; (void)self; return fe_thread_busy(); }
JNIEXPORT jdouble JNICALL JNI(fps)(JNIEnv *env, jobject self) { (void)env; (void)self; return fe_fps(); }
JNIEXPORT jint JNICALL JNI(videoW)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)fe_video_w(); }
JNIEXPORT jint JNICALL JNI(videoH)(JNIEnv *env, jobject self) { (void)env; (void)self; return (jint)fe_video_h(); }
JNIEXPORT jint JNICALL JNI(led)(JNIEnv *env, jobject self, jint i) { (void)env; (void)self; return fe_led(i); }
