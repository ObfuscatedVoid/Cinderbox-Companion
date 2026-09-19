package com.sdvsync.saves

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sdvsync.fileaccess.AllFilesAccess
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.ModManifestParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveStorageTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var manager: SaveFileManager
    private lateinit var backups: SaveBackupManager
    private lateinit var bundles: SaveBundleManager
    private lateinit var saveRoot: File
    private val folder = "StorageTest_123456789"

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = Files.createTempDirectory(target.cacheDir.toPath(), "save-storage-").toFile()
        context = object : ContextWrapper(target) {
            override fun getFilesDir() = root.resolve("files").apply { mkdirs() }
            override fun getCacheDir() = root.resolve("cache").apply { mkdirs() }
        }
        saveRoot = root.resolve("saves")
        manager = SaveFileManager(SaveMetadataParser()) { SaveLocation(AllFilesAccess(), saveRoot.path) }
        backups = SaveBackupManager(context)
        bundles =
            SaveBundleManager(
                context,
                manager,
                ModFileManager(context, ModManifestParser()),
                SaveMetadataParser(),
                backups,
                SaveValidator()
            )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun saveLocationChangesWithoutRecreatingTheManager() = runBlocking {
        val first = saveRoot
        assertTrue(manager.writeLocalSave(folder, files("first")))
        saveRoot = root.resolve("other")
        assertTrue(manager.readLocalSave(folder).isEmpty())
        assertTrue(manager.writeLocalSave(folder, files("second")))
        assertEquals("first", first.resolve("$folder/extra").readText())
        assertEquals("second", manager.readLocalSave(folder).getValue("extra").decodeToString())
    }

    @Test
    fun pathTraversalIsRejectedBeforeWriting() = runBlocking {
        for (name in listOf("../escape", "/escape", "..", "a/b", "a\\b")) {
            try {
                manager.writeLocalSave(folder, mapOf(name to byteArrayOf(1)))
                fail("Accepted invalid name: $name")
            } catch (_: IllegalArgumentException) { }
        }
        assertFalse(saveRoot.resolve(folder).exists())
    }

    @Test
    fun successiveBackupsNeverOverwriteEachOther() {
        val first = backups.backupSaveData(folder, files("first"))
        val second = backups.backupSaveData(folder, files("second"))
        assertNotEquals(first, second)
        assertEquals("first", first.resolve("extra").readText())
        assertEquals("second", second.resolve("extra").readText())
    }

    @Test
    fun importBacksUpExistingFilesAndRestoresExportedContent() = runBlocking {
        assertTrue(manager.writeLocalSave(folder, files("exported")))
        val exported = bundles.exportBundle(folder).readBytes()
        assertTrue(manager.writeLocalSave(folder, files("changed")))
        assertTrue(bundles.importBundle(ByteArrayInputStream(exported), null) is ImportResult.Success)
        assertEquals("exported", manager.readLocalSave(folder).getValue("extra").decodeToString())
        assertEquals("changed", backups.listBackups(folder).first().resolve("extra").readText())
    }

    @Test
    fun invalidImportPreservesExistingSave() = runBlocking {
        assertTrue(manager.writeLocalSave(folder, files("original")))
        val result = bundles.importBundle(ByteArrayInputStream(bundle("save/$folder", "invalid")), null)
        assertTrue(result is ImportResult.Error)
        assertEquals("original", manager.readLocalSave(folder).getValue("extra").decodeToString())
    }

    @Test
    fun bundleCannotWriteOutsideItsSaveFolder() = runBlocking {
        val result = bundles.importBundle(ByteArrayInputStream(bundle("save/../../escape", "bad")), null)
        assertTrue(result is ImportResult.Error)
        assertFalse(root.resolve("escape").exists())
    }

    @Test
    fun interruptedTemporaryFilesAreNotUploadedOrExported() = runBlocking {
        assertTrue(manager.writeLocalSave(folder, files("original")))
        saveRoot.resolve("$folder/extra.sdvsync_tmp").writeText("partial")
        assertFalse(manager.readLocalSave(folder).containsKey("extra.sdvsync_tmp"))
    }

    private fun files(extra: String) = mapOf(
        folder to (
            "<SaveGame><player><name>StorageTest</name></player><locations/>" +
                "<currentSeason>spring</currentSeason><dayOfMonth>1</dayOfMonth><year>1</year>" + " ".repeat(1100) +
                "</SaveGame>"
            ).toByteArray(),
        "SaveGameInfo" to (
            "<Farmer><name>StorageTest</name><farmName>Test</farmName>" +
                "<seasonForSaveGame>0</seasonForSaveGame><dayOfMonthForSaveGame>1</dayOfMonthForSaveGame>" +
                "<yearForSaveGame>1</yearForSaveGame><gameVersion>1.6.15</gameVersion></Farmer>"
            ).toByteArray(),
        "extra" to extra.toByteArray()
    )

    private fun bundle(name: String, content: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"save":{"folderName":"$folder"}}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
