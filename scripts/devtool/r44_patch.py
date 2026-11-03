#!/usr/bin/env python3
"""Exact-tree R44 visual-only migration; guarded source edits, no game access."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib

BASE = '8079c692dc997a28187457ffaed37faa4c1e5da6'
TREE = '272e163a9f14fbba2d5ea8e77c9044cc28e65ec3'
THEME = 'app/src/main/java/com/sdvsync/ui/theme/Theme.kt'
CARD = 'app/src/main/java/com/sdvsync/ui/components/StardewCard.kt'
BUTTON = 'app/src/main/java/com/sdvsync/ui/components/StardewButton.kt'
SCREEN = 'app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt'
TOML = '.devtool.toml'
PINNED = {
    THEME: '4b2756e0e1fb1fb30acb1c75b535168ba6d3cbf2',
    CARD: 'bb3ef2ef103ece699826f1030a95deb5ec88b21a',
    BUTTON: '28085b9e2cfe50d8b345e688a8035e037b7794c7',
    SCREEN: '73d7bfbb51706e84b542c3df0d9c0aa8a4fa7913',
    TOML: 'e013a1cffd8e3a4d3f0fec0544feb647b5d967d8',
}

def replace_one(src: str, old: str, new: str) -> str:
    count = src.count(old)
    assert count == 1, 'R44_ANCHOR_DRIFT:' + repr(old[:90]) + ':count=' + str(count)
    return src.replace(old, new, 1)

def blob_sha(b: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\x00' + b).hexdigest()

def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def migrate(rel: str, source: str) -> str:
    if rel == THEME:
        s = replace_one(source,
            '            typography = Typography,\n            shapes = StardewShapes,',
            '            typography = if (style == AppearanceStyle.EARTHY) R44ProductTypography else Typography,\n'
            '            shapes = if (style == AppearanceStyle.EARTHY) R44ProductShapes else StardewShapes,')
        return s
    if rel == CARD:
        s = replace_one(source,
            'import androidx.compose.ui.Modifier\n',
            'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.semantics.Role\n')
        s = replace_one(s,
            '''    val cardModifier = modifier
        .fillMaxWidth()
        .pixelBorder()
        .padding(8.dp) // inner padding matching the 4-layer border width
''',
            '''    // R44: Earthy uses Material 3 surfaces. Classic retains its pixel border.
    val cardModifier = if (earthy) modifier.fillMaxWidth() else modifier
        .fillMaxWidth()
        .pixelBorder()
        .padding(8.dp) // Classic's original 4-layer border inset
''')
        s = replace_one(s, 'cardModifier.clickable(onClick = onClick)',
            'cardModifier.clickable(role = Role.Button, onClick = onClick)')
        s = replace_one(s,
            '        shape = RectangleShape,\n        color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh\n',
            '        shape = if (earthy) MaterialTheme.shapes.large else RectangleShape,\n'
            '        color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,\n'
            '        tonalElevation = if (earthy) 2.dp else 0.dp,\n'
            '        shadowElevation = if (earthy) 1.dp else 0.dp\n')
        return s
    if rel == BUTTON:
        s = replace_one(source,
            'import androidx.compose.foundation.layout.PaddingValues\n',
            'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.layout.PaddingValues\n'
            'import androidx.compose.foundation.layout.heightIn\n')
        old = '        modifier = modifier.drawBehind {\n'
        assert s.count(old) == 2, 'R44_BUTTON_CLASSIC_BEVEL_COUNT_DRIFT'
        s = s.replace(old,
            '        // Earthy: rounded, responsive minimum 48dp touch target; Classic: pixel bevel.\n'
            '        modifier = (if (SdvSyncThemeExtras.isEarthy) modifier.heightIn(min = 48.dp)\n'
            '                    else modifier.drawBehind {\n')
        old = '        },\n        enabled = enabled,\n        shape = RectangleShape,\n'
        assert s.count(old) == 2, 'R44_BUTTON_CLOSURE_COUNT_DRIFT'
        s = s.replace(old,
            '        }),\n        enabled = enabled,\n'
            '        shape = if (SdvSyncThemeExtras.isEarthy) MaterialTheme.shapes.medium else RectangleShape,\n')
        s = replace_one(s,
            '        border = null,\n        content = content\n',
            '        border = if (SdvSyncThemeExtras.isEarthy)\n'
            '            BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,\n'
            '        content = content\n')
        return s
    if rel == SCREEN:
        s = replace_one(source,
            'import com.sdvsync.ui.policy.R43DDestinationPresentation\n',
            'import com.sdvsync.ui.policy.R43DDestinationPresentation\n'
            'import com.sdvsync.ui.policy.R44StatusTone\n'
            'import com.sdvsync.ui.components.R44StatusSummary\n'
            'import com.sdvsync.ui.theme.SdvSyncThemeExtras\n')
        s = replace_one(s,
            '''                    Text(destinationNotice.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(destinationNotice.detail,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Next step: ${destinationNotice.nextStep}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
''',
            '''                    if (SdvSyncThemeExtras.isEarthy) {
                        // Visual status only; actual download authorization remains in Manager/Service.
                        R44StatusSummary(
                            title = destinationNotice.title,
                            detail = destinationNotice.detail,
                            nextStep = destinationNotice.nextStep,
                            tone = R44StatusTone.fromDestination(destinationNotice.kind)
                        )
                    } else {
                        // Keep Classic's original presentation unchanged.
                        Text(destinationNotice.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(destinationNotice.detail,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Next step: ${destinationNotice.nextStep}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
''')
        return s
    if rel == TOML:
        s = replace_one(source, '[wrapper.commands.m23-check]\n',
            '''[wrapper.commands.r44-check]
description = "R44 Material 3 Earthy foundation, Classic preservation and safety source contracts"
workflow = "r44-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        s = replace_one(s, '[targets.cinderbox.workflows]\n',
            '''[targets.cinderbox.jobs.r44-design-system-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r44_gate.py"]
inputs = ["scripts/devtool/r44_*.py", "scripts/devtool/test_r44_gate.py", "app/src/main/java/com/sdvsync/ui/theme/Theme.kt", "app/src/main/java/com/sdvsync/ui/theme/R44ProductDesignTokens.kt", "app/src/main/java/com/sdvsync/ui/components/StardewCard.kt", "app/src/main/java/com/sdvsync/ui/components/StardewButton.kt", "app/src/main/java/com/sdvsync/ui/components/R44StatusSummary.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", "app/src/main/java/com/sdvsync/ui/policy/R44StatusTone.kt", "app/src/test/java/com/sdvsync/ui/policy/R44StatusToneTest.kt", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, area = "r44-material3-earthy-design-foundation", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r44-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43d", ref = "job:r43d-destination-ux-source", depends_on = ["security"] },
  { id = "r44", ref = "job:r44-design-system-source", depends_on = ["r43d"] },
]
''')
        assert tomllib.loads(s)
        return s
    raise AssertionError('R44_UNEXPECTED_PATH:' + rel)

def check(root: Path) -> None:
    theme = (root/THEME).read_text(encoding='utf-8')
    card = (root/CARD).read_text(encoding='utf-8')
    button = (root/BUTTON).read_text(encoding='utf-8')
    screen = (root/SCREEN).read_text(encoding='utf-8')
    cfg = tomllib.loads((root/TOML).read_text(encoding='utf-8'))
    assert 'if (style == AppearanceStyle.EARTHY) R44ProductTypography else Typography' in theme
    assert 'if (style == AppearanceStyle.EARTHY) R44ProductShapes else StardewShapes' in theme
    assert '.pixelBorder()' in card and 'if (earthy) modifier.fillMaxWidth() else modifier' in card
    assert 'if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh' in card
    assert 'cardModifier.clickable(role = Role.Button, onClick = onClick)' in card
    assert button.count('if (SdvSyncThemeExtras.isEarthy) modifier.heightIn(min = 48.dp)') == 2
    assert button.count('else modifier.drawBehind {') == 2
    assert button.count('MaterialTheme.shapes.medium else RectangleShape') == 2
    assert 'R44StatusSummary(' in screen and 'R44StatusTone.fromDestination(destinationNotice.kind)' in screen
    assert 'Existing installations are protected' in screen
    assert 'Live replacement remains disabled until device acceptance.' in screen
    assert cfg['wrapper']['commands']['r44-check']['workflow'] == 'r44-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r44-check']] == ['security','r43d','r44']
    assert cfg['targets']['cinderbox']['jobs']['r44-design-system-source']['metadata']['read_only'] is True
    assert cfg['targets']['cinderbox']['jobs']['r44-design-system-source']['metadata']['live_update_enabled'] is False
    print('R44_PATCH_POSTCONDITION_PASS')

def apply(root: Path) -> None:
    assert git(root,'rev-parse','HEAD') == BASE, 'R44_BASE_DRIFT'
    assert git(root,'rev-parse','HEAD^{tree}') == TREE, 'R44_TREE_DRIFT'
    assert git(root,'diff','--name-only','--',*PINNED) == '', 'R44_PREEXISTING_TRACKED_DIFF'
    patched = {}
    for rel, expected in PINNED.items():
        path = root / rel
        assert path.is_file() and not path.is_symlink(), 'R44_FILE_MISSING_OR_SYMLINK:' + rel
        raw = path.read_bytes()
        assert blob_sha(raw) == expected, 'R44_SOURCE_PIN_DRIFT:' + rel
        patched[rel] = migrate(rel, raw.decode('utf-8'))
    assert tomllib.loads(patched[TOML])
    # No mutations until every pinned source and anchor is validated.
    temps = {rel:root/(rel+'.r44-tmp') for rel in patched}
    for rel, temp in temps.items():
        assert not temp.exists() and not temp.is_symlink(), 'R44_TEMP_COLLISION:' + rel
    try:
        for rel, new in patched.items():
            path = root / rel
            temp = temps[rel]
            with temp.open('x',encoding='utf-8') as h:
                h.write(new)
                h.flush()
                os.fsync(h.fileno())
            os.chmod(temp, path.stat().st_mode & 0o777)
        for rel, temp in temps.items():
            os.replace(temp,root/rel)
    finally:
        for temp in temps.values():
            if temp.exists(): temp.unlink()
    check(root)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--check',action='store_true')
    args = parser.parse_args()
    (apply if args.apply else check)(Path.cwd().resolve())
