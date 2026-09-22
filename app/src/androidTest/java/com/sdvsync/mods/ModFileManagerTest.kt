package com.sdvsync.mods

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.mods.models.InstallResult
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModFileManagerTest {
    private lateinit var root: File
    private lateinit var manager: ModFileManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = Files.createTempDirectory(context.cacheDir.toPath(), "mod-test-").toFile()
        manager = ModFileManager(context, ModManifestParser(), root.resolve("Mods"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun installDisableUpdateEnableAndRemove() {
        assertTrue(manager.installFromZip(archive("1.0.0")) is InstallResult.Success)
        assertTrue(manager.disableMod("TestMod"))
        val disabled = manager.listInstalledMods().single()
        assertFalse(disabled.enabled)
        assertEquals(".TestMod", disabled.folderName)
        File(disabled.folderPath, "config.json").writeText("{\"option\":true}")
        assertTrue(manager.installFromZip(archive("2.0.0")) is InstallResult.Success)
        val updated = manager.listInstalledMods().single()
        assertFalse(updated.enabled)
        assertEquals("2.0.0", updated.manifest.version)
        assertEquals("{\"option\":true}", File(updated.folderPath, "config.json").readText())
        assertTrue(manager.enableMod(updated.folderName))
        assertTrue(manager.listInstalledMods().single().enabled)
        assertTrue(manager.removeMod("TestMod"))
        assertTrue(manager.listInstalledMods().isEmpty())
    }

    @Test
    fun legacyDisabledFoldersAreConvertedToSmapiIgnoredNames() {
        assertTrue(manager.installFromZip(archive("1.0.0")) is InstallResult.Success)
        val old = root.resolve("Mods/TestMod.disabled")
        assertTrue(root.resolve("Mods/TestMod").renameTo(old))
        val mod = manager.listInstalledMods().single()
        assertEquals(".TestMod", mod.folderName)
        assertFalse(mod.enabled)
        assertFalse(old.exists())
    }

    @Test
    fun archiveTraversalIsRejected() {
        val archive = root.resolve("unsafe.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }
        assertTrue(manager.installFromZip(archive) is InstallResult.Error)
        assertFalse(root.resolve("escape").exists())
    }

    @Test
    fun disablingNeverOverwritesAnotherFolder() {
        assertTrue(manager.installFromZip(archive("1.0.0")) is InstallResult.Success)
        root.resolve("Mods/.TestMod").mkdirs()
        root.resolve("Mods/.TestMod/keep").writeText("keep")
        assertFalse(manager.disableMod("TestMod"))
        assertEquals("keep", root.resolve("Mods/.TestMod/keep").readText())
        assertTrue(root.resolve("Mods/TestMod/manifest.json").isFile)
    }

    @Test
    fun profileMatchingIgnoresIdCaseAndReportsFailedChanges() {
        assertTrue(manager.installFromZip(archive("1.0.0")) is InstallResult.Success)
        assertTrue(manager.applyProfile(setOf("test.fixture")).isEmpty())
        assertTrue(manager.listInstalledMods().single().enabled)
        root.resolve("Mods/.TestMod").mkdirs()
        assertEquals(listOf("Test Mod"), manager.applyProfile(emptySet()))
        assertTrue(manager.listInstalledMods().single().enabled)
    }

    @Test
    fun unsafeFolderNamesAndSymlinksCannotRemoveOutsideFiles() {
        val outside = root.resolve("outside").apply { mkdirs() }
        outside.resolve("keep").writeText("keep")
        root.resolve("Mods").mkdirs()
        java.nio.file.Files.createSymbolicLink(root.resolve("Mods/link").toPath(), outside.toPath())
        for (name in listOf("../outside", "/outside", "link")) {
            try {
                manager.removeMod(name)
                fail("Unsafe path accepted: $name")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals("keep", outside.resolve("keep").readText())
    }

    @Test
    fun invalidSecondModCannotPartiallyInstallTheFirst() {
        val archive = root.resolve("multiple.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for ((name, json) in listOf(
                "Valid/manifest.json" to """{"Name":"Valid","Author":"Test","Version":"1.0","UniqueID":"Test.Valid"}""",
                "Invalid/manifest.json" to "not json"
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(json.toByteArray())
                zip.closeEntry()
            }
        }
        assertTrue(manager.installFromZip(archive) is InstallResult.Error)
        assertTrue(manager.listInstalledMods().isEmpty())
        assertFalse(root.resolve("Mods/Valid").exists())
    }

    private fun archive(version: String): File {
        val archive = root.resolve("mod.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("TestMod/manifest.json"))
            zip.write(
                """{
                    "Name":"Test Mod", "Author":"Test", "Version":"$version",
                    "Description":"Test fixture", "UniqueID":"Test.Fixture",
                    "ContentPackFor":{"UniqueID":"Pathoschild.ContentPatcher"}
                }""".toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("TestMod/content.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        return archive
    }
}
