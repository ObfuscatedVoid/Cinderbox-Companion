package com.sdvsync.cinderbox

import java.io.File
import java.io.IOException
import java.nio.file.Files

data class CinderboxMigrationResult(
    val moved: List<String> = emptyList(),
    val leftoverConflicts: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = errors.isEmpty()
}

class CinderboxLayout(val root: File = File(CinderboxPaths.ROOT_DIR)) {
    val desktopDir = File(root, "desktop")
    val savesDir = File(desktopDir, "Saves")
    val modsDir = File(desktopDir, "Mods")
    val gameFilesDir = File(desktopDir, "GameFiles")
    val legacySavesDir = File(root, "Saves")
    val legacyModsDir = File(root, "Mods")
    val legacyGameFilesDir = File(root, "GameFiles")

    fun isInstalled(): Boolean = desktopDir.isDirectory || hasLegacyContent()

    fun hasLegacyContent(): Boolean = legacyTrees().any { (source, _) ->
        source.isDirectory && source.listFiles()?.isNotEmpty() == true
    }

    fun migrate(): CinderboxMigrationResult = synchronized(migrationLock) {
        val moved = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for ((source, destination) in legacyTrees()) {
            try {
                if (!source.exists()) continue
                val children = source.listFiles() ?: throw IOException("Cannot read ${source.path}")
                if (children.isEmpty()) continue
                Files.createDirectories(desktopDir.toPath())
                if (!destination.exists()) {
                    Files.move(source.toPath(), destination.toPath())
                    moved += source.name
                } else if (source == legacyGameFilesDir) {
                    // Game files form one version; merging can mix incompatible DLLs and Content.
                    conflicts += source.name
                } else {
                    require(destination.isDirectory) { "${destination.path} is not a directory" }
                    var movedAny = false
                    for (child in children) {
                        val target = File(destination, child.name)
                        if (target.exists()) {
                            // A save or mod folder must stay intact, even if only one file overlaps.
                            conflicts += "${source.name}/${child.name}"
                            continue
                        }
                        try {
                            Files.move(child.toPath(), target.toPath())
                            movedAny = true
                        } catch (e: IOException) {
                            errors += "${source.name}/${child.name}: ${e.message}"
                        }
                    }
                    if (movedAny) moved += source.name
                    if (source.listFiles()?.isEmpty() == true) source.delete()
                }
            } catch (e: Exception) {
                errors += "${source.name}: ${e.message ?: "Move failed"}"
            }
        }
        CinderboxMigrationResult(moved, conflicts, errors)
    }

    private fun legacyTrees() = listOf(
        legacySavesDir to savesDir,
        legacyModsDir to modsDir,
        legacyGameFilesDir to gameFilesDir
    )

    companion object {
        private val migrationLock = Any()
    }
}
