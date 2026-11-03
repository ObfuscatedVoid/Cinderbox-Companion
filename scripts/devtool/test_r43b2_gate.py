"""R43-B2 host test entrypoint. Actual Kotlin tests run on hosted Android CI."""
import unittest
import r43b2_gate

class R43B2ReadOnlyContract(unittest.TestCase):
    def test_source_and_config_bound(self):
        result=r43b2_gate.check()
        self.assertEqual(result['overall'],'PASS_R43B2_READ_ONLY_ADMISSION_SOURCE')
        self.assertFalse(result['live_update_enabled'])
        self.assertFalse(result['device_qualified'])

    def test_gate_rejects_mutations_to_installation(self):
        source=r43b2_gate.read('app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt')
        for snippet in ('Files.move(', 'deleteRecursively(', '.writeText(', 'mkdirs()'):
            self.assertNotIn(snippet,source)

if __name__=='__main__': unittest.main()
