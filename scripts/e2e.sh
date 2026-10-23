#!/usr/bin/env bash
# Autonomous E2E runner (criterion E2E-1). All inputs come from e2e/fixtures/e2e.env.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/e2e/fixtures/e2e.env"
GEN_ENV=""            # set after fixtures: e2e/run/e2e.gen.env
ARTIFACTS="$ROOT/e2e/artifacts"
RUNDIR="$ROOT/e2e/run"

. "$ROOT/scripts/e2e_lib.sh"
e2e_reexec_gitbash "$@"
e2e_python

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/sdk}}"
EMULATOR="${EMULATOR:-$SDK/emulator/emulator.exe}"
ADB="${ADB:-$SDK/platform-tools/adb.exe}"
SERVER_EXE="${SERVER_EXE:-$ROOT/../vrhub-server/bin/vrhub-server-windows-amd64.exe}"
AVD="${AVD:-vrhub_e2e}"

mkdir -p "$ARTIFACTS" "$RUNDIR"
# Never reuse stale pid/ready markers from a previous crashed run (they could
# point at recycled Windows pids or skip the readiness wait entirely).
rm -f "$RUNDIR/.emulator.pid" "$RUNDIR/.server.pid" "$RUNDIR/.listener.pid" \
      "$RUNDIR/.ready" "$RUNDIR/e2e.gen.env"

fail_prereq() {
  echo "E2E PREREQUISITE MISSING: $1" >&2
  echo "  required: ANDROID_HOME (or \$LOCALAPPDATA/Android/sdk) with platform-tools+emulator" >&2
  echo "  required: AVD '$AVD' (create: avdmanager create avd -n $AVD -k 'system-images;android-34;google_apis;x86_64' -d pixel_6)" >&2
  echo "  required: vrhub-server binary at $SERVER_EXE (build the sibling vrhub-server repo, or set SERVER_EXE=/path/to/binary)" >&2
  exit 1
}
[ -x "$ADB" ] || fail_prereq "adb not found under SDK '$SDK'"
[ -x "$EMULATOR" ] || fail_prereq "emulator not found under SDK '$SDK'"
[ -x "$SERVER_EXE" ] || fail_prereq "vrhub-server binary not found"
"$EMULATOR" -list-avds 2>/dev/null | tr -d '\r' | grep -qx "$AVD" || fail_prereq "AVD '$AVD' not created"

# --- load static config ---
e2e_load_env "$ENV_FILE"

kill_pid() {
  [ -f "$1" ] || return 0
  p="$(cat "$1")"
  kill "$p" 2>/dev/null || true
  MSYS_NO_PATHCONS=1 taskkill //F //PID "$p" >/dev/null 2>&1 || true
}

cleanup() {
  kill_pid "$RUNDIR/.emulator.pid"
  kill_pid "$RUNDIR/.server.pid"
  kill_pid "$RUNDIR/.listener.pid"
}
trap cleanup EXIT

# --- fixtures + isolated DB/games (E2E-2) first: it writes e2e/run/e2e.gen.env ---
cd "$ROOT"
"$PY" scripts/e2e_fixtures.py

# --- (re)load env: static config then generated per-run values (E2E-1) ---
GEN_ENV="$RUNDIR/e2e.gen.env"
e2e_load_env "$ENV_FILE" "$GEN_ENV"

# --- control channel (E2E-6): owns server lifecycle, kill + restart on request ---
export SERVER_EXE
"$PY" scripts/e2e_control.py >"$ARTIFACTS/control.log" 2>&1 &
echo $! > "$RUNDIR/.listener.pid"
for i in $(seq 1 40); do
  [ -f "$RUNDIR/.ready" ] && break
  sleep 1
done
[ -f "$RUNDIR/.ready" ] || { echo "E2E-2 FAIL: control channel not ready"; tail -5 "$ARTIFACTS/control.log"; exit 1; }
echo "[e2e] server ready on $E2E_PORT, control on $E2E_CONTROL_PORT"

