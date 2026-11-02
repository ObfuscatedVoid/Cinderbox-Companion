"""R41 source-only regression tests: fail closed without user installation consent."""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MOD_PATH = Path(__file__).with_name('r41_release_device.py')
spec = importlib.util.spec_from_file_location('r41_release_device', MOD_PATH)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class R41Regression(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)

    def fixture(self):
        folder = self.path / 'kit'
        folder.mkdir(exist_ok=True)
        (folder / mod.APK_NAME).write_bytes(b'fake-APK-for-source-only-test')
        h = mod.sha256(folder / mod.APK_NAME)
        candidate = dict(schema_version=1, producer='cinderbox-m21-manual-release-candidate',
                         repository=mod.REPO, source_commit=mod.SOURCE,
                         package=mod.PKG, variant='release', workflow_run_id=41502904211,
                         release_signed=True, signature_verified=True, debuggable=False,
                         installed=False, device_qualified=False, release_qualified=False,
                         publication_allowed=False, automatic_publication=False,
                         release_certificate_sha256=mod.RELEASE_CERT,
                         debug_certificate_sha256='4'*64, apk_sha256=h,
                         version_code=14, version_name='0.1.1')
        accepted = dict(schema_version=1, kind='r40-verified-signed-build',
                        repository=mod.REPO, source_commit=mod.SOURCE,
                        run_id=41502904211, overall='PASS_R40_SIGNING_AND_REMOTE_ATTESTATION_ONLY',
                        release_apk_sha256=h, signing_certificate_sha256=mod.RELEASE_CERT,
                        physical_device_qualified=False, emulator_instrumentation_qualified=False,
                        publication_allowed=False, release_qualified=False,
                        junit_reports=37, junit_tests=221)
        (folder / 'M21_RELEASE_CANDIDATE.json').write_text(json.dumps(candidate))
        (folder / 'R40_SIGNED_BUILD_ACCEPTANCE.json').write_text(json.dumps(accepted))
        (folder / 'SHA256SUMS').write_text(f'{h}  {mod.APK_NAME}\n')
        return folder

    def test_receipts_accept_only_exact_immutable_apk(self):
        folder = self.fixture()
        result = mod.verify_kit(folder, 41502904211, mod.SOURCE, online=False)
        self.assertEqual(result['certificate_sha256'], mod.RELEASE_CERT)
        (folder / mod.APK_NAME).write_bytes(b'different')
        with self.assertRaisesRegex(mod.Refused, 'R41_APK_SUMS_MISMATCH'):
            mod.verify_kit(folder, 41502904211, mod.SOURCE, online=False)

    def test_wrong_cert_and_published_state_refused(self):
        folder = self.fixture()
        f = folder / 'M21_RELEASE_CANDIDATE.json'
        obj = json.loads(f.read_text())
        obj['release_certificate_sha256'] = 'a'*64
        f.write_text(json.dumps(obj))
        with self.assertRaisesRegex(mod.Refused, 'R41_SIGNED_APK_OR_CERTIFICATE_MISMATCH'):
            mod.verify_kit(folder, 41502904211, mod.SOURCE, online=False)
        obj['release_certificate_sha256'] = mod.RELEASE_CERT
        obj['publication_allowed'] = True
        f.write_text(json.dumps(obj))
        with self.assertRaisesRegex(mod.Refused, 'R41_RELEASE_RECEIPT_NOT_CANDIDATE'):
            mod.verify_kit(folder, 41502904211, mod.SOURCE, online=False)

    def test_approval_bound_to_exact_plan_context(self):
        p = dict(source_commit=mod.SOURCE, apk_sha256='a'*64,
                 r40_run_id=41502904211, r41_run_id=12345, r41_source_commit='e'*40,
                 serial='localhost:5555', device_identity_sha256='b'*64,
                 created_at_utc='2026-11-02T18:00:00+00:00', nonce='c'*32)
        token = mod.approval(p)
        self.assertRegex(token, r'^R41_INSTALL_[0-9a-f]{24}$')
        for k, v in [('serial','emulator-5554'),('apk_sha256','d'*64),
                     ('nonce','d'*32),('device_identity_sha256','f'*64),
                     ('r41_run_id',12346),('r41_source_commit','f'*40)]:
            changed = dict(p, **{k:v})
            self.assertNotEqual(mod.approval(changed), token)

    def test_plan_existing_apps_cannot_get_approval(self):
        folder = self.fixture()
        output = self.path / 'plan.json'
        fake = dict(apk_sha256=mod.sha256(folder/mod.APK_NAME), certificate_sha256=mod.RELEASE_CERT,
                    source_commit=mod.SOURCE, r40_run_id=41502904211, attestation_verified=True)
        args = type('Args', (), dict(kit=folder, r40_run_id=41502904211, r41_run_id=321,
                                    source=mod.SOURCE, serial='localhost:5555', output=output))()
        with patch.object(mod,'verify_kit',return_value=fake), patch.object(mod,'check_host',return_value=mod.SOURCE), \
             patch.object(mod,'command',return_value=mod.SOURCE), \
             patch.object(mod,'device_state',return_value=dict(serial='localhost:5555',
                 device_identity_sha256='b'*64, installed={mod.PKG:True,mod.TEST_PKG:True})), \
             patch.object(mod,'adb',side_effect=AssertionError('plan must not perform adb install')):
            mod.plan(args)
        self.assertEqual(mod.load(output)['overall'], 'BLOCKED_EXISTING_INSTALLATION')

    def test_install_missing_approval_refused_before_any_process(self):
        args = type('Args', (), dict(plan=self.path/'plan.json', serial='localhost:5555',
            source=mod.SOURCE, r40_run_id=41502904211, r41_run_id=300,
            approve_install='NO', kit=self.path, output=self.path/'result.json', approve_launch=False))()
        p = dict(kind='r41-device-install-plan', overall='READY_FOR_MANUAL_APPROVAL',
                 installation_approved=False, serial=args.serial,
                 source_commit=mod.SOURCE, r40_run_id=args.r40_run_id, r41_run_id=args.r41_run_id,
                 r41_source_commit='e'*40,
                 certificate_sha256=mod.RELEASE_CERT, nonce='e'*32,
                 device_identity_sha256='a'*64, installed_packages={mod.PKG:False,mod.TEST_PKG:False})
        args.plan.write_text(json.dumps(p))
        with patch.object(mod,'command',side_effect=AssertionError('no external commands without approval')):
            with self.assertRaisesRegex(mod.Refused,'R41_EXPLICIT_INSTALL_APPROVAL_REQUIRED'):
                mod.install(args)

    def test_new_workflow_never_touches_android_or_signing(self):
        source = (MOD_PATH.parents[2]/'.github/workflows/cinderbox-r41-release-device.yml').read_text()
        self.assertIn('workflow_dispatch:',source)
        self.assertIn("inputs.confirm == 'R41_PREPARE_ONLY'",source)
        self.assertIn('r40-verified-signed-build',source)
        self.assertIn('r41-verified-release-device-kit',source)
        self.assertNotIn('adb ',source)
        self.assertNotIn('su -c',source)
        self.assertNotIn('gh release create',source)
        self.assertNotIn('secrets.',source)


if __name__ == '__main__':
    unittest.main()
