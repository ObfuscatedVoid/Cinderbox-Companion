"""M12 contracts: read-only unified state, privacy-safe exports, and discoverable navigation."""
from pathlib import Path
import unittest

from ci_workflow_contract import effective_ci_source
ROOT = Path(__file__).resolve().parents[2]
def read(path): return effective_ci_source(ROOT,path)

class M12OperationCenter(unittest.TestCase):
    def test_dashboard_has_explicit_safety_boundaries(self):
        ui=read('app/src/main/java/com/sdvsync/ui/screens/OperationCenterScreen.kt')
        self.assertIn('OperationCenterPolicy.build(',ui)
        self.assertIn('OperationCenterEvidenceReader.read(context)',ui)
        self.assertIn('GameDownloadManager = koinInject()',ui)
        self.assertIn('ModDownloadManager = koinInject()',ui)
        self.assertIn('ActivityResultContracts.CreateDocument(',ui)
        self.assertIn('"allowlisted-status-only"',ui)
        self.assertNotIn('errorMessage ?: ',ui)
        self.assertNotIn('.cancelDownload()',ui)
        self.assertNotIn('.startDownload(',ui)
        self.assertNotIn('.recover()',ui)
    def test_operation_policy_uses_only_closed_status_categories(self):
        policy=read('app/src/main/java/com/sdvsync/diagnostics/OperationCenterPolicy.kt')
        self.assertIn('enum class CenterStatus',policy)
        self.assertIn('private val journalTitles = mapOf(',policy)
        self.assertIn('if (id !in journals) continue',policy)
        self.assertIn('syncOutcomes.lastOrNull { it in setOf(',policy)
    def test_evidence_is_read_only_app_private_marker_scan(self):
        evidence=read('app/src/main/java/com/sdvsync/diagnostics/OperationCenterEvidence.kt')
        self.assertIn('context.filesDir',evidence)
        self.assertIn('LinkOption.NOFOLLOW_LINKS',evidence)
        for forbidden in ('.readText(', '.inputStream(', '.delete(', '.writeText(', 'Environment.getExternalStorage'):
            self.assertNotIn(forbidden,evidence)
    def test_navigation_is_present_offline_and_logged_in(self):
        activity=read('app/src/main/java/com/sdvsync/MainActivity.kt')
        settings=read('app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt')
        self.assertIn('composable("operation_center")',activity)
        self.assertGreaterEqual(activity.count('onOpenOperationCenter = {'), 2)
        self.assertIn('onOpenOperationCenter: () -> Unit = {}',settings)
        self.assertIn('onClick = onOpenOperationCenter',settings)
    def test_both_ci_workflows_enable_m12_contracts(self):
        for workflow in ('.github/workflows/build.yml','.github/workflows/devtool-android-build.yml'):
            self.assertIn("test_m12_*.py",read(workflow))

if __name__ == '__main__':unittest.main()
