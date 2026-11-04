#!/usr/bin/env python3
"""Host-only R48A guard: real Activity acceptance remains isolated and non-release."""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def check(root: Path = ROOT) -> list[str]:
    errors = []
    policy = json.loads((root / 'docs/security/M3_RELEASE_POLICY.json').read_text())
    for key in ('release_enabled', 'publication_enabled', 'automatic_device_install',
                'device_qualification_complete'):
        if policy.get(key) is not False:
            errors.append('release policy enabled: ' + key)
    for key in ('release_signing_certificate_sha256', 'release_keystore_source'):
        if policy.get(key) is not None:
            errors.append('unexpected release signing state: ' + key)
    coordinator = (root / 'app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt').read_text()
    for line in ('const val LIVE_UPDATES_ENABLED: Boolean = false',
                 'const val DEVICE_ACCEPTED: Boolean = false'):
        if line not in coordinator:
            errors.append('live update admission modified')
    activity = (root / 'app/src/main/java/com/sdvsync/MainActivity.kt').read_text()
    for expected in ('class MainActivity : ComponentActivity()',
                     'fun SdvSyncNavGraph(', 'fun MainScreen(',
                     'startDestination = "login"', 'startDestination = "home"',
                     'onContinueOffline = {', 'StardewBottomBar('):
        if expected not in activity:
            errors.append('R48 real-Activity navigation prerequisite missing: ' + expected)
    test = (root / 'app/src/androidTest/java/com/sdvsync/qualification/R48RealActivityDeviceTest.kt').read_text()
    for required in ('createAndroidComposeRule<MainActivity>()', 'realOfflineFiveTabNavigationAndPrivatePreviews',
                     'realHomeShortcutsAndNestedBackNavigation', 'realPreLoginSettingsBackRequiresNoSteam',
                     'realOperationCenterReadOnlyReturn', 'captureToImage()'):
        if required not in test:
            errors.append('R48 test prerequisite missing: ' + required)
    workflow = (root / '.github/workflows/r48-real-activity-acceptance.yml').read_text()
    for required in ('api-level: 36', 'arch: x86_64', 'r48_emulator_ci.sh', 'r48_verify.py',
                     'permissions:', 'contents: read', 'if: always()'):
        if required not in workflow:
            errors.append('R48 workflow gate missing: ' + required)
    script = (root / 'scripts/devtool/r48_emulator_ci.sh').read_text()
    for required in ('ro.kernel.qemu', 'emulator-5554', 'font_scale 1.6',
                     'wm size 640x1440', 'APK_IDENTITY.json', 'R48_INSTALLED_BYTES_MISMATCH'):
        if required not in script:
            errors.append('R48 emulator safety gate missing: ' + required)
    return errors


def main() -> int:
    errors = check()
    if errors:
        for reason in errors:
            print('R48_GATE_REFUSED: ' + reason)
        return 1
    print('R48_HOST_SOURCE_AND_SAFETY_PASS')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
