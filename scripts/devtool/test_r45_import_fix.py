"""R45 R3 host-only Kotlin import regression."""
from __future__ import annotations
import unittest
from r45_import_fix import replace_once, transformed, WRONG, RIGHT, GATE, GATE_OLD, GATE_NEW, SCREENS, git_blob

class KotlinImportRegression(unittest.TestCase):
    def test_every_screen_has_explicit_source_pin(self):
        self.assertEqual(4,len(SCREENS))
        self.assertTrue(all(len(s)==40 for s in SCREENS.values()))

    def test_import_transform(self):
        for rel in SCREENS:
            original='package foo\n'+WRONG+'fun stuff() = R45AreaGuide(R45GuideArea.GAME)\n'
            changed=transformed(rel, original)
            self.assertIn(RIGHT,changed)
            self.assertNotIn(WRONG,changed)
            self.assertEqual(original.replace(WRONG,RIGHT),changed)

    def test_no_duplicate_or_missing_anchor(self):
        with self.assertRaises(AssertionError):replace_once('','x','y','missing')
        with self.assertRaises(AssertionError):replace_once('xx','x','y','duplicate')
        with self.assertRaises(AssertionError):replace_once('xy','x','y','already')

    def test_gate_is_hardened(self):
        updated=transformed(GATE,'abc\n'+GATE_OLD+'def')
        self.assertIn('R45R3_MISSING_POLICY_IMPORT',updated)
        self.assertIn('R45R3_WRONG_COMPONENT_IMPORT',updated)
        self.assertEqual(updated.count(GATE_NEW),1)

    def test_git_blob(self):
        import hashlib
        b=b'abc'
        self.assertEqual(git_blob(b),hashlib.sha1(b'blob 3\0abc').hexdigest())

if __name__=='__main__':unittest.main()
