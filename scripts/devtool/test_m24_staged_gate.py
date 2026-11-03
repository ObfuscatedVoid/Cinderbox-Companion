"""M24 isolated staging/promotion host gate; no user game, Steam, APK or release actions."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import m24_gate
import m24_staged_device_run

ROOT=Path(__file__).resolve().parents[2]
REC=ROOT/'app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt'
SERVICE=ROOT/'app/src/main/java/com/sdvsync/download/GameDownloadService.kt'
MANAGER=ROOT/'app/src/main/java/com/sdvsync/download/GameDownloadManager.kt'

class M24StagedSourceGate(unittest.TestCase):
    def test_source_bound_gateway_includes_atomic_stage(self):
        d=m24_gate.generate()
        self.assertEqual('PASS_SOURCE_ONLY',d['overall'])
        self.assertTrue(d['atomic_stage_source_implemented'])
        self.assertFalse(d['licensed_depot_manifest_verified'])
        self.assertFalse(d['atomic_game_install_committed'])
        self.assertFalse(d['publication_allowed'])
    def test_no_unsafe_fallback_or_direct_download(self):
        rec=REC.read_text()
        svc=SERVICE.read_text()
        manager=MANAGER.read_text()
        self.assertIn('StandardCopyOption.ATOMIC_MOVE',rec)
        self.assertIn('fun isPromotedStage(',rec)
        self.assertIn('fun requireActiveStage(',rec)
        self.assertNotIn('Files.move(stage.toPath(), safe.toPath())',rec)
        self.assertIn('installDirectory = stageRoot.absolutePath',svc)
        self.assertIn('promoteStaged(File(installDir))',svc)
        self.assertIn('recovery.beginStaged(directory, branch, os, verifyAfterDownload)',manager)
        self.assertIn('PICS_BOUND_DEPOT_SHA1_UNSIGNED',svc)
        self.assertIn('M24DepotManifestCrosscheck.verify(root, depotIds, completedDepots)',svc)
    def test_exact_fixture_matrix(self):
        from re import findall
        src=(ROOT/'app/src/androidTest/java/com/sdvsync/qualification/M24StagedTransactionDeviceTest.kt').read_text()
        names=findall(r'@Test fun (m24s_\d\d\w+)\s*\(',src)
        self.assertEqual(list(m24_staged_device_run.METHODS),names)
        self.assertEqual(4,len(names))
        for dangerous in ('/storage/emulated/0','SteamClientManager','pm clear','am force-stop'):
            self.assertNotIn(dangerous,src)
    def test_device_parser_requires_all_cases(self):
        lines=[]
        for m in m24_staged_device_run.METHODS:
            lines+=['INSTRUMENTATION_STATUS: test='+m,'INSTRUMENTATION_STATUS_CODE: 0']
        lines+=['INSTRUMENTATION_CODE: -1']
        self.assertEqual(4,m24_staged_device_run.parse('\n'.join(lines))['completed'])
    def test_kotlin_runtime_stage_and_crash_reconciliation(self):
        if not shutil.which('kotlinc') or not shutil.which('kotlin'):
            self.skipTest('Kotlin compiler/runtime unavailable')
        with tempfile.TemporaryDirectory(prefix='m24-staged-kotlin-') as td:
            root=Path(td)
            harness=root/'Harness.kt'
            harness.write_text('''import com.sdvsync.download.GameDownloadRecovery
import java.io.File
fun main(args:Array<String>) {
  val root=File(args[0]);val owner=File(root,"private").apply{mkdirs()}
  val dest=File(root,"target");val r=GameDownloadRecovery(owner)
  r.beginStaged(dest,"public","windows",true)
  r.bindSteamBuildId(42)
  val stage=r.requireActiveStage(dest)
  check(!dest.exists() && stage.isDirectory)
  File(stage,"chunk.bin").writeText("synthetic bytes")
  val newr=GameDownloadRecovery(owner)
  check(newr.requireActiveStage(dest)==stage)
  try {newr.requireSteamBuildId(43);error("build drift authorized")}
    catch (_:IllegalStateException) {}
  newr.promoteStaged(dest)
  check(newr.isPromotedStage(dest))
  check(!stage.exists() && File(dest,"chunk.bin").readText()=="synthetic bytes")
  val after=GameDownloadRecovery(owner)
  check(after.isPromotedStage(dest))
  after.completed()
  check(after.pending()==null)
  check(File(dest,"chunk.bin").exists())
  check(!File(dest,".cinderbox-download.session").exists())
  println("PASS_M24_STAGED_ATOMIC_RECONCILIATION")
}
''')
            jar=root/'smoke.jar'
            # Compile the real R43-B3 recovery dependencies. The only test stub
            # replaces CinderboxLayout's Android-dependent isInstalled() method;
            # production CinderboxPaths, admission and SHA-256 integrity are real.
            layout_stub = root / 'CinderboxLayoutStub.kt'
            layout_stub.write_text(
                'package com.sdvsync.cinderbox\n'
                'class CinderboxLayout { fun isInstalled(): Boolean = false }\n'
            )
            dependencies = (
                'app/src/main/java/com/sdvsync/local/PlatformStoragePaths.kt',
                'app/src/main/java/com/sdvsync/cinderbox/CinderboxPaths.kt',
                'app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt',
                'app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt',
            )
            build = subprocess.run(
                ['kotlinc', *(str(ROOT / p) for p in dependencies),
                 str(REC), str(layout_stub), str(harness), '-d', str(jar)],
                capture_output=True, text=True, timeout=60
            )
            self.assertEqual(0,build.returncode,build.stderr)
            run=subprocess.run(['kotlin','-classpath',str(jar),'HarnessKt',str(root)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,run.returncode,run.stderr)
            self.assertIn('PASS_M24_STAGED_ATOMIC_RECONCILIATION',run.stdout)

if __name__=='__main__':unittest.main()
