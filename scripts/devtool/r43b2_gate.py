#!/usr/bin/env python3
"""R43-B2 source gate: installed integrity is retained until verified publication."""
from pathlib import Path
import json
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    p=ROOT/path
    assert p.is_file() and not p.is_symlink(), 'MISSING_OR_LINKED:'+path
    return p.read_text(encoding='utf-8')

def check():
    integrity = read('app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt')
    service = read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
    admission = read('app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt')
    swap = read('app/src/main/java/com/sdvsync/download/R43BUpdateSwap.kt')
    mgr = read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
    recovery = read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
    test = read('app/src/test/java/com/sdvsync/download/R43BUpdateAdmissionTest.kt')
    workflow = read('.github/workflows/r43b2-update-admission.yml')
    cfg = tomllib.loads(read('.devtool.toml'))
    assert 'persistLedger: Boolean = true' in integrity
    assert 'if (!persistLedger) return GameIntegrityResult(second.size, emptyList())' in integrity
    assert service.count('persistLedger = false') == 1
    assert 'GameFileIntegrity(filesDir).verifyAndRecord(destination,' in service
    assert service.index('persistLedger = false') < service.index('promoteStaged(File(installDir))')
    for term in ('fun inspect(privateRoot: File)', 'fun inspectFixture(',
                 'PlatformStoragePaths.requireSafe(gameFiles)', 'GameFileIntegrity(privateRoot).verifyRecorded(safe)',
                 'Code.EXISTING_GAME_CHANGED', 'Code.PROTECTED_USER_CONTENT',
                 'Code.NO_INSTALLED_INTEGRITY_LEDGER', 'Code.RECOVERY_REVIEW_REQUIRED',
                 'File(safe, ".cinderbox-download.session")', 'Files.isSymbolicLink('):
        assert term in admission, 'R43B2_ADMISSION_GUARD_MISSING:'+term
    assert all(term not in admission for term in ('Files.move(', 'deleteRecursively(', 'mkdirs()', '.writeText(', '.renameTo(')), \
        'R43B2_ADMISSION_MUST_BE_READ_ONLY'
    for testname in ('unchangedInstallationWithPrivateLedgerIsOnlyEligibleCase',
                     'modifiedGameFileBlocksPreservingOriginal', 'unknownExtraFileBlocksPreservingOriginal',
                     'nestedModsFolderBlocksEvenWhenRecordedInLocalLedger',
                     'incompleteDownloadCheckpointBlocks', 'symlinkUnderGameFilesFailsClosed',
                     'stagedVerificationDoesNotReplaceInstalledBaseline',
                     'siblingSavesModsSmapiRemainUntouched'):
        assert testname in test, 'R43B2_KOTLIN_CASE_MISSING:'+testname
    assert 'R43BUpdateSwap' not in mgr and 'R43BUpdateSwap' not in service, \
        'R43B2_MUST_NOT_ACTIVATE_LIVE_GAME_REPLACEMENT'
    assert 'check(!safe.exists())' in recovery and 'fun publishVerifiedStage()' in swap
    assert [n['id'] for n in cfg['targets']['cinderbox']['workflows']['r43b2-check']] == [
        'security','r43a','r43b1','admission']
    assert cfg['wrapper']['commands']['r43b2-check']['workflow'] == 'r43b2-check'
    assert cfg['targets']['cinderbox']['jobs']['r43b2-admission-source']['metadata']['read_only']
    assert 'test_r43b2_gate.py' in workflow and 'r43b2_gate.py' in workflow
    assert 'gradle ' not in workflow and 'adb ' not in workflow
    policy=json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    assert not any(policy[k] for k in ('release_enabled','publication_enabled','automatic_device_install'))
    return {'overall':'PASS_R43B2_READ_ONLY_ADMISSION_SOURCE',
            'stage_checksum_receipt_isolation':True, 'live_update_enabled':False,
            'device_qualified':False, 'authenticated_steam_update_qualified':False}

if __name__ == '__main__':
    try:
        print(json.dumps(check(),sort_keys=True))
    except (AssertionError, OSError, KeyError, ValueError) as e:
        print('R43B2_SOURCE_FAIL:',str(e),file=sys.stderr)
        sys.exit(1)
