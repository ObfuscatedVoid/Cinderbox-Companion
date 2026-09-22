package com.sdvsync.download

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GameInstallationTest {
    @Test
    fun completeInstallationReplacesOneVersionAndPreservesSiblingSaves() = fixture { root ->
        val target = original(root, "GameFiles")
        val save = root.resolve("Saves/Farmer_1").apply {
            parentFile!!.mkdirs()
            writeText("save")
        }
        StagedInstallation(target).use { stage ->
            stage.directory.resolve("new.dll").writeText("new")
            assertEquals("old", target.resolve("old.dll").readText())
            publishInstallations(listOf(stage))
        }
        assertEquals(listOf("new.dll"), target.list()!!.toList())
        assertEquals("save", save.readText())
        assertFalse(root.list()!!.any { it.startsWith('.') })
    }

    @Test
    fun failedSecondPublicationRestoresBothOriginalDirectories() = fixture { root ->
        val game = original(root, "GameFiles")
        val smapi = original(root, "smapi-internal")
        StagedInstallation(game).use { first ->
            StagedInstallation(smapi).use { second ->
                first.directory.resolve("new.dll").writeText("new")
                second.directory.resolve("new.dll").writeText("new")
                assertThrows(IOException::class.java) {
                    publishInstallations(listOf(first, second)) { from, to ->
                        if (from == second.directory) throw IOException("Disk failure")
                        Files.move(from.toPath(), to.toPath())
                    }
                }
                assertEquals("old", game.resolve("old.dll").readText())
                assertEquals("old", smapi.resolve("old.dll").readText())
                assertFalse(game.resolve("new.dll").exists())
            }
        }
        assertFalse(root.list()!!.any { it.startsWith('.') })
    }

    @Test
    fun rollbackFailureRetainsRecoveryCopyAndBlocksAnotherInstall() = fixture { root ->
        val target = original(root, "GameFiles")
        StagedInstallation(target).use { stage ->
            val failure = assertThrows(IOException::class.java) {
                publishInstallations(listOf(stage)) { from, to ->
                    if (from != target) throw IOException("Disk unavailable")
                    Files.move(from.toPath(), to.toPath())
                }
            }
            assertEquals(1, failure.suppressed.size)
            assertEquals("old", stage.backup.resolve("old.dll").readText())
            assertThrows(IllegalArgumentException::class.java) { StagedInstallation(target) }
        }
        assertEquals("old", root.resolve(".GameFiles.install-backup/old.dll").readText())
    }

    @Test
    fun extractionCancellationLeavesOriginalInstallationIntact() = fixture { root ->
        runBlocking {
            val archive = archive(root, "one.dll" to "one", "nested/two.dll" to "two")
            val target = original(root, "smapi-internal")
            StagedInstallation(target).use { stage ->
                try {
                    extractSmapi(archive, stage.directory) { throw CancellationException() }
                    fail("Cancellation swallowed")
                } catch (_: CancellationException) { }
            }
            assertEquals("old", target.resolve("old.dll").readText())
            assertFalse(root.list()!!.any { it.startsWith('.') })
        }
    }

    @Test
    fun completeArchiveAndGameCopiesPreserveBytes() = fixture { root ->
        runBlocking {
            val archive = archive(root, "one.dll" to "one", "nested/two.dll" to "two")
            val target = root.resolve("smapi-internal")
            StagedInstallation(target).use { stage ->
                val names = mutableListOf<String>()
                extractSmapi(archive, stage.directory, names::add)
                assertEquals(listOf("one.dll", "nested/two.dll"), names)
                publishInstallations(listOf(stage))
            }
            val copy = root.resolve("GameFiles/nested/two.dll")
            copyGameFile(target.resolve("nested/two.dll"), copy)
            assertEquals("two", copy.readText())
            assertEquals("one", target.resolve("one.dll").readText())
        }
    }

    @Test
    fun traversalAndEmptyArchivesAreRejectedBeforeExtraction() = fixture { root ->
        for (name in listOf("../outside", "/absolute", "nested/../../outside", "bad\\path")) {
            val archive = archive(root, name to "data")
            assertThrows(IllegalArgumentException::class.java) { smapiFileCount(archive) }
        }
        assertThrows(IllegalArgumentException::class.java) { smapiFileCount(archive(root)) }
    }

    @Test
    fun truncatedArchiveCannotReplaceAnInstallation() = fixture { root ->
        val archive = archive(root, "one.dll" to "data")
        archive.writeBytes(archive.readBytes().dropLast(20).toByteArray())
        assertThrows(IOException::class.java) { smapiFileCount(archive) }
    }

    private fun original(root: File, name: String): File = root.resolve(name).apply {
        mkdirs()
        resolve("old.dll").writeText("old")
    }

    private fun archive(root: File, vararg entries: Pair<String, String>): File = root.resolve("archive.zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun fixture(action: (File) -> Unit) {
        val root = Files.createTempDirectory("game-install-test").toFile()
        try {
            action(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
