"""R47 host regressions. No actual ADB, emulator, network or sensitive data."""
from pathlib import Path
import json
import struct
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zlib
import r47_gate
import r47_patch
import r47_capture
import r47_verify

SHA='a'*40
RUN='47123'


def png_chunk(kind:bytes,payload:bytes)->bytes:
    return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload)&0xffffffff)


def make_png(value:int=1,width:int=320,height:int=101)->bytes:
    ihdr=struct.pack('>IIBBBBB',width,height,8,2,0,0,0)
    scanline=b'\x00'+bytes([value])*width*3
    return (b'\x89PNG\r\n\x1a\n'+png_chunk(b'IHDR',ihdr)+
            png_chunk(b'IDAT',zlib.compress(scanline*height))+png_chunk(b'IEND',b''))


class R47Contracts(unittest.TestCase):
    def test_shallow_checkout_source_check_requires_no_history(self):
        # PR merged-ref checkout defaults to depth=1; historical BASE is absent.
        with patch.object(r47_patch.subprocess, 'check_output',
                          side_effect=AssertionError('historical git fetch forbidden')):
            r47_patch.check(r47_gate.ROOT)

    def test_compose_interaction_methods_not_imported_as_top_level_functions(self):
        source = (r47_gate.ROOT / 'app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt').read_text()
        for member in ('assertDoesNotExist', 'assertExists', 'onNode'):
            self.assertNotIn('import androidx.compose.ui.test.' + member + '\n', source)

    def test_exact_source_gate(self):
        self.assertEqual(r47_gate.check()['result'],'R47_SYNTHETIC_ACCEPTANCE_SOURCE_PASS')

    def test_success_parser_fails_closed(self):
        ok='INSTRUMENTATION_STATUS_CODE: 0\nOK (11 tests)\nINSTRUMENTATION_CODE: -1\n'
        self.assertTrue(r47_capture.parse_result(ok))
        for bad in ('OK (11 tests)','OK (10 tests)\nINSTRUMENTATION_CODE: -1',
                    'FAILURES!!!\n'+ok,'INSTRUMENTATION_FAILED\n'+ok,
                    'Process crashed\n'+ok,ok.replace('-1','0')):
            self.assertFalse(r47_capture.parse_result(bad),bad)

    def test_png_requires_complete_checksum_and_decodable_pixels(self):
        png=make_png(2)
        self.assertEqual((320,101),r47_capture.png_dimensions(png))
        variants=[b'not png',png[:24],png[:-9],png+b'garbage',png[:-1]+b'X']
        broken=bytearray(png);broken[38]^=1;variants.append(bytes(broken))
        # Valid outer CRC, but corrupt zlib inside the IDAT chunk.
        bad=png_chunk(b'IDAT',b'broken deflate')
        variants.append(png[:33]+bad+png_chunk(b'IEND',b''))
        for v in variants:
            with self.assertRaises((ValueError,zlib.error)):
                r47_capture.png_dimensions(v)

    def test_capture_inventory_is_public_and_unique(self):
        self.assertEqual(7,len(r47_capture.NAMES))
        self.assertEqual(len(r47_capture.NAMES),len(set(r47_capture.NAMES)))
        self.assertTrue(all(n.endswith('320dp-fs160.png') for n in r47_capture.NAMES))
        self.assertTrue(all('steam-id' not in n and 'save-file' not in n for n in r47_capture.NAMES))

    def test_release_policy_remains_disabled(self):
        policy=json.loads((r47_gate.ROOT/'docs/security/M3_RELEASE_POLICY.json').read_text())
        for name in ('release_enabled','publication_enabled','automatic_device_install','device_qualification_complete'):
            self.assertFalse(policy[name])

    def test_emulator_script_prohibits_physical_device_and_mutations(self):
        source=(r47_gate.ROOT/'scripts/devtool/r47_emulator_ci.sh').read_text()
        self.assertIn('ro.kernel.qemu',source)
        self.assertIn('SERIAL=emulator-5554',source)
        for unsafe in ('am force-stop','pm clear','reboot','su -c'):
            self.assertNotIn(unsafe,source)

    def fake_adb(self,fail_index=None,duplicate=False):
        def fake(argv,**kwargs):
            if argv[:3]==['git','rev-parse','HEAD']:
                return SimpleNamespace(returncode=0,stdout=SHA+'\n')
            if argv[:3]!=['adb','-s','emulator-5554']:
                raise AssertionError('unapproved subprocess call: '+repr(argv))
            parts=argv[3:]
            if parts==['get-state']: out=b'device\n'
            elif parts==['shell','getprop','ro.kernel.qemu']:out=b'1\n'
            elif parts[:3]==['shell','pm','path']:out=b'package:/data/app/test.apk\n'
            elif parts[:3]==['shell','run-as','com.sdvsync']:out=b'uid=10000\n'
            elif parts[:3]==['shell','am','instrument']:
                out=b'OK (11 tests)\nINSTRUMENTATION_CODE: -1\n'
            elif parts[:4]==['exec-out','run-as','com.sdvsync','cat']:
                name=parts[4].split('/')[-1]
                index=r47_capture.NAMES.index(name)
                if index==fail_index:out=b'broken screenshot'
                else:out=make_png(1 if duplicate else index+1)
            else:raise AssertionError('unapproved adb invocation: '+repr(parts))
            return SimpleNamespace(returncode=0,stdout=out)
        return fake

    def invoke(self,out,adber):
        args=['r47_capture.py','--output-dir',str(out),'--source-sha',SHA,'--run-id',RUN]
        with patch.object(sys,'argv',args), patch.object(r47_capture.subprocess,'run',side_effect=adber):
            return r47_capture.main()

    def test_complete_capture_and_independent_receipt_verification(self):
        with tempfile.TemporaryDirectory() as path:
            folder=Path(path)
            self.assertEqual(0,self.invoke(folder,self.fake_adb()))
            receipt=json.loads((folder/'R47_SYNTHETIC_EVIDENCE.json').read_text())
            self.assertEqual('PASS_SYNTHETIC_EMULATOR',receipt['overall'])
            self.assertEqual(7,receipt['screenshot_count'])
            self.assertEqual(7,r47_verify.verify(folder,SHA,RUN)['screenshots_verified'])
            self.assertFalse(receipt['physical_device_qualified'])
            (folder/r47_capture.NAMES[0]).write_bytes(b'changed')
            with self.assertRaises((ValueError,AssertionError)):
                r47_verify.verify(folder,SHA,RUN)

    def test_partial_collection_fails_closed_and_cleans_old_images(self):
        with tempfile.TemporaryDirectory() as path:
            folder=Path(path)
            (folder/r47_capture.NAMES[0]).write_bytes(make_png())
            self.assertEqual(1,self.invoke(folder,self.fake_adb(fail_index=4)))
            record=json.loads((folder/'R47_SYNTHETIC_EVIDENCE.json').read_text())
            self.assertEqual('REFUSED',record['overall'])
            self.assertEqual([],list(folder.glob('*.png')))
            with self.assertRaises(AssertionError):
                r47_verify.verify(folder,SHA,RUN)

    def test_corrupt_deflate_stream_keeps_receipt_refused(self):
        def broken_adb(argv,**kwargs):
            base=self.fake_adb()(argv,**kwargs)
            if argv[:7]==['adb','-s','emulator-5554','exec-out','run-as','com.sdvsync','cat']:
                bad=png_chunk(b'IDAT',b'not zlib deflate')
                base.stdout=(b'\x89PNG\r\n\x1a\n'+
                             png_chunk(b'IHDR',struct.pack('>IIBBBBB',320,101,8,2,0,0,0))+
                             bad+png_chunk(b'IEND',b''))
            return base
        with tempfile.TemporaryDirectory() as path:
            folder=Path(path)
            self.assertEqual(1,self.invoke(folder,broken_adb))
            record=json.loads((folder/'R47_SYNTHETIC_EVIDENCE.json').read_text())
            self.assertEqual('REFUSED',record['overall'])
            self.assertFalse(list(folder.glob('*.png')))

    def test_duplicate_screenshots_refused(self):
        with tempfile.TemporaryDirectory() as path:
            folder=Path(path)
            self.assertEqual(1,self.invoke(folder,self.fake_adb(duplicate=True)))
            self.assertFalse(list(folder.glob('*.png')))

    def test_independent_verifier_refuses_wrong_run_or_missing_file(self):
        with tempfile.TemporaryDirectory() as path:
            folder=Path(path)
            self.assertEqual(0,self.invoke(folder,self.fake_adb()))
            for wrong in (('b'*40,RUN),(SHA,'123')):
                with self.assertRaises(AssertionError):
                    r47_verify.verify(folder,*wrong)
            (folder/r47_capture.NAMES[3]).unlink()
            with self.assertRaises(AssertionError):
                r47_verify.verify(folder,SHA,RUN)

    def test_ci_retriggers_on_ui_changes_and_checks_receipt(self):
        text=(r47_gate.ROOT/'.github/workflows/r47-ui-acceptance.yml').read_text()
        for required in ('app/src/main/java/com/sdvsync/ui/**','app/src/main/java/com/sdvsync/MainActivity.kt',
                         'app/build.gradle.kts','scripts/devtool/r47_verify.py',
                         'if: always()',':app:assembleDebug :app:assembleDebugAndroidTest'):
            self.assertIn(required,text)

if __name__=='__main__':unittest.main()
