#!/usr/bin/env python3
"""R43-D read-only destination UX gate. No Gradle, ADB, Steam or filesystem writes."""
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]

def read(rel: str) -> str:
    f = ROOT / rel
    assert f.is_file() and not f.is_symlink(), 'R43D_MISSING_OR_LINKED:' + rel
    return f.read_text(encoding='utf-8')

def check():
    vm = read('app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt')
    screen = read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
    wizard = read('app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt')
    policy = read('app/src/main/java/com/sdvsync/ui/policy/DeveloperFeaturePolicy.kt')
    present = read('app/src/main/java/com/sdvsync/ui/policy/R43DDestinationPresentation.kt')
    unit = read('app/src/test/java/com/sdvsync/ui/policy/R43DDestinationPresentationTest.kt')
    manager = read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
    service = read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
    coordinator = read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    security = json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    cfg = tomllib.loads(read('.devtool.toml'))

    for key in ('release_enabled','publication_enabled', 'automatic_device_install',
                'device_qualification_complete'):
        assert security[key] is False, 'R43D_RELEASE_GUARD_CHANGED:' + key
    for needle in ('const val LIVE_UPDATES_ENABLED: Boolean = false',
                   'const val DEVICE_ACCEPTED: Boolean = false'):
        assert needle in coordinator, 'R43D_LIVE_GATE_CHANGED'
    assert 'debugBuild && unlocked' in policy and 'BuildConfig.DEBUG' in policy
    assert 'if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {' in wizard
    assert 'if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {' in vm
    assert vm.count('if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context))') == 2
    assert 'CinderboxPaths.GAME_FILES_DIR\n        }' in vm
    assert 'fixedDestinationCode = inspectFixedDestination()' in vm
    assert vm.count('fixedDestinationCode = inspectFixedDestination()') == 2
    assert '.getOrDefault(CinderboxDownloadDestination.Code.STORAGE_INACCESSIBLE)' in vm
    assert 'CinderboxDownloadDestination.requireWritableForInstall(' in manager
    assert 'CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)' in service
    assert 'Game destination · Cinderbox' in screen
    assert 'R43DDestinationPresentation.from(' in screen
    assert 'R43DDestinationPresentation.installedLabel(' in screen
    assert 'Cinderbox game location: ${CinderboxPaths.GAME_FILES_DIR}' not in screen
    assert "state.primaryGameStatus.name.replace('_', ' ')" not in screen
    assert "state.primarySmapiStatus.name.replace('_', ' ')" not in screen
    assert screen.count('DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)') >= 3
    assert 'state.installDirectory != CinderboxPaths.GAME_FILES_DIR' in screen
    # Legacy debug-only destinations still follow original backend restrictions.
    assert 'DownloadPresentation.destinationStatus(state.destinationCode)' in screen
    assert 'Live replacement remains disabled until device acceptance.' in screen
    assert 'R43DDestinationPresentation.Kind' in unit and unit.count('@Test fun ') >= 7
    assert 'enum class Kind' in present and 'EMPTY_GAME_DIRECTORY_PRESENT -> Notice(' in present
    assert 'CinderboxDownloadDestination.Code.WRONG_DESTINATION -> Notice(' in present
    assert 'CinderboxDownloadDestination.Code.EXISTING_GAME_PROTECTED -> Notice(' in present
    assert 'M23InstallationDiscovery.Status.RECOVERY_REVIEW' in present
    for disallowed in ('/storage/emulated/', 'java.lang.Exception', 'Runtime.getRuntime()'):
        assert disallowed not in present, 'R43D_PRESENTATION_LEAK:' + disallowed
    assert cfg['wrapper']['commands']['r43d-check']['workflow'] == 'r43d-check'
    assert [node['id'] for node in cfg['targets']['cinderbox']['workflows']['r43d-check']] == [
        'security', 'r43c', 'r43d']
    assert cfg['targets']['cinderbox']['jobs']['r43d-destination-ux-source']['metadata'][
        'live_update_enabled'] is False
    workflow = read('.github/workflows/r43d-destination-ux.yml')
    for required in ('r43c_gate.py', 'r43d_gate.py', 'r43_policy_gate.py',
                     'r43b4_gate.py', 'r42_product_gate.py'):
        assert required in workflow, 'R43D_MISSING_REGRESSION_GATE:' + required
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    return {'result':'R43D_DESTINATION_UX_SOURCE_PASS', 'source_only': True,
            'production_fixed_destination': True, 'production_update_enabled': False,
            'physical_device_qualified': False, 'release_enabled': False}

if __name__ == '__main__':
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, ValueError, OSError, KeyError) as exc:
        print('R43D_SOURCE_REFUSED:' + str(exc), file=sys.stderr)
        raise SystemExit(1)
