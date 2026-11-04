package com.sdvsync.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R45GuidePolicyTest {
    @Test fun allFourAreasHaveThreeExplicitSteps() {
        assertEquals(4, R45GuideArea.entries.size)
        R45GuideArea.entries.forEach { area ->
            val copy = R45GuidePolicy.forArea(area)
            assertTrue(copy.heading.isNotBlank())
            assertEquals(3, copy.steps.size)
            assertTrue(copy.steps.all { it.isNotBlank() })
        }
    }

    @Test fun textNeverConfersUnconditionalWritePermission() {
        val combined = R45GuideArea.entries.joinToString(" ") {
            R45GuidePolicy.forArea(it).steps.joinToString(" ")
        }
        assertTrue(combined.contains("explicit"))
        assertTrue(combined.contains("Confirm"))
    }
}
