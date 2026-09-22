package com.sdvsync.download

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HttpFileDownloadTest {
    @Test
    fun incompleteDownloadPreservesExistingCacheAndRemovesPartialFile() = runBlocking {
        val root = Files.createTempDirectory("download-test").toFile()
        try {
            val target = root.resolve("cache.zip").apply { writeText("original") }
            try {
                copyDownload(ByteArrayInputStream(byteArrayOf(1)), target, 100)
                fail("Incomplete download accepted")
            } catch (_: IllegalStateException) { }
            assertEquals("original", target.readText())
            assertEquals(listOf("cache.zip"), root.list()!!.toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationRemovesPartialFileAndCannotPublishIt() = runBlocking {
        val root = Files.createTempDirectory("download-test").toFile()
        try {
            val target = root.resolve("cache.zip")
            try {
                copyDownload(ByteArrayInputStream(ByteArray(100)), target, 100) { _, _ ->
                    throw CancellationException()
                }
                fail("Cancellation swallowed")
            } catch (_: CancellationException) { }
            assertFalse(target.exists())
            assertTrue(root.list()!!.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun completeDownloadReplacesCacheOnlyAfterAllBytesArrive() = runBlocking {
        val root = Files.createTempDirectory("download-test").toFile()
        try {
            val target = root.resolve("cache.zip").apply { writeText("original") }
            copyDownload(ByteArrayInputStream("complete".toByteArray()), target, 8) { _, _ ->
                assertEquals("original", target.readText())
            }
            assertEquals("complete", target.readText())
            assertEquals(1, root.list()!!.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
