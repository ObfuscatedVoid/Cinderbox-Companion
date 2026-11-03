#!/usr/bin/env python3
"""R43-C source-only contracts: no live update, no real device qualification."""
import json
import pathlib
import sys
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(path):
    p = ROOT / path
    assert p.is_file() and not p.is_symlink(), 'R43C_MISSING_OR_LINKED:' + path
    return p.read_text(encoding='utf-8')

def check():
    core = read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    swap = read('app/src/main/java/com/sdvsync/download/R43BUpdateSwap.kt')
    guard = read('app/src/main/java/com/sdvsync/download/R43CUpdateSafety.kt')
    cold = read('app/src/main/java/com/sdvsync/download/R43CRecoveryReadOnly.kt')
    service = read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
    unit = read('app/src/test/java/com/sdvsync/download/R43CUpdateSafetyTest.kt')
    recovery_unit = read('app/src/test/java/com/sdvsync/download/R43CRecoveryReadOnlyTest.kt')
    device = read('app/src/androidTest/java/com/sdvsync/qualification/R43CReadOnlyDeviceTest.kt')
    policy = json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    for key in ('release_enabled','publication_enabled','automatic_device_install',
                'device_qualification_complete'):
        assert policy[key] is False, 'R43C_RELEASE_POLICY_ENABLED:' + key
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in core
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in core
    assert 'requireEarlyDownloadBudget(null)' in core
    assert 'R43CUpdateSafety.requireProductionQuiescence()' in core
    assert 'R43CUpdateSafety.requirePreparedPublicationSpace(' in core
    assert 'R43CRecoveryReadOnly.inspect(' in core
    assert 'R43CUpdateSafety.requireProductionQuiescence()' in service
    assert service.index('R43CUpdateSafety.requireProductionQuiescence()') < service.index(
        'R43BUpdateAdmission.inspectOwnedStage(filesDir, ownedUpdate).eligible')
    assert swap.count('R43CUpdateSafety.requireProductionQuiescence()') >= 5
    assert 'R43CUpdateSafety.requirePreparedPublicationSpace(' in swap
    assert 'fun requireProductionQuiescence() = requireQuiescence(unavailableWitness)' in guard
    assert 'GameState.UNKNOWN, false' in guard
    assert 'fun requireEarlyDownloadBudget(verifiedExpectedBytes: Long?)' in guard
    assert 'Files.isSymbolicLink(path)' in guard
    assert 'if (stageAlreadyComplete) 0L else expectedNewStageBytes' in guard
    assert 'fun inspectPreparedStage(' in guard
    assert 'fun inspect(' in cold and 'swap.reconcile(' not in cold
    assert 'R43BLedgerArchive(privateRoot).verifyOriginal(' in cold
    assert 'siblingPrefixes' in cold
    assert unit.count('@Test fun ') >= 6 and recovery_unit.count('@Test fun ') >= 2
    assert 's.CapacityCode.' not in unit, 'R43C_INVALID_NESTED_TYPE_REFERENCE'
    assert 'R43CUpdateSafety.CapacityCode.' in unit
    assert device.count('@Test fun r43cd_') == 3
    assert '.targetContext' in device and 'ctx.cacheDir' in device
    assert 'CinderboxPaths.GAME_FILES_DIR' not in device
    assert 'deleteRecursively(' not in core and 'deleteRecursively(' not in swap
    cfg = tomllib.loads(read('.devtool.toml'))
    assert cfg['wrapper']['commands']['r43c-check']['workflow'] == 'r43c-check'
    assert [j['id'] for j in cfg['targets']['cinderbox']['workflows']['r43c-check']] == [
        'security','r43b3','r43b4','r43c']
    assert cfg['targets']['cinderbox']['jobs']['r43c-protected-safety-source']['metadata'][
        'live_update_enabled'] is False
    workflow = read('.github/workflows/r43c-protected-safety.yml')
    assert 'r43b4_gate.py' in workflow and 'r43c_gate.py' in workflow
    assert 'gradle' not in workflow.lower() and 'adb ' not in workflow
    return {'result': 'R43C_SOURCE_CONTRACT_PASS', 'production_updates_enabled': False,
            'production_launch_fence_qualified': False,
            'space_and_crash_recovery_device_qualified': False,
            'source_only': True, 'release_enabled': False}

if __name__ == '__main__':
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, OSError, ValueError, KeyError) as err:
        print('R43C_SOURCE_REFUSED:' + str(err), file=sys.stderr)
        raise SystemExit(1)
