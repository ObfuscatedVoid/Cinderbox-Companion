package com.sdvsync.mods

import android.content.Context
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.InstalledMod
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Manages local mod files in the Mods/ directory.
 * Uses direct java.io.File access (Mods/ is on shared storage, not inside /Android/data/).
 */
class ModFileManager(
    private val context: Context,
    private val parser: ModManifestParser,
    private val modsDir: File = File(CinderboxPaths.MODS_DIR)
) {
    companion object {
        private const val TAG = "ModFileManager"
        private const val DISABLED_SUFFIX = ".disabled"
    }

    fun isModsDirAccessible(): Boolean = modsDir.exists() && modsDir.isDirectory && modsDir.canRead()

    /**
     * Scan the Mods/ directory and parse all installed mods.
     */
    fun listInstalledMods(): List<InstalledMod> {
        if (!modsDir.exists()) return emptyList()

        return modsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { folder ->
                val normalized = if (folder.name.endsWith(DISABLED_SUFFIX) &&
                    !folder.name.startsWith(".") &&
                    File(folder, "manifest.json").isFile
                ) {
                    val target = File(modsDir, ".${folder.name.removeSuffix(DISABLED_SUFFIX)}")
                    if (!target.exists() && folder.renameTo(target)) target else folder
                } else {
                    folder
                }
                parseMod(normalized)
            }
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

        val enabled = !folder.name.startsWith(".")
        val folderSize = folder.walk().filter { it.isFile }.sumOf { it.length() }

        return InstalledMod(
            manifest = manifest,
            folderName = folder.name,
            folderPath = folder.absolutePath,
            enabled = enabled,
            installedAt = manifestFile.lastModified(),
            fileSize = folderSize
        )
    }

    fun enableMod(folderName: String): Boolean {
        if (!folderName.startsWith(".")) return true
        return renameMod(folderName, folderName.removePrefix("."))
    }

    fun disableMod(folderName: String): Boolean {
        if (folderName.startsWith(".")) return true
        // SMAPI only skips folders whose names start with a dot.
        return renameMod(folderName, ".$folderName")
    }

    private fun renameMod(from: String, to: String): Boolean {
        val target = File(modsDir, to)
        if (target.exists()) return false
        return File(modsDir, from).renameTo(target)
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
        val tempDir = Files.createTempDirectory(context.cacheDir.toPath(), "mod_extract_").toFile()
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

                require(targetName.isNotBlank() && targetName != "." && targetName != "..") {
                    "Invalid mod folder name"
                }
                val existing = listInstalledMods().firstOrNull { it.manifest.uniqueID == manifest.uniqueID }
                val targetDir = existing?.let { File(it.folderPath) } ?: File(modsDir, targetName)
                check(!targetDir.exists() || existing != null) { "A different mod already uses $targetName" }
                Files.createDirectories(modsDir.toPath())
                val staged = Files.createTempDirectory(modsDir.toPath(), ".install-").toFile()
                val previous = File(modsDir, ".backup-${UUID.randomUUID()}")
                try {
                    check(modFolder.copyRecursively(staged, overwrite = true)) { "Could not stage mod files" }
                    val config = File(targetDir, "config.json")
                    if (config.isFile) config.copyTo(File(staged, "config.json"), overwrite = true)
                    if (targetDir.exists()) Files.move(targetDir.toPath(), previous.toPath())
                    try {
                        Files.move(staged.toPath(), targetDir.toPath())
                    } catch (e: Exception) {
                        if (previous.exists()) Files.move(previous.toPath(), targetDir.toPath())
                        throw e
                    }
                    previous.deleteRecursively()
                } finally {
                    staged.deleteRecursively()
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
                if (!file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
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
