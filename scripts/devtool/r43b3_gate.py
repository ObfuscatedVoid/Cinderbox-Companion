#!/usr/bin/env python3
"""R43-B3 host source contract: verified handoff is implemented but unwired."""
from pathlib import Path
import json
import sys
import tomllib
ROOT=Path(__file__).resolve().parents[2]
def read(path):
    p=ROOT/path
    assert p.is_file() and not p.is_symlink(), 'MISSING_OR_LINKED:'+path
    return p.read_text(encoding='utf8')

def check():
    recovery=read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
    bridge=read('app/src/main/java/com/sdvsync/download/R43BVerifiedStageBridge.kt')
    admission=read('app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt')
    mgr=read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
    svc=read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
    test=read('app/src/test/java/com/sdvsync/download/R43BUpdateStageSessionTest.kt')
    for term in ('updateMode: Boolean = false', '"update_mode"', 'fun beginStagedUpdate(',
                 'R43BUpdateAdmission.inspect(privateRoot).eligible', 'if (current.updateMode)',
                 'check(!safe.exists())', 'check(!current.updateMode)',
                 'fun requireActiveStage(', 'fun isPromotedStage('):
        assert term in recovery, 'R43B3_RECOVERY_GUARD_MISSING:'+term
    assert recovery.index('R43BUpdateAdmission.inspect(privateRoot).eligible') < recovery.index('beginOwned(directory, branch, os, verify, true, updateMode = true)'), 'MISSING_BEFORE_BEGIN'
    for term in ('fun inspectOwnedStage(', 'fun inspectOwnedStageFixture(',
                 'GameDownloadRecovery(privateRoot).pending()', 'current == checkpoint',
                 'checkpoint.matches(gameFiles,', 'GameFileIntegrity(privateRoot).verifyRecorded(safe)',
                 'swapJournal.exists()', 'Code.PROTECTED_USER_CONTENT'):
        assert term in admission, 'R43B3_ADMISSION_GUARD_MISSING:'+term
    for term in ('fun prepareForProduction(', 'M24DepotManifestCrosscheck.verify(',
                 'GameFileIntegrity(privateRoot).verifyAndRecord(stage, persistLedger = false)',
                 'recovery.requireDepotManifestFingerprint(selection.manifestFingerprint)',
                 'R43BUpdateAdmission.inspectOwnedStage(privateRoot, checkpoint).eligible',
                 'check(recheckAuthenticatedSelection() == selection)', 'recordVerifiedStage(checkpoint.token)'):
        assert term in bridge, 'R43B3_BRIDGE_MISSING:'+term
    assert bridge.count('R43BUpdateAdmission.inspectOwnedStage(privateRoot, checkpoint).eligible')==2
    assert bridge.index('M24DepotManifestCrosscheck.verify(')<bridge.index('recordVerifiedStage(checkpoint.token)')
    assert bridge.index('recheckAuthenticatedSelection()')<bridge.index('recordVerifiedStage(checkpoint.token)')
    # Production must not create updates until B4 ties both private journals to
    # recovery and post-promotion manifest verification.
    assert 'beginStagedUpdate(' not in mgr and 'beginStagedUpdate(' not in svc
    assert 'R43BVerifiedStageBridge' not in svc and 'R43BVerifiedStageBridge' not in mgr
    assert 'R43BUpdateSwap' not in mgr and 'R43BUpdateSwap' not in svc
    assert 'R43BVerifiedStageBridge' not in read('app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt')
    for name in ('ownedUpdateStageCoexistsWithOriginalWithoutClaimingPublication',
                 'oldM24CheckpointCannotTreatOccupiedTargetAsOwnedUpdate',
                 'mutatedInstalledGameBlocksPreparedUpdate',
                 'unknownUserFileInsideGameFilesBlocksPreparedUpdate',
                 'nestedModsBlocksPreparedUpdateEvenIfBaselineWasOwned',
                 'missingSessionStageNeverAuthorizesUpdate',
                 'concurrentSwapJournalBlocksNewHandoff',
                 'defaultAdmissionStillBlocksAnyIncompleteDownload',
                 'invalidUpdateModeValueFailsClosed'):
        assert name in test, 'R43B3_TEST_MISSING:'+name
    cfg=tomllib.loads(read('.devtool.toml'))
    assert cfg['wrapper']['commands']['r43b3-check']['workflow']=='r43b3-check'
    assert [n['id'] for n in cfg['targets']['cinderbox']['workflows']['r43b3-check']]==['security','r43b2','handoff']
    assert cfg['targets']['cinderbox']['jobs']['r43b3-handoff-source']['metadata']['read_only']
    wf=read('.github/workflows/r43b3-stage-handoff.yml')
    assert 'test_r43b3_gate.py' in wf and 'r43b3_gate.py' in wf and 'r43b2_gate.py' in wf
    assert 'gradle ' not in wf and 'adb ' not in wf
    release=json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    assert all(release[x] is False for x in ('release_enabled','publication_enabled','automatic_device_install'))
    return {'overall':'PASS_R43B3_VERIFIED_STAGE_PREPARATION_SOURCE_ONLY',
            'update_mode_checkpoint_present':True, 'steam_manifest_handoff_source_present':True,
            'pre_publish_original_baseline_preserved':True, 'live_existing_game_update_enabled':False,
            'device_qualified':False, 'publication_allowed':False}

if __name__=='__main__':
    try: print(json.dumps(check(),sort_keys=True))
    except (AssertionError,KeyError,ValueError,OSError) as e:
        print('R43B3_SOURCE_FAIL:'+str(e),file=sys.stderr);sys.exit(1)
