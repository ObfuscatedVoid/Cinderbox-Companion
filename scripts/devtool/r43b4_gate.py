#!/usr/bin/env python3
"""R43 consolidated candidate gate: source-only, fail-closed, no live update."""
from __future__ import annotations
import json,sys,tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def read(rel: str)->str:
 p=ROOT/rel
 assert p.is_file() and not p.is_symlink(),f'R43B4_MISSING_OR_LINKED:{rel}'
 return p.read_text(encoding='utf-8')

def check():
 coordinator=read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
 ledger=read('app/src/main/java/com/sdvsync/download/R43BLedgerArchive.kt')
 rec=read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
 integrity=read('app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt')
 svc=read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
 mgr=read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
 vm=read('app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt')
 screen=read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
 tests=read('app/src/test/java/com/sdvsync/download/R43BUpdateCoordinatorTest.kt')
 device=read('app/src/androidTest/java/com/sdvsync/qualification/R43B4ConsolidatedDeviceTest.kt')
 cfg=tomllib.loads(read('.devtool.toml'))
 workflow=read('.github/workflows/r43b4-coordinated-update.yml')
 for guard in ('const val LIVE_UPDATES_ENABLED: Boolean = false',
               'const val DEVICE_ACCEPTED: Boolean = false',
               'fun forProduction(privateRoot: File)', 'fun forFixture(',
               'fun beginOwnedUpdate(', 'fun publishPrepared(', 'fun publishFromSteam(',
               'fun rollbackPublished()', 'fun reconcileWithoutSteam()',
               'R43BVerifiedStageBridge.prepareForProduction(',
               'M24DepotManifestCrosscheck.verify(', 'fun finishVerifiedPublication(',
               'archive.verifyOriginal(token, state.backup, destination)',
               'archive.restore(token, destination)', 'recovery.completeUpdateJournal(token)',
               'recovery.completeRolledBackUpdate(token)',
               'R43BUpdateSwap.Finding.MANUAL_REVIEW', 'fun inspectReadOnly()'):
  assert guard in coordinator,'R43B4_COORDINATOR_MISSING:'+guard
 assert coordinator.count('requireEnabled()')>=5
 assert coordinator.index('archive.capture(state.token, destination)') < coordinator.index('swap.publishVerifiedStage()')
 assert coordinator.index('swap.publishVerifiedStage()') < coordinator.index('verifyPublished(destination)')
 assert coordinator.index('try {\n            // BOTH same-volume renames') < coordinator.index('swap.publishVerifiedStage()')
 assert coordinator.index('verifyPublished(destination)') < coordinator.index('finishVerifiedPublication(state.token)')
 for guard in ('r43b-original-integrity-$token.properties', 'fun capture(', 'fun verifyOriginal(',
               'fun restore(', 'StandardCopyOption.ATOMIC_MOVE',
               'GameFileIntegrity(privateRoot).verifyRecorded(', 'output.fd.sync()',
               '!Files.isSymbolicLink'):
  assert guard in ledger,'R43B4_LEDGER_MISSING:'+guard
 for guard in ('fun completeUpdateJournal(token: String)', 'fun completeRolledBackUpdate(token: String)',
               'Published update owner changed', 'Rollback has not preserved both game versions',
               'check(!safe.exists())'):
  assert guard in rec,'R43B4_M24_MISSING:'+guard
 assert 'receiptFile: File = ledger' in integrity and 'recordedDestination: File = root' in integrity
 assert 'Untracked directory: $relative' in integrity and 'val expectedDirectories = buildSet<String>' in integrity
 assert 'check(receiptFile.isFile && !Files.isSymbolicLink(receiptFile.toPath())' in integrity
 assert 'R43BUpdateCoordinator.forProduction(context.filesDir)' in mgr
 assert 'R43BUpdateAdmission.inspect(context.filesDir).eligible' in mgr
 assert 'fun startProtectedUpdate(' in mgr
 assert 'fun rollbackProtectedUpdate()' in mgr
 assert 'fun reconcileProtectedUpdate()' in mgr
 assert 'fun protectedUpdateStatus()' in mgr
 assert 'R43BUpdateSwap' not in mgr, 'Manager bypasses coordinator boundary'
 assert 'R43BLiveUpdatePolicy.DEVICE_ACCEPTED' in mgr
 assert 'R43BUpdateCoordinator.forProduction(filesDir)' in svc
 assert 'R43BUpdateAdmission.inspectOwnedStage(filesDir, ownedUpdate).eligible' in svc
 assert 'R43BLiveUpdatePolicy.DEVICE_ACCEPTED' in svc
 assert 'R43BUpdateCoordinator.Selection(' in svc
 assert 'refreshSelection = {' in svc
 assert 'GameDownloadRecovery(filesDir).promoteStaged(File(installDir))' in svc
 assert 'fun startProtectedUpdateConfirmed()' in vm
 assert 'fun rollbackProtectedUpdateConfirmed()' in vm
 assert 'fun recoverProtectedUpdateConfirmed()' in vm
 assert 'confirmProtectedGameUpdate' in screen and 'Confirm protected update' in screen
 assert 'confirmProtectedRollback' in screen and 'confirmProtectedRecovery' in screen
 assert 'Live replacement remains disabled until device acceptance.' in screen
 assert tests.count('@Test fun ')>=13
 assert device.count('@Test fun r43b4d_')==5
 assert 'targetContext.cacheDir.canonicalFile' in device
 assert 'CinderboxPaths.GAME_FILES_DIR' not in device
 for name in ('postPromotionVerificationFailureRestoresOriginal',
              'conflictAfterBackupRenameNeverOverwritesThirdPartyGame',
              'interruptedOriginalMoveAutomaticallyRestoresWithoutOverwrite',
              'explicitRollbackRestoresOriginalReceiptAndRetainsNewGame',
              'newUserManagedFolderBlocksBeforeAnyRename',
              'finalizedPublicationIsRecognizedReadOnlyAfterRestart',
              'unknownEmptyUserDirectoryBlocksBeforeAnyRename'):
  assert name in tests,'R43B4_TEST_MISSING:'+name
 assert cfg['wrapper']['commands']['r43b4-check']['workflow']=='r43b4-check'
 assert cfg['targets']['cinderbox']['jobs']['r43b4-coordinator-source']['metadata']['read_only'] is True
 assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r43b4-check']]==['security','r43b3','coordinator']
 assert 'test_r43b4_gate.py' in workflow and 'r43b4_gate.py' in workflow
 assert not any(x in workflow for x in ('gradle ', 'adb ', 'gh release', 'npm install'))
 policy=json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
 assert all(policy[x] is False for x in ('release_enabled','publication_enabled','automatic_device_install'))
 return {'overall':'R43B4_CONSOLIDATED_SOURCE_PASS',
         'steam_provenance_handoff_source':True,
         'rollback_witness_source':True,
         'crash_reconciliation_source':True,
         'explicit_consent_ui_source':True,
         'live_update_enabled':False,
         'device_qualified':False,
         'original_installs_untouched_in_ci':True}
if __name__=='__main__':
 try: print(json.dumps(check(),sort_keys=True))
 except (AssertionError,OSError,ValueError,KeyError) as e:
  print('R43B4_SOURCE_REFUSED:'+str(e),file=sys.stderr);sys.exit(1)
