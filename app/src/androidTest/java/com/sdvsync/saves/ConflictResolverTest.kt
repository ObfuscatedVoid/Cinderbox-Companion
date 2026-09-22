package com.sdvsync.saves

import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.sync.ConflictResolver
import com.sdvsync.sync.SyncDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictResolverTest {
    private val resolver = ConflictResolver(InstrumentationRegistry.getInstrumentation().targetContext)
    private val metadata = SaveMetadata("Farmer", "Farm", 1, 0, 1, "1.6.15", 1000, "1")

    @Test
    fun matchingMetadataDoesNotProveMatchingSaves() {
        assertEquals(SyncDirection.CONFLICT, resolver.compare(metadata, metadata).direction)
        assertEquals(SyncDirection.SKIP, resolver.compare(metadata, metadata, contentMatches = true).direction)
    }

    @Test
    fun differentProgressRetainsItsDirection() {
        assertEquals(SyncDirection.PULL, resolver.compare(metadata.copy(dayOfMonth = 2), metadata).direction)
        assertEquals(SyncDirection.PUSH, resolver.compare(metadata, metadata.copy(dayOfMonth = 2)).direction)
    }
}
