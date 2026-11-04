#!/usr/bin/env python3
"""R48 host parser, provenance and policy negative fixtures (no SDK or network)."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import tempfile
import unittest
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


r48_capture = load('r48_capture')
r48_verify = load('r48_verify')
r48_gate = load('r48_gate')


def fake_png(seed: int) -> bytes:
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    w, h = 320, 480
    raw = (bytes([0]) + bytes([seed, 0, 0, 255]) * w) * h
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


class EvidenceTests(unittest.TestCase):
    def good_output(self):
        out = []
        for name in r48_capture.TESTS:
            out.extend([f'INSTRUMENTATION_STATUS: test={name}',
                        'INSTRUMENTATION_STATUS_CODE: 1',
                        f'INSTRUMENTATION_STATUS: test={name}',
                        'INSTRUMENTATION_STATUS_CODE: 0'])
        out.extend(['OK (4 tests)', 'INSTRUMENTATION_CODE: -1'])
        return '\n'.join(out)

    def test_exact_four_test_roster(self):
        s = r48_capture.summarize(self.good_output())
        self.assertEqual(s['started_tests'], list(r48_capture.TESTS))
        self.assertEqual(s['passed_tests'], list(r48_capture.TESTS))
        self.assertTrue(s['reported_ok'] and s['terminal_success'])
        self.assertFalse(s['raw_logs_retained'])

    def test_missing_test_refused(self):
        s = r48_capture.summarize(self.good_output().replace('realOperationCenterReadOnlyReturn',
                                                             'notAnAllowlistedTest'))
        self.assertNotEqual(len(s['passed_tests']), len(r48_capture.TESTS))

    def test_failure_cannot_be_promoted(self):
        raw = self.good_output() + ('\nINSTRUMENTATION_STATUS: test=realOfflineFiveTabNavigationAndPrivatePreviews'
                                    '\nINSTRUMENTATION_STATUS_CODE: -2')
        s = r48_capture.summarize(raw)
        self.assertIn('realOfflineFiveTabNavigationAndPrivatePreviews', s['failed_tests'])
        self.assertNotIn('realOfflineFiveTabNavigationAndPrivatePreviews', s['passed_tests'])

    def test_no_terminal_code_refused(self):
        s = r48_capture.summarize(self.good_output().replace('INSTRUMENTATION_CODE: -1', ''))
        self.assertFalse(s['terminal_success'])

    def test_no_raw_exception_exposure(self):
        s = r48_capture.summarize(self.good_output() + '\njava.lang.AssertionError: /storage/private')
        self.assertEqual(s['error_categories'], ['assertion'])
        self.assertNotIn('/storage/private', json.dumps(s))

    def make_receipt(self, folder, source='a' * 40, run='1234'):
        records = []
        for index, filename in enumerate(r48_capture.NAMES, start=1):
            raw = fake_png(index)
            (folder / filename).write_bytes(raw)
            records.append(dict(name=filename, width_px=320, height_px=480,
                                sha256=hashlib.sha256(raw).hexdigest()))
        identity = dict(schema_version=1, source_sha=source, package='com.sdvsync',
                        app_is_debug=True, app_apk_sha256='1' * 64,
                        android_test_apk_sha256='2' * 64, installed_base_apk_sha256='1' * 64,
                        debug_signing_certificate_sha256='3' * 64,
                        physical_device_qualified=False, release_qualified=False)
        (folder / 'APK_IDENTITY.json').write_text(json.dumps(identity))
        evidence = dict(schema_version=1, producer='cinderbox-r48-real-activity-hosted-emulator',
                        overall='PASS_REAL_ACTIVITY_HOSTED_EMULATOR',
                        source_sha=source, run_id=run, real_main_activity=True, hosted_emulator_only=True,
                        fresh_no_account_fixture=True, physical_device_qualified=False, talkback_qualified=False,
                        real_steam_qualified=False, real_game_data_qualified=False,
                        release_qualified=False, publication_allowed=False, automatic_game_update=False,
                        test_count=len(r48_capture.TESTS), screenshot_count=len(records),screenshots=records,
                        instrumentation_summary=r48_capture.summarize(self.good_output()))
        (folder / 'R48_REAL_ACTIVITY_EVIDENCE.json').write_text(json.dumps(evidence))
        return evidence

    def test_independent_verifier_accepts_complete_fixture(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.make_receipt(folder)
            out = r48_verify.verify(folder, 'a' * 40, '1234')
            self.assertEqual(out['screenshots_verified'], 5)
            self.assertFalse(out['physical_device_qualified'])

    def test_verifier_refuses_tampered_png(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.make_receipt(folder)
            filename = r48_capture.NAMES[0]
            path = folder / filename
            path.write_bytes(path.read_bytes()[:-16])
            with self.assertRaises((AssertionError, ValueError)):
                r48_verify.verify(folder, 'a' * 40, '1234')

    def test_verifier_refuses_source_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.make_receipt(folder)
            with self.assertRaises(AssertionError):
                r48_verify.verify(folder, 'b' * 40, '1234')

    def test_host_policy_is_fail_closed(self):
        self.assertEqual(r48_gate.check(), [])

    def test_host_policy_deny_is_independent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ('.github/workflows/r48-real-activity-acceptance.yml',
                         'scripts/devtool/r48_emulator_ci.sh',
                         'app/src/androidTest/java/com/sdvsync/qualification/R48RealActivityDeviceTest.kt'):
                (root / name).parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, root / name)
            path = root / 'app/src/main/java/com/sdvsync/MainActivity.kt'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(' '.join((
                'class MainActivity : ComponentActivity()', 'fun SdvSyncNavGraph(',
                'fun MainScreen(', 'startDestination = "login"', 'startDestination = "home"',
                'onContinueOffline = {', 'StardewBottomBar(')))
            path = root / 'app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('const val LIVE_UPDATES_ENABLED: Boolean = false\n'
                            'const val DEVICE_ACCEPTED: Boolean = false')
            path = root / 'docs/security/M3_RELEASE_POLICY.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            allowed = {'release_enabled': False, 'publication_enabled': False,
                       'automatic_device_install': False, 'device_qualification_complete': False,
                       'release_signing_certificate_sha256': None, 'release_keystore_source': None}
            path.write_text(json.dumps(allowed))
            self.assertEqual(r48_gate.check(root), [])
            allowed['release_enabled'] = True
            path.write_text(json.dumps(allowed))
            self.assertIn('release policy enabled: release_enabled', r48_gate.check(root))


if __name__ == '__main__':
    unittest.main()
