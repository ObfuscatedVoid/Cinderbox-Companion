"""Focused host regressions without Android/Steam dependencies."""
import unittest
from r44b_patch import migrate, replace_one, DIALOG, EMPTY, OPERATIONS, HEALTH, CONFIG


class R44BHostContracts(unittest.TestCase):
    def test_ambiguous_anchor_fails_closed(self):
        with self.assertRaisesRegex(AssertionError, 'R44B_ANCHOR_DRIFT'):
            replace_one('abc abc', 'abc', 'new')

    def test_missing_anchor_fails_closed(self):
        with self.assertRaisesRegex(AssertionError, 'R44B_ANCHOR_DRIFT'):
            replace_one('not present', 'wanted', 'new')

    def test_dialog_keeps_classic_pixel_and_adds_earthy_shape(self):
        source = '''import androidx.compose.foundation.layout.height\nimport androidx.compose.ui.graphics.RectangleShape\n'''+'''            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder()
                .padding(8.dp),
            shape = RectangleShape,
            color = if (earthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
'''+'''modifier = Modifier.verticalScroll(rememberScrollState()).padding('''+'''                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
'''
        updated = migrate(DIALOG, source)
        self.assertIn('if (earthy) Modifier.fillMaxWidth() else Modifier', updated)
        self.assertIn('.pixelBorder()', updated)
        self.assertIn('modifier = Modifier.semantics { heading() }', updated)

    def test_empty_state_keeps_d02_palette_contract(self):
        source = '''import androidx.compose.foundation.layout.height\nimport androidx.compose.material3.Text\nimport androidx.compose.ui.graphics.Color\n'''+'''fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {'''+'''        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,'''+'''            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))'''+'''            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
'''+'''palette = if (earthy) listOf('''
        updated = migrate(EMPTY, source)
        self.assertIn('palette = if (earthy) listOf(', updated)
        self.assertIn('onAction != null', updated)
        self.assertIn('heightIn(min = 48.dp)', updated)

    def test_operation_card_retains_classic_action(self):
        source = '''import com.sdvsync.ui.components.StardewTopAppBar\n'''+'''        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {'''+'''private fun CenterStatusCard(card: CenterItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
'''+'''            TextButton(onClick = onClick) { Text(card.action) }
        }
    }
}
'''
        updated = migrate(OPERATIONS, source)
        self.assertIn('R44BStatePanel(', updated)
        self.assertIn('TextButton(onClick = onClick) { Text(card.action) }', updated)
        self.assertIn('Retain Classic', updated)

    def test_health_is_presentation_only(self):
        source = '''import com.sdvsync.ui.components.StardewTopAppBar\n'''+'''        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {'''+'''            } ?: CircularProgressIndicator()\n'''
        updated = migrate(HEALTH, source)
        self.assertIn('R44BStateKind.LOADING', updated)
        self.assertIn('R44BResponsiveColumn(', updated)

    def test_devtool_workflow_source_only(self):
        source = '''[wrapper.commands.m23-check]
description = "yes"
[targets.cinderbox.workflows]
previous-check = ["job:security-contract"]
'''
        updated = migrate(CONFIG, source)
        self.assertIn('r44b-check = [', updated)
        self.assertIn('live_update_enabled = false', updated)


if __name__ == '__main__':
    unittest.main()
