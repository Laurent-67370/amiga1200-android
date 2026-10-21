/* Remplaçant d'Emscripten pour Android : le frontal de la version web (amiga1200/wasm/frontend.c) et
   amiga1200/wasm-amiberry/amb_glue.cpp se compilent tels quels avec le NDK. */
#pragma once
#include <time.h>

#define EMSCRIPTEN_KEEPALIVE __attribute__((used, visibility("default")))

/* Horloge en millisecondes, comme performance.now() */
static inline double emscripten_get_now(void) {
  struct timespec t;
  clock_gettime(CLOCK_MONOTONIC, &t);
  return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
}
