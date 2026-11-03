#!/usr/bin/env python3
"""R43 offline negative/source tests: no Gradle, device, credentials or network."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent


def load(name: str, filename: str):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


patch = load("r43_patch", "r43_patch.py")
gate = load("r43_gate", "r43_policy_gate.py")


class R43OfflineContracts(unittest.TestCase):
    def test_source_is_exact_merged_r42_commit(self):
        self.assertEqual(patch.BASE, "47dc6c1a3cd8659b6fe969d6dd8faad910a77cf5")
        self.assertEqual(len(patch.SOURCE_BLOBS), 6)
        self.assertTrue(all(len(sha) == 40 for sha in patch.SOURCE_BLOBS.values()))

    def test_r40_r41_producer_and_release_files_are_not_finalizer_targets(self):
        self.assertFalse(any("r40" in p or "r41" in p for p in patch.SOURCE_BLOBS))
        self.assertFalse(any("recovery" in p.lower() for p in patch.SOURCE_BLOBS))

    def test_missing_anchor_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "R43_ANCHOR_MISMATCH"):
            patch.replace_once("other", "unique-needle", "replacement", "missing")

    def test_ambiguous_anchor_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "count=2"):
            patch.replace_once("xx xx", "xx", "yy", "duplicate")

    def test_policy_blocks_unknown_location_and_existing_game(self):
        p = (HERE.parent.parent / gate.FILES["policy"]).read_text()
        self.assertIn("Code.WRONG_DESTINATION", p)
        self.assertIn("Code.EXISTING_GAME_PROTECTED", p)
        self.assertIn("Code.EMPTY_GAME_DIRECTORY_PRESENT", p)
        self.assertIn("!selected.isAbsolute", p)
        self.assertIn("PlatformStoragePaths.inspect(selected)", p)

    def test_policy_only_allows_new_or_owned_promotion(self):
        p = (HERE.parent.parent / gate.FILES["policy"]).read_text()
        self.assertIn("assessment.canStartNewDownload ||", p)
        self.assertIn("ownedPromotedRecovery && assessment.code == Code.EXISTING_GAME_PROTECTED", p)
        self.assertIn("StoragePreflight.requireWritable(selected)", p)

    def test_kotlin_cases_are_all_present(self):
        p = (HERE.parent.parent / gate.FILES["kotlin_test"]).read_text()
        for n in ("absentManagedTargetIsAllowed", "wrongDestinationIsNeverProductionEligible",
                  "absentCinderboxRootBlocksWithExplanation", "absentDesktopBlocks",
                  "evenEmptyExistingGameDirectoryIsProtected", "existingGameBytesAreProtected",
                  "anUnrelatedTargetFileIsBlocked"):
            self.assertIn(n, p)

    def test_source_gate_fails_when_required_file_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(AssertionError, "R43_MISSING_OR_SYMLINK"):
                gate.check(Path(tmp))

    def test_source_gate_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = root / "original"
            target.write_text("a")
            link = root / "alias"
            link.symlink_to(target)
            with self.assertRaisesRegex(AssertionError, "R43_MISSING_OR_SYMLINK"):
                gate.read(root, "alias")

    def test_manifest_source_inventory_filenames_are_distinct(self):
        added = [".github/workflows/r43-cinderbox-destination.yml",
                 "app/src/main/java/com/sdvsync/local/CinderboxDownloadDestination.kt",
                 "app/src/test/java/com/sdvsync/local/CinderboxDownloadDestinationTest.kt",
                 "docs/devtool/R43_CINDERBOX_DESTINATION.md",
                 "scripts/devtool/r43_patch.py", "scripts/devtool/r43_policy_gate.py",
                 "scripts/devtool/test_r43_policy.py"]
        self.assertEqual(len(set(added)), len(added))
        self.assertFalse(set(added) & set(patch.SOURCE_BLOBS))


if __name__ == "__main__":
    unittest.main()
