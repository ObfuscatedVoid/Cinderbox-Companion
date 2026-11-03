#!/usr/bin/env python3
"""R44 style-only / original Classic / fail-closed source-bound qualification."""
from __future__ import annotations
import json
import re
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]

def read(rel: str) -> str:
    p = ROOT/rel
    assert p.is_file() and not p.is_symlink(), 'R44_MISSING_OR_LINKED:' + rel
    return p.read_text(encoding='utf-8')

def kotlin_code_only(source: str) -> str:
    """Strip Kotlin comments for code-only security checks.

    Documentation mentioning forbidden APIs must not cause false positives.
    These R44 design-token files contain no Kotlin raw-string literals.
    """
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.DOTALL)


def check() -> dict:
    theme = read('app/src/main/java/com/sdvsync/ui/theme/Theme.kt')
    tokens = read('app/src/main/java/com/sdvsync/ui/theme/R44ProductDesignTokens.kt')
    card = read('app/src/main/java/com/sdvsync/ui/components/StardewCard.kt')
    button = read('app/src/main/java/com/sdvsync/ui/components/StardewButton.kt')
    status = read('app/src/main/java/com/sdvsync/ui/components/R44StatusSummary.kt')
    tone = read('app/src/main/java/com/sdvsync/ui/policy/R44StatusTone.kt')
    unit = read('app/src/test/java/com/sdvsync/ui/policy/R44StatusToneTest.kt')
    screen = read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
    old_presentation = read('app/src/main/java/com/sdvsync/ui/policy/R43DDestinationPresentation.kt')
    earthy = read('app/src/main/java/com/sdvsync/ui/theme/EarthyColorSchemes.kt')
    coordinator = read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    ui_policy = read('app/src/main/java/com/sdvsync/ui/policy/DeveloperFeaturePolicy.kt')
    release = json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    cfg = tomllib.loads(read('.devtool.toml'))
    workflow = read('.github/workflows/r44-design-system.yml')
    for key in ('release_enabled','publication_enabled','automatic_device_install',
                'device_qualification_complete'):
        assert release[key] is False, 'R44_RELEASE_GUARD_CHANGED:' + key
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in coordinator
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in coordinator
    assert 'debugBuild && unlocked' in ui_policy and 'BuildConfig.DEBUG' in ui_policy
    assert 'AppearanceStyle.CLASSIC' in theme and 'AppearanceStyle.EARTHY' in theme
    assert 'R44ProductTypography else Typography' in theme
    assert 'R44ProductShapes else StardewShapes' in theme
    assert 'MaterialTheme(' in theme and 'LocalAppearanceStyle provides style' in theme
    assert 'EarthyLightColorScheme' in theme and 'EarthyDarkColorScheme' in theme
    assert 'lightColorScheme(' in earthy and 'darkColorScheme(' in earthy
    for shape in ('extraSmall','small','medium','large','extraLarge'):
        assert shape + ' = RoundedCornerShape(' in tokens, 'R44_SHAPE_MISSING:' + shape
    assert 'fontFamily = NunitoFamily' in tokens and 'fontSize = 36.sp' in tokens
    token_code = kotlin_code_only(tokens)
    assert 'fontScale' not in token_code and 'TextUnit.Unspecified' not in token_code, 'R44_TYPOGRAPHY_SCALING_OVERRIDE'
    assert 'PixelFontFamily' not in tokens, 'R44_EARTHY_PIXEL_TYPOGRAPHY'
    # UI-D02's style-specific literal remains, and Classic still draws the old border.
    assert '.pixelBorder()' in card
    assert 'if (earthy) modifier.fillMaxWidth() else modifier' in card
    assert 'if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh' in card
    assert 'shape = if (earthy) MaterialTheme.shapes.large else RectangleShape' in card
    assert 'clickable(role = Role.Button, onClick = onClick)' in card
    assert button.count('if (SdvSyncThemeExtras.isEarthy) modifier.heightIn(min = 48.dp)') == 2
    assert button.count('else modifier.drawBehind {') == 2
    assert button.count('shape = if (SdvSyncThemeExtras.isEarthy) MaterialTheme.shapes.medium else RectangleShape') == 2
    assert 'border = if (SdvSyncThemeExtras.isEarthy)' in button
    assert 'BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null' in button
    assert 'R44StatusSummary(' in screen and 'R44StatusTone.fromDestination(destinationNotice.kind)' in screen
    assert 'if (SdvSyncThemeExtras.isEarthy)' in screen and 'Classic\'s original presentation unchanged' in screen
    assert 'R43DDestinationPresentation.from(' in screen
    assert 'Existing installations are protected' in screen
    assert 'Live replacement remains disabled until device acceptance.' in screen
    assert 'Cinderbox game location: ${CinderboxPaths.GAME_FILES_DIR}' not in screen
    for word in ('File(', 'delete(', 'Files.move(', 'Runtime.getRuntime()', 'startDownload(', 'startProtectedUpdate('):
        assert word not in status and word not in tone and word not in tokens, 'R44_VISUAL_MUTATION:' + word
    for kind in ('AVAILABLE_FOR_CHECKS','SETUP_REQUIRED','ACCESS_BLOCKED','EXISTING_PROTECTED','MANUAL_REVIEW'):
        assert kind in tone and kind in old_presentation, 'R44_STATUS_KIND_OMITTED:' + kind
    assert unit.count('@Test fun ') >= 2 and 'MANUAL_REVIEW' in unit and 'EXISTING_PROTECTED' in unit
    assert 'R44StatusTone.BLOCKED -> colors.errorContainer' in status
    assert 'R44StatusTone.BLOCKED -> colors.onErrorContainer' in status
    assert 'tone.label' in status and 'Next step: $nextStep' in status
    assert cfg['wrapper']['commands']['r44-check']['workflow'] == 'r44-check'
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r44-check']] == ['security','r43d','r44']
    meta = cfg['targets']['cinderbox']['jobs']['r44-design-system-source']['metadata']
    assert meta['read_only'] is True and meta['live_update_enabled'] is False
    for name in ('r44_gate.py','r43d_gate.py','r43c_gate.py','r43_policy_gate.py','r42_product_gate.py'):
        assert name in workflow, 'R44_CI_MISSING:' + name
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    return {'result':'R44_M3_FOUNDATION_SOURCE_PASS', 'source_only':True,
            'classic_style_preserved':True, 'earthy_m3_enabled':True,
            'ui_device_qualified':False, 'live_updates_enabled':False, 'release_enabled':False}

if __name__ == '__main__':
    try:
        print(json.dumps(check(),sort_keys=True))
    except (AssertionError, KeyError, ValueError, OSError) as exc:
        print('R44_SOURCE_REFUSED:'+str(exc),file=sys.stderr)
        sys.exit(1)
