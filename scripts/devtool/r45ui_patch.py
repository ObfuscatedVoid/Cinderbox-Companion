#!/usr/bin/env python3
"""R45/R46 merged-R44B patch: page hierarchy and safe error copy only."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib
import r45a_patch

BASE = r45a_patch.BASE
TREE = r45a_patch.TREE
PREFIX = 'app/src/main/java/com/sdvsync/'
DASH = PREFIX + 'ui/screens/DashboardScreen.kt'
MODS = PREFIX + 'ui/screens/ModManagerScreen.kt'
GAME = PREFIX + 'ui/screens/GameDownloadScreen.kt'
MORE = PREFIX + 'ui/screens/SettingsScreen.kt'
SYNC = PREFIX + 'ui/components/SyncErrorCard.kt'
CONFIG = '.devtool.toml'
PINNED = {
    DASH:'933bd6b2f335f6da6228df453567288474daf191',
    MODS:'28fe004578b09735dc2504abf5f8a4169eb3cd2c',
    GAME:'bb880e7a83219c8583b51e084036c535b6284ea3',
    MORE:'cd35f9a448ef3f25487e2849fa9f9eccc907533d',
    SYNC:'0825e2389f3dcc69f72b40bc1c5935f51e140e0c',
}


def replace(source:str, old:str, new:str, times:int=1)->str:
    assert source.count(old)==times, f'R45UI_ANCHOR_DRIFT:{old[:70]}:{source.count(old)}!={times}'
    return source.replace(old,new,times)


def transform(rel:str, s:str)->str:
    if rel == DASH:
        s=replace(s, 'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R45SectionIntro\n'
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.policy.R46UiErrorArea\n')
        s=replace(s, '''                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            StardewButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }''', '''                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center) {
                        R46ErrorPanel(
                            area = R46UiErrorArea.SAVES,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }''')
        s=replace(s,'                        if (state.isStagingMode) {', '''                        item {
                            R45SectionIntro(
                                "Saves",
                                "Review your saves, backups and sync status. Changes require explicit action."
                            )
                        }
                        if (state.isStagingMode) {''')
        return s
    if rel == MODS:
        s=replace(s,'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R45SectionIntro\n'
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.policy.R46UiErrorArea\n')
        s=replace(s,'''                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            StardewButton(onClick = { viewModel.loadInstalledMods() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }''', '''                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center) {
                        R46ErrorPanel(
                            area = R46UiErrorArea.MODS,
                            onRetry = { viewModel.loadInstalledMods() }
                        )
                    }
                }''')
        s=replace(s,'                        // M7: local manifest checks; never silently enable dependencies.', '''                        item {
                            R45SectionIntro(
                                "Mods",
                                "Review installed mods, profiles and compatibility before making changes."
                            )
                        }
                        // M7: local manifest checks; never silently enable dependencies.''')
        return s
    if rel == GAME:
        s=replace(s,'import com.sdvsync.ui.components.R44StatusSummary\n',
            'import com.sdvsync.ui.components.R44StatusSummary\n'
            'import com.sdvsync.ui.components.R45SectionIntro\n')
        s=replace(s,'            // App info card\n', '''            R45SectionIntro(
                "Game",
                "Inspect Cinderbox storage and game version checks. Existing installations stay protected."
            )
            // App info card
''')
        return s
    if rel == MORE:
        s=replace(s,'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R45SectionIntro\n')
        s=replace(s,'            // Offline, app-private visual preferences. No Steam or storage permissions required.\n', '''            if (!preLogin) {
                R45SectionIntro(
                    "More",
                    "Appearance, permissions and account settings. Sensitive actions retain their confirmations."
                )
            }
            // Offline, app-private visual preferences. No Steam or storage permissions required.
''')
        return s
    if rel == SYNC:
        s=replace(s,'import androidx.compose.material3.Text\n',
            'import androidx.compose.material3.Text\n'
            'import androidx.compose.material3.TextButton\n'
            'import androidx.compose.ui.platform.LocalContext\n'
            'import com.sdvsync.ui.policy.DeveloperFeaturePolicy\n')
        s=replace(s,'''            Spacer(Modifier.height(8.dp))

            Text(
                text = if (showDetails) {
                    stringResource(R.string.sync_error_details_label) + " ▾"
                } else {
                    stringResource(R.string.sync_error_details_label) +
                        " ▸"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showDetails = !showDetails }
            )

            AnimatedVisibility(visible = showDetails) {
                Text(
                    text = rawMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }''', '''            // Raw backend messages can contain private save paths or account details.
            // Only unlocked debug builds expose them, behind an explicit action.
            if (DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current) &&
                rawMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(stringResource(R.string.sync_error_details_label))
                }
                AnimatedVisibility(visible = showDetails) {
                    Text(
                        text = rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }''')
        return s
    if rel == CONFIG:
        s=replace(s, '[wrapper.commands.m23-check]\n', '''[wrapper.commands.r45-ui-check]
description = "R45 unified five-tab UI and R46 friendly error-state source contracts"
workflow = "r45-ui-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        s=replace(s, '[targets.cinderbox.workflows]\n', '''[targets.cinderbox.jobs.r45-ui-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r45ui_gate.py"]
inputs = ["scripts/devtool/r45ui_*.py", "scripts/devtool/test_r45ui_gate.py", "scripts/devtool/r45a_*.py", "app/src/main/java/com/sdvsync/ui/components/R45SectionIntro.kt", "app/src/main/java/com/sdvsync/ui/components/R46ErrorPanel.kt", "app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt", "app/src/main/java/com/sdvsync/ui/components/SyncErrorCard.kt", "app/src/main/java/com/sdvsync/ui/policy/R46UiErrorPolicy.kt", "app/src/test/java/com/sdvsync/ui/policy/R46UiErrorPolicyTest.kt", ".devtool.toml", "docs/security/M3_RELEASE_POLICY.json"]
metadata = { read_only = true, area = "r45-all-screens-r46-error-presentation", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r45-ui-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r44a", ref = "job:r44-design-system-source", depends_on = ["security"] },
  { id = "r44b", ref = "job:r44b-experience-source", depends_on = ["r44a"] },
  { id = "r45a", ref = "job:r45a-navigation-source", depends_on = ["r44b"] },
  { id = "r45ui", ref = "job:r45-ui-source", depends_on = ["r45a"] },
]
''')
        tomllib.loads(s)
        return s
    raise AssertionError('R45UI_UNEXPECTED_TARGET:'+rel)


def git(root:Path,*args:str)->str:
    return subprocess.check_output(['git',*args],cwd=root,text=True).strip()


def blob(raw:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()


def check(root:Path)->None:
    config=tomllib.loads((root/CONFIG).read_text())
    assert config['wrapper']['commands']['r45-ui-check']['workflow']=='r45-ui-check'
    assert [n['id'] for n in config['targets']['cinderbox']['workflows']['r45-ui-check']]==[
        'security','r44a','r44b','r45a','r45ui']
    assert config['targets']['cinderbox']['jobs']['r45-ui-source']['metadata']['live_update_enabled'] is False
    for rel in PINNED:
        text=(root/rel).read_text(encoding='utf-8')
        assert 'import com.sdvsync.ui.components.R45SectionIntro' in text or rel==SYNC, rel
    dash=(root/DASH).read_text(); mods=(root/MODS).read_text()
    for text in (dash,mods):
        assert 'R46ErrorPanel(' in text
        assert 'state.error!!' not in text
        assert 'R45SectionIntro(' in text
    assert 'onRetry = { viewModel.refresh() }' in dash
    assert 'onRetry = { viewModel.loadInstalledMods() }' in mods
    assert 'R45SectionIntro(' in (root/GAME).read_text()
    assert 'R45SectionIntro(' in (root/MORE).read_text()
    sync=(root/SYNC).read_text()
    assert 'DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)' in sync
    assert 'rawMessage.isNotBlank()' in sync
    assert 'TextButton(onClick = { showDetails = !showDetails })' in sync
    assert 'text = rawMessage' in sync
    print('R45UI_POSTCONDITION_PASS')


def apply(root:Path)->None:
    assert git(root,'rev-parse','HEAD')==BASE, 'R45UI_BASE_DRIFT'
    assert git(root,'rev-parse','HEAD^{tree}')==TREE, 'R45UI_TREE_DRIFT'
    assert git(root,'diff','--name-only','--',*PINNED)=='' ,'R45UI_PREEXISTING_CHANGES'
    originals={}
    for rel,expected in PINNED.items():
        f=root/rel
        assert f.is_file() and not f.is_symlink(), 'R45UI_MISSING_OR_LINKED:'+rel
        raw=f.read_bytes()
        assert blob(raw)==expected, 'R45UI_SOURCE_DRIFT:'+rel
        originals[rel]=raw.decode('utf-8')
    configpath=root/CONFIG
    assert configpath.is_file() and not configpath.is_symlink()
    base_config=subprocess.check_output(['git','show',BASE+':'+CONFIG],cwd=root).decode('utf-8')
    assert blob(base_config.encode())==r45a_patch.PINNED[CONFIG]
    expected_intermediate=r45a_patch.migrate(CONFIG,base_config)
    assert configpath.read_text()==expected_intermediate, 'R45UI_R45A_CONFIG_NOT_READY'
    originals[CONFIG]=expected_intermediate
    modified={rel:transform(rel,src) for rel,src in originals.items()}
    temps={rel:root/(rel+'.r45ui-tmp') for rel in modified}
    for rel,tmp in temps.items():
        assert not tmp.exists() and not tmp.is_symlink(), 'R45UI_TEMP_COLLISION:'+rel
    try:
        for rel,src in modified.items():
            tmp=temps[rel]
            with tmp.open('x',encoding='utf-8') as f:
                f.write(src); f.flush(); os.fsync(f.fileno())
        for rel,tmp in temps.items():
            os.replace(tmp,root/rel)
    finally:
        for tmp in temps.values():
            if tmp.exists(): tmp.unlink()
    check(root)


def main():
    cli=argparse.ArgumentParser()
    group=cli.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--check',action='store_true')
    args=cli.parse_args()
    root=Path(__file__).resolve().parents[2]
    if args.apply: apply(root)
    else: check(root)

if __name__=='__main__':
    main()