# --- install/build APKs (fail fast with prerequisites if gradle artifacts absent) ---
APP_APK=$(ls app/build/outputs/apk/dev/debug/*.apk 2>/dev/null | head -1)
TEST_APK=$(ls app/build/outputs/apk/androidTest/dev/debug/*.apk 2>/dev/null | head -1)
if [ -z "$APP_APK" ] || [ -z "$TEST_APK" ]; then
  echo "[e2e] APKs missing: running gradle assemble tasks..."
  ./scripts/gradlew assembleDevDebug assembleDevDebugAndroidTest \
    || { echo "E2E PREREQUISITE MISSING: gradle assembleDevDebug/assembleDevDebugAndroidTest failed"; exit 1; }
  APP_APK=$(ls app/build/outputs/apk/dev/debug/*.apk | head -1)
  TEST_APK=$(ls app/build/outputs/apk/androidTest/dev/debug/*.apk | head -1)
fi

# --- boot headless emulator (E2E-0: adb wait < 120s) ---
booted=0
for i in 1 2 3; do
  "$ADB" devices | grep -q "emulator-5554[[:space:]]*device" && { booted=1; break; }
  sleep 1
done
if [ "$booted" -ne 1 ]; then
  "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >"$ARTIFACTS/emulator.log" 2>&1 &
  echo $! > "$RUNDIR/.emulator.pid"
  for i in $(seq 1 120); do
    if "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then booted=1; break; fi
    sleep 1
  done
fi
[ "$booted" -ne 1 ] && { echo "E2E-0 FAIL: emulator not booted in 120s"; exit 1; }
echo "[e2e] device booted"

"$ADB" install -r -g "$APP_APK" >/dev/null || { echo "install app failed"; exit 1; }
"$ADB" install -r "$TEST_APK" >/dev/null || { echo "install test failed"; exit 1; }
"$ADB" shell appops set com.vrhub.debug MANAGE_EXTERNAL_STORAGE allow >/dev/null

# --- run instrumentation with args from e2e.env (E2E-1) ---
LOG="$ARTIFACTS/logcat.txt"
"$ADB" logcat -c
set +e
"$ADB" shell am instrument -w \
  -e baseUri "http://$E2E_HOST:$E2E_PORT" \
  -e host "$E2E_HOST" \
  -e port "$E2E_PORT" \
  -e password "$(printf '%s' "$E2E_PASSWORD" | base64)" \
  -e passwordPlain "$E2E_PASSWORD" \
  -e release "$E2E_GAME_RELEASE" \
  -e pkg "$E2E_GAME_PKG" \
  -e controlHost "$E2E_HOST" \
  -e controlPort "$E2E_CONTROL_PORT" \
  -e shaA "$E2E_SHA_A" -e shaB "$E2E_SHA_B" -e shaC "$E2E_SHA_C" \
  -e sha7z "$E2E_SEGMENT_SHA" -e segName "$E2E_SEGMENT_NAME" -e segSize "$E2E_SEGMENT_SIZE" \
  -e class com.vrhub.e2e.EndToEndPipelineTest \
  com.vrhub.debug.test/androidx.test.runner.AndroidJUnitRunner > "$ARTIFACTS/instrument.txt" 2>&1
RC=$?
set -e
"$ADB" logcat -d > "$LOG" 2>/dev/null

# am instrument returns 0 even when tests fail: rely on the instrumentation report.
if [ "$RC" -ne 0 ] || grep -qE "FAILURES|Error in |Process crashed|shortMsg" "$ARTIFACTS/instrument.txt"; then
  "$ADB" exec-out screencap -p > "$ARTIFACTS/failure.png" 2>/dev/null
  echo "[e2e] FAIL (rc=$RC) - artifacts: $LOG, failure.png"
  tail -20 "$ARTIFACTS/instrument.txt"
  exit 1
fi

grep -E "OK|FAILURES" "$ARTIFACTS/instrument.txt" | tail -10
echo "[e2e] PASS"
exit 0
