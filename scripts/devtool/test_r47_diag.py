"""Host-only R47 privacy, taxonomy and failure-evidence regression tests."""
from pathlib import Path
import importlib.util
import json
import re
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import r47_diag
import r47_capture


class R47SanitizedDiagnostics(unittest.TestCase):
    def test_recognizes_failed_and_successful_known_cases(self):
        a,b=r47_diag.ALLOWED_TESTS[:2]
        sample=(f'INSTRUMENTATION_STATUS: test={a}\n'
                'INSTRUMENTATION_STATUS_CODE: 1\n'
                f'INSTRUMENTATION_STATUS: test={a}\n'
                'INSTRUMENTATION_STATUS_CODE: 0\n'
                f'INSTRUMENTATION_STATUS: test={b}\n'
                'INSTRUMENTATION_STATUS_CODE: 1\n'
                f'INSTRUMENTATION_STATUS: test={b}\n'
                'INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: PRIVATE_SECRET\n'
                'INSTRUMENTATION_STATUS_CODE: -2\n'
                'INSTRUMENTATION_CODE: -1\n')
        result=r47_diag.summarize_instrumentation(sample)
        self.assertEqual(result['started_tests'],[a,b])
        self.assertEqual(result['passed_tests'],[a])
        self.assertEqual(result['failed_tests'],[b])
        self.assertTrue(result['terminal_success_code'])
        self.assertEqual(result['error_categories'],['assertion'])
        self.assertFalse(result['reported_ok_11'])
        self.assertNotIn('PRIVATE_SECRET',json.dumps(result))

    def test_unknown_test_identifiers_and_raw_paths_are_never_reported(self):
        private='secret@example.com /private/steam-id-12345'
        output=('INSTRUMENTATION_STATUS: test='+private+'\n'
                'INSTRUMENTATION_STATUS: stack='+private+'\n'
                'INSTRUMENTATION_STATUS_CODE: -2\n')
        record=r47_diag.summarize_instrumentation(output)
        serialized=json.dumps(record)
        self.assertNotIn('secret@example',serialized)
        self.assertNotIn('/private/',serialized)
        self.assertEqual([],record['failed_tests'])
        self.assertFalse(record['raw_output_retained'])

    def test_empty_and_malformed_outputs_do_not_claim_success(self):
        for sample in ('','OK (11 tests)','INSTRUMENTATION_CODE: -1\n',
                       'FAILURES!!!\nINSTRUMENTATION_CODE: 0\n'):
            record=r47_diag.summarize_instrumentation(sample)
            self.assertFalse(record['reported_ok_11'] and record['terminal_success_code'])
        self.assertFalse(r47_capture.parse_result('FAILURES!!!\nINSTRUMENTATION_CODE: 0\n'))

    def test_pass_diagnostic_cannot_promote_failing_gate(self):
        sample=('INSTRUMENTATION_STATUS: test=earthyGame320Font160\n'
                'INSTRUMENTATION_STATUS_CODE: -2\n'
                'OK (11 tests)\nINSTRUMENTATION_CODE: -1\n')
        self.assertTrue(r47_capture.parse_result(sample))  # existing legacy parser alone
        record=r47_diag.summarize_instrumentation(sample)
        self.assertEqual(record['failed_tests'],['earthyGame320Font160'])
        source=Path(r47_capture.__file__).read_text()
        self.assertIn("or summary['failed_tests']",source)

    def test_emulator_failure_receipt_contains_only_fixed_diagnostic_schema(self):
        src=Path(r47_capture.__file__).read_text()
        self.assertIn("result['instrumentation_summary']=summary",src)
        self.assertIn('summarize_instrumentation(',src)
        self.assertNotIn("result['raw_instrumentation']",src)

    def test_failed_runner_persists_only_sanitized_per_test_summary(self):
        with tempfile.TemporaryDirectory() as root:
            out=Path(root)
            source='a'*40
            failed='earthySaves320Font160'
            trace='sensitive@example.com /storage/emulated/0/private/game-files'
            output=(f'INSTRUMENTATION_STATUS: test={failed}\n'
                    'INSTRUMENTATION_STATUS_CODE: 1\n'
                    f'INSTRUMENTATION_STATUS: test={failed}\n'
                    f'INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: {trace}\n'
                    'INSTRUMENTATION_STATUS_CODE: -2\n'
                    'INSTRUMENTATION_CODE: 0\n').encode()
            def fake_run(args,**kwargs):
                if args[:3]==['git','rev-parse','HEAD']:
                    return SimpleNamespace(returncode=0,stdout=source+'\n')
                if args[:3]!=['adb','-s','emulator-5554']:
                    raise AssertionError('unexpected command')
                tail=args[3:]
                if tail==['get-state']:data=b'device\n'
                elif tail==['shell','getprop','ro.kernel.qemu']:data=b'1\n'
                elif tail[:3]==['shell','pm','path']:data=b'package:/data/app/mock.apk\n'
                elif tail[:3]==['shell','run-as','com.sdvsync']:data=b'uid=10000\n'
                elif tail[:3]==['shell','am','instrument']:data=output
                else:raise AssertionError('unexpected adb invocation')
                return SimpleNamespace(returncode=0,stdout=data)
            argv=['r47_capture.py','--output-dir',str(out),'--source-sha',source,'--run-id','41759471916']
            with patch.object(sys,'argv',argv),patch.object(r47_capture.subprocess,'run',side_effect=fake_run):
                self.assertEqual(1,r47_capture.main())
            record=json.loads((out/'R47_SYNTHETIC_EVIDENCE.json').read_text())
            self.assertEqual('REFUSED',record['overall'])
            self.assertEqual([failed],record['instrumentation_summary']['failed_tests'])
            self.assertTrue(record['instrumentation_exit_success'])
            self.assertNotIn(trace,json.dumps(record))
            self.assertFalse(list(out.glob('*.png')))

    def test_all_cases_are_unique_and_allowlisted(self):
        self.assertEqual(11,len(r47_diag.ALLOWED_TESTS))
        self.assertEqual(11,len(set(r47_diag.ALLOWED_TESTS)))
        self.assertTrue(all(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*',t) for t in r47_diag.ALLOWED_TESTS))

if __name__=='__main__':unittest.main()
