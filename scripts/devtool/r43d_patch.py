#!/usr/bin/env python3
"""R43-D exact-pinned destination UI migration: no live-file operations, no rebase."""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib

BASE = 'a307c2d26828c86cd2544a3c0debb58871abaf09'
TREE = '783322acca7759c3ba3819eca0611f98d3234c48'
SCREEN = 'app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt'
VM = 'app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt'
TOML = '.devtool.toml'
PINNED = {
    SCREEN: 'ad1da5cfae6c088684c2a65755bb6799f18eba29',
    VM: '903da8a46d89e601ab0b6d5d66756b875442ec0f',
    TOML: '1d15ee38fce472dfc2ae6c56a933597c01ef3636',
}

def once(source: str, old: str, new: str) -> str:
    count = source.count(old)
    assert count == 1, f'R43D_ANCHOR_DRIFT(count={count}):{old[:90]!r}'
    return source.replace(old, new, 1)

def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def patch(rel: str, source: str) -> str:
    if rel == VM:
        s = once(source,
            'import com.sdvsync.local.CinderboxDownloadDestination\n',
            'import com.sdvsync.local.CinderboxDownloadDestination\n')
        s = once(s,
            '    val destinationCode: M23CinderboxFirstPolicy.Code = M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE,\n',
            '    val destinationCode: M23CinderboxFirstPolicy.Code = M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE,\n'
            '    // Fixed Cinderbox destination; separate from legacy developer-only selection.\n'
            '    val fixedDestinationCode: CinderboxDownloadDestination.Code =\n'
            '        CinderboxDownloadDestination.Code.STORAGE_INACCESSIBLE,\n')
        s = once(s,
            '    init {\n        val defaultDir = Environment.getExternalStoragePublicDirectory(',
            '    /** Never crash the download UI when shared storage is unavailable. */\n'
            '    private fun inspectFixedDestination(): CinderboxDownloadDestination.Code =\n'
            '        runCatching { CinderboxDownloadDestination.inspect(\n'
            '            File(CinderboxPaths.GAME_FILES_DIR)).code }\n'
            '            .getOrDefault(CinderboxDownloadDestination.Code.STORAGE_INACCESSIBLE)\n\n'
            '    init {\n        val defaultDir = Environment.getExternalStoragePublicDirectory(')
        s = once(s,
            '            installDirectory = selectedDir,\n            selectedBranch = pending?.branch ?: "public",',
            '            installDirectory = selectedDir,\n'
            '            fixedDestinationCode = inspectFixedDestination(),\n'
            '            selectedBranch = pending?.branch ?: "public",')
        s = once(s,
            '    fun refreshSelectedDirectoryHealth() {\n        val path = if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {\n'
            '            _state.value.installDirectory\n        } else {\n            CinderboxPaths.GAME_FILES_DIR\n        }\n'
            '        _state.value = _state.value.copy(\n            installDirectory = path,',
            '    fun refreshSelectedDirectoryHealth() {\n        val path = if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {\n'
            '            _state.value.installDirectory\n        } else {\n            CinderboxPaths.GAME_FILES_DIR\n        }\n'
            '        _state.value = _state.value.copy(\n            installDirectory = path,\n'
            '            fixedDestinationCode = inspectFixedDestination(),')
        return s
    if rel == SCREEN:
        s = once(source,
            'import com.sdvsync.ui.policy.DownloadPresentation\n',
            'import com.sdvsync.ui.policy.DownloadPresentation\n'
            'import com.sdvsync.ui.policy.R43DDestinationPresentation\n')
        s = once(s,
            '''                    Text("Main app · Cinderbox", style = MaterialTheme.typography.titleMedium)
                    Text("Game: ${state.primaryGameStatus.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("SMAPI: ${state.primarySmapiStatus.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Cinderbox game location: ${CinderboxPaths.GAME_FILES_DIR}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("An imported game can be used through Cinderbox without " +
                        "downloading another copy. Detection is read-only; versions " +
                        "and Steam integrity are not verified.",
                        style = MaterialTheme.typography.bodySmall)
''',
            '''                    val destinationNotice = R43DDestinationPresentation.from(
                        state.fixedDestinationCode, state.primaryGameStatus,
                        state.protectedUpdateStatus)
                    Text("Game destination · Cinderbox",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(destinationNotice.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(destinationNotice.detail,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Next step: ${destinationNotice.nextStep}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Game: ${R43DDestinationPresentation.installedLabel(state.primaryGameStatus)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("SMAPI: ${R43DDestinationPresentation.installedLabel(state.primarySmapiStatus)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Installation discovery is read-only and does not verify a Steam version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
''')
        s = once(s,
            '''                        Text("Game destination · Cinderbox",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Game files are downloaded only to Cinderbox's GameFiles folder. " +
                            "Existing installations are protected until safe updates are supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
''',
            '''                        Text("Existing-game updates",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Replacing an existing game is disabled in this build. " +
                            "Your files remain protected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
''')
        s = once(s,
            '''                            Text("Protected updates: transaction and rollback candidate ready. " +
                                "Live replacement remains disabled until device acceptance.",''',
            '''                            Text("Existing-game updates are not available yet. " +
                                "Live replacement remains disabled until device acceptance.",''')
        s = once(s,
            '''            Text(state.storageDetail, style = MaterialTheme.typography.bodySmall)
''',
            '''            // Technical alternate-path inspection is restricted to unlocked debug builds.
            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                state.installDirectory != CinderboxPaths.GAME_FILES_DIR) {
                Text(state.storageDetail, style = MaterialTheme.typography.bodySmall)
            }
''')
        return s
    if rel == TOML:
        s = once(source,
            '[wrapper.commands.m23-check]\n',
            '''[wrapper.commands.r43d-check]
description = "R43-D fixed Cinderbox destination UX and developer isolation (host-only)"
workflow = "r43d-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        s = once(s,
            '[targets.cinderbox.workflows]\n',
            '''[targets.cinderbox.jobs.r43d-destination-ux-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43d_gate.py"]
inputs = ["scripts/devtool/r43d_*.py", "scripts/devtool/test_r43d_gate.py", "app/src/main/java/com/sdvsync/ui/policy/R43DDestinationPresentation.kt", "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", "app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt", "app/src/test/java/com/sdvsync/ui/policy/R43DDestinationPresentationTest.kt", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, area = "r43d-fixed-destination-ux", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r43d-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43c", ref = "job:r43c-protected-safety-source", depends_on = ["security"] },
  { id = "r43d", ref = "job:r43d-destination-ux-source", depends_on = ["r43c"] },
]
''')
        assert tomllib.loads(s)
        return s
    raise AssertionError('R43D_UNEXPECTED_TARGET:' + rel)

def check(root: Path) -> None:
    vm = (root / VM).read_text(encoding='utf-8')
    screen = (root / SCREEN).read_text(encoding='utf-8')
    cfg = tomllib.loads((root / TOML).read_text(encoding='utf-8'))
    assert 'fixedDestinationCode = inspectFixedDestination()' in vm
    assert 'R43DDestinationPresentation.from(' in screen
    assert 'Cinderbox game location: ${CinderboxPaths.GAME_FILES_DIR}' not in screen
    assert 'Text(state.storageDetail' in screen
    assert cfg['wrapper']['commands']['r43d-check']['workflow'] == 'r43d-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r43d-check']] == [
        'security', 'r43c', 'r43d']
    assert cfg['targets']['cinderbox']['jobs']['r43d-destination-ux-source']['metadata'][
        'live_update_enabled'] is False
    print('R43D_PATCH_POSTCONDITION_PASS')

def apply(root: Path) -> None:
    assert git(root, 'rev-parse', 'HEAD') == BASE, 'R43D_BASE_DRIFT'
    assert git(root, 'rev-parse', 'HEAD^{tree}') == TREE, 'R43D_TREE_DRIFT'
    assert not git(root, 'diff', '--name-only', '--', *PINNED), 'R43D_PREEXISTING_DIFF'
    edited = {}
    for rel, expected in PINNED.items():
        p = root / rel
        assert p.is_file() and not p.is_symlink(), 'R43D_MISSING_OR_LINKED:' + rel
        raw = p.read_bytes()
        assert blob_sha(raw) == expected, 'R43D_SOURCE_PIN_DRIFT:' + rel
        edited[rel] = patch(rel, raw.decode('utf-8'))
    # All pins, anchors and TOML are checked before ANY writes.
    temp = {rel: root / (rel + '.r43d-tmp') for rel in edited}
    for rel, p in temp.items():
        assert not p.exists() and not p.is_symlink(), 'R43D_TEMP_COLLISION:' + rel
    try:
        for rel, data in edited.items():
            p = temp[rel]
            with p.open('x', encoding='utf-8') as out:
                out.write(data)
                out.flush()
                os.fsync(out.fileno())
            os.chmod(p, (root / rel).stat().st_mode & 0o777)
        for rel, p in temp.items():
            os.replace(p, root / rel)
    finally:
        for p in temp.values():
            if p.exists(): p.unlink()
    check(root)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    switches = parser.add_mutually_exclusive_group(required=True)
    switches.add_argument('--apply', action='store_true')
    switches.add_argument('--check', action='store_true')
    parser.add_argument('--repo', type=Path, default=Path.cwd())
    args = parser.parse_args()
    (apply if args.apply else check)(args.repo.resolve())
