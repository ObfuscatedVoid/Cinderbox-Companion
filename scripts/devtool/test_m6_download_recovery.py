"""M6 source regressions: portable, no Android SDK required."""
from pathlib import Path
import unittest

from ci_workflow_contract import effective_ci_source
ROOT = Path(__file__).resolve().parents[2]
def read(rel): return effective_ci_source(ROOT,rel)

class M6DownloadRecoveryTest(unittest.TestCase):
    def test_resume_requires_owned_app_private_session(self):
        manager = read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
        self.assertIn('GameDownloadRecovery(context.filesDir)', manager)
        self.assertIn('recovery.beginStaged(', manager)
        self.assertIn('recovery.requireMatching(', manager)
        self.assertIn('fun pendingDownload()', manager)
        self.assertIn('fun discardPendingDownload()', manager)
    def test_service_never_bypasses_session(self):
        src = read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
        # The R43 service reuses a single recovery journal instance.
        # Ownership proof must precede depot downloader initialization.
        self.assertIn('val recovery = GameDownloadRecovery(filesDir)', src)
        self.assertIn('recovery.requireMatching(installRoot, branch, os, verify)', src)
        self.assertLess(
            src.index('recovery.requireMatching(installRoot, branch, os, verify)'),
            src.index('depotDownloader = downloader')
        )
        self.assertIn('GameDownloadRecovery(filesDir).completed()', src)
        self.assertIn('GameFileIntegrity(filesDir).verifyAndRecord(', src)
        self.assertNotIn('while (input.read(buffer) != -1) { }',src)
    def test_viewmodel_user_visible_resume(self):
        vm = read('app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt')
        screen = read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
        self.assertIn('hasRecoverableDownload',vm)
        self.assertIn('fun discardInterruptedDownload()',vm)
        self.assertIn('Resume interrupted download',screen)
        self.assertIn('Keep game files',screen)
    def test_verification_is_not_misrepresented(self):
        verify = read('app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt')
        self.assertIn('NOT a replacement for Steam',verify)
        self.assertIn('SHA-256',verify)
        self.assertIn('verifyRecorded(',verify)
    def test_ci_enforces_m6(self):
        for workflow in ('.github/workflows/build.yml',
                         '.github/workflows/devtool-android-build.yml'):
            self.assertIn("test_m6_*.py", read(workflow))
    def test_junit_coverage(self):
        tests=read('app/src/test/java/com/sdvsync/download/GameDownloadRecoveryTest.kt')
        for name in ('interruptedSessionSurvivesReopening',
                     'differentBranchAndDestinationCannotResume',
                     'corruptMetadataFailsClosed',
                     'integrityLedgerDetectsChangesAndMissingFiles'):
            self.assertIn(name,tests)

if __name__ == '__main__': unittest.main()
