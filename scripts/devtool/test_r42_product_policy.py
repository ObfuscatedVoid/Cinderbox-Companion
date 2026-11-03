"""R42 source regression: isolated, no Android device and no Gradle."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("r42_patch", BASE / "r42_patch.py")
patch = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(patch)


class R42PolicyTests(unittest.TestCase):
    def test_fail_closed_anchors(self):
        with self.assertRaisesRegex(RuntimeError, "R42_SOURCE_ANCHOR_REFUSED"):
            patch.replace_once("nothing", "expected", "changed", "negative")
        with self.assertRaisesRegex(RuntimeError, "count=2"):
            patch.replace_once("abc abc", "abc", "changed", "duplicate")

    def test_route_guard_transformation(self):
        original = '''import androidx.compose.ui.Modifier
import com.sdvsync.ui.screens.SteamDiagnosticsScreen
        composable("steam_diagnostics") {
            SteamDiagnosticsScreen(onBack = { navController.popBackStack() })
        }'''
        changed = patch.transform("app/src/main/java/com/sdvsync/MainActivity.kt", original)
        self.assertIn("DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)", changed)
        self.assertIn("LaunchedEffect(Unit) { navController.popBackStack() }", changed)
        self.assertEqual(changed.count('composable("steam_diagnostics")'), 1)

    def test_path_setter_and_picker_denied_by_default(self):
        original = '''import com.sdvsync.logging.AppLogger
    fun updateInstallDirectory(path: String) {
    fun chooseAndroidFolder(uri: Uri) {
'''
        changed = patch.transform("app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt", original)
        self.assertEqual(changed.count("if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context))"), 2)
        self.assertNotIn('adb ', changed)

    def test_download_screen_has_debug_scope_and_truthful_default(self):
        original = '''import com.sdvsync.cinderbox.CinderboxPaths
            // A separate optional Steam download destination; not the imported game.
            // Read-only assessment of the selected path, not a hash verification.
Text("Destination review: ${state.destinationCode.name.replace('_', ' ')}",
"The final Steam manifest source is not independently signed-verified."'''
        changed = patch.transform("app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", original)
        self.assertIn("if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current))", changed)
        self.assertIn("This version downloads Steam game files to a separate folder", changed)
        self.assertIn("DownloadPresentation.destinationStatus(state.destinationCode)", changed)
        self.assertNotIn("Destination review:", changed)

    def test_settings_uses_same_unlock_gate(self):
        original = '''import com.sdvsync.logging.AppLogger
            if (developerUnlocked) {
'''
        changed = patch.transform("app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt", original)
        self.assertIn("developerUnlocked && DeveloperFeaturePolicy.canOpenDiagnostics(context)", changed)

    def test_devtool_has_real_source_dag(self):
        original = '''[wrapper.commands.m23-check]
[targets.cinderbox.workflows]
'''
        changed = patch.transform(".devtool.toml", original)
        self.assertIn("[wrapper.commands.r42-check]", changed)
        self.assertIn('ref = "job:r42-product-source"', changed)
        self.assertIn('execution_environment = "host"', changed)

    def test_workflow_never_builds_or_touches_phone(self):
        workflow = (BASE.parents[1] / ".github/workflows/r42-product-policy.yml").read_text()
        self.assertIn("pull_request:", workflow)
        for command in ("adb ", "gradlew", "assembleRelease", "gh release", "pm uninstall"):
            self.assertNotIn(command, workflow)

    def test_only_expected_sources_patched(self):
        self.assertEqual(len(patch.SOURCE_BLOBS), 6)
        for file in patch.SOURCE_BLOBS:
            self.assertNotIn("r40", file.lower())
            self.assertNotIn("r41", file.lower())
            self.assertNotIn("GameDownloadService", file)
            self.assertNotIn("GameDownloadManager", file)


if __name__ == "__main__":
    unittest.main()
