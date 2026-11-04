#!/usr/bin/env python3
"""R45/R46 audited UI source gate. No Android, account or file operations."""
from pathlib import Path
import json
import sys
import tomllib
import r45r2_patch
ROOT=Path(__file__).resolve().parents[2]

def read(rel:str)->str:
    path=ROOT/rel
    assert path.is_file() and not path.is_symlink(),'R45R2_MISSING_OR_LINK:'+rel
    return path.read_text(encoding='utf-8')

def check()->dict:
    r45r2_patch.check(ROOT)
    policy=read('app/src/main/java/com/sdvsync/ui/policy/R45GuidePolicy.kt')
    panel=read('app/src/main/java/com/sdvsync/ui/components/R45AreaGuide.kt')
    errors=read('app/src/main/java/com/sdvsync/ui/policy/R46UiErrorPolicy.kt')
    error_panel=read('app/src/main/java/com/sdvsync/ui/components/R46ErrorPanel.kt')
    games=read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
    login=read('app/src/main/java/com/sdvsync/ui/screens/LoginScreen.kt')
    save=read('app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt')
    mods=read('app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt')
    sync=read('app/src/main/java/com/sdvsync/ui/components/SyncErrorCard.kt')
    previews=read('app/src/main/java/com/sdvsync/ui/theme/R45FullUiPreviews.kt')
    security=json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    code=read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    cfg=tomllib.loads(read('.devtool.toml'))
    workflow=read('.github/workflows/r45-r46-audited.yml')
    for field in ('release_enabled','publication_enabled','automatic_device_install','device_qualification_complete'):
        assert security[field] is False,'R45R2_RELEASE_GUARD:'+field
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in code
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in code
    for area in ('GAME','SAVES','MODS','MORE'):
        assert area in policy and ('R45AreaGuide(R45GuideArea.'+area+')') in (games if area=='GAME' else save if area=='SAVES' else mods if area=='MODS' else read('app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt'))
    for screen in (games, save, mods, read('app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt')):
        assert 'import com.sdvsync.ui.policy.R45GuideArea' in screen, 'R45R3_MISSING_POLICY_IMPORT'
        assert 'import com.sdvsync.ui.components.R45GuideArea' not in screen, 'R45R3_WRONG_COMPONENT_IMPORT'
    assert 'R45GuidePolicy.forArea(area)' in panel
    for area in ('SAVES','MODS','GAME','GAME_DOWNLOAD','SMAPI','CINDERBOX','STEAM_LOGIN'):
        assert 'R46UiErrorArea.'+area in errors,'R45R2_ERROR_AREA:'+area
    assert 'onRetry: (() -> Unit)? = null' in error_panel
    assert 'actionLabel = if (onRetry != null) copy.retryLabel else null' in error_panel
    assert 'R46ErrorPanel(R46UiErrorArea.GAME_DOWNLOAD)' in games
    assert 'R46ErrorPanel(R46UiErrorArea.SMAPI)' in games
    assert 'R46ErrorPanel(R46UiErrorArea.CINDERBOX)' in games
    assert 'R46ErrorPanel(R46UiErrorArea.STEAM_LOGIN)' in login
    assert 'if (DeveloperFeaturePolicy.canOpenDiagnostics(context))' in login
    assert 'if (DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current) &&' in sync
    assert 'onRetry = { viewModel.refresh() }' in save
    assert 'onRetry = { viewModel.loadInstalledMods() }' in mods
    for source in (save,mods):
        assert 'if (SdvSyncThemeExtras.isEarthy)' in source
        assert 'R44BStateKind.LOADING' in source
        assert 'PixelLoadingSpinner(' in source
    assert previews.count('@Preview(')>=7
    assert cfg['wrapper']['commands']['r45r2-check']['workflow']=='r45r2-check'
    assert [i['id'] for i in cfg['targets']['cinderbox']['workflows']['r45r2-check']]==['security','r44a','r44b','r45','r45r2']
    for name in ('r45r2_patch.py','r45r2_gate.py','r45ui_gate.py','r45a_gate.py','r44b_gate.py','r43d_gate.py','r42_product_gate.py'):
        assert name in workflow, 'R45R2_CI_MISSING:'+name
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    for source in (errors,policy,panel,error_panel):
        for forbidden in ('/storage/emulated/','java.io.File(', 'deleteRecursively(', 'Runtime.getRuntime()', 'startDownload(', 'forceSync('):
            assert forbidden not in source,'R45R2_UI_WRITE_LEAK:'+forbidden
    return {'result':'R45_R46_R2_AUDITED_SOURCE_PASS','primary_tabs':5,'guided_areas':4,
            'public_error_contexts':7,'game_errors_redacted':True,'login_error_redacted':True,
            'source_only':True,'device_qualified':False,'android_build':'NOT_RUN',
            'release_enabled':False,'existing_game_update_enabled':False}

if __name__=='__main__':
    try:print(json.dumps(check(),sort_keys=True))
    except (AssertionError,KeyError,ValueError,OSError) as err:
        print('R45R2_SOURCE_REFUSED:'+str(err),file=sys.stderr);raise SystemExit(1)
