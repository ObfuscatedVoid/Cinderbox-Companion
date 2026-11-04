#!/usr/bin/env bash
# Run ONLY against the disposable GitHub Actions API36 emulator.
set -euo pipefail
: "${GITHUB_SHA:?}"; : "${GITHUB_RUN_ID:?}"; : "${RUNNER_TEMP:?}"
SERIAL=emulator-5554
OUT="$RUNNER_TEMP/r48-real-activity"
mkdir -p "$OUT"
[[ "$RUNNER_OS" == Linux && "$(uname -m)" == x86_64 ]] || { echo R48_RUNNER_REFUSED >&2; exit 1; }
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo R48_SOURCE_REFUSED >&2; exit 1; }
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || { echo R48_DEVICE_UNAVAILABLE >&2; exit 1; }
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
    echo R48_PHYSICAL_DEVICE_REFUSED >&2; exit 1;
}
[[ -z "$(adb -s "$SERIAL" shell pm path com.sdvsync 2>/dev/null | tr -d '\r')" ]] || {
    echo R48_NONFRESH_APP_REFUSED >&2; exit 1;
}
# 640px / 2 density = 320dp; explicit 160% dynamic type, real window insets.
adb -s "$SERIAL" shell wm size 640x1440 >/dev/null
adb -s "$SERIAL" shell wm density 320 >/dev/null
adb -s "$SERIAL" shell settings put system font_scale 1.6 >/dev/null
[[ "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || {
    echo R48_API36_REQUIRED >&2; exit 1;
}
[[ "$(adb -s "$SERIAL" shell wm size | tr -d '\r')" == *"Override size: 640x1440"* ]] || {
    echo R48_WINDOW_SIZE_NOT_APPLIED >&2; exit 1;
}
[[ "$(adb -s "$SERIAL" shell wm density | tr -d '\r')" == *"Override density: 320"* ]] || {
    echo R48_WINDOW_DENSITY_NOT_APPLIED >&2; exit 1;
}
[[ "$(adb -s "$SERIAL" shell settings get system font_scale | tr -d '\r')" == 1.6 ]] || {
    echo R48_FONT_SCALE_NOT_APPLIED >&2; exit 1;
}
# This fixture has no Steam account and no real files. Disable available radios.
adb -s "$SERIAL" shell svc wifi disable >/dev/null
adb -s "$SERIAL" shell svc data disable >/dev/null
shopt -s nullglob
app=(app/build/outputs/apk/debug/*.apk)
test_apk=(app/build/outputs/apk/androidTest/debug/*.apk)
[[ ${#app[@]} == 1 && ${#test_apk[@]} == 1 ]] || { echo R48_APK_PAIR_REFUSED >&2; exit 1; }
APP_HASH="$(sha256sum "${app[0]}" | cut -d' ' -f1)"
TEST_HASH="$(sha256sum "${test_apk[0]}" | cut -d' ' -f1)"
APK_SIGNER="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?}}/build-tools/35.0.0/apksigner"
[[ -x "$APK_SIGNER" ]] || { echo R48_APKSIGNER_MISSING >&2; exit 1; }
CERT_SHA="$($APK_SIGNER verify --print-certs "${app[0]}" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
[[ "$CERT_SHA" =~ ^[a-fA-F0-9]{64}$ ]] || { echo R48_SIGNATURE_REFUSED >&2; exit 1; }
adb -s "$SERIAL" install -r -t "${app[0]}" >/dev/null
adb -s "$SERIAL" install -r -t "${test_apk[0]}" >/dev/null
INSTALLED_PATH="$(adb -s "$SERIAL" shell pm path com.sdvsync | tr -d '\r' | sed -n 's/^package:\(.*base.apk\)$/\1/p' | head -1)"
[[ "$INSTALLED_PATH" == /data/app/*/base.apk ]] || { echo R48_INSTALLED_PACKAGE_REFUSED >&2; exit 1; }
INSTALLED_HASH="$(adb -s "$SERIAL" shell sha256sum "$INSTALLED_PATH" | tr -d '\r' | cut -d' ' -f1)"
[[ "$INSTALLED_HASH" == "$APP_HASH" ]] || { echo R48_INSTALLED_BYTES_MISMATCH >&2; exit 1; }
python3 - "$OUT/APK_IDENTITY.json" "$GITHUB_SHA" "$APP_HASH" "$TEST_HASH" "$INSTALLED_HASH" "$CERT_SHA" <<'PYID'
import json,sys
f,source,apk,test,installed,cert=sys.argv[1:]
with open(f,'w',encoding='utf-8') as w:
    json.dump(dict(schema_version=1,source_sha=source,package='com.sdvsync',
                   app_apk_sha256=apk,android_test_apk_sha256=test,
                   installed_base_apk_sha256=installed,debug_signing_certificate_sha256=cert.lower(),
                   app_is_debug=True,release_qualified=False,physical_device_qualified=False),w,sort_keys=True,indent=2)
    w.write('\n')
PYID
python3 -B scripts/devtool/r48_capture.py --serial "$SERIAL" --output-dir "$OUT" \
  --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID"
