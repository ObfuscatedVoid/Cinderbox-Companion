"""Host source assertions; Kotlin JUnit tests run in GitHub Android build."""
import unittest
import r43b3_gate
class R43B3SourceTests(unittest.TestCase):
    def test_isolated_handoff_and_recovery_guards(self):
        r=r43b3_gate.check()
        self.assertEqual(r['overall'],'PASS_R43B3_VERIFIED_STAGE_PREPARATION_SOURCE_ONLY')
        self.assertFalse(r['live_existing_game_update_enabled'])
        self.assertFalse(r['device_qualified'])
    def test_admission_remains_read_only(self):
        s=r43b3_gate.read('app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt')
        for danger in ('Files.move(', 'deleteRecursively(', '.writeText(', 'mkdirs()'):
            self.assertNotIn(danger,s)
if __name__=='__main__': unittest.main()
