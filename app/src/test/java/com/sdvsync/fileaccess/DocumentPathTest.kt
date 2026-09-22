package com.sdvsync.fileaccess

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DocumentPathTest {
    @Test
    fun resolvesRootAndNestedDocuments() {
        assertEquals(emptyList<String>(), documentSegments("/staging", File("/staging")))
        assertEquals(
            listOf("Farmer_123", "SaveGameInfo"),
            documentSegments("/staging", File("/staging/Farmer_123/SaveGameInfo"))
        )
    }

    @Test
    fun rejectsUnrelatedPathsAndSharedPrefixes() {
        listOf("/other/file", "/staging-other/file").forEach(::assertRejected)
    }

    @Test
    fun rejectsTraversalAndUnsafeProviderNames() {
        listOf(
            "/staging/../outside",
            "/staging/Farm/../../outside",
            "/staging/./file",
            "/staging/bad\\name",
            "/staging/bad\nname"
        ).forEach(::assertRejected)
    }

    private fun assertRejected(path: String) {
        assertThrows(IllegalArgumentException::class.java) { documentSegments("/staging", File(path)) }
    }
}
