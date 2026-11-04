#!/usr/bin/env python3
"""R45-A exact merged-R44B patch: primary navigation UI only, fail closed."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib

BASE = 'a331d08a5e997c65f8d2d31fc35b0108f7260641'
TREE = '0310f72c52d10615c84610e5d0171cfe761aeb88'
MAIN = 'app/src/main/java/com/sdvsync/MainActivity.kt'
BOTTOM = 'app/src/main/java/com/sdvsync/ui/components/StardewBottomBar.kt'
STRINGS = 'app/src/main/res/values/strings.xml'
CONFIG = '.devtool.toml'
PINNED = {
    MAIN: '0e63b9ca8baa5fb5cead6aa9b431f728d2f591d6',
    BOTTOM: '0231db2561f59f3d7c84d471e363f8f25f2fe62d',
    STRINGS: '24f00147667dd19087bcc9e05ec64be793aeec36',
    CONFIG: '42331e34e209ca829f7a1907251355df1831cc93',
}

def replace_exact(source: str, old: str, new: str, count: int = 1) -> str:
    actual = source.count(old)
    assert actual == count, f'R45A_ANCHOR_DRIFT:{actual}!={count}:{old[:90]!r}'
    return source.replace(old, new, count)

def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def git_blob(raw: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()

def migrate(rel: str, source: str) -> str:
    if rel == MAIN:
        s = replace_exact(source,
            'import com.sdvsync.ui.screens.GameDownloadScreen\n',
            'import com.sdvsync.ui.screens.GameDownloadScreen\n'
            'import com.sdvsync.ui.screens.HomeScreen\n')
        s = replace_exact(s,
            'import com.sdvsync.ui.policy.DeveloperFeaturePolicy\n',
            'import com.sdvsync.ui.policy.DeveloperFeaturePolicy\n'
            'import com.sdvsync.ui.policy.R45NavigationPolicy\n')
        s = replace_exact(s,
            'private val BOTTOM_BAR_ROUTES = setOf("saves", "mods", "game_download", "settings")',
            'private val BOTTOM_BAR_ROUTES = setOf("home", "game_download", "saves", "mods", "settings")')
        s = replace_exact(s,
            '                "saves" -> BottomTab.SAVES\n',
            '                "home" -> BottomTab.HOME\n'
            '                "saves" -> BottomTab.SAVES\n', count=2)
        s = replace_exact(s, '                else -> BottomTab.SAVES\n',
            '                else -> BottomTab.HOME\n', count=2)
        s = replace_exact(s, '            startDestination = "saves",',
            '            startDestination = "home",')
        s = replace_exact(s,
            '    Column(Modifier.fillMaxSize()) {\n        NavHost(\n            navController = mainNavController,',
            '''    // Reuse the established state-restoring tab navigation. Unknown destinations
    // are ignored here; privileged sub-screens retain their existing explicit paths.
    val navigateToPrimaryTab: (String) -> Unit = { route ->
        if (R45NavigationPolicy.isPrimaryRoute(route)) {
            mainNavController.navigate(route) {
                popUpTo(mainNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = mainNavController,''')
        s = replace_exact(s,
            '            // Saves tab\n            composable("saves") {',
            '''            // Home is an explicit, navigation-only entry point.
            composable("home") {
                HomeScreen(
                    onOpenGame = { navigateToPrimaryTab("game_download") },
                    onOpenSaves = { navigateToPrimaryTab("saves") },
                    onOpenMods = { navigateToPrimaryTab("mods") },
                    onOpenMore = { navigateToPrimaryTab("settings") },
                    onOpenInstallationHealth = {
                        parentNavController.navigate("installation_health")
                    },
                    onOpenOperationCenter = {
                        parentNavController.navigate("operation_center")
                    }
                )
            }

            // Saves tab
            composable("saves") {''')
        # Existing Game and More back controls should return to the new Home tab.
        s = replace_exact(s,
            'mainNavController.navigate("saves") {',
            'mainNavController.navigate("home") {', count=2)
        return s
    if rel == BOTTOM:
        s = replace_exact(source,
            '''enum class BottomTab(val route: String, val labelRes: Int) {
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    DOWNLOAD("game_download", R.string.tab_download),
    SETTINGS("settings", R.string.tab_settings)
}''',
            '''/** Stable enum keys preserve internal callers; the visible labels are Home/Game/Saves/Mods/More. */
enum class BottomTab(val route: String, val labelRes: Int) {
    HOME("home", R.string.tab_home),
    DOWNLOAD("game_download", R.string.tab_game),
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    SETTINGS("settings", R.string.tab_more)
}''')
        s = replace_exact(s,
            '    val iconData = when (tab) {\n        BottomTab.SAVES -> WheatData',
            '    val iconData = when (tab) {\n        BottomTab.HOME -> R45HomeIconData\n        BottomTab.SAVES -> WheatData')
        return s
    if rel == STRINGS:
        return replace_exact(source,
            '    <!-- Bottom navigation tabs -->\n',
            '''    <!-- Bottom navigation tabs -->
    <string name="tab_home">Home</string>
    <string name="tab_game">Game</string>
    <string name="tab_more">More</string>
''')
    if rel == CONFIG:
        s = replace_exact(source,
            '[wrapper.commands.m23-check]\n',
            '''[wrapper.commands.r45a-check]
description = "R45-A five-tab navigation and read-only Home entry point (host-only)"
workflow = "r45a-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        s = replace_exact(s,
            '[targets.cinderbox.workflows]\n',
            '''[targets.cinderbox.jobs.r45a-navigation-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r45a_gate.py"]
inputs = ["scripts/devtool/r45a_*.py", "scripts/devtool/test_r45a_gate.py", "app/src/main/java/com/sdvsync/MainActivity.kt", "app/src/main/java/com/sdvsync/ui/components/StardewBottomBar.kt", "app/src/main/java/com/sdvsync/ui/components/R45HomeIcon.kt", "app/src/main/java/com/sdvsync/ui/screens/HomeScreen.kt", "app/src/main/java/com/sdvsync/ui/policy/R45NavigationPolicy.kt", "app/src/test/java/com/sdvsync/ui/policy/R45NavigationPolicyTest.kt", "app/src/main/java/com/sdvsync/ui/theme/R45HomePreviews.kt", "app/src/main/res/values/strings.xml", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, area = "r45a-five-tab-home-navigation", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r45a-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r44a", ref = "job:r44-design-system-source", depends_on = ["security"] },
  { id = "r44b", ref = "job:r44b-experience-source", depends_on = ["r44a"] },
  { id = "r45a", ref = "job:r45a-navigation-source", depends_on = ["r44b"] },
]
''')
        tomllib.loads(s)
        return s
    raise AssertionError('R45A_UNEXPECTED_PATH:' + rel)


def check(root: Path) -> None:
    main = (root/MAIN).read_text(encoding='utf-8')
    bottom = (root/BOTTOM).read_text(encoding='utf-8')
    strings = (root/STRINGS).read_text(encoding='utf-8')
    cfg = tomllib.loads((root/CONFIG).read_text(encoding='utf-8'))
    assert 'startDestination = "home"' in main
    assert main.count('"home" -> BottomTab.HOME') == 2
    assert 'HomeScreen(' in main and 'composable("home")' in main
    assert 'R45NavigationPolicy.isPrimaryRoute(route)' in main
    assert 'onOpenOperationCenter' in main and 'onOpenInstallationHealth' in main
    assert 'BottomTab.HOME -> R45HomeIconData' in bottom
    assert '.selectableGroup()' in bottom and 'role = Role.Tab' in bottom
    assert '.heightIn(min = 56.dp)' in bottom
    for needle in ('R.string.tab_home', 'R.string.tab_game', 'R.string.tab_more'):
        assert needle in bottom
    for name in ('tab_home', 'tab_game', 'tab_more'):
        assert strings.count('name="'+name+'"') == 1
    assert cfg['wrapper']['commands']['r45a-check']['workflow'] == 'r45a-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r45a-check']] == [
        'security','r44a','r44b','r45a']
    assert cfg['targets']['cinderbox']['jobs']['r45a-navigation-source']['metadata']['read_only'] is True
    print('R45A_NAVIGATION_PATCH_POSTCONDITION_PASS')


def apply(root: Path) -> None:
    assert git(root,'rev-parse','HEAD') == BASE, 'R45A_BASE_DRIFT'
    assert git(root,'rev-parse','HEAD^{tree}') == TREE, 'R45A_TREE_DRIFT'
    assert git(root,'diff','--name-only','--',*PINNED) == '', 'R45A_PREEXISTING_MODIFICATION'
    modified: dict[str,str] = {}
    for rel, sha in PINNED.items():
        p = root/rel
        assert p.is_file() and not p.is_symlink(), 'R45A_MISSING_OR_LINKED:' + rel
        raw = p.read_bytes()
        assert git_blob(raw) == sha, 'R45A_SOURCE_PIN_DRIFT:' + rel
        modified[rel] = migrate(rel, raw.decode('utf-8'))
    tomllib.loads(modified[CONFIG])
    # All source hashes and anchors have now passed before the first write.
    temps = {rel: root/(rel+'.r45a-tmp') for rel in modified}
    for rel,t in temps.items():
        assert not t.exists() and not t.is_symlink(), 'R45A_TEMP_COLLISION:'+rel
    try:
        for rel,source in modified.items():
            original = root/rel
            temp = temps[rel]
            with temp.open('x', encoding='utf-8') as stream:
                stream.write(source)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(temp,original.stat().st_mode & 0o777)
        for rel,temp in temps.items():
            os.replace(temp,root/rel)
    finally:
        for temp in temps.values():
            if temp.exists(): temp.unlink()
    check(root)

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--apply',action='store_true')
    mode.add_argument('--check',action='store_true')
    a=parser.parse_args()
    (apply if a.apply else check)(Path.cwd().resolve())
