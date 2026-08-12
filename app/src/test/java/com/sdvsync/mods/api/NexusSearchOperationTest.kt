package com.sdvsync.mods.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusSearchOperationTest {
    @Test
    fun `http response codes map to actionable failures`() {
        assertEquals(NexusApiFailure.AUTHENTICATION, classifyNexusHttpFailure(401))
        assertEquals(NexusApiFailure.AUTHENTICATION, classifyNexusHttpFailure(403))
        assertEquals(NexusApiFailure.RATE_LIMITED, classifyNexusHttpFailure(429))
        assertEquals(NexusApiFailure.SERVER, classifyNexusHttpFailure(503))
        assertEquals(NexusApiFailure.HTTP, classifyNexusHttpFailure(404))
    }

    @Test
    fun `api key validation only rejects authentication responses`() {
        assertTrue(handleApiKeyValidationResponse(200, ""))
        assertFalse(handleApiKeyValidationResponse(401, ""))
        assertFalse(handleApiKeyValidationResponse(403, ""))

        val rateLimit = assertThrows(NexusApiException::class.java) {
            handleApiKeyValidationResponse(429, "")
        }
        val serverFailure = assertThrows(NexusApiException::class.java) {
            handleApiKeyValidationResponse(503, "")
        }

        assertEquals(NexusApiFailure.RATE_LIMITED, rateLimit.failure)
        assertEquals(NexusApiFailure.SERVER, serverFailure.failure)
    }

    @Test
    fun `operation matches the Nexus mods search schema`() {
        val document = createNexusSearchOperation("da").document

        assertTrue(document.contains("${'$'}gameId: String!"))
        assertTrue(document.contains("gameId: { value: ${'$'}gameId, op: EQUALS }"))
        assertTrue(document.contains("name: { value: ${'$'}search, op: WILDCARD }"))
        assertTrue(document.contains("sort: { endorsements: { direction: DESC } }"))
        assertTrue(document.contains("count: $NEXUS_SEARCH_PAGE_SIZE"))
        assertTrue(document.contains("endorsementCount: endorsements"))
        assertTrue(document.contains("modDownloadCount: downloads"))
        assertTrue(document.contains("categoryName: category"))
        assertFalse(document.contains("searchQuery:"))
    }

    @Test
    fun `variables preserve the query and calculate a page offset`() {
        val operation = createNexusSearchOperation("da", page = 3)

        assertEquals(
            mapOf(
                "search" to "da",
                "gameId" to "1303",
                "offset" to 100
            ),
            operation.variables
        )
    }

    @Test
    fun `page numbers below one use the first page`() {
        val operation = createNexusSearchOperation("da", page = 0)

        assertEquals(0, operation.variables["offset"])
    }
}
