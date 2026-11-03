#!/usr/bin/env bash
# Called inside the disposable API36 emulator-runner action. Preserve every test gate and exit code.
set -euo pipefail
mkdir -p app/build/reports/emulator-diagnostics
capture_emulator_diagnostics() {
  diagnostic_status=$?
  adb logcat -d -v threadtime > app/build/reports/emulator-diagnostics/logcat.txt 2>&1 || true
  adb shell dumpsys activity lastanr > app/build/reports/emulator-diagnostics/last-anr.txt 2>&1 || true
  return "$diagnostic_status"
}
trap capture_emulator_diagnostics EXIT
./gradlew --no-daemon connectedDebugAndroidTest
python3 scripts/ci/update-e2e.py
python3 scripts/ci/native-restore-e2e.py
