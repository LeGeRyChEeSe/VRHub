#!/usr/bin/env bash
# Reproduces e2e/fixtures/apkbuild/fixed.apk from AndroidManifest.xml.
# Recipe (verified: aapt2 dump badging reports versionCode='1234' /
# versionName='v1.0.0' in BOTH attribute namespaces, which the Go server's
# androidbinary manifest parser needs; aligned single-entry zip, unsigned,
# no resources.arsc - the exact shape the server importer and the harness
# rely on):
#   1. aapt2 link with --version-code/--version-name against the compile SDK
#   2. aapt remove resources.arsc (the manifest-only fallback parser in
#      vrhub-server internal/game/apk.go handles the resulting archive)
# Requires ANDROID_HOME (or $LOCALAPPDATA/Android/sdk) with build-tools + platforms/android-34.
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/sdk}}"
SDK="${SDK//\\//}"
BT="${BT:-$SDK/build-tools/34.0.0}"
JAR="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/e2e/fixtures/apkbuild"
MANIFEST="$OUT/AndroidManifest.xml"

for t in aapt aapt2 zipalign; do
  [ -x "$BT/$t.exe" ] || { echo "missing $t under $BT (set ANDROID_HOME or BT=/path/to/build-tools)"; exit 1; }
done
[ -f "$JAR" ] || { echo "android.jar not found: $JAR"; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$MANIFEST" "$work/AndroidManifest.xml"

cd "$work"
"$BT/aapt2.exe" link -o linked.apk --manifest AndroidManifest.xml -I "$JAR" \
  --version-code 1234 --version-name v1.0.0
"$BT/zipalign.exe" -f -p 4 linked.apk aligned.apk
"$BT/aapt.exe" remove aligned.apk resources.arsc >/dev/null 2>&1 || true
cp aligned.apk "$OUT/fixed.apk"

badging="$("$BT/aapt2.exe" dump badging "$OUT/fixed.apk" | head -1)"
echo "$badging"
case "$badging" in
  *"versionCode='1234'"*"versionName='v1.0.0'"*) ;;
  *) echo "RECIPE CHECK FAILED: badging missing versionCode/versionName: $badging"; exit 1 ;;
esac
echo "fixed.apk rebuilt: $(wc -c < "$OUT/fixed.apk") bytes"
