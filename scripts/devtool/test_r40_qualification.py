"""R40 source-only hostile receipts and JUnit/instrumentation parsing."""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SRC = Path(__file__).with_name('r40_qualification.py')
spec = importlib.util.spec_from_file_location('r40_source', SRC)
mod = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(mod)
S = 'e3dc69842fab247c3ecd36ec85204f2bffe84cfd'
I = dict(source=S, run='41388020171', attempt='1')

class Qualification(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def make_junit(self, text: str):
        folder = self.root/'reports'
        folder.mkdir(exist_ok=True)
        (folder/'TEST-example.xml').write_text(text)
        return folder

    def test_junit_parses_real_testcases(self):
        report = self.make_junit('<testsuite tests="2" errors="0" failures="0" skipped="0"><testcase classname="a" name="x"/><testcase classname="a" name="y"/></testsuite>')
        result = mod.junit(report, **I)
        self.assertEqual(result['tests'], 2)
        self.assertEqual(result['status'], 'PASS')
        self.assertFalse(result['release_qualified'])

    def test_zero_tests_missing_and_inflated_counts_refused(self):
        with self.assertRaises(mod.Refused):mod.junit(self.root, **I)
        report = self.make_junit('<testsuite tests="0" errors="0" failures="0" skipped="0"/>')
        with self.assertRaises(mod.Refused):mod.junit(report, **I)
        report = self.make_junit('<testsuite tests="10" errors="0" failures="0" skipped="0"><testcase classname="a" name="x"/></testsuite>')
        with self.assertRaises(mod.Refused):mod.junit(report, **I)

    def test_junit_failure_and_skipped_refused(self):
        for tag, key in [('failure','failures'), ('error','errors'), ('skipped','skipped')]:
            with self.subTest(tag=tag):
                attrs={'failures':0,'errors':0,'skipped':0}
                attrs[key]=1
                s=f'<testsuite tests="1" failures="{attrs["failures"]}" errors="{attrs["errors"]}" skipped="{attrs["skipped"]}"><testcase classname="x" name="y"><{tag}/></testcase></testsuite>'
                with self.assertRaises(mod.Refused):mod.junit(self.make_junit(s), **I)

    def test_duplicate_junit_testcase_refused(self):
        xml='<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="a" name="x"/><testcase classname="a" name="x"/></testsuite>'
        with self.assertRaises(mod.Refused):mod.junit(self.make_junit(xml), **I)

    def test_instrumentation_pass_and_fail_closed(self):
        f=self.root/'instrument.txt'
        f.write_text('INSTRUMENTATION_STATUS: class=Class\nINSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n')
        self.assertEqual(mod.instrument(f, expected=1, **I)['tests'],1)
        for sample in ['FAILURES!!!', 'INSTRUMENTATION_CODE: 0', 'OK (0 tests)', 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.']:
            with self.subTest(sample=sample):
                f.write_text(sample)
                with self.assertRaises(mod.Refused):mod.instrument(f, expected=1, **I)

    def test_identity_and_path_controls(self):
        with self.assertRaises(mod.Refused):mod.identity('notsha','3','1')
        with self.assertRaises(mod.Refused):mod.identity(S,'0','1')
        with self.assertRaises(mod.Refused):mod.identity(S,'3','0')
        with self.assertRaises(mod.Refused):mod.installed(self.root/'nonexistent.apk',serial='personal-phone',**I)
        f=self.root/'out.json'
        mod.put(f,{'pass':True})
        with self.assertRaises(mod.Refused):mod.put(f,{'pass':True})

    def test_workflow_source_has_enforced_boundaries(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root/'.github/workflows/cinderbox-r40-qualification.yml').read_text()
        import re
        refs = re.findall(r'(?m)^\s*uses: ([^@\s]+)@([^\s]+)', workflow)
        self.assertGreaterEqual(len(refs), 10)
        for action, sha in refs:
            self.assertRegex(sha, r'^[a-f0-9]{40}$')
        for name in ('unit-tests:', 'emulator-debug:', 'github-sign:', 'signed-emulator:', 'remote-verify:'):
            self.assertIn('  '+name, workflow)
        private = workflow.split('  github-sign:',1)[1].split('  signed-emulator:',1)[0]
        public_before = workflow.split('  github-sign:',1)[0]
        public_after = workflow.split('  signed-emulator:',1)[1]
        self.assertIn('environment: cinderbox-release', private)
        self.assertIn('actions/attest@', private)
        self.assertIn('secrets.CINDERBOX_RELEASE_KEYSTORE_B64',private)
        self.assertNotIn('secrets.CINDERBOX_RELEASE_KEYSTORE_B64', public_before+public_after)
        self.assertIn('inputs.reviewed_source_sha == github.sha', workflow)
        self.assertIn('R40_SIGNED_CANDIDATE', workflow)
        self.assertNotIn('gh release create', workflow)
        self.assertNotIn('assembleRelease', public_before)
        self.assertIn('gh attestation verify', public_after)

    def test_merged_receipt_consistency(self):
        base=dict(repository=mod.REPO,source_commit=S,run_id=int(I['run']),attempt=1,status='PASS')
        def write(p,d):
            f=self.root/p
            f.parent.mkdir(parents=True,exist_ok=True)
            f.write_text(json.dumps(d))
        h='a'*64
        write('unit/R40_JUNIT.json',dict(base,schema_version=1,kind='r40-parsed-junit',tests=2,failures=0,errors=0,skipped=0,report_files=1))
        (self.root/'unit/TEST-r40.xml').write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="a" name="x"/><testcase classname="a" name="y"/></testsuite>')
        write('emulator/R40_EMULATOR.json',dict(base,schema_version=1,kind='r40-emulator-instrumentation',tests=1,device_scope='github-ephemeral-android-emulator'))
        f=self.root/'signed/CinderboxCompanion-release-candidate.apk'
        f.parent.mkdir(exist_ok=True)
        f.write_bytes(b'signed APK placeholder for receipt test')
        digest=mod.sha256(f)
        write('signed/M21_RELEASE_CANDIDATE.json', dict(schema_version=1,producer='cinderbox-m21-manual-release-candidate',variant='release',package=mod.PKG,installed=False,automatic_publication=False,version_code=14,repository=mod.REPO,source_commit=S,workflow_run_id=int(I['run']),release_signed=True,signature_verified=True,publication_allowed=False,release_qualified=False,apk_sha256=digest,release_certificate_sha256=h,debug_certificate_sha256='b'*64))
        (f.parent/'SHA256SUMS').write_text(f'{digest}  {f.name}\n')
        write('signed-emulator/R40_SIGNED_EMULATOR.json',dict(base,schema_version=1,kind='r40-emulator-signed-candidate-install',apk_sha256=digest,device_scope='github-ephemeral-android-emulator'))
        self.assertEqual(mod.finalize(self.root,**I)['overall'],'PASS_R40_HOSTED_CANDIDATE_EVIDENCE')
        a=json.loads((self.root/'signed-emulator/R40_SIGNED_EMULATOR.json').read_text())
        a['apk_sha256']='0'*64
        write('signed-emulator/R40_SIGNED_EMULATOR.json',a)
        with self.assertRaises(mod.Refused):mod.finalize(self.root,**I)
        # An altered or absent original XML report cannot inherit a PASS receipt.
        self.root.joinpath('unit/TEST-r40.xml').unlink()
        with self.assertRaises(mod.Refused):mod.finalize(self.root,**I)

if __name__ == '__main__':unittest.main()
