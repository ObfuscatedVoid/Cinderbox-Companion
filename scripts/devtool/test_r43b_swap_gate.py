"""Host checks: Kotlin implementation source and explicit non-activation policy."""
import unittest
from r43b_swap_gate import check

class SwapCoreSource(unittest.TestCase):
    def test_fail_closed_source_policy(self):
        result=check()
        self.assertFalse(result['live_update_enabled'])
        self.assertFalse(result['device_tested'])
        self.assertEqual('PASS_SOURCE_ONLY', result['r43b_swap_source'])

    def test_old_game_recovery_not_modified(self):
        result=check()
        self.assertTrue(result['preexisting_game_untouched'])

if __name__=='__main__': unittest.main()
