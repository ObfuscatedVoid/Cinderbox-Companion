package com.sdvsync.download

import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Cold recovery observations never mutate the game tree. */
class R43CRecoveryReadOnlyTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun orphanWithoutPrivateJournalAlwaysRequiresManualReview() {
        val desktop = tmp.newFolder("desktop")
        val target = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
        File(target, "old.bin").writeText("original")
        val privateRoot = tmp.newFolder("private")
        val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-${UUID.randomUUID()}")
        assertTrue(stage.mkdir())
        val original = File(target, "old.bin").readBytes()
        val a = R43CRecoveryReadOnly.inspect(null, target, privateRoot)
        val b = R43CRecoveryReadOnly.inspect(null, target, privateRoot)
        assertTrue(a.manualReview)
        assertEquals(a, b)
        assertFalse(a.rollbackPossible)
        assertFalse(a.recoveryPossible)
        assertArrayEquals(original, File(target, "old.bin").readBytes())
        assertTrue(stage.isDirectory)
    }

    @Test fun fabricatedPublishedStateCannotClaimRollbackWithoutPrivateWitness() {
        val desktop = tmp.newFolder("desktop")
        val target = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
        val privateRoot = tmp.newFolder("private")
        val token = UUID.randomUUID().toString()
        File(target, "game.bin").writeText("new")
        File(target, ".cinderbox-download.session").writeText(token)
        val state = R43BUpdateSwap.State(R43BUpdateSwap.Phase.PUBLISHED, token,
            target, File(desktop, ".GameFiles.cinderbox-m24-stage-$token"),
            File(desktop, ".GameFiles.cinderbox-r43-backup-$token").apply {
                assertTrue(mkdir())
                File(this, "game.bin").writeText("old")
            }, File(desktop, ".GameFiles.cinderbox-r43-rolledback-$token"))
        val before = File(target, "game.bin").readBytes()
        val decision = R43CRecoveryReadOnly.inspect(state, target, privateRoot)
        assertTrue(decision.manualReview)
        assertFalse(decision.rollbackPossible)
        assertFalse(decision.recoveryPossible)
        assertArrayEquals(before, File(target, "game.bin").readBytes())
        assertTrue(state.backup.isDirectory)
    }
}
