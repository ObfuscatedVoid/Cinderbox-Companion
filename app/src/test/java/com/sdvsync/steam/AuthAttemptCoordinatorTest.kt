package com.sdvsync.steam

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAttemptCoordinatorTest {
    @Test
    fun `cancelled job cannot begin an attempt`() {
        val coordinator = AuthAttemptCoordinator()
        val cancelledJob = Job().apply { cancel() }

        assertNull(coordinator.tryBegin(AuthAttemptKind.RESUME_SESSION, cancelledJob))
        assertNull(coordinator.activeAttempt())
    }

    @Test
    fun `cancelled owner cannot publish but can still finish cleanup`() {
        val coordinator = AuthAttemptCoordinator()
        val job = Job()
        val attempt = requireNotNull(coordinator.tryBegin(AuthAttemptKind.CREDENTIALS, job))
        job.cancel()

        assertNull(coordinator.activeAttempt())
        assertFalse(coordinator.isActive(attempt.id))
        assertFalse(coordinator.runIfActive(attempt.id) { error("cancelled attempt published") })
        assertTrue(coordinator.finish(attempt))
    }

    @Test
    fun `overlapping attempt is rejected without cancelling active attempt`() {
        val coordinator = AuthAttemptCoordinator()
        val activeJob = Job()
        val active = coordinator.tryBegin(AuthAttemptKind.CREDENTIALS, activeJob)

        assertNull(coordinator.tryBegin(AuthAttemptKind.QR, Job()))
        assertSame(active, coordinator.activeAttempt())
        assertTrue(activeJob.isActive)
    }

    @Test
    fun `finishing stale attempt cannot clear newer attempt`() {
        val coordinator = AuthAttemptCoordinator()
        val first = requireNotNull(coordinator.tryBegin(AuthAttemptKind.CREDENTIALS, Job()))
        var firstCleanupRan = false
        assertTrue(coordinator.finish(first) { firstCleanupRan = true })
        assertTrue(firstCleanupRan)

        val second = requireNotNull(coordinator.tryBegin(AuthAttemptKind.QR, Job()))
        var staleCleanupRan = false
        assertFalse(coordinator.finish(first) { staleCleanupRan = true })
        assertFalse(staleCleanupRan)
        assertSame(second, coordinator.activeAttempt())
    }

    @Test
    fun `stale attempt cannot publish into newer attempt`() {
        val coordinator = AuthAttemptCoordinator()
        val first = requireNotNull(coordinator.tryBegin(AuthAttemptKind.CREDENTIALS, Job()))
        var publishedValue = ""
        assertTrue(coordinator.runIfActive(first.id) { publishedValue = "first" })
        assertTrue(coordinator.finish(first))

        val second = requireNotNull(coordinator.tryBegin(AuthAttemptKind.QR, Job()))
        assertFalse(coordinator.runIfActive(first.id) { publishedValue = "stale" })
        assertTrue(coordinator.runIfActive(second.id) { publishedValue = "second" })
        assertEquals("second", publishedValue)
    }

    @Test
    fun `kind scoped cancellation leaves other attempt active`() {
        val coordinator = AuthAttemptCoordinator()
        val job = Job()
        val attempt = requireNotNull(coordinator.tryBegin(AuthAttemptKind.CREDENTIALS, job))

        assertNull(coordinator.cancelActive(AuthAttemptKind.QR))
        assertTrue(job.isActive)
        assertSame(attempt, coordinator.activeAttempt())
        assertNull(coordinator.cancelActive(AuthAttemptKind.CREDENTIALS) { false })
        assertTrue(job.isActive)
        assertSame(attempt, coordinator.activeAttempt())

        assertSame(attempt, coordinator.cancelActive(AuthAttemptKind.CREDENTIALS))
        assertTrue(job.isCancelled)
        assertNull(coordinator.activeAttempt())
    }

    @Test
    fun `polling interval converts advertised seconds to milliseconds`() {
        assertEquals(5000L, pollingIntervalMillis(5f))
        assertEquals(1250L, pollingIntervalMillis(1.25f))
        assertEquals(1000L, pollingIntervalMillis(0f))
        assertEquals(1000L, pollingIntervalMillis(Float.NaN))
    }

    @Test
    fun `only library initiated transport replacement is ignored`() {
        assertTrue(shouldIgnoreTransportDisconnect(callbackIsUserInitiated = true, appDisconnect = false))
        assertFalse(shouldIgnoreTransportDisconnect(callbackIsUserInitiated = true, appDisconnect = true))
        assertFalse(shouldIgnoreTransportDisconnect(callbackIsUserInitiated = false, appDisconnect = false))
        assertFalse(shouldIgnoreTransportDisconnect(callbackIsUserInitiated = false, appDisconnect = true))
    }
}
