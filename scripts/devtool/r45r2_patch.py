#!/usr/bin/env python3
"""R45/R46 audited R2 extension: fail closed on exact post-R45 intermediate source."""
from __future__ import annotations
import argparse
from pathlib import Path
import hashlib
import os
import subprocess
import tomllib
import r45a_patch
import r45ui_patch

BASE=r45a_patch.BASE
TREE=r45a_patch.TREE
DASH=r45ui_patch.DASH
MODS=r45ui_patch.MODS
GAME=r45ui_patch.GAME
MORE=r45ui_patch.MORE
LOGIN='app/src/main/java/com/sdvsync/ui/screens/LoginScreen.kt'
CONFIG='.devtool.toml'
FILES=(DASH, MODS, GAME, MORE, LOGIN, CONFIG)
LOGIN_BLOB='5081507cd27323b9130092754d3691d1f6cd85d4'


def replace_once(s:str, old:str, new:str, label:str)->str:
    assert s.count(old)==1, 'R45R2_ANCHOR_DRIFT:'+label+':'+str(s.count(old))
    return s.replace(old,new,1)


def transform(rel:str,s:str)->str:
    if rel==DASH:
        s=replace_once(s, 'import com.sdvsync.ui.components.R46ErrorPanel\n',
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.components.R45AreaGuide\n'
            'import com.sdvsync.ui.components.R44BStatePanel\n'
            'import com.sdvsync.ui.policy.R44BStateKind\n'
            'import com.sdvsync.ui.components.R45GuideArea\n'
            'import com.sdvsync.ui.theme.SdvSyncThemeExtras\n','dash-import')
        s=replace_once(s, '                        if (state.isStagingMode) {',
            '''                        item {
                            R45AreaGuide(R45GuideArea.SAVES)
                        }
                        if (state.isStagingMode) {''','dash-guide')
        s=replace_once(s, '''                        PixelLoadingSpinner(
                            modifier = Modifier.align(Alignment.Center)
                        )''', '''                        if (SdvSyncThemeExtras.isEarthy) {
                            R44BStatePanel(
                                kind = R44BStateKind.LOADING,
                                title = "Loading your saves",
                                description = "Checking local save information. No sync has been started."
                            )
                        } else {
                            PixelLoadingSpinner(modifier = Modifier.align(Alignment.Center))
                        }''','dash-loading')
        return s
    if rel==MODS:
        s=replace_once(s, 'import com.sdvsync.ui.components.R46ErrorPanel\n',
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.components.R45AreaGuide\n'
            'import com.sdvsync.ui.components.R44BStatePanel\n'
            'import com.sdvsync.ui.policy.R44BStateKind\n'
            'import com.sdvsync.ui.components.R45GuideArea\n'
            'import com.sdvsync.ui.theme.SdvSyncThemeExtras\n','mods-import')
        s=replace_once(s, '                        // M7: local manifest checks; never silently enable dependencies.',
            '''                        item {
                            R45AreaGuide(R45GuideArea.MODS)
                        }
                        // M7: local manifest checks; never silently enable dependencies.''','mods-guide')
        s=replace_once(s, '''                        PixelLoadingSpinner(
                            modifier = Modifier.align(Alignment.Center)
                        )''', '''                        if (SdvSyncThemeExtras.isEarthy) {
                            R44BStatePanel(
                                kind = R44BStateKind.LOADING,
                                title = "Loading installed mods",
                                description = "Reading the current mod inventory. No mod changes were requested."
                            )
                        } else {
                            PixelLoadingSpinner(modifier = Modifier.align(Alignment.Center))
                        }''','mods-loading')
        return s
    if rel==GAME:
        s=replace_once(s, 'import com.sdvsync.ui.components.R45SectionIntro\n',
            'import com.sdvsync.ui.components.R45SectionIntro\n'
            'import com.sdvsync.ui.components.R45AreaGuide\n'
            'import com.sdvsync.ui.components.R45GuideArea\n'
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.policy.R46UiErrorArea\n','game-import')
        s=replace_once(s, '            // App info card\n',
            '            R45AreaGuide(R45GuideArea.GAME)\n            // App info card\n','game-guide')
        s=replace_once(s, '''            // Error
            if (state.error != null) {
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }''', '''            // No backend exception text is surfaced in production UI.
            if (state.error != null) {
                R46ErrorPanel(R46UiErrorArea.GAME)
                Spacer(Modifier.height(16.dp))
            }''','game-error')
        s=replace_once(s, '''                    StardewCard {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.download_error),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.downloadProgress.errorMessage ?: stringResource(R.string.download_error_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }''', '''                    R46ErrorPanel(R46UiErrorArea.GAME_DOWNLOAD)''','download-error')
        s=replace_once(s, '''            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("SMAPI setup could not start: ${smapiProgress.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }''', '''            R46ErrorPanel(R46UiErrorArea.SMAPI)''','smapi-start')
        s=replace_once(s, '''                        Text(
                            stringResource(R.string.smapi_extract_error, smapiProgress.errorMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )''', '''                        R46ErrorPanel(R46UiErrorArea.SMAPI)''','smapi-extract')
        s=replace_once(s, '''            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }''', '''            R46ErrorPanel(R46UiErrorArea.CINDERBOX)''','cinderbox-error')
        return s
    if rel==MORE:
        s=replace_once(s, 'import com.sdvsync.ui.components.R45SectionIntro\n',
            'import com.sdvsync.ui.components.R45SectionIntro\n'
            'import com.sdvsync.ui.components.R45AreaGuide\n'
            'import com.sdvsync.ui.components.R45GuideArea\n','more-import')
        s=replace_once(s, '            // Offline, app-private visual preferences. No Steam or storage permissions required.\n',
            '''            if (!preLogin) {
                R45AreaGuide(R45GuideArea.MORE)
            }
            // Offline, app-private visual preferences. No Steam or storage permissions required.
''','more-guide')
        return s
    if rel==LOGIN:
        s=replace_once(s, 'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R46ErrorPanel\n'
            'import com.sdvsync.ui.policy.R46UiErrorArea\n'
            'import com.sdvsync.ui.policy.DeveloperFeaturePolicy\n','login-import')
        s=replace_once(s, '''                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )''', '''                    R46ErrorPanel(R46UiErrorArea.STEAM_LOGIN)''','login-error')
        s=replace_once(s, '''                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StardewOutlinedButton(
                            onClick = { AppLogger.copyLogs(context) },''', '''                    if (DeveloperFeaturePolicy.canOpenDiagnostics(context)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StardewOutlinedButton(
                            onClick = { AppLogger.copyLogs(context) },''','login-log-guard')
        s=replace_once(s, '''                        StardewOutlinedButton(
                            onClick = { AppLogger.shareLogs(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.login_share_logs))
                        }
                    }''', '''                        StardewOutlinedButton(
                            onClick = { AppLogger.shareLogs(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.login_share_logs))
                        }
                    }
                    }''','login-log-end')
        return s
    if rel==CONFIG:
        s=replace_once(s,'[wrapper.commands.m23-check]\n', '''[wrapper.commands.r45r2-check]
description = "R45/R46 consolidated audited UI, privacy and state source contracts"
workflow = "r45r2-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''','config-wrapper')
        s=replace_once(s,'[targets.cinderbox.workflows]\n', '''[targets.cinderbox.jobs.r45r2-audit-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r45r2_gate.py"]
inputs = ["scripts/devtool/r45r2_*.py", "scripts/devtool/test_r45r2_gate.py", "app/src/main/java/com/sdvsync/ui/components/R45AreaGuide.kt", "app/src/main/java/com/sdvsync/ui/components/R46ErrorPanel.kt", "app/src/main/java/com/sdvsync/ui/policy/R46UiErrorPolicy.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/LoginScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt", ".devtool.toml", "docs/security/M3_RELEASE_POLICY.json"]
metadata = { read_only = true, area = "r45-r46-audited-production-ui", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r45r2-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r44a", ref = "job:r44-design-system-source", depends_on = ["security"] },
  { id = "r44b", ref = "job:r44b-experience-source", depends_on = ["r44a"] },
  { id = "r45", ref = "job:r45-ui-source", depends_on = ["r44b"] },
  { id = "r45r2", ref = "job:r45r2-audit-source", depends_on = ["r45"] },
]
''','config-workflow')
        tomllib.loads(s)
        return s
    raise AssertionError('R45R2_BAD_PATH:'+rel)


def git(root:Path,*args:str)->str:
    return subprocess.check_output(['git',*args],cwd=root,text=True).strip()

def blob(raw:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()

def expected_intermediate(root:Path,rel:str)->str:
    original=subprocess.check_output(['git','show',BASE+':'+rel],cwd=root).decode('utf-8')
    if rel==LOGIN:
        assert blob(original.encode())==LOGIN_BLOB,'R45R2_LOGIN_BLOB_DRIFT'
        return original
    if rel==CONFIG:
        assert blob(original.encode())==r45a_patch.PINNED[CONFIG], 'R45R2_CONFIG_PIN_DRIFT'
        return r45ui_patch.transform(CONFIG,r45a_patch.migrate(CONFIG,original))
    assert blob(original.encode())==r45ui_patch.PINNED[rel], 'R45R2_SOURCE_DRIFT:'+rel
    return r45ui_patch.transform(rel,original)


def check(root:Path)->None:
    c=tomllib.loads((root/CONFIG).read_text())
    assert c['wrapper']['commands']['r45r2-check']['workflow']=='r45r2-check'
    assert [x['id'] for x in c['targets']['cinderbox']['workflows']['r45r2-check']]==['security','r44a','r44b','r45','r45r2']
    assert c['targets']['cinderbox']['jobs']['r45r2-audit-source']['metadata']['live_update_enabled'] is False
    for rel,marker in [(DASH,'R45AreaGuide(R45GuideArea.SAVES)'),(MODS,'R45AreaGuide(R45GuideArea.MODS)'),
                       (GAME,'R45AreaGuide(R45GuideArea.GAME)'),(MORE,'R45AreaGuide(R45GuideArea.MORE)'),
                       (LOGIN,'R46ErrorPanel(R46UiErrorArea.STEAM_LOGIN)')]:
        assert marker in (root/rel).read_text(), 'R45R2_POST_MISSING:'+marker
    game=(root/GAME).read_text()
    assert game.count('R46ErrorPanel(')>=5,'R45R2_GAME_ERRORS_NOT_COVERED'
    for disallowed in ('state.error!!','state.downloadProgress.errorMessage ?: stringResource',
                       '${smapiProgress.errorMessage}',
                       'stringResource(R.string.smapi_extract_error, smapiProgress.errorMessage)',
                       'stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage)'):
        assert disallowed not in game, 'R45R2_GAME_RAW_LEAK:'+disallowed
    login=(root/LOGIN).read_text()
    assert 'state.message,' not in login and 'if (DeveloperFeaturePolicy.canOpenDiagnostics(context))' in login
    assert 'onClick = { AppLogger.copyLogs(context) }' in login and 'onClick = { AppLogger.shareLogs(context) }' in login
    for rel in (DASH,MODS):
        code=(root/rel).read_text()
        assert 'R44BStatePanel(' in code and 'if (SdvSyncThemeExtras.isEarthy)' in code
        assert 'PixelLoadingSpinner(' in code,'R45R2_CLASSIC_SPINNER_LOST'
    print('R45R2_SOURCE_POSTCONDITION_PASS')


def apply(root:Path)->None:
    assert git(root,'rev-parse','HEAD')==BASE,'R45R2_BASE_DRIFT'
    assert git(root,'rev-parse','HEAD^{tree}')==TREE,'R45R2_TREE_DRIFT'
    changes={}
    for rel in FILES:
        p=root/rel
        assert p.is_file() and not p.is_symlink(),'R45R2_SOURCE_MISSING:'+rel
        before=expected_intermediate(root,rel)
        assert p.read_text(encoding='utf-8')==before,'R45R2_INTERMEDIATE_DRIFT:'+rel
        changes[rel]=transform(rel,before)
    temps={rel:root/(rel+'.r45r2-tmp') for rel in changes}
    for rel,tmp in temps.items():
        assert not tmp.exists() and not tmp.is_symlink(),'R45R2_TEMP_COLLISION:'+rel
    try:
        for rel,tmp in temps.items():
            with tmp.open('x',encoding='utf-8') as f:
                f.write(changes[rel]); f.flush(); os.fsync(f.fileno())
            os.chmod(tmp,(root/rel).stat().st_mode&0o777)
        for rel,tmp in temps.items():os.replace(tmp,root/rel)
    finally:
        for tmp in temps.values():
            if tmp.exists():tmp.unlink()
    check(root)

if __name__=='__main__':
    args=argparse.ArgumentParser()
    x=args.add_mutually_exclusive_group(required=True)
    x.add_argument('--apply',action='store_true')
    x.add_argument('--check',action='store_true')
    a=args.parse_args()
    (apply if a.apply else check)(Path.cwd().resolve())
