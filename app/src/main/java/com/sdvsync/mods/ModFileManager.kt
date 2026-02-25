package com.sdvsync.mods

import android.content.Context
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.InstalledMod
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Manages local mod files in the Mods/ directory.
 * Uses direct java.io.File access (Mods/ is on shared storage, not inside /Android/data/).
 */
class ModFileManager(
    private val context: Context,
    private val parser: ModManifestParser,
) {
    companion object {
        private const val TAG = "ModFileManager"
        const val MODS_DIR = "/storage/emulated/0/StardewValley/Mods"
        private const val DISABLED_SUFFIX = ".disabled"
    }

    private val modsDir = File(MODS_DIR)

    fun isModsDirAccessible(): Boolean {
        return modsDir.exists() && modsDir.isDirectory && modsDir.canRead()
    }

    /**
     * Scan the Mods/ directory and parse all installed mods.
     */
    fun listInstalledMods(): List<InstalledMod> {
        if (!modsDir.exists()) return emptyList()

        return modsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { folder -> parseMod(folder) }
            ?.sortedBy { it.manifest.name.lowercase() }
            ?: emptyList()
    }

    private fun parseMod(folder: File): InstalledMod? {
        val manifestFile = File(folder, "manifest.json")
        if (!manifestFile.exists()) return null

        val manifest = try {
            parser.parse(manifestFile.readText())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read manifest in ${folder.name}", e)
            null
        } ?: return null

        val enabled = !folder.name.endsWith(DISABLED_SUFFIX)
        val folderSize = folder.walk().filter { it.isFile }.sumOf { it.length() }

        return InstalledMod(
            manifest = manifest,
            folderName = folder.name,
            folderPath = folder.absolutePath,
            enabled = enabled,
            installedAt = manifestFile.lastModified(),
            fileSize = folderSize,
        )
    }

    /**
     * Enable a mod by removing the .disabled suffix from its folder.
     */
    fun enableMod(folderName: String): Boolean {
        if (!folderName.endsWith(DISABLED_SUFFIX)) return true
        val folder = File(modsDir, folderName)
        val newName = folderName.removeSuffix(DISABLED_SUFFIX)
        val target = File(modsDir, newName)
        return folder.renameTo(target).also {
            AppLogger.d(TAG, "Enable mod: $folderName -> $newName: $it")
        }
    }

    /**
     * Disable a mod by appending .disabled to its folder name.
     */
    fun disableMod(folderName: String): Boolean {
        if (folderName.endsWith(DISABLED_SUFFIX)) return true
        val folder = File(modsDir, folderName)
        val target = File(modsDir, "$folderName$DISABLED_SUFFIX")
        return folder.renameTo(target).also {
            AppLogger.d(TAG, "Disable mod: $folderName -> ${target.name}: $it")
        }
    }

    /**
     * Remove a mod by recursively deleting its folder.
     */
    fun removeMod(folderName: String): Boolean {
        val folder = File(modsDir, folderName)
        if (!folder.exists()) return true
        return folder.deleteRecursively().also {
            AppLogger.d(TAG, "Remove mod: $folderName: $it")
        }
    }

    /**
     * Install a mod from a zip file.
     * Handles single-mod, nested-folder, and multi-mod archives.
     */
    fun installFromZip(zipFile: File): InstallResult {
        val tempDir = File(context.cacheDir, "mod_extract_${System.currentTimeMillis()}")
        try {
            // Extract zip to temp directory
            tempDir.mkdirs()
            extractZip(zipFile, tempDir)

            // Find all manifest.json files
            val manifests = tempDir.walk()
                .filter { it.name.equals("manifest.json", ignoreCase = true) && it.isFile }
                .toList()

            if (manifests.isEmpty()) {
                return InstallResult.Error("No manifest.json found in archive")
            }

            val installed = mutableListOf<InstalledMod>()

            for (manifestFile in manifests) {
                val modFolder = manifestFile.parentFile ?: continue
                val manifest = parser.parse(manifestFile.readText()) ?: continue

                // Determine the target folder name
                val targetName = modFolder.name.takeIf { it != tempDir.name }
                    ?: manifest.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")

                val targetDir = File(modsDir, targetName)

                // Remove existing if updating
                if (targetDir.exists()) {
                    // Preserve config.json if it exists
                    val existingConfig = File(targetDir, "config.json")
                    val savedConfig = if (existingConfig.exists()) {
                        existingConfig.readText()
                    } else null

                    targetDir.deleteRecursively()

                    // Copy mod folder to Mods/
                    modFolder.copyRecursively(targetDir, overwrite = true)

                    // Restore config.json
                    if (savedConfig != null) {
                        File(targetDir, "config.json").writeText(savedConfig)
                    }
                } else {
                    modsDir.mkdirs()
                    modFolder.copyRecursively(targetDir, overwrite = true)
                }

                parseMod(targetDir)?.let { installed.add(it) }
                AppLogger.d(TAG, "Installed mod: ${manifest.name} to ${targetDir.name}")
            }

            return if (installed.isEmpty()) {
                InstallResult.Error("Failed to install any mods from archive")
            } else {
                InstallResult.Success(installed)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install from zip", e)
            return InstallResult.Error(e.message ?: "Unknown error")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                // Protect against zip slip
                if (!file.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().buffered().use { out ->
                        zip.copyTo(out)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
