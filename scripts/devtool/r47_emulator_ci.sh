#!/usr/bin/env bash
# The ONLY permitted target is the disposable GitHub-hosted Android emulator.
set -euo pipefail
: "${GITHUB_SHA:?}"; : "${GITHUB_RUN_ID:?}"; : "${RUNNER_TEMP:?}"
SERIAL=emulator-5554
OUT="$RUNNER_TEMP/r47-synthetic"
mkdir -p "$OUT"
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo R47_SOURCE_DRIFT >&2; exit 1; }
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || exit 1
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo R47_PHYSICAL_DEVICE_REFUSED >&2; exit 1;
}
shopt -s nullglob
app=(app/build/outputs/apk/debug/*.apk)
test_apk=(app/build/outputs/apk/androidTest/debug/*.apk)
[[ ${#app[@]} -eq 1 && ${#test_apk[@]} -eq 1 ]] || { echo R47_APK_PAIR_REFUSED >&2; exit 1; }
adb -s "$SERIAL" install -r -t "${app[0]}" >/dev/null
adb -s "$SERIAL" install -r -t "${test_apk[0]}" >/dev/null
python3 -B scripts/devtool/r47_capture.py --serial "$SERIAL" --output-dir "$OUT" \
  --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID"
