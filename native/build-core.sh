#!/usr/bin/env bash
# Cœur Amiberry (base WinUAE 6) pour Android arm64, avec le JIT, et son frontal : app/src/main/jniLibs/arm64-v8a/libamiga.so
#
#   ANDROID_NDK_ROOT=/chemin/du/ndk native/build-core.sh
#
# Les sources d'Amiberry sont clonées dans native/src (même version que la version web), adaptées par
# native/patches/*.patch (touches, manette CD32, mémoire RTG des sauvegardes d'état, le frontal cadence les images),
# puis compilées par le Makefile libretro d'Amiberry (platform=android-arm64 : JIT ARM64 compris).
# Le frontal est celui de la version web (amiga1200/wasm/frontend.c, sous-module), compilé tel quel : native/compat
# remplace les quelques fonctions d'Emscripten qu'il appelle. native/jni_bridge.c le relie à l'application.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
WEB="$ROOT/amiga1200"
AMB_REPO=https://github.com/BlitterStudio/amiberry.git
AMB_REV=ca2332df8cf914ec97c4a7c6ace38adcb3543b25   # Amiberry 8.3, comme amiga1200/wasm-amiberry/build.sh
SRC="$HERE/src"
OUT="$HERE/out"
LIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"
JOBS=${JOBS:-$(nproc)}
API=28
: "${ANDROID_NDK_ROOT:=${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}}"
[ -d "$ANDROID_NDK_ROOT" ] || { echo "ANDROID_NDK_ROOT : NDK introuvable" >&2; exit 1; }
[ -f "$WEB/wasm/frontend.c" ] || { echo "sous-module amiga1200 absent : git submodule update --init" >&2; exit 1; }
LLVM="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$LLVM/aarch64-linux-android$API-clang"
CXX="$LLVM/aarch64-linux-android$API-clang++"

if [ ! -d "$SRC/.git" ]; then
  git clone --filter=blob:none "$AMB_REPO" "$SRC"
fi
git -C "$SRC" fetch -q origin "$AMB_REV" 2>/dev/null || true
git -C "$SRC" checkout -q -f "$AMB_REV"
git -C "$SRC" clean -fdq -e '*.o' -e .patches.sum -e deps
git -C "$SRC" submodule update --init libretro/libco libretro/libretro-common
for p in "$HERE"/patches/*.patch; do git -C "$SRC" apply "$p"; done
# Fonctions C++ d'Amiberry appelées par le frontal (lecteurs de disquettes…) : compilées avec le cœur, mêmes options
cp "$WEB/wasm-amiberry/amb_glue.cpp" "$SRC/libretro/amb_glue.cpp"
sed -i 's|^SOURCES_CXX := \(.*\)$(SOURCES_MISC)$|SOURCES_CXX := \1$(SOURCES_MISC) $(LIBRETRO_DIR)/amb_glue.cpp|' "$SRC/libretro/Makefile"
grep -q 'amb_glue.cpp' "$SRC/libretro/Makefile" || { echo "Makefile d'Amiberry : liste des sources introuvable" >&2; exit 1; }
# Le Makefile ne suit pas les en-têtes : si les correctifs ont changé, les objets d'une compilation précédente sont jetés
PATCH_SUM=$(cat "$HERE"/patches/*.patch "$WEB/wasm-amiberry/amb_glue.cpp" | sha256sum | cut -c1-16)
if [ "$(cat "$SRC/.patches.sum" 2>/dev/null)" != "$PATCH_SUM" ]; then
  find "$SRC" -name '*.o' -delete
  echo "$PATCH_SUM" > "$SRC/.patches.sum"
fi

mkdir -p "$OUT" "$LIBS"
# zlib du NDK ; native/compat (faux en-têtes Emscripten pour amb_glue.cpp) passe par ZLIB_CFLAGS, ajouté aux chemins d'en-têtes
(cd "$SRC/libretro" && make -j"$JOBS" platform=android-arm64 ANDROID_NDK_ROOT="$ANDROID_NDK_ROOT" ANDROID_API=$API \
  ZLIB_CFLAGS="-I$HERE/compat" ZLIB_LIBS=-lz >"$OUT/make.log" 2>&1) || { tail -60 "$OUT/make.log"; exit 1; }
grep -q 'compemu_support' <(find "$SRC/src/jit" -name '*.o') || { echo "JIT absent de la compilation" >&2; exit 1; }

# Le cœur n'exporte que l'API libretro : on relie nous-mêmes ses objets au frontal et au pont JNI, en une bibliothèque
cd "$OUT"
FLAGS=(-O3 -fPIC -DFE_AMIBERRY -I"$HERE/compat" -I"$SRC/libretro")
"$CC" "${FLAGS[@]}" -c "$WEB/wasm/frontend.c" -o frontend.o
"$CC" "${FLAGS[@]}" -Wall -c "$HERE/jni_bridge.c" -o jni_bridge.o
find "$SRC/src" "$SRC/libretro" "$SRC/external" -name '*.o' | sort >objs.txt
[ "$(wc -l <objs.txt)" -ge 300 ] || { echo "compilation incomplète, voir $OUT/make.log" >&2; exit 1; }
"$CXX" -shared -fPIC -o "$LIBS/libamiga.so" frontend.o jni_bridge.o @objs.txt \
  -Wl,--no-undefined -Wl,--gc-sections -Wl,-z,max-page-size=16384 \
  -landroid -laaudio -llog -lz -ldl -lm -static-libstdc++
"$LLVM/llvm-strip" --strip-unneeded "$LIBS/libamiga.so"

# ROM AROS libres (démarrage sans Kickstart)
ASSETS="$ROOT/app/src/main/assets/aros"
mkdir -p "$ASSETS"
cp "$WEB/vendor/aros/aros-20260820-rom.bin" "$ASSETS/aros-rom.bin"
cp "$WEB/vendor/aros/aros-20260820-ext.bin" "$ASSETS/aros-ext.bin"
echo "OK : $LIBS/libamiga.so ($(stat -c %s "$LIBS/libamiga.so") octets)"
