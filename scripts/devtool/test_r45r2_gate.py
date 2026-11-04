"""R45/R46 consolidated R2 audit contracts and safety regressions."""
import unittest
import r45r2_gate

class R45R2HostTests(unittest.TestCase):
    def test_real_screen_integration(self):
        report=r45r2_gate.check()
        self.assertEqual(report['guided_areas'],4)
        self.assertEqual(report['primary_tabs'],5)

    def test_actual_game_errors_are_not_raw(self):
        game=r45r2_gate.read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
        self.assertNotIn('state.error!!',game)
        self.assertNotIn('${smapiProgress.errorMessage}',game)
        self.assertNotIn('stringResource(R.string.cinderbox_download_error, cinderboxProgress.errorMessage)',game)
        self.assertGreaterEqual(game.count('R46ErrorPanel('),5)

    def test_login_logs_and_raw_errors_are_debug_only(self):
        login=r45r2_gate.read('app/src/main/java/com/sdvsync/ui/screens/LoginScreen.kt')
        self.assertNotIn('state.message,',login)
        self.assertIn('if (DeveloperFeaturePolicy.canOpenDiagnostics(context))',login)

    def test_classic_and_explicit_callbacks_preserved(self):
        saves=r45r2_gate.read('app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt')
        mods=r45r2_gate.read('app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt')
        self.assertIn('PixelLoadingSpinner(',saves)
        self.assertIn('PixelLoadingSpinner(',mods)
        self.assertIn('onRetry = { viewModel.refresh() }',saves)
        self.assertIn('onRetry = { viewModel.loadInstalledMods() }',mods)

    def test_release_and_protected_update_remain_disabled(self):
        report=r45r2_gate.check()
        self.assertFalse(report['release_enabled'])
        self.assertFalse(report['existing_game_update_enabled'])
        self.assertFalse(report['device_qualified'])

if __name__=='__main__':unittest.main()
