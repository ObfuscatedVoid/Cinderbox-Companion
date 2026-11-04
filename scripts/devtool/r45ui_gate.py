#!/usr/bin/env python3
"""Consolidated R45+R46 UI host gate: no Gradle, ADB or device operations."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tomllib

ROOT=Path(__file__).resolve().parents[2]

def read(rel:str)->str:
    p=ROOT/rel
    assert p.is_file() and not p.is_symlink(), 'R45UI_MISSING_OR_LINK:'+rel
    return p.read_text(encoding='utf-8')

def check()->dict:
    main=read('app/src/main/java/com/sdvsync/MainActivity.kt')
    bottom=read('app/src/main/java/com/sdvsync/ui/components/StardewBottomBar.kt')
    home=read('app/src/main/java/com/sdvsync/ui/screens/HomeScreen.kt')
    dash=read('app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt')
    mods=read('app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt')
    game=read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
    more=read('app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt')
    sync=read('app/src/main/java/com/sdvsync/ui/components/SyncErrorCard.kt')
    intro=read('app/src/main/java/com/sdvsync/ui/components/R45SectionIntro.kt')
    panel=read('app/src/main/java/com/sdvsync/ui/components/R46ErrorPanel.kt')
    policy=read('app/src/main/java/com/sdvsync/ui/policy/R46UiErrorPolicy.kt')
    tests=read('app/src/test/java/com/sdvsync/ui/policy/R46UiErrorPolicyTest.kt')
    previews=read('app/src/main/java/com/sdvsync/ui/theme/R45FullUiPreviews.kt')
    cfg=tomllib.loads(read('.devtool.toml'))
    workflow=read('.github/workflows/r45-full-ui.yml')
    guards=read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    release=json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    for key in ('release_enabled','publication_enabled','automatic_device_install','device_qualification_complete'):
        assert release[key] is False, 'R45UI_RELEASE_GUARD_CHANGED:'+key
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in guards
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in guards
    assert 'startDestination = "home"' in main
    for label,route in [('HOME','home'),('DOWNLOAD','game_download'),('SAVES','saves'),('MODS','mods'),('SETTINGS','settings')]:
        assert f'{label}("{route}"' in bottom, 'R45UI_TAB_MISSING:'+label
    assert 'R45NavigationPolicy.isPrimaryRoute(route)' in main
    assert 'HomeScreen(' in main and 'onOpenOperationCenter' in home
    assert main.count('restoreState = true')>=2 and 'DeveloperFeaturePolicy.canOpenDiagnostics' in main
    for area,source in [('SAVES',dash),('MODS',mods)]:
        assert 'R45SectionIntro(' in source and 'R46ErrorPanel(' in source
        assert 'area = R46UiErrorArea.'+area in source
        assert 'state.error!!' not in source, 'R45UI_RAW_ERROR_VISIBLE:'+area
    assert 'onRetry = { viewModel.refresh() }' in dash
    assert 'onRetry = { viewModel.loadInstalledMods() }' in mods
    assert 'R45SectionIntro(' in game and 'R45SectionIntro(' in more
    assert 'if (!preLogin)' in more
    assert 'Existing installations are protected' in game
    assert 'Live replacement remains disabled until device acceptance.' in game
    assert 'developer' not in intro.lower() or 'developer' not in panel.lower()
    assert 'modifier = Modifier.semantics { heading() }' in intro
    assert 'R44BStatePanel(' in panel and 'onAction = onRetry' in panel
    assert 'R44BStateKind.NEEDS_ATTENTION' in panel
    assert 'enum class R46UiErrorArea' in policy
    for area in ('SAVES','MODS','GAME','GAME_DOWNLOAD','SMAPI','CINDERBOX','STEAM_LOGIN'):
        assert area in policy, 'R45UI_R46_AREA_MISSING:'+area
    assert 'does not mean your saved files are missing or deleted' in policy
    assert 'Existing mods were not changed by this message' in policy
    assert 'DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)' in sync
    assert 'if (DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current) &&' in sync
    assert 'rawMessage.isNotBlank()' in sync and 'TextButton(onClick = { showDetails = !showDetails })' in sync
    assert 'text = rawMessage' in sync
    assert 'onForceSync: (() -> Unit)?' in sync and 'onSwitchToCinderbox: (() -> Unit)?' in sync
    assert 'when (category)' in sync and 'SyncErrorCategory.WRITE_FAILED' in sync
    assert tests.count('@Test fun ')>=3 and previews.count('@Preview(')>=4
    for bad in ('/storage/emulated/', 'Runtime.getRuntime()', 'File(', 'deleteRecursively(', 'startDownload(', 'applyProfile('):
        assert bad not in intro and bad not in panel and bad not in policy, 'R45UI_PRESENTATION_WRITE_LEAK:'+bad
    assert cfg['wrapper']['commands']['r45-ui-check']['workflow']=='r45-ui-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r45-ui-check']]==[
        'security','r44a','r44b','r45a','r45ui']
    job=cfg['targets']['cinderbox']['jobs']['r45-ui-source']
    assert job['runner']=='command' and job['execution_environment']=='host'
    assert job['metadata']['read_only'] is True and job['metadata']['live_update_enabled'] is False
    for req in ('r45ui_gate.py','r45a_gate.py','r44b_gate.py','r44_gate.py',
                'r43d_gate.py','r43_policy_gate.py','r42_product_gate.py'):
        assert req in workflow, 'R45UI_CI_REGRESSION_GATE_MISSING:'+req
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    return {'result':'R45_R46_UI_SOURCE_PASS','primary_tabs':5,'screen_sections':4,
            'friendly_error_areas':2,'debug_only_raw_sync_diagnostics':True,
            'source_only':True,'android_build':'NOT_RUN','device_accepted':False,
            'game_update_enabled':False,'release_enabled':False}

if __name__=='__main__':
    try:
        print(json.dumps(check(),sort_keys=True))
    except (AssertionError,KeyError,OSError,ValueError) as e:
        print('R45UI_SOURCE_REFUSED:'+str(e),file=sys.stderr)
        raise SystemExit(1)
