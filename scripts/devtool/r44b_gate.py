#!/usr/bin/env python3
"""R44B host-only UI source contract. No Gradle, ADB or filesystem mutation."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    target = ROOT/rel
    assert target.is_file() and not target.is_symlink(), 'R44B_MISSING_OR_LINKED:' + rel
    return target.read_text(encoding='utf-8')


def check() -> dict:
    policy = read('app/src/main/java/com/sdvsync/ui/policy/R44BLayoutPolicy.kt')
    component = read('app/src/main/java/com/sdvsync/ui/components/R44BExperience.kt')
    previews = read('app/src/main/java/com/sdvsync/ui/theme/R44BExperiencePreview.kt')
    tests = read('app/src/test/java/com/sdvsync/ui/policy/R44BLayoutPolicyTest.kt')
    dialog = read('app/src/main/java/com/sdvsync/ui/components/StardewDialog.kt')
    empty = read('app/src/main/java/com/sdvsync/ui/components/EmptyState.kt')
    operation = read('app/src/main/java/com/sdvsync/ui/screens/OperationCenterScreen.kt')
    health = read('app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt')
    old = read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
    r43 = read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    security = json.loads(read('docs/security/M3_RELEASE_POLICY.json'))
    cfg = tomllib.loads(read('.devtool.toml'))
    workflow = read('.github/workflows/r44b-experience.yml')

    for key in ('release_enabled','publication_enabled', 'automatic_device_install',
                'device_qualification_complete'):
        assert security[key] is False, 'R44B_RELEASE_POLICY_DRIFT:' + key
    for guard in ('const val LIVE_UPDATES_ENABLED: Boolean = false',
                  'const val DEVICE_ACCEPTED: Boolean = false'):
        assert guard in r43, 'R44B_PROTECTED_UPDATE_DRIFT'
    assert 'Existing installations are protected' in old, 'R44B_OLD_R43_STATUS_COPY_MISSING'
    assert 'R44StatusSummary(' in old, 'R44B_OLD_R44A_STATUS_MISSING'
    assert 'enum class WidthClass { COMPACT, MEDIUM, EXPANDED }' in policy
    assert 'availableWidthDp < 600' in policy and 'availableWidthDp < 840' in policy
    assert 'else -> WidthClass.EXPANDED' in policy
    assert 'R44BStateKind.fromOperationStatus(card.status.name)' in operation
    assert 'R44BResponsiveColumn(' in operation and 'R44BResponsiveColumn(' in health
    assert 'R44BStatePanel(' in operation and 'R44BStatePanel(' in health
    assert 'card.action' in operation and 'onAction = onClick' in operation
    assert 'Existing installation to repair' in health and 'VerifiedGameRepair(context.filesDir)' in health
    assert 'R44BStateKind.LOADING' in health and 'Reading local installation details' in health
    for required in ('LiveRegionMode.Polite', 'heading()',
                     'heightIn(min = 48.dp)', 'R44BStateKind.NEEDS_ATTENTION',
                     'onAction != null', 'R44BLayoutPolicy.maxContentWidthDp(maxWidth.value.toInt())'):
        assert required in component, 'R44B_COMPONENT_CONTRACT:' + required
    assert 'if (earthy) Modifier.fillMaxWidth() else Modifier' in dialog
    assert '.pixelBorder()' in dialog and 'shape = if (earthy) MaterialTheme.shapes.large else RectangleShape' in dialog
    assert 'modifier = Modifier.semantics { heading() }' in dialog
    assert 'palette = if (earthy) listOf(' in empty  # retain D02 palette split
    assert 'onAction: (() -> Unit)? = null' in empty and 'heightIn(min = 48.dp)' in empty
    assert 'if (!actionLabel.isNullOrBlank() && onAction != null)' in empty
    assert previews.count('@Preview(') >= 4 and 'widthDp = 900' in previews
    assert 'Synthetic and privacy-safe preview' in previews
    assert tests.count('@Test fun ') >= 3
    assert cfg['wrapper']['commands']['r44b-check']['workflow'] == 'r44b-check'
    assert [entry['id'] for entry in cfg['targets']['cinderbox']['workflows']['r44b-check']] == [
        'security','r44a','r44b']
    job = cfg['targets']['cinderbox']['jobs']['r44b-experience-source']
    assert job['execution_environment'] == 'host' and job['runner'] == 'command'
    assert job['metadata']['read_only'] is True
    assert job['metadata']['device_required'] is False
    assert job['metadata']['live_update_enabled'] is False
    for required in ('r44b_patch.py', 'r44b_gate.py', 'r44_gate.py',
                     'r43d_gate.py', 'r43c_gate.py', 'r42_product_gate.py'):
        assert required in workflow, 'R44B_MISSING_PREVIOUS_REGRESSION:' + required
    assert 'gradle ' not in workflow.lower() and 'adb ' not in workflow.lower()
    for disallowed in ('/storage/emulated/0/', 'java.io.File(', 'Runtime.getRuntime()',
                       'R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED = true'):
        assert disallowed not in component and disallowed not in policy, 'R44B_UI_WRITE_LEAK:' + disallowed
    return {'result': 'R44B_ACCESSIBLE_RESPONSIVE_SOURCE_PASS', 'source_only': True,
            'existing_game_update_enabled': False, 'physical_device_qualified': False,
            'release_enabled': False, 'screens_integrated': 2,
            'preview_variants': previews.count('@Preview(')}


if __name__ == '__main__':
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, ValueError, OSError, KeyError) as exc:
        print('R44B_SOURCE_REFUSED:' + str(exc), file=sys.stderr)
        raise SystemExit(1)
