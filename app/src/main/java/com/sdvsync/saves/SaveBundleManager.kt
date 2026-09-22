package com.sdvsync.saves

import android.content.Context
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModFileManager
import com.sdvsync.util.MAX_SAVE_BYTES
import com.sdvsync.util.readLimited
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class SaveBundleManager(
    private val context: Context,
    private val saveFileManager: SaveFileManager,
    private val modFileManager: ModFileManager,
    private val metadataParser: SaveMetadataParser,
    private val backupManager: SaveBackupManager,
    private val validator: SaveValidator
) {
    companion object {
        private const val TAG = "SaveBundleManager"
        private const val MANIFEST_FILENAME = "manifest.json"
        private const val SAVE_DIR_PREFIX = "save/"
    }

    /**
     * Create a .sdvsync ZIP bundle from a local save.
     * Returns the temp file in cache dir.
     */
    suspend fun exportBundle(saveFolderName: String): File {
        val saveFiles = saveFileManager.readLocalSave(saveFolderName)
        if (saveFiles.isEmpty()) {
            throw IllegalStateException("No local save files found for $saveFolderName")
        }

        // Parse metadata from SaveGameInfo
        val infoData = saveFiles["SaveGameInfo"]
        val metadata = if (infoData != null) {
            metadataParser.parseFromBytes(infoData)
        } else {
            null
        }

        // List installed mods
        val mods = try {
            modFileManager.listInstalledMods()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not list mods for bundle", e)
            emptyList()
        }

        // Build manifest
        val manifest = BundleManifest(
            exportedAt = System.currentTimeMillis(),
            appVersion = getAppVersion(),
            save = BundleSaveInfo(
                folderName = saveFolderName,
                farmerName = metadata?.farmerName ?: saveFolderName.substringBefore("_"),
                farmName = metadata?.farmName ?: "",
                season = metadata?.season ?: 0,
                dayOfMonth = metadata?.dayOfMonth ?: 1,
                year = metadata?.year ?: 1,
                gameVersion = metadata?.gameVersion ?: ""
            ),
            mods = mods.map { mod ->
                BundleModInfo(
                    uniqueID = mod.manifest.uniqueID,
                    name = mod.manifest.name,
                    version = mod.manifest.version,
                    enabled = mod.enabled
                )
            }
        )

        // Create ZIP
        val directory = File(context.cacheDir, "exports")
        java.nio.file.Files.createDirectories(directory.toPath())
        val exportFile = File(directory, "$saveFolderName.sdvsync")
        ZipOutputStream(exportFile.outputStream().buffered()).use { zip ->
            // Write manifest
            zip.putNextEntry(ZipEntry(MANIFEST_FILENAME))
            zip.write(serializeManifest(manifest).toByteArray())
            zip.closeEntry()

            // Write save files
            for ((filename, data) in saveFiles) {
                zip.putNextEntry(ZipEntry("$SAVE_DIR_PREFIX$filename"))
                zip.write(data)
                zip.closeEntry()
            }
        }

        AppLogger.d(TAG, "Exported bundle: ${exportFile.name} (${exportFile.length()} bytes, ${saveFiles.size} files)")
        return exportFile
    }

    /**
     * Read the manifest from a .sdvsync ZIP without full extraction.
     */
    fun readManifest(inputStream: InputStream): BundleManifest? = readBundle(inputStream).first

    private fun readBundle(inputStream: InputStream): Pair<BundleManifest?, Map<String, ByteArray>> {
        var manifest: BundleManifest? = null
        val files = mutableMapOf<String, ByteArray>()
        val names = mutableSetOf<String>()
        var remaining = MAX_SAVE_BYTES
        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                check(names.size < 100 && names.add(entry.name)) { "Too many or duplicate bundle entries" }
                val data = zip.readLimited(
                    if (entry.name ==
                        MANIFEST_FILENAME
                    ) {
                        minOf(remaining, 1024 * 1024)
                    } else {
                        remaining
                    }
                )
                remaining -= data.size
                when {
                    entry.name == MANIFEST_FILENAME -> manifest = parseManifest(data.decodeToString())
                    entry.name.startsWith(SAVE_DIR_PREFIX) && !entry.isDirectory -> {
                        val name = entry.name.removePrefix(SAVE_DIR_PREFIX)
                        saveFileManager.requireSafeName(name)
                        files[name] = data
                    }
                }
            }
        }
        return manifest to files
    }

    /**
     * Import a .sdvsync bundle.
     * Backs up existing local save before overwriting.
     */
    suspend fun importBundle(inputStream: InputStream, targetFolderName: String?): ImportResult {
        try {
            val (manifest, saveFiles) = readBundle(inputStream)

            if (manifest == null) {
                return ImportResult.Error("No manifest found in bundle")
            }
            if (saveFiles.isEmpty()) {
                return ImportResult.Error("No save files found in bundle")
            }

            require(targetFolderName == null || targetFolderName == manifest.save.folderName) {
                "Bundle folder renaming is not supported"
            }
            val folderName = manifest.save.folderName

            saveFileManager.requireSafeName(folderName)
            val validation = validator.validateSaveData(saveFiles[folderName], saveFiles["SaveGameInfo"])
            if (!validation.valid) return ImportResult.Error(validation.errors.joinToString(", "))
            val existing = saveFileManager.readLocalSave(folderName)
            if (existing.isNotEmpty()) backupManager.backupSaveData(folderName, existing)

            // Write save files to local
            val writeSuccess = saveFileManager.writeLocalSave(folderName, saveFiles)
            if (!writeSuccess) {
                return ImportResult.Error("Failed to write save files to device")
            }

            AppLogger.d(TAG, "Imported bundle for $folderName (${saveFiles.size} files)")
            return ImportResult.Success(manifest)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "Import failed", e)
            return ImportResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    private fun serializeManifest(manifest: BundleManifest): String {
        val obj = JSONObject().apply {
            put("version", manifest.version)
            put("exportedAt", manifest.exportedAt)
            put("app", "SDV Sync")
            put("appVersion", manifest.appVersion)
            put(
                "save",
                JSONObject().apply {
                    put("folderName", manifest.save.folderName)
                    put("farmerName", manifest.save.farmerName)
                    put("farmName", manifest.save.farmName)
                    put("season", manifest.save.season)
                    put("dayOfMonth", manifest.save.dayOfMonth)
                    put("year", manifest.save.year)
                    put("gameVersion", manifest.save.gameVersion)
                }
            )
            put(
                "mods",
                JSONArray().apply {
                    manifest.mods.forEach { mod ->
                        put(
                            JSONObject().apply {
                                put("uniqueID", mod.uniqueID)
                                put("name", mod.name)
                                put("version", mod.version)
                                put("enabled", mod.enabled)
                            }
                        )
                    }
                }
            )
        }
        return obj.toString(2) // Pretty-print
    }

    private fun parseManifest(json: String): BundleManifest? = try {
        val obj = JSONObject(json)
        require(obj.optInt("version", 1) == 1) { "Unsupported bundle version" }
        val saveObj = obj.getJSONObject("save")
        saveFileManager.requireSafeName(saveObj.getString("folderName"))
        val modsArray = obj.optJSONArray("mods") ?: JSONArray()

        BundleManifest(
            version = obj.optInt("version", 1),
            exportedAt = obj.optLong("exportedAt", 0),
            appVersion = obj.optString("appVersion", "unknown"),
            save = BundleSaveInfo(
                folderName = saveObj.getString("folderName"),
                farmerName = saveObj.optString("farmerName", ""),
                farmName = saveObj.optString("farmName", ""),
                season = saveObj.optInt("season", 0),
                dayOfMonth = saveObj.optInt("dayOfMonth", 1),
                year = saveObj.optInt("year", 1),
                gameVersion = saveObj.optString("gameVersion", "")
            ),
            mods = (0 until modsArray.length()).map { i ->
                val modObj = modsArray.getJSONObject(i)
                BundleModInfo(
                    uniqueID = modObj.getString("uniqueID"),
                    name = modObj.optString("name", ""),
                    version = modObj.optString("version", ""),
                    enabled = modObj.optBoolean("enabled", true)
                )
            }
        )
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse bundle manifest", e)
        null
    }
}
