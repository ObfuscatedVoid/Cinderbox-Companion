#!/usr/bin/env python3
"""Exact merged-R44A source-pin migration for visual-only R44B changes."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib

BASE = '47886857021dc395810989fb486c02937a9432a0'
TREE = '2493fed063830e65639513625ce1c38cd3215087'
DIALOG = 'app/src/main/java/com/sdvsync/ui/components/StardewDialog.kt'
EMPTY = 'app/src/main/java/com/sdvsync/ui/components/EmptyState.kt'
OPERATIONS = 'app/src/main/java/com/sdvsync/ui/screens/OperationCenterScreen.kt'
HEALTH = 'app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt'
CONFIG = '.devtool.toml'
PINNED = {
    DIALOG: 'eb34bc7936aa9a9704dfd39cd1bd4e94dafe851e',
    EMPTY: 'fb74c6e3ba5baf7301afe8134a8e609eb7bed88a',
    OPERATIONS: 'a6cc8fcf126b5bf2cf55d7084031beb077023b6b',
    HEALTH: '8352225d084ae12ed95243532fb752bb0503fcd0',
    CONFIG: 'ec4b0c194bef12bc5638247600fbf98771e3e251',
}


def replace_one(source: str, old: str, new: str) -> str:
    count = source.count(old)
    assert count == 1, f'R44B_ANCHOR_DRIFT:{count}:{old[:90]!r}'
    return source.replace(old, new, 1)


def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def migrate(rel: str, source: str) -> str:
    if rel == DIALOG:
        s = replace_one(source,
            'import androidx.compose.foundation.layout.height\n',
            'import androidx.compose.foundation.layout.height\n'
            'import androidx.compose.foundation.layout.heightIn\n')
        s = replace_one(s,
            'import androidx.compose.ui.graphics.RectangleShape\n',
            'import androidx.compose.ui.graphics.RectangleShape\n'
            'import androidx.compose.ui.semantics.heading\n'
            'import androidx.compose.ui.semantics.semantics\n')
        s = replace_one(s,
            '''            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder()
                .padding(8.dp),
            shape = RectangleShape,
            color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
''',
            '''            // Earthy: modern surface. Classic: untouched pixel trim and inset.
            modifier = if (earthy) Modifier.fillMaxWidth() else Modifier
                .fillMaxWidth()
                .pixelBorder()
                .padding(8.dp),
            shape = if (earthy) MaterialTheme.shapes.large else RectangleShape,
            color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (earthy) 3.dp else 0.dp
''')
        s = replace_one(s,
            'modifier = Modifier.verticalScroll(rememberScrollState()).padding(',
            'modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(')
        s = replace_one(s,
            '''                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
''',
            '''                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
''')
        return s
    if rel == EMPTY:
        s = replace_one(source,
            'import androidx.compose.foundation.layout.height\n',
            'import androidx.compose.foundation.layout.height\n'
            'import androidx.compose.foundation.layout.heightIn\n'
            'import androidx.compose.foundation.layout.padding\n')
        s = replace_one(s,
            'import androidx.compose.material3.Text\n',
            'import androidx.compose.material3.Text\n'
            'import androidx.compose.material3.TextButton\n')
        s = replace_one(s,
            'import androidx.compose.ui.graphics.Color\n',
            'import androidx.compose.ui.graphics.Color\n'
            'import androidx.compose.ui.semantics.heading\n'
            'import androidx.compose.ui.semantics.semantics\n')
        s = replace_one(s,
            'fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {',
            '''fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {''')
        s = replace_one(s,
            '        modifier = modifier,\n        horizontalAlignment = Alignment.CenterHorizontally,',
            '        modifier = modifier.padding(horizontal = 16.dp),\n        horizontalAlignment = Alignment.CenterHorizontally,')
        s = replace_one(s,
            '''            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))''',
            '''            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(8.dp))''')
        s = replace_one(s,
            '''            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
''',
            '''            color = if (earthy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        // Optional explicit navigation only; an empty-state message never starts work.
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel)
            }
        }
    }
}
''')
        return s
    if rel == OPERATIONS:
        s = replace_one(source,
            'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R44BResponsiveColumn\n'
            'import com.sdvsync.ui.components.R44BStatePanel\n'
            'import com.sdvsync.ui.policy.R44BStateKind\n'
            'import com.sdvsync.ui.theme.SdvSyncThemeExtras\n')
        s = replace_one(s,
            '        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {',
            '        R44BResponsiveColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {')
        s = replace_one(s,
            '''private fun CenterStatusCard(card: CenterItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
''',
            '''private fun CenterStatusCard(card: CenterItem, onClick: () -> Unit) {
    if (SdvSyncThemeExtras.isEarthy) {
        R44BStatePanel(
            kind = R44BStateKind.fromOperationStatus(card.status.name),
            title = card.title,
            description = card.detail,
            supportingText = card.percent?.let { "$it% reported" },
            actionLabel = card.action,
            onAction = onClick
        )
    } else {
        // Retain Classic's original status card and actions.
        Card(Modifier.fillMaxWidth()) {
''')
        # Place additional closing brace only at the end of this file/function.
        assert s.endswith('            TextButton(onClick = onClick) { Text(card.action) }\n        }\n    }\n}\n'), 'R44B_OPERATION_TAIL_DRIFT'
        s = s[:-len('            TextButton(onClick = onClick) { Text(card.action) }\n        }\n    }\n}\n')] + \
            '            TextButton(onClick = onClick) { Text(card.action) }\n        }\n        }\n    }\n}\n'
        return s
    if rel == HEALTH:
        s = replace_one(source,
            'import com.sdvsync.ui.components.StardewTopAppBar\n',
            'import com.sdvsync.ui.components.StardewTopAppBar\n'
            'import com.sdvsync.ui.components.R44BResponsiveColumn\n'
            'import com.sdvsync.ui.components.R44BStatePanel\n'
            'import com.sdvsync.ui.policy.R44BStateKind\n')
        s = replace_one(s,
            '        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {',
            '        R44BResponsiveColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {')
        s = replace_one(s,
            '            } ?: CircularProgressIndicator()\n',
            '''            } ?: R44BStatePanel(
                kind = R44BStateKind.LOADING,
                title = "Checking installation",
                description = "Reading local installation details without changing game files."
            )
''')
        return s
    if rel == CONFIG:
        s = replace_one(source, '[wrapper.commands.m23-check]\n',
            '''[wrapper.commands.r44b-check]
description = "R44-B dialogs, responsive states, accessibility and visual-only safety"
workflow = "r44b-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        s = replace_one(s, '[targets.cinderbox.workflows]\n',
            '''[targets.cinderbox.jobs.r44b-experience-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r44b_gate.py"]
inputs = ["scripts/devtool/r44b_*.py", "scripts/devtool/test_r44b_gate.py", "app/src/main/java/com/sdvsync/ui/components/R44BExperience.kt", "app/src/main/java/com/sdvsync/ui/components/StardewDialog.kt", "app/src/main/java/com/sdvsync/ui/components/EmptyState.kt", "app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt", "app/src/main/java/com/sdvsync/ui/screens/OperationCenterScreen.kt", "app/src/main/java/com/sdvsync/ui/policy/R44BLayoutPolicy.kt", "app/src/test/java/com/sdvsync/ui/policy/R44BLayoutPolicyTest.kt", "app/src/main/java/com/sdvsync/ui/theme/R44BExperiencePreview.kt", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, area = "r44b-responsive-accessible-ui", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r44b-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r44a", ref = "job:r44-design-system-source", depends_on = ["security"] },
  { id = "r44b", ref = "job:r44b-experience-source", depends_on = ["r44a"] },
]
''')
        assert tomllib.loads(s), 'R44B_BAD_DEVTOOL_TOML'
        return s
    raise AssertionError('R44B_UNEXPECTED_PATH:' + rel)


def check(root: Path) -> None:
    dialog = (root/DIALOG).read_text(encoding='utf-8')
    empty = (root/EMPTY).read_text(encoding='utf-8')
    operation = (root/OPERATIONS).read_text(encoding='utf-8')
    health = (root/HEALTH).read_text(encoding='utf-8')
    cfg = tomllib.loads((root/CONFIG).read_text(encoding='utf-8'))
    assert 'MaterialTheme.shapes.large else RectangleShape' in dialog
    assert '.pixelBorder()' in dialog and 'if (earthy) Modifier.fillMaxWidth() else Modifier' in dialog
    assert 'modifier = Modifier.semantics { heading() }' in dialog
    assert 'palette = if (earthy) listOf(' in empty
    assert 'onAction: (() -> Unit)? = null' in empty and 'heightIn(min = 48.dp)' in empty
    assert 'R44BResponsiveColumn(' in operation and 'R44BResponsiveColumn(' in health
    assert 'R44BStateKind.fromOperationStatus(card.status.name)' in operation
    assert 'R44BStatePanel(' in operation and 'R44BStatePanel(' in health
    assert 'VerifiedGameRepair(context.filesDir)' in health  # original backend untouched
    assert cfg['wrapper']['commands']['r44b-check']['workflow'] == 'r44b-check'
    assert [e['id'] for e in cfg['targets']['cinderbox']['workflows']['r44b-check']] == [
        'security','r44a','r44b']
    assert cfg['targets']['cinderbox']['jobs']['r44b-experience-source']['metadata']['read_only'] is True
    assert cfg['targets']['cinderbox']['jobs']['r44b-experience-source']['metadata']['live_update_enabled'] is False
    print('R44B_PATCH_POSTCONDITION_PASS')


def apply(root: Path) -> None:
    assert git(root, 'rev-parse', 'HEAD') == BASE, 'R44B_BASE_DRIFT'
    assert git(root, 'rev-parse', 'HEAD^{tree}') == TREE, 'R44B_TREE_DRIFT'
    assert git(root, 'diff', '--name-only', '--', *PINNED) == '', 'R44B_PREEXISTING_MODIFICATION'
    changed = {}
    for rel, expected_sha in PINNED.items():
        path = root/rel
        assert path.is_file() and not path.is_symlink(), 'R44B_MISSING_OR_LINKED:' + rel
        raw = path.read_bytes()
        assert blob_sha(raw) == expected_sha, 'R44B_SOURCE_PIN_DRIFT:' + rel
        changed[rel] = migrate(rel, raw.decode('utf-8'))
    assert tomllib.loads(changed[CONFIG])
    # Verify every source and every anchor before any mutation.
    temps = {rel: root/(rel+'.r44b-tmp') for rel in changed}
    for rel, temp in temps.items():
        assert not temp.exists() and not temp.is_symlink(), 'R44B_TEMP_COLLISION:' + rel
    try:
        for rel, new in changed.items():
            original = root/rel
            temp = temps[rel]
            with temp.open('x', encoding='utf-8') as out:
                out.write(new)
                out.flush()
                os.fsync(out.fileno())
            os.chmod(temp, original.stat().st_mode & 0o777)
        for rel, temp in temps.items():
            os.replace(temp, root/rel)
    finally:
        for temp in temps.values():
            if temp.exists():
                temp.unlink()
    check(root)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    (apply if args.apply else check)(Path.cwd().resolve())
