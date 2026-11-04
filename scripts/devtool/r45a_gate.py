#!/usr/bin/env python3
"""R45-A navigation-only host gate. Never accesses a device or game files."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]

def read(rel: str) -> str:
    p = ROOT / rel
    assert p.is_file() and not p.is_symlink(), 'R45A_MISSING_OR_LINKED:' + rel
    return p.read_text(encoding='utf-8')

def check() -> dict:
    main = read('app/src/main/java/com/sdvsync/MainActivity.kt')
    bottom = read('app/src/main/java/com/sdvsync/ui/components/StardewBottomBar.kt')
    home = read('app/src/main/java/com/sdvsync/ui/screens/HomeScreen.kt')
    icon = read('app/src/main/java/com/sdvsync/ui/components/R45HomeIcon.kt')
    icon = read('app/src/main/java/com/sdvsync/ui/components/R45HomeIcon.kt')
    policy = read('app/src/main/java/com/sdvsync/ui/policy/R45NavigationPolicy.kt')
    unit = read('app/src/test/java/com/sdvsync/ui/policy/R45NavigationPolicyTest.kt')
    previews = read('app/src/main/java/com/sdvsync/ui/theme/R45HomePreviews.kt')
    strings = read('app/src/main/res/values/strings.xml')
    original_guard = read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    security = json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    cfg = tomllib.loads(read('.devtool.toml'))
    workflow = read('.github/workflows/r45a-navigation.yml')

    for key in ('release_enabled','publication_enabled','automatic_device_install',
                'device_qualification_complete'):
        assert security[key] is False, 'R45A_RELEASE_POLICY_DRIFT:' + key
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in original_guard
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in original_guard
    assert 'startDestination = "home"' in main
    assert 'private val BOTTOM_BAR_ROUTES = setOf("home", "game_download", "saves", "mods", "settings")' in main
    assert main.count('"home" -> BottomTab.HOME') == 2
    assert 'composable("home")' in main and 'HomeScreen(' in main
    assert 'R45NavigationPolicy.isPrimaryRoute(route)' in main
    assert 'switchToTab' in main and 'restoreState = true' in main
    assert 'DeveloperFeaturePolicy.canOpenDiagnostics' in main
    assert 'composable("game_download")' in main and 'composable("saves")' in main
    assert 'composable("mods")' in main and 'composable("settings")' in main
    # Legacy enum identities and deep-link routes stay stable: just add Home and change captions.
    assert 'HOME("home", R.string.tab_home)' in bottom
    assert 'DOWNLOAD("game_download", R.string.tab_game)' in bottom
    assert 'SAVES("saves", R.string.tab_saves)' in bottom
    assert 'MODS("mods", R.string.tab_mods)' in bottom
    assert 'SETTINGS("settings", R.string.tab_more)' in bottom
    assert 'BottomTab.HOME -> R45HomeIconData' in bottom
    assert 'val R45HomeIconData = arrayOf(' in icon
    assert all('intArrayOf(' in line for line in icon.splitlines() if line.strip().startswith('intArrayOf('))
    assert all(ch in {'0','1'} for line in icon.splitlines() if 'intArrayOf(' in line for ch in line.split('intArrayOf(',1)[1].split(')',1)[0] if ch.isdigit())
    assert 'val R45HomeIconData = arrayOf(' in icon
    assert all(ch in {'0','1'} for line in icon.splitlines() if 'intArrayOf(' in line for ch in line.split('intArrayOf(',1)[1].split(')',1)[0] if ch.isdigit())
    assert '.selectableGroup()' in bottom
    assert '.selectable(selected = selected, role = Role.Tab' in bottom
    assert '.heightIn(min = 56.dp)' in bottom
    for key in ('tab_home','tab_game','tab_more','tab_saves','tab_mods','tab_download','tab_settings'):
        assert strings.count('name="'+key+'"') == 1, 'R45A_LABEL_MISSING_OR_AMBIGUOUS:'+key
    assert 'R44BResponsiveColumn' in home and 'R44BStatePanel(' in home
    assert 'modifier = Modifier.semantics { heading() }' in home
    for key in ('onOpenGame','onOpenSaves','onOpenMods','onOpenMore',
                'onOpenInstallationHealth','onOpenOperationCenter'):
        assert key in home and key in main, 'R45A_HOME_ACTION_MISSING:'+key
    assert 'No files' not in home  # avoid unverifiable claims about current file state
    for forbidden in ('GameDownloadManager(', 'File(', 'Runtime.getRuntime()',
                      'deleteRecursively(', 'onStartDownload(', 'onForceSync(',
                      'SteamAuthenticator(', '/storage/emulated/0/'):
        assert forbidden not in home and forbidden not in policy, 'R45A_HOME_SIDE_EFFECT:'+forbidden
    assert 'enum class R45PrimaryTab' in policy
    assert 'fun isPrimaryRoute(route: String?): Boolean' in policy
    assert '?: R45PrimaryTab.HOME' in policy
    assert unit.count('@Test fun ') >= 4
    assert previews.count('@Preview(') >= 3
    assert 'Synthetic previews: no personal accounts, game folders, saves or mod data.' in previews
    assert cfg['wrapper']['commands']['r45a-check']['workflow'] == 'r45a-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r45a-check']] == [
        'security', 'r44a', 'r44b', 'r45a']
    job = cfg['targets']['cinderbox']['jobs']['r45a-navigation-source']
    assert job['runner'] == 'command' and job['execution_environment'] == 'host'
    assert job['metadata']['read_only'] is True
    assert job['metadata']['live_update_enabled'] is False
    for required in ('r45a_gate.py', 'r44b_gate.py', 'r44_gate.py',
                     'r43d_gate.py', 'r43_policy_gate.py', 'r42_product_gate.py'):
        assert required in workflow, 'R45A_REGRESSION_CI_MISSING:' + required
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    return {'result': 'R45A_FIVE_TAB_HOME_SOURCE_PASS', 'source_only': True,
            'primary_tabs': 5, 'ui_device_qualified': False,
            'game_update_enabled': False, 'release_enabled': False}

if __name__ == '__main__':
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, ValueError, KeyError, OSError) as exc:
        print('R45A_SOURCE_REFUSED:' + str(exc), file=sys.stderr)
        raise SystemExit(1)
