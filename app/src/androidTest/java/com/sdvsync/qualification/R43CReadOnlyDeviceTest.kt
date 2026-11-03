package com.sdvsync.qualification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.download.R43CUpdateSafety
import com.sdvsync.download.R43CRecoveryReadOnly
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** App-private fixtures only. Absolutely no access to real Cinderbox GameFiles. */
@RunWith(AndroidJUnit4::class)
class R43CReadOnlyDeviceTest {
    @Test fun r43cd_01UnknownGameAndUnfencedStoppedStateStayBlocked() {
        for (state in listOf(R43CUpdateSafety.GameState.UNKNOWN,
            R43CUpdateSafety.GameState.RUNNING, R43CUpdateSafety.GameState.STOPPED)) {
            try {
                R43CUpdateSafety.requireQuiescence(R43CUpdateSafety.GameWitness {
                    R43CUpdateSafety.LaunchObservation(state, false, "fixture-only")
                })
                fail("Unfenced game was accepted")
            } catch (_: IllegalStateException) { }
        }
    }

    @Test fun r43cd_02OrphanAfterSimulatedDataLossIsReadOnly() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val privateRoot = File(ctx.cacheDir, "r43c-device-${UUID.randomUUID()}")
        assertTrue(privateRoot.mkdir())
        try {
            val desktop = File(privateRoot, "desktop").apply { assertTrue(mkdir()) }
            val game = File(desktop, "GameFiles").apply { assertTrue(mkdir()) }
            File(game, "game.bin").writeText("user-original")
            val stage = File(desktop, ".GameFiles.cinderbox-m24-stage-${UUID.randomUUID()}")
                .apply { assertTrue(mkdir()) }
            File(stage, "stage.bin").writeText("partial")
            val receiptGone = File(privateRoot, "private-gone").apply { assertTrue(mkdir()) }
            val before = File(game, "game.bin").readBytes()
            val finding = R43CRecoveryReadOnly.inspect(null, game, receiptGone)
            assertTrue(finding.manualReview)
            assertFalse(finding.recoveryPossible)
            assertArrayEquals(before, File(game, "game.bin").readBytes())
            assertTrue(stage.exists())
        } finally { assertTrue(privateRoot.deleteRecursively()) }
    }

    @Test fun r43cd_03SharedSpaceUncertaintyDoesNotAuthorizeWrites() {
        val a = R43CUpdateSafety.assessCapacity(null, null, null, false, true)
        assertFalse(a.eligible)
        assertEquals(R43CUpdateSafety.CapacityCode.UNKNOWN_STAGE_BYTES, a.code)
        assertFalse(R43CUpdateSafety.assessCapacity(0L, 0L, 1L, false, true).eligible)
    }
}
