#!/usr/bin/env bash
set -euo pipefail
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
sdk_manager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$sdk_manager" ]; then sdk_manager="$(command -v sdkmanager)"; fi
yes | "$sdk_manager" --licenses >/dev/null || true
"$sdk_manager" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
