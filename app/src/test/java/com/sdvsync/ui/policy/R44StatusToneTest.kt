package com.sdvsync.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure presentation tests. No filesystem, Steam, Android or side effects. */
class R44StatusToneTest {
    @Test fun mapsEveryDestinationStateWithoutPermissionSideEffects() {
        val expected = mapOf(
            R43DDestinationPresentation.Kind.AVAILABLE_FOR_CHECKS to R44StatusTone.AVAILABLE,
            R43DDestinationPresentation.Kind.SETUP_REQUIRED to R44StatusTone.SETUP,
            R43DDestinationPresentation.Kind.ACCESS_BLOCKED to R44StatusTone.BLOCKED,
            R43DDestinationPresentation.Kind.EXISTING_PROTECTED to R44StatusTone.PROTECTED,
            R43DDestinationPresentation.Kind.MANUAL_REVIEW to R44StatusTone.REVIEW
        )
        assertEquals(R43DDestinationPresentation.Kind.entries.size, expected.size)
        for ((kind, tone) in expected) {
            assertEquals(tone, R44StatusTone.fromDestination(kind))
            assertTrue(tone.label.isNotBlank())
        }
    }

    @Test fun protectedAndReviewNeverAppearAvailable() {
        assertTrue(R44StatusTone.fromDestination(
            R43DDestinationPresentation.Kind.EXISTING_PROTECTED) != R44StatusTone.AVAILABLE)
        assertTrue(R44StatusTone.fromDestination(
            R43DDestinationPresentation.Kind.MANUAL_REVIEW) != R44StatusTone.AVAILABLE)
        assertTrue(R44StatusTone.fromDestination(
            R43DDestinationPresentation.Kind.ACCESS_BLOCKED) != R44StatusTone.AVAILABLE)
    }
}
