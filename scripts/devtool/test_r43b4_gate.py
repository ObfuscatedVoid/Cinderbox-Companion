"""Consolidated read-only source contracts; Android/JUnit remains GitHub-hosted."""
import unittest
import r43b4_gate
class ConsolidatedR43B4Contracts(unittest.TestCase):
 def test_update_transaction_is_integrated_but_cannot_activate(self):
  outcome=r43b4_gate.check()
  self.assertEqual('R43B4_CONSOLIDATED_SOURCE_PASS',outcome['overall'])
  self.assertFalse(outcome['live_update_enabled'])
  self.assertFalse(outcome['device_qualified'])
 def test_original_m24_no_overwrite_path_survives(self):
  code=r43b4_gate.read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
  self.assertIn('check(!safe.exists()) { "Target appeared during staged transfer; no overwrite" }',code)
  self.assertIn('check(!current.updateMode)',code)
  self.assertIn('fun completeUpdateJournal(token: String)',code)
 def test_rollback_does_not_delete_game_trees(self):
  for p in ('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt',
            'app/src/main/java/com/sdvsync/download/R43BLedgerArchive.kt'):
   code=r43b4_gate.read(p)
   for forbidden in ('deleteRecursively(', 'rm -rf', 'Files.move(stage.toPath(), safe.toPath())'):
    self.assertNotIn(forbidden,code)
 def test_rollback_witness_survives_published_ledger_overwrite(self):
  s=r43b4_gate.read('app/src/main/java/com/sdvsync/download/R43BLedgerArchive.kt')
  self.assertIn('recordedDestination = liveTarget, receiptFile = witness',s)
  self.assertIn('atomicCopy(snapshot(token), installed, replace = true)',s)
 def test_user_content_and_manual_recovery_controls_are_present(self):
  integrity=r43b4_gate.read('app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt')
  self.assertIn('Untracked directory: $relative',integrity)
  manager=r43b4_gate.read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
  for name in ('rollbackProtectedUpdate()', 'reconcileProtectedUpdate()', 'protectedUpdateStatus()'):
   self.assertIn(name,manager)
  screen=r43b4_gate.read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
  for name in ('confirmProtectedRollback','confirmProtectedRecovery'):
   self.assertIn(name,screen)
 def test_service_always_stages_before_choosing_commit_path(self):
  s=r43b4_gate.read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
  self.assertLess(s.index('val stageRoot = recovery.requireActiveStage(installRoot)'),
                  s.index('val checkpoint = GameDownloadRecovery(filesDir).pending()',s.index('private suspend fun verifyFiles')))
if __name__=='__main__':unittest.main()
