#!/usr/bin/env bash
# Called inside the disposable API36 emulator-runner action. Preserve every test gate and exit code.
set -euo pipefail
case "${1:-all}" in
  all|suite) check_scope="${1:-all}" ;;
  *) echo "Usage: $0 [all|suite]" >&2; exit 2 ;;
esac
mkdir -p app/build/reports/emulator-diagnostics
capture_emulator_diagnostics() {
  diagnostic_status=$?
  adb logcat -d -v threadtime > app/build/reports/emulator-diagnostics/logcat.txt 2>&1 || true
  adb shell dumpsys activity lastanr > app/build/reports/emulator-diagnostics/last-anr.txt 2>&1 || true
  return "$diagnostic_status"
}
trap capture_emulator_diagnostics EXIT
# A boot-time SystemUI/launcher ANR is already a failed test environment, never dismiss it.
adb logcat -d -v brief > app/build/reports/emulator-diagnostics/boot-logcat.txt
command -v grep >/dev/null
if grep -Eq 'ANR in com\.android\.(systemui|launcher3)([^a-zA-Z0-9_.]|$)' app/build/reports/emulator-diagnostics/boot-logcat.txt; then
  echo "CI emulator boot failed: SystemUI/launcher ANR before M-POS installation" >&2
  exit 1
fi
./gradlew --no-daemon connectedDebugAndroidTest
if [[ "$check_scope" == all ]]; then
  python3 scripts/ci/update-e2e.py
  python3 scripts/ci/native-restore-e2e.py
fi
