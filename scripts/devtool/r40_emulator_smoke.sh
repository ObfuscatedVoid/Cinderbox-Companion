#!/usr/bin/env bash
# Only the ephemeral GitHub emulator. No physical device, user data or Steam account.
set -euo pipefail
: "${RUNNER_TEMP:?}"
: "${GITHUB_SHA:?}"
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_RUN_ATTEMPT:?}"
SERIAL=emulator-5554
mkdir -p "$RUNNER_TEMP/r40-emulator"
shopt -s nullglob
apps=(app/build/outputs/apk/debug/*.apk)
tests=(app/build/outputs/apk/androidTest/debug/*.apk)
test "${#apps[@]}" -eq 1 && test "${#tests[@]}" -eq 1
adb -s "$SERIAL" get-state | grep -qx device
adb -s "$SERIAL" install -r -t "${apps[0]}" >/dev/null
adb -s "$SERIAL" install -r -t "${tests[0]}" >/dev/null
adb -s "$SERIAL" shell am instrument -w \
  -e class com.sdvsync.qualification.PlatformStorageAliasDeviceTest \
  com.sdvsync.test/androidx.test.runner.AndroidJUnitRunner \
  > "$RUNNER_TEMP/r40-emulator/instrumentation-output.txt"
python3 scripts/devtool/r40_qualification.py instrument \
  --input "$RUNNER_TEMP/r40-emulator/instrumentation-output.txt" \
  --expected 1 --output "$RUNNER_TEMP/r40-emulator/R40_EMULATOR.json" \
  --source "$GITHUB_SHA" --run "$GITHUB_RUN_ID" --attempt "$GITHUB_RUN_ATTEMPT"
# Raw device output never leaves the ephemeral runner.
rm -f -- "$RUNNER_TEMP/r40-emulator/instrumentation-output.txt"
