package com.sdvsync.saves

import com.sdvsync.fileaccess.FileAccessStrategy
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SaveFileTransactionTest {
    private val directory = File("/test-save")
    private val original = mapOf("main" to "original main", "info" to "original info")
    private val updated = mapOf("main" to "new main".toByteArray(), "info" to "new info".toByteArray())

    @Test
    fun aFailedSecondReplacementRestoresEveryOriginal() = runBlocking {
        val access = MemoryFiles(original) { operation, name -> operation == "rename" && name.endsWith("info") }
        assertFalse(writeSaveFiles(access, directory, updated))
        assertEquals(original, access.content())
    }

    @Test
    fun partialStagingNeverTouchesOriginals() = runBlocking {
        val access = MemoryFiles(original) { operation, name -> operation == "write" && name.startsWith("info.") }
        assertFalse(writeSaveFiles(access, directory, updated))
        assertEquals(original, access.content())
    }

    @Test
    fun cancellationRollsBackBeforeItPropagates() = runBlocking {
        val access = MemoryFiles(original) { operation, name ->
            if (operation == "rename" && name == "info") throw CancellationException("cancel")
            false
        }
        try {
            writeSaveFiles(access, directory, updated)
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        assertEquals(original, access.content())
    }

    @Test
    fun successInstallsAllFilesAndRemovesTemporaryFiles() = runBlocking {
        val access = MemoryFiles(original)
        assertTrue(writeSaveFiles(access, directory, updated))
        assertEquals(updated.mapValues { it.value.decodeToString() }, access.content())
    }

    private class MemoryFiles(
        initial: Map<String, String>,
        private var fail: (String, String) -> Boolean = { _, _ ->
            false
        }
    ) : FileAccessStrategy {
        override val name = "fixture"
        private val files = initial.mapValues { it.value.toByteArray() }.toMutableMap()
        fun content() = files.mapValues { it.value.decodeToString() }
        override suspend fun exists(file: File) = file.name in files
        override suspend fun listDirectories(dir: File) = emptyList<String>()
        override suspend fun listFiles(dir: File) = files.keys.toList()
        override suspend fun readFile(file: File) = files[file.name]
        override suspend fun writeFile(file: File, data: ByteArray): Boolean {
            if (fail("write", file.name)) return false
            files[file.name] = data.copyOf()
            return true
        }
        override suspend fun deleteFile(file: File): Boolean {
            files.remove(file.name)
            return true
        }
        override suspend fun mkdirs(dir: File) = true
        override suspend fun renameFile(from: File, to: File): Boolean {
            // Fail once, so rollback can exercise the same provider independently.
            val failure = fail
            fail = { _, _ -> false }
            try {
                if (failure("rename", to.name)) return false
            } catch (e: CancellationException) {
                throw e
            }
            fail = failure
            val value = files.remove(from.name) ?: return false
            files[to.name] = value
            return true
        }
    }
}
