#!/usr/bin/env bash
# Signe l'APK avec la clé de publication, gardée dans les secrets du dépôt (jamais dans le dépôt) :
#   SIGNING_KEYSTORE_B64  magasin de clés PKCS12 en base64 (alias amiga1200)
#   SIGNING_PASSWORD      son mot de passe
# La clé de test (app/debug.keystore) passe le relais à la clé de publication (rotation de clé, signature APK v3,
# Android 9 et plus) : le nouvel APK s'installe par-dessus les versions de test, sans désinstaller ni perdre réglages,
# Bibliothèque et sauvegardes. Sans secret, l'APK garde la clé de test ; SIGN_SELFTEST=1 vérifie alors la rotation
# avec une clé jetable, sans toucher à l'APK publié.
#
#   KEYSTORE_B64=… KEYSTORE_PASSWORD=… scripts/sign-apk.sh amiga1200-android.apk
set -euo pipefail
APK=$1
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
SIGNER="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)/apksigner"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

if [ -n "${KEYSTORE_B64:-}" ]; then
  printf '%s' "$KEYSTORE_B64" | base64 -d >"$TMP/release.p12"
  export SIGN_PASS=${KEYSTORE_PASSWORD:?SIGNING_PASSWORD manquant}
  OUT=$APK
  echo "Signature avec la clé de publication"
elif [ "${SIGN_SELFTEST:-}" = 1 ]; then
  export SIGN_PASS=essai-jetable
  keytool -genkeypair -keystore "$TMP/release.p12" -storetype PKCS12 -storepass "$SIGN_PASS" -alias amiga1200 \
    -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=essai" >/dev/null 2>&1
  OUT=$TMP/essai.apk
  echo "Pas de clé de publication : APK laissé avec la clé de test ; essai de la rotation avec une clé jetable"
else
  echo "Pas de clé de publication : APK laissé avec la clé de test"
  exit 0
fi

OLD=(--ks "$ROOT/app/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android)
NEW=(--ks "$TMP/release.p12" --ks-key-alias amiga1200 --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS)
# Lignée : la clé de test atteste la clé de publication (elle ne contient aucune clé privée)
"$SIGNER" rotate --out "$TMP/lineage" --old-signer "${OLD[@]}" --new-signer "${NEW[@]}"
"$SIGNER" sign "${OLD[@]}" --next-signer "${NEW[@]}" --lineage "$TMP/lineage" --rotation-min-sdk-version 28 \
  --out "$TMP/signed.apk" "$APK"
"$SIGNER" verify "$TMP/signed.apk"   # échec : la compilation s'arrête, rien n'est publié
"$SIGNER" verify --verbose --print-certs "$TMP/signed.apk" | grep -E "^Verified using|certificate SHA-256" || true
"$SIGNER" lineage --in "$TMP/signed.apk" --print-certs | grep -E "SHA-256|Signer" || true
cp "$TMP/signed.apk" "$OUT"
