package com.sdvsync.mods

import android.content.Context
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.models.InstallResult
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.saves.requireSafeSaveName
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
    @Synchronized
    fun listInstalledMods(): List<InstalledMod> {
        if (!modsDir.exists()) return emptyList()

        return modsDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    !it.name.startsWith(".install-") &&
                    !it.name.startsWith(".backup-") &&
                    it.canonicalFile.parentFile == modsDir.canonicalFile
            }
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

    @Synchronized
    fun enableMod(folderName: String): Boolean {
        modFolder(folderName)
        if (!folderName.startsWith(".")) return true
        return renameMod(folderName, folderName.removePrefix("."))
    }

    @Synchronized
    fun disableMod(folderName: String): Boolean {
        modFolder(folderName)
        if (folderName.startsWith(".")) return true
        // SMAPI only skips folders whose names start with a dot.
        return renameMod(folderName, ".$folderName")
    }

    private fun renameMod(from: String, to: String): Boolean {
        val target = modFolder(to)
        if (target.exists()) return false
        return modFolder(from).renameTo(target)
    }

    /**
     * Remove a mod by recursively deleting its folder.
     */
    @Synchronized
    fun removeMod(folderName: String): Boolean {
        val folder = modFolder(folderName)
        if (!folder.exists()) return true
        return folder.deleteRecursively().also {
            AppLogger.d(TAG, "Remove mod: $folderName: $it")
        }
    }

    private fun modFolder(name: String): File {
        requireSafeSaveName(name)
        val file = File(modsDir, name)
        require(file.canonicalFile.parentFile == modsDir.canonicalFile) { "Mod folder is outside the Mods directory" }
        return file
    }

    @Synchronized
    fun applyProfile(enabledIds: Set<String>): List<String> {
        val wanted = enabledIds.map { it.lowercase() }.toSet()
        return listInstalledMods().mapNotNull { mod ->
            val enable = mod.manifest.uniqueID.lowercase() in wanted
            if (mod.enabled == enable) return@mapNotNull null
            val success = if (enable) enableMod(mod.folderName) else disableMod(mod.folderName)
            mod.manifest.name.takeUnless { success }
        }
    }

    @Synchronized
    fun installFromZip(zipFile: File): InstallResult {
        val tempDir = Files.createTempDirectory(context.cacheDir.toPath(), "mod_extract_").toFile()
        val staged = mutableListOf<Pair<File, File>>()
        val backups = mutableListOf<Pair<File, File>>()
        val installed = mutableListOf<File>()
        try {
            extractZip(zipFile, tempDir)
            val manifests = tempDir.walk().filter { it.name.equals("manifest.json", true) && it.isFile }.toList()
            check(manifests.isNotEmpty()) { "No manifest.json found in archive" }
            val existing = listInstalledMods()
            val ids = mutableSetOf<String>()
            val targets = mutableSetOf<String>()
            for (file in manifests) {
                check(file.length() <= 1024 * 1024) { "Mod manifest is too large" }
                val manifest =
                    checkNotNull(parser.parse(file.readText())) { "Invalid mod manifest: ${file.parentFile?.name}" }
                check(ids.add(manifest.uniqueID.lowercase())) { "Duplicate mod ID in archive" }
                val folder = checkNotNull(file.parentFile)
                check(
                    manifests.none {
                        it != file && folder.toPath().startsWith(checkNotNull(it.parentFile).toPath())
                    }
                ) {
                    "Archive contains nested mod manifests"
                }
                val targetName = folder.name.takeIf { folder != tempDir }
                    ?: manifest.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                val previous = existing.firstOrNull { it.manifest.uniqueID.equals(manifest.uniqueID, true) }
                val target = modFolder(previous?.folderName ?: targetName)
                check(targets.add(target.name.lowercase())) { "Multiple mods use the same folder" }
                check(!target.exists() || previous != null) { "A different mod already uses ${target.name}" }
                Files.createDirectories(modsDir.toPath())
                val stage = Files.createTempDirectory(modsDir.toPath(), ".install-").toFile()
                staged.add(stage to target)
                check(folder.copyRecursively(stage, overwrite = true)) { "Could not stage mod files" }
                val config = File(target, "config.json")
                if (config.isFile) config.copyTo(File(stage, "config.json"), overwrite = true)
            }
            for ((stage, target) in staged) {
                if (target.exists()) {
                    val backup = File(modsDir, ".backup-${UUID.randomUUID()}")
                    Files.move(target.toPath(), backup.toPath())
                    backups.add(backup to target)
                }
                Files.move(stage.toPath(), target.toPath())
                installed.add(target)
            }
            val mods = installed.map { checkNotNull(parseMod(it)) { "Installed mod could not be read" } }
            backups.forEach { (backup, _) -> backup.deleteRecursively() }
            return InstallResult.Success(mods)
        } catch (e: Exception) {
            var recovered = true
            installed.asReversed().forEach { if (!it.deleteRecursively()) recovered = false }
            backups.asReversed().forEach { (backup, target) ->
                try {
                    Files.move(backup.toPath(), target.toPath())
                } catch (_: Exception) {
                    recovered = false
                }
            }
            AppLogger.e(TAG, "Mod installation failed", e)
            return InstallResult.Error(
                if (recovered) {
                    e.message ?: "Installation failed"
                } else {
                    "Installation failed. Original mods are retained in .backup folders; recovery is required."
                }
            )
        } finally {
            staged.forEach { (stage, _) -> stage.deleteRecursively() }
            tempDir.deleteRecursively()
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var count = 0
            var total = 0L
            val names = mutableSetOf<String>()
            var entry = zip.nextEntry
            while (entry != null) {
                check(++count <= 10000 && names.add(entry.name)) { "Too many or duplicate archive entries" }
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
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val bytes = zip.read(buffer)
                            if (bytes == -1) break
                            total += bytes
                            check(total <= 2L * 1024 * 1024 * 1024) { "Mod archive exceeds 2 GiB" }
                            out.write(buffer, 0, bytes)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
