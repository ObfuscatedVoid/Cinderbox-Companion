package com.sdvsync.steam

import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTwoFactorChallengeTest {
    private val attemptId = 7L

    @Test
    fun `blank submission does not consume pending challenge`() {
        val challenge = PendingTwoFactorChallenge()
        val pendingCode = challenge.awaitCode(attemptId)

        assertFalse(challenge.submit(attemptId, " \n "))
        assertFalse(pendingCode.isDone)

        assertTrue(challenge.submit(attemptId, "A1B2C"))
        assertEquals("A1B2C", pendingCode.join())
    }

    @Test
    fun `submitted code is trimmed and challenge is consumed once`() {
        val challenge = PendingTwoFactorChallenge()
        val pendingCode = challenge.awaitCode(attemptId)

        assertTrue(challenge.submit(attemptId, "  A1B2C\n"))
        assertEquals("A1B2C", pendingCode.join())
        assertFalse(challenge.submit(attemptId, "A1B2C"))
    }

    @Test
    fun `submission without pending challenge is ignored`() {
        val challenge = PendingTwoFactorChallenge()

        assertFalse(challenge.submit(attemptId, "A1B2C"))
    }

    @Test
    fun `new challenge cannot replace unresolved challenge`() {
        val challenge = PendingTwoFactorChallenge()
        val pendingCode = challenge.awaitCode(attemptId)

        assertThrows(IllegalStateException::class.java) {
            challenge.awaitCode(attemptId + 1)
        }
        assertFalse(pendingCode.isDone)
        assertFalse(challenge.submit(attemptId + 1, "A1B2C"))
        assertTrue(challenge.submit(attemptId, "A1B2C"))
        assertEquals("A1B2C", pendingCode.join())
    }

    @Test
    fun `retry challenge installed during completion is preserved`() {
        val challenge = PendingTwoFactorChallenge()
        val firstCode = challenge.awaitCode(attemptId)
        lateinit var retryCode: CompletableFuture<String>
        firstCode.thenRun {
            retryCode = challenge.awaitCode(attemptId)
        }

        assertTrue(challenge.submit(attemptId, "A1B2C"))
        assertEquals("A1B2C", firstCode.join())

        assertTrue(challenge.submit(attemptId, "D3E4F"))
        assertEquals("D3E4F", retryCode.join())
    }

    @Test
    fun `cancelling owner clears and cancels its challenge`() {
        val challenge = PendingTwoFactorChallenge()
        val pendingCode = challenge.awaitCode(attemptId)

        assertFalse(challenge.cancel(attemptId + 1))
        assertFalse(pendingCode.isDone)
        assertTrue(challenge.cancel(attemptId))
        assertTrue(pendingCode.isCancelled)
    }
}
