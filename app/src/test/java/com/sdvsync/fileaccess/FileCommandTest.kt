package com.sdvsync.fileaccess

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FileCommandTest {
    @Test
    fun shellMetacharactersAndApostrophesRemainLiteral() {
        val path = "/saves/Farmer's \$(echo injected); & \"farm\""
        val result = runFileCommand(listOf("sh", "-c"), "printf %s ${shellQuote(path)}")
        assertTrue(result.successful)
        assertEquals(path, result.bytes.decodeToString())
    }

    @Test
    fun failedCommandCannotLookLikeASuccessfulWrite() {
        val result = runFileCommand(listOf("sh", "-c"), "printf failure >&2; exit 1")
        assertFalse(result.successful)
        assertTrue(result.bytes.isEmpty())
    }

    @Test
    fun fileOperationsPreserveEmptyFilesAndQuoteNames() = runBlocking {
        val directory = Files.createTempDirectory("root-file-test").toFile()
        try {
            val access = RootFileAccess(listOf("sh", "-c"))
            val file = directory.resolve("Farmer's save")
            assertTrue(access.writeFile(file, byteArrayOf()))
            assertArrayEquals(byteArrayOf(), access.readFile(file))
            assertEquals(listOf(file.name), access.listFiles(directory))
            assertFalse(access.renameFile(directory.resolve("missing"), file))
            assertTrue(file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
