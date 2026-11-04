"""Source-only R47 R6 KVM preflight regression; never touches /dev/kvm.

KVM paths in extracted shell blocks are replaced with safe stand-ins before
execution. No root, emulator, Android, Gradle, network, or hardware access.
"""
from __future__ import annotations
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
import r47_r6_kvm_fix as fix

ROOT=Path(__file__).resolve().parents[2]


def preflight() -> str:
    source=(ROOT/fix.WF).read_text(encoding='utf-8')
    begin=source.index('      - name: Enable KVM on Linux hosted runner\n')
    end=source.index('      - name: Run 11 offline synthetic UI cases',begin)
    lines=source[begin:end].splitlines()
    index=next(i for i,line in enumerate(lines) if line=='        run: |')
    return '\n'.join(line[10:] for line in lines[index+1:] if line.startswith('          '))+'\n'


class KvmHostContracts(unittest.TestCase):
    def test_exact_pinned_source_and_immutable_postconditions(self):
        fix.check(ROOT)
        self.assertEqual(fix.BASE,'b19df0062f4df9815b9c40a84eb9fbcd35c995a4')
        self.assertEqual(fix.TREE,'f8dba28b744de132c5bbadb27b98e4b782b387f3')
        self.assertEqual(set(fix.SOURCE),{fix.WF,fix.HELPER,fix.TEST,fix.DOC})

    def test_kvm_preflight_shell_is_syntactically_valid(self):
        proc=subprocess.run(['bash','-n'],input=preflight(),capture_output=True,text=True,timeout=10)
        self.assertEqual(proc.returncode,0,proc.stderr)

    def test_preflight_accepts_existing_readable_writable_char_device(self):
        # /dev/null is a harmless char-device stand-in. The short-circuit
        # leaves the chmod branch unexecuted; no system configuration changes.
        cmd=preflight().replace('/dev/kvm','/dev/null')
        self.assertNotIn('udevadm',cmd)
        self.assertNotIn('sudo chmod a+rw /dev/null ||',cmd.split('if [[ ! -r /dev/null || ! -w /dev/null ]]')[0])
        proc=subprocess.run(['bash','-c',cmd],capture_output=True,text=True,timeout=10)
        self.assertEqual(proc.returncode,0,proc.stderr)
        self.assertIn('R47_KVM_READY',proc.stdout)

    def test_preflight_refuses_missing_character_device(self):
        with tempfile.TemporaryDirectory() as tmp:
            absent=str(Path(tmp)/'absent-kvm')
            cmd=preflight().replace('/dev/kvm',absent)
            proc=subprocess.run(['bash','-c',cmd],capture_output=True,text=True,timeout=10)
            self.assertNotEqual(proc.returncode,0)
            self.assertIn('R47_KVM_DEVICE_UNAVAILABLE',proc.stderr)
            self.assertNotIn('R47_KVM_READY',proc.stdout)

    def test_no_udev_dependency_and_no_software_fallback(self):
        block=preflight()
        self.assertNotIn('udevadm',block)
        self.assertNotIn('/etc/udev',block)
        self.assertNotIn('-accel off',block)
        self.assertNotIn('-no-accel',block)
        self.assertIn('sudo chmod a+rw /dev/kvm',block)
        self.assertIn('if [[ ! -c /dev/kvm ]]',block)
        self.assertIn('if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]',block)

    def test_keeps_api36_kvm_and_existing_evidence_gate(self):
        import r47_mmrl_emulator_fix
        workflow=(ROOT/fix.WF).read_text()
        r47_mmrl_emulator_fix.validate_workflow(workflow)
        for required in ('runs-on: ubuntu-24.04','api-level: 36','profile: pixel_6',
                         'cores: 2','ram-size: 2048M','scripts/devtool/r47_verify.py',
                         'if: always()','--source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID"'):
            self.assertIn(required,workflow)
        self.assertIn('R47_KVM_READY',workflow)
        self.assertIn('without a udev daemon',(ROOT/fix.DOC).read_text())
        self.assertIn("test_r47_r6_kvm.py' -v",workflow)

    def test_removal_of_any_required_marker_is_rejected(self):
        import r47_mmrl_emulator_fix
        workflow=(ROOT/fix.WF).read_text()
        for marker in ('R47_KVM_DEVICE_UNAVAILABLE','R47_KVM_PERMISSION_CHANGE_FAILED',
                       'R47_KVM_NOT_ACCESSIBLE','sudo chmod a+rw /dev/kvm',
                       'echo R47_KVM_READY'):
            with self.subTest(marker=marker),self.assertRaises(AssertionError):
                r47_mmrl_emulator_fix.validate_workflow(workflow.replace(marker,'REMOVED',1))

if __name__=='__main__':unittest.main()
