"""R45-A host source regression tests: explicit navigation and fail-closed anchors."""
from pathlib import Path
import tempfile
import unittest

import r45a_gate
from r45a_patch import replace_exact, migrate, BOTTOM, STRINGS

class R45ANavigationSourceTests(unittest.TestCase):
    def test_exact_new_navigation_replaces_only_one_legacy_bar(self):
        old = '''enum class BottomTab(val route: String, val labelRes: Int) {
    SAVES("saves", R.string.tab_saves),
    MODS("mods", R.string.tab_mods),
    DOWNLOAD("game_download", R.string.tab_download),
    SETTINGS("settings", R.string.tab_settings)
}
    val iconData = when (tab) {
        BottomTab.SAVES -> WheatData
'''
        new = migrate(BOTTOM, old)
        self.assertIn('HOME("home", R.string.tab_home)', new)
        self.assertIn('BottomTab.HOME -> R45HomeIconData', new)
        self.assertIn('SETTINGS("settings", R.string.tab_more)', new)
        self.assertEqual(new.count('DOWNLOAD("game_download"'), 1)

    def test_strings_keep_legacy_keys(self):
        updated = migrate(STRINGS, '''    <!-- Bottom navigation tabs -->
    <string name="tab_saves">Saves</string>
    <string name="tab_download">Download</string>
    <string name="tab_settings">Settings</string>
''')
        for key in ('tab_home','tab_game','tab_more','tab_saves','tab_download','tab_settings'):
            self.assertEqual(updated.count('name="'+key+'"'), 1)

    def test_ambiguous_anchor_refused(self):
        with self.assertRaises(AssertionError):
            replace_exact('anchor\nanchor\n','anchor','new')

    def test_missing_anchor_refused(self):
        with self.assertRaises(AssertionError):
            replace_exact('unrelated','missing','new')

    def test_source_gate_on_real_transaction_worktree(self):
        self.assertEqual(r45a_gate.check()['primary_tabs'], 5)

    def test_release_boundary_and_navigation_only_home(self):
        root = r45a_gate.ROOT
        txt=(root/'app/src/main/java/com/sdvsync/ui/screens/HomeScreen.kt').read_text()
        self.assertNotIn('File(', txt)
        self.assertNotIn('startProtectedUpdate(',txt)
        self.assertIn('Shortcuts do not change files',txt)
        self.assertIn('HomeScreen(',txt)

if __name__ == '__main__':
    unittest.main()
