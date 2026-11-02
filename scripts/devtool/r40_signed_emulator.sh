#!/usr/bin/env bash
# Install previously signed and attested release candidate on a disposable emulator.
set -euo pipefail
: "${RUNNER_TEMP:?}"
: "${GITHUB_SHA:?}"
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_RUN_ATTEMPT:?}"
SERIAL=emulator-5554
APK="$RUNNER_TEMP/r40-download-signed/CinderboxCompanion-release-candidate.apk"
test -s "$APK" && test ! -L "$APK"
adb -s "$SERIAL" get-state | grep -qx device
adb -s "$SERIAL" install -r "$APK" >/dev/null
python3 scripts/devtool/r40_qualification.py installed \
  --apk "$APK" --serial "$SERIAL" \
  --output "$RUNNER_TEMP/r40-signed-emulator/R40_SIGNED_EMULATOR.json" \
  --source "$GITHUB_SHA" --run "$GITHUB_RUN_ID" --attempt "$GITHUB_RUN_ATTEMPT"
