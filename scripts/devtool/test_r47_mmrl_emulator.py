"""R47 R5 host-only regression checks for MMRL-LSPosed emulator parity.

They never invoke Gradle, ADB, KVM, emulators, network, or root commands.
"""
from __future__ import annotations
import copy
from pathlib import Path
import unittest
import r47_mmrl_emulator_fix as fix

ROOT=Path(__file__).resolve().parents[2]

class MMRLEmulatorParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=(ROOT/fix.WF).read_text(encoding='utf-8')
        cls.gate=(ROOT/fix.GATE).read_text(encoding='utf-8')

    def test_exact_postcondition_and_pinned_provenance(self):
        fix.check(ROOT)
        self.assertEqual(fix.BASE,'c5e2917de7f59694f8b56b98c0ce80849ce63d80')
        self.assertEqual(fix.TREE,'d86153ee2849fc6656586283fa6d49adcbecbcf4')
        for rel,expected in fix.POST.items():
            self.assertEqual(fix.blob((ROOT/rel).read_bytes()),expected)

    def test_ubuntu_kvm_android16_matches_mmrl_s09(self):
        fix.validate_workflow(self.source)
        for text in ('runs-on: ubuntu-24.04','api-level: 36','profile: pixel_6',
                     'cores: 2','ram-size: 2048M','-gpu swiftshader_indirect'):
            self.assertIn(text,self.source)
        self.assertNotIn('macos-15-intel',self.source)
        self.assertNotIn('pixel_4',self.source)

    def test_refuses_runner_or_avd_configuration_drift(self):
        pairs=[('needs: [host-source]\n    runs-on: ubuntu-24.04', 'needs: [host-source]\n    runs-on: macos-15-intel'),
               ('api-level: 36','api-level: 35'),
               ('target: google_apis','target: default'),
               ('arch: x86_64','arch: arm64-v8a'),
               ('profile: pixel_6','profile: pixel_4'),
               ('cores: 2','cores: 4'),
               ('ram-size: 2048M','ram-size: 1024M'),
               ('-gpu swiftshader_indirect','-gpu host')]
        for old,new in pairs:
            with self.subTest(setting=old):
                with self.assertRaises(AssertionError):
                    fix.validate_workflow(self.source.replace(old,new,1))

    def test_kvm_required_and_checked_before_boot(self):
        for key in ('Enable KVM on Linux hosted runner',
                    'if [[ ! -c /dev/kvm ]]; then',
                    'R47_KVM_DEVICE_UNAVAILABLE',
                    'sudo chmod a+rw /dev/kvm',
                    'R47_KVM_PERMISSION_CHANGE_FAILED',
                    'R47_KVM_NOT_ACCESSIBLE',
                    'R47_KVM_READY'):
            with self.subTest(check=key):
                with self.assertRaises(AssertionError):
                    fix.validate_workflow(self.source.replace(key,'NOT_CONFIGURED',1))

    def test_emulator_success_cannot_bypass_screenshots_or_junit(self):
        for old in ('scripts/devtool/r47_verify.py',
                    ':app:assembleDebug :app:assembleDebugAndroidTest',
                    'if-no-files-found: error',
                    'script: bash scripts/devtool/r47_emulator_ci.sh'):
            with self.subTest(required=old):
                with self.assertRaises(AssertionError):
                    fix.validate_workflow(self.source.replace(old,'DELETED',1))
        self.assertIn('r47_mmrl_emulator_fix.validate_workflow(workflow)',self.gate)
        self.assertIn("test_r47_diag.py' -v",self.source)

    def test_no_application_or_test_changes_from_infrastructure_patch(self):
        self.assertEqual(set(fix.SOURCE),{fix.WF,fix.GATE})
        self.assertEqual(set(fix.POST),{fix.WF,fix.GATE})
        self.assertNotIn('secrets.',self.source)
        self.assertIn('physical_device_qualified',self.source)
        self.assertIn('publication_allowed',self.source)
        self.assertIn('test "$RUNNER_OS" = Linux',self.source)
        self.assertIn('test "$(uname -m)" = x86_64',self.source)

    def test_kvm_preflight_does_not_depend_on_udev_or_fallback(self):
        for forbidden in ('sudo udevadm', '/etc/udev/rules.d/', '-accel off', '-no-accel'):
            self.assertNotIn(forbidden,self.source)
        for marker in ('R47_KVM_DEVICE_UNAVAILABLE', 'R47_KVM_PERMISSION_CHANGE_FAILED',
                       'R47_KVM_NOT_ACCESSIBLE', 'R47_KVM_READY'):
            self.assertIn(marker,self.source)

    def test_checkout_and_dependency_chain_retain_fail_closed_qualification(self):
        self.assertIn('needs: [host-source]',self.source)
        self.assertIn('test "$(git rev-parse HEAD)" = "$GITHUB_SHA"',self.source)
        self.assertIn("'platforms;android-35' 'platforms;android-36'",self.source)
        self.assertIn('if: always()',self.source)
        self.assertIn('--source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID"',self.source)

    def test_transform_refuses_duplicate_or_rebased_application(self):
        # The migration is intentionally not idempotent: reapplying it is a drift error.
        for rel in fix.SOURCE:
            with self.subTest(rel=rel):
                with self.assertRaises(AssertionError):
                    fix.transform(rel,(ROOT/rel).read_text(encoding='utf-8'))

if __name__=='__main__':unittest.main()
