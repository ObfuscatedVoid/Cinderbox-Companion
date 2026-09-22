package com.sdvsync.download

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class GameInstallationDeviceTest {
    @Test
    fun stagedDirectoryReplacementWorksOnEmulatedExternalStorage() = withDirectory { root ->
        val target = root.resolve("GameFiles").apply {
            mkdirs()
            resolve("original").writeText("old")
        }
        StagedInstallation(target).use { stage ->
            stage.directory.resolve("replacement").writeText("new")
            publishInstallations(listOf(stage))
        }
        assertEquals(listOf("replacement"), target.list()!!.toList())
        assertEquals("new", target.resolve("replacement").readText())
    }

    @Test
    fun failedPublicationRestoresExternalStorageDirectory() = withDirectory { root ->
        val target = root.resolve("GameFiles").apply {
            mkdirs()
            resolve("original").writeText("old")
        }
        StagedInstallation(target).use { stage ->
            stage.directory.resolve("replacement").writeText("new")
            try {
                publishInstallations(listOf(stage)) { from, to ->
                    if (from == stage.directory) throw IOException("Injected failure")
                    Files.move(from.toPath(), to.toPath())
                }
                fail("Publication failure was swallowed")
            } catch (_: IOException) { }
        }
        assertEquals(listOf("original"), target.list()!!.toList())
        assertEquals("old", target.resolve("original").readText())
        assertFalse(root.list()!!.any { it.startsWith('.') })
    }

    private fun withDirectory(action: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.getExternalFilesDir(null)!!.toPath(), "install-test-").toFile()
        try {
            action(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
