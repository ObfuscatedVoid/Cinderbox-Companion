package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic temp directory only: never reaches the real Cinderbox installation. */
class R43BUpdateSwapTest {
    @get:Rule val tmp = TemporaryFolder()

    private data class Fixture(
        val engine: R43BUpdateSwap, val root: File, val target: File,
        val stage: File, val token: String
    )

    private fun fixture(): Fixture {
        val root = tmp.newFolder("StardewValley")
        val desktop = File(root, "desktop")
        assertTrue(desktop.mkdir())
        val target = File(desktop, "GameFiles")
        assertTrue(target.mkdir())
        File(target, "old-game.dll").writeText("OLD")
        // R43 renames only the GameFiles child. The user's saves, mods and
        // smapi-internal siblings may not be inspected or modified by swaps.
        File(desktop, "Saves").mkdir()
        File(desktop, "Mods").mkdir()
        File(root, "smapi-internal").mkdir()
        File(desktop, "Saves/keep.txt").writeText("SAVE")
        File(desktop, "Mods/keep.txt").writeText("MOD")
        File(root, "smapi-internal/keep.txt").writeText("SMAPI")
        val token = UUID.randomUUID().toString()
        val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-$token")
        assertTrue(stage.mkdir())
        File(stage, ".cinderbox-download.session").writeText(token)
        File(stage, "new-game.dll").writeText("NEW")
        val engine = R43BUpdateSwap.forFixture(tmp.newFolder("private-journal"), target)
        return Fixture(engine, root, target, stage, token)
    }

    private fun unchanged(f: Fixture) {
        assertEquals("SAVE", File(f.root, "desktop/Saves/keep.txt").readText())
        assertEquals("MOD", File(f.root, "desktop/Mods/keep.txt").readText())
        assertEquals("SMAPI", File(f.root, "smapi-internal/keep.txt").readText())
    }

    @Test fun noJournalLeavesExistingGameAlone() {
        val f = fixture()
        assertEquals(R43BUpdateSwap.Finding.NO_JOURNAL, f.engine.reconcile())
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        unchanged(f)
    }

    @Test fun preparingNeverOverwritesOriginal() {
        val f = fixture()
        f.engine.recordVerifiedStage(f.token)
        assertEquals(R43BUpdateSwap.Finding.READY_TO_PROMOTE, f.engine.reconcile())
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        assertTrue(f.stage.exists())
        unchanged(f)
    }

    @Test fun publishKeepsPreviousVersionInBackup() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        assertEquals(R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP,
            f.engine.publishVerifiedStage())
        assertEquals("NEW", File(f.target, "new-game.dll").readText())
        assertEquals("OLD", File(state.backup, "old-game.dll").readText())
        assertFalse(f.stage.exists())
        assertEquals(R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP, f.engine.reconcile())
        unchanged(f)
    }

    @Test fun crashAfterOldRenameRestoresOriginal() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        Files.move(f.target.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertFalse(f.target.exists())
        assertEquals(R43BUpdateSwap.Finding.ORIGINAL_RESTORED,
            f.engine.reconcile())
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        assertTrue(f.stage.exists())
        assertFalse(state.backup.exists())
        unchanged(f)
    }

    @Test fun crashAfterStageRenameRecognizesPublishedBackup() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        Files.move(f.target.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Files.move(f.stage.toPath(), f.target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertEquals(R43BUpdateSwap.Finding.PUBLISHED_WITH_BACKUP,
            f.engine.reconcile())
        assertEquals("OLD", File(state.backup, "old-game.dll").readText())
        assertEquals("NEW", File(f.target, "new-game.dll").readText())
        unchanged(f)
    }

    @Test fun explicitRollbackKeepsBothVersions() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        f.engine.publishVerifiedStage()
        assertEquals(R43BUpdateSwap.Finding.ROLLED_BACK_WITH_NEW_PRESERVED,
            f.engine.rollbackPublished())
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        assertEquals("NEW", File(state.displaced, "new-game.dll").readText())
        assertFalse(state.backup.exists())
        unchanged(f)
    }

    @Test fun crashDuringRollbackRestoresOriginalWithoutDeletingNew() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        f.engine.publishVerifiedStage()
        Files.move(f.target.toPath(), state.displaced.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertEquals(R43BUpdateSwap.Finding.ORIGINAL_RESTORED, f.engine.reconcile())
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        assertEquals("NEW", File(state.displaced, "new-game.dll").readText())
        unchanged(f)
    }

    @Test fun targetConflictRequiresManualReviewWithoutOverwrite() {
        val f = fixture()
        val state = f.engine.recordVerifiedStage(f.token)
        Files.move(f.target.toPath(), state.backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertTrue(f.target.mkdir())
        File(f.target, "foreign.txt").writeText("OTHER")
        assertEquals(R43BUpdateSwap.Finding.MANUAL_REVIEW, f.engine.reconcile())
        assertEquals("OTHER", File(f.target, "foreign.txt").readText())
        assertEquals("OLD", File(state.backup, "old-game.dll").readText())
        unchanged(f)
    }

    @Test fun mismatchedStageMarkerFailsClosed() {
        val f = fixture()
        File(f.stage, ".cinderbox-download.session").writeText(UUID.randomUUID().toString())
        try {
            f.engine.recordVerifiedStage(f.token)
            fail("Unowned stage was accepted")
        } catch (_: IllegalStateException) { }
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        unchanged(f)
    }

    @Test fun linkedStageChildFailsClosed() {
        val f = fixture()
        val unsafe = File(f.stage, "untrusted-link")
        Files.createSymbolicLink(unsafe.toPath(), File(f.root, "desktop/Saves").toPath())
        try {
            f.engine.recordVerifiedStage(f.token)
            fail("Linked payload was accepted")
        } catch (_: IllegalStateException) { }
        assertEquals("OLD", File(f.target, "old-game.dll").readText())
        unchanged(f)
    }

    @Test fun duplicateUpdateJournalBlocksSecondTransaction() {
        val f = fixture()
        f.engine.recordVerifiedStage(f.token)
        try {
            f.engine.recordVerifiedStage(f.token)
            fail("Overlapping update was accepted")
        } catch (_: IllegalStateException) { }
        unchanged(f)
    }
}
