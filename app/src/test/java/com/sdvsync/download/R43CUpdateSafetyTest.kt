package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Fail-closed policy fixtures; NOT a game-running proof or real update. */
class R43CUpdateSafetyTest {
    @get:Rule val tmp = TemporaryFolder()
    private val MiB = 1024L * 1024L

    private fun denied(w: R43CUpdateSafety.GameWitness) {
        try { R43CUpdateSafety.requireQuiescence(w); fail("Unsafe witness was accepted") }
        catch (_: IllegalStateException) { }
    }

    @Test fun runningUnknownAndPermissionDeniedNeverAuthorize() {
        for (state in listOf(R43CUpdateSafety.GameState.RUNNING,
                             R43CUpdateSafety.GameState.UNKNOWN)) {
            denied(R43CUpdateSafety.GameWitness {
                R43CUpdateSafety.LaunchObservation(state, true, "fixture")
            })
        }
        denied(R43CUpdateSafety.GameWitness { throw SecurityException("process visibility denied") })
        denied(R43CUpdateSafety.GameWitness {
            R43CUpdateSafety.LaunchObservation(R43CUpdateSafety.GameState.STOPPED,
                false, "ps-alone-is-not-a-fence")
        })
        denied(R43CUpdateSafety.GameWitness {
            R43CUpdateSafety.LaunchObservation(R43CUpdateSafety.GameState.STOPPED,
                true, "")
        })
    }

    @Test fun secondObservationDeniesGameLaunchedAfterFirstCheck() {
        var calls = 0
        val witness = R43CUpdateSafety.GameWitness {
            calls++
            R43CUpdateSafety.LaunchObservation(
                if (calls == 1) R43CUpdateSafety.GameState.STOPPED
                else R43CUpdateSafety.GameState.RUNNING, true, "synthetic-exclusive-fence")
        }
        R43CUpdateSafety.requireQuiescence(witness)
        denied(witness)
        assertEquals(2, calls)
    }

    @Test fun productionIsAlwaysUnknownUntilDeviceQualifiedFenceIsImplemented() {
        try { R43CUpdateSafety.requireProductionQuiescence(); fail("Live unsafe gate opened") }
        catch (_: IllegalStateException) { }
    }

    @Test fun absentSteamSizeAndUnknownCapacityFailClosed() {
        val s = R43CUpdateSafety
        assertEquals(R43CUpdateSafety.CapacityCode.UNKNOWN_STAGE_BYTES,
            s.assessCapacity(4096L*MiB, 1024L*MiB, null, false, true).code)
        assertEquals(R43CUpdateSafety.CapacityCode.UNKNOWN_CAPACITY,
            s.assessCapacity(null, 1024L*MiB, 20L*MiB, false, true).code)
        assertEquals(R43CUpdateSafety.CapacityCode.UNKNOWN_CAPACITY,
            s.assessCapacity(4096L*MiB, 0, 20L*MiB, false, true).code)
    }

    @Test fun budgetHandlesZeroBorderlineShrinkAndOverflow() {
        val s = R43CUpdateSafety
        val total = (256L + 16L + 32L)*MiB
        assertEquals(R43CUpdateSafety.CapacityCode.READY,
            s.assessCapacity(total, 17L*MiB, 32L*MiB, false, true).code)
        assertEquals(R43CUpdateSafety.CapacityCode.INSUFFICIENT_SPACE,
            s.assessCapacity(total-1, 17L*MiB, 32L*MiB, false, true).code)
        assertEquals(R43CUpdateSafety.CapacityCode.INSUFFICIENT_SPACE,
            s.assessCapacity(0, 17L*MiB, 32L*MiB, false, true).code)
        assertEquals(R43CUpdateSafety.CapacityCode.INVALID_BYTE_COUNT,
            s.assessCapacity(Long.MAX_VALUE, 17L*MiB, 32L*MiB,
                false, true, Long.MAX_VALUE, Long.MAX_VALUE).code)
        assertEquals(R43CUpdateSafety.CapacityCode.UNSAFE_FILESTORE,
            s.assessCapacity(Long.MAX_VALUE, 17L*MiB, 32L*MiB, true, false).code)
        // Publication does not require a second stage-size copy.
        assertEquals((256L+16L)*MiB,
            s.assessCapacity(400L*MiB, 17L*MiB, 10L*MiB, true, true).requiredBytes)
    }

    @Test fun linkedStageAndForeignLayoutAreRejectedWithoutMutation() {
        val desktop = tmp.newFolder("desktop")
        val game = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
        File(game, "original.bin").writeText("keep")
        val privateRoot = tmp.newFolder("owner")
        val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-fixture").apply {
            assertTrue(mkdir())
        }
        File(stage, "stage.bin").writeText("new")
        val baseline = File(game, "original.bin").readBytes()
        val proof = R43CUpdateSafety.inspectPreparedStage(stage, game, privateRoot)
        assertNotEquals(R43CUpdateSafety.CapacityCode.INVALID_LAYOUT, proof.code)
        val external = tmp.newFolder("other")
        assertEquals(R43CUpdateSafety.CapacityCode.INVALID_LAYOUT,
            R43CUpdateSafety.inspectPreparedStage(external, game, privateRoot).code)
        val link = File(stage, "alias")
        try {
            Files.createSymbolicLink(link.toPath(), File(game, "original.bin").toPath())
            assertEquals(R43CUpdateSafety.CapacityCode.LINKED_ENTRY,
                R43CUpdateSafety.inspectPreparedStage(stage, game, privateRoot).code)
        } finally { Files.deleteIfExists(link.toPath()) }
        assertArrayEquals(baseline, File(game, "original.bin").readBytes())
    }
}
