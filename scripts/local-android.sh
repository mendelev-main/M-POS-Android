#!/bin/bash
# Project-local Android 16 tablet. No signing credentials or production device access.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
local_root="${MPOS_ANDROID_LOCAL_ROOT:-$(cd "$repo/.." && pwd)/android-local}"
export ANDROID_HOME="$local_root/sdk" ANDROID_SDK_ROOT="$local_root/sdk"
export ANDROID_USER_HOME="$local_root/android-user" ANDROID_AVD_HOME="$local_root/avd"
export GRADLE_USER_HOME="$local_root/gradle"
unset ANDROID_PREFS_ROOT ANDROID_SDK_HOME
export JAVA_HOME="${MPOS_JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_SERIAL=emulator-5560
adb="$ANDROID_HOME/platform-tools/adb"
avd=M_POS_API36
cd "$repo"
require_sdk() {
  if [[ ! -x "$adb" || ! -x "$ANDROID_HOME/emulator/emulator" ]]; then
    echo "Android SDK is incomplete at $ANDROID_HOME. See docs/LOCAL-ANDROID.md." >&2
    exit 1
  fi
}
discover_device() {
  local candidate actual
  while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    actual="$("$adb" -s "$candidate" emu avd name 2>/dev/null | head -1 | tr -d '\r')" || continue
    if [[ "$actual" == "$avd" ]]; then export ANDROID_SERIAL="$candidate"; return; fi
  done < <("$adb" devices | /usr/bin/awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" {print $1}')
}
check_device() {
  local actual
  actual="$($adb -s "$ANDROID_SERIAL" emu avd name 2>/dev/null | head -1 | tr -d '\r')" || return 1
  [[ "$actual" == "$avd" ]] || { echo "Port 5560 belongs to another AVD; refusing to use it." >&2; exit 1; }
}
start() {
  require_sdk
  "$adb" start-server >/dev/null
  discover_device
  if [[ "$($adb -s "$ANDROID_SERIAL" get-state 2>/dev/null || true)" == device ]]; then
    check_device
  else
    mkdir -p "$local_root/logs"
    local emulator_flags=(-avd "$avd" -port 5560 -no-snapshot-load -no-boot-anim -gpu auto)
    if [[ "${MPOS_ANDROID_HEADLESS:-0}" == 1 ]]; then emulator_flags+=(-no-window); fi
    nohup "$ANDROID_HOME/emulator/emulator" "${emulator_flags[@]}" \
      > "$local_root/logs/emulator.log" 2>&1 < /dev/null &
    echo "$!" > "$local_root/emulator.pid"
  fi
  for ((attempt=0; attempt<120; attempt++)); do
    if [[ -f "$local_root/logs/emulator.log" ]] && /usr/bin/grep -Eq 'Incompatible processor|requires the following features|PANIC:|HV_ERROR' "$local_root/logs/emulator.log"; then
      echo "Emulator cannot start in this process environment. Run the macOS launcher from Finder/Terminal; see docs/LOCAL-ANDROID.md." >&2
      exit 1
    fi
    if [[ "$($adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
      check_device
      [[ "$($adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || exit 1
      "$adb" -s "$ANDROID_SERIAL" shell input keyevent 82
      echo "Android 16 tablet ready: $ANDROID_SERIAL"
      return
    fi
    sleep 2
  done
  echo "Emulator boot timed out. See $local_root/logs/emulator.log" >&2
  exit 1
}
gradle() {
  if [[ -x "$local_root/tools/gradle-8.13/bin/gradle" ]]; then
    /bin/bash "$local_root/tools/gradle-8.13/bin/gradle" --no-daemon -Dorg.gradle.vfs.watch=false "$@"
  else
    /bin/bash ./gradlew --no-daemon -Dorg.gradle.vfs.watch=false "$@"
  fi
}
install_app() {
  "$adb" -s "$ANDROID_SERIAL" install -r "$repo/app/build/outputs/apk/debug/app-debug.apk"
  "$adb" -s "$ANDROID_SERIAL" shell am start -W -n com.mendelev.mpos.debug/com.mendelev.mpos.MainActivity
}
command="${1:-status}"
if [[ $# -gt 0 ]]; then shift; fi
case "$command" in
  status) require_sdk; "$adb" devices -l; "$ANDROID_HOME/emulator/emulator" -list-avds ;;
  start) start ;;
  build) gradle testDebugUnitTest lintDebug assembleDebug "$@" ;;
  run) start; gradle assembleDebug; install_app ;;
  launch) start; install_app ;;
  test) start; gradle connectedDebugAndroidTest "$@" ;;
  stop)
    require_sdk; discover_device; check_device; "$adb" -s "$ANDROID_SERIAL" emu kill
    for ((attempt=0; attempt<15; attempt++)); do
      if [[ "$("$adb" -s "$ANDROID_SERIAL" get-state 2>/dev/null || true)" != device ]]; then break; fi
      sleep 1
    done
    ;;
  *) echo "Usage: $0 {status|start|build|run|launch|test|stop}" >&2; exit 2 ;;
esac
