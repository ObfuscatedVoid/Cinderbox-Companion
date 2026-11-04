"""Host-only R45 consolidated UI and R46 error-gate regression tests."""
import unittest
import r45ui_gate
from pathlib import Path

class R45UiHostTests(unittest.TestCase):
    def test_integrated_four_sections_and_home(self):
        r=r45ui_gate.check()
        self.assertEqual(r['primary_tabs'],5)
        self.assertEqual(r['screen_sections'],4)
        self.assertTrue(r['source_only'])

    def test_friendly_error_copy_has_explicit_retry(self):
        a=r45ui_gate.read('app/src/main/java/com/sdvsync/ui/components/R46ErrorPanel.kt')
        self.assertIn('onAction = onRetry',a)
        self.assertNotIn('rawMessage',a)

    def test_raw_sync_errors_are_developer_gated(self):
        a=r45ui_gate.read('app/src/main/java/com/sdvsync/ui/components/SyncErrorCard.kt')
        self.assertIn('DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)',a)
        self.assertIn('rawMessage.isNotBlank()',a)

    def test_read_only_policy_and_release_interlocks(self):
        r=r45ui_gate.check()
        self.assertFalse(r['game_update_enabled'])
        self.assertFalse(r['release_enabled'])
        self.assertFalse(r['device_accepted'])

    def test_seven_error_contexts_are_covered(self):
        s=r45ui_gate.read('app/src/main/java/com/sdvsync/ui/policy/R46UiErrorPolicy.kt')
        for area in ('SAVES','MODS','GAME','GAME_DOWNLOAD','SMAPI','CINDERBOX','STEAM_LOGIN'):
            self.assertIn(area,s)

    def test_preview_is_synthetic(self):
        s=r45ui_gate.read('app/src/main/java/com/sdvsync/ui/theme/R45FullUiPreviews.kt')
        self.assertIn('Synthetic privacy-safe layout sample',s)
        self.assertGreaterEqual(s.count('@Preview('),4)

if __name__=='__main__': unittest.main()
