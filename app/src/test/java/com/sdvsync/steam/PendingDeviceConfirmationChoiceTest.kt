package com.sdvsync.steam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeviceConfirmationChoiceTest {
    private val attemptId = 11L

    @Test
    fun `mobile approval choice completes pending decision`() {
        val choice = PendingDeviceConfirmationChoice()
        val pendingChoice = choice.awaitChoice(attemptId)

        assertTrue(choice.submit(attemptId, useMobileApproval = true))
        assertTrue(pendingChoice.join())
    }

    @Test
    fun `code fallback choice completes pending decision`() {
        val choice = PendingDeviceConfirmationChoice()
        val pendingChoice = choice.awaitChoice(attemptId)

        assertTrue(choice.submit(attemptId, useMobileApproval = false))
        assertFalse(pendingChoice.join())
    }

    @Test
    fun `choice is accepted only once`() {
        val choice = PendingDeviceConfirmationChoice()
        choice.awaitChoice(attemptId)

        assertTrue(choice.submit(attemptId, useMobileApproval = true))
        assertFalse(choice.submit(attemptId, useMobileApproval = false))
    }

    @Test
    fun `new choice cannot replace unresolved choice`() {
        val choice = PendingDeviceConfirmationChoice()
        val pendingChoice = choice.awaitChoice(attemptId)

        assertThrows(IllegalStateException::class.java) {
            choice.awaitChoice(attemptId + 1)
        }
        assertFalse(pendingChoice.isDone)
        assertFalse(choice.submit(attemptId + 1, useMobileApproval = false))
        assertTrue(choice.submit(attemptId, useMobileApproval = false))
        assertFalse(pendingChoice.join())
    }

    @Test
    fun `cancelling owner clears and cancels its choice`() {
        val choice = PendingDeviceConfirmationChoice()
        val pendingChoice = choice.awaitChoice(attemptId)

        assertFalse(choice.cancel(attemptId + 1))
        assertFalse(pendingChoice.isDone)
        assertTrue(choice.cancel(attemptId))
        assertTrue(pendingChoice.isCancelled)
    }
}
