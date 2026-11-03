import unittest
import r43c_gate

class R43CSafetySourceContracts(unittest.TestCase):
    def test_source_policy_still_fail_closed(self):
        result = r43c_gate.check()
        self.assertEqual(result['result'], 'R43C_SOURCE_CONTRACT_PASS')
        self.assertFalse(result['production_updates_enabled'])
        self.assertFalse(result['production_launch_fence_qualified'])
    def test_no_automatic_or_implicit_real_recovery(self):
        source = r43c_gate.read('app/src/main/java/com/sdvsync/download/R43CRecoveryReadOnly.kt')
        self.assertNotIn('Files.move(', source)
        self.assertNotIn('swap.reconcile(', source)
        self.assertNotIn('deleteRecursively(', source)
    def test_production_interlock_not_a_ps_guess(self):
        source = r43c_gate.read('app/src/main/java/com/sdvsync/download/R43CUpdateSafety.kt')
        self.assertIn('GameState.UNKNOWN', source)
        self.assertIn('launchExcluded', source)
        self.assertNotIn('Runtime.getRuntime().exec("ps', source)

if __name__ == '__main__': unittest.main()
