package com.sdvsync.saves

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveBackupManager(private val context: Context) {

    companion object {
        private const val MAX_BACKUPS = 5
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }

    private val backupRoot: File
        get() = File(context.filesDir, "backups").also { it.mkdirs() }

    /**
     * Create a backup of a local save folder.
     * Returns the backup directory path.
     */
    fun backupSave(saveDir: File): File? {
        if (!saveDir.exists() || !saveDir.isDirectory) return null

        val timestamp = DATE_FORMAT.format(Date())
        val backupDir = File(backupRoot, "${saveDir.name}/$timestamp")
        backupDir.mkdirs()

        saveDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.copyTo(File(backupDir, file.name), overwrite = true)
            }
        }

        // Prune old backups
        pruneBackups(saveDir.name)

        return backupDir
    }

    /**
     * Create a backup from in-memory save data.
     */
    fun backupSaveData(saveFolderName: String, files: Map<String, ByteArray>): File {
        val timestamp = DATE_FORMAT.format(Date())
        val backupDir = File(backupRoot, "$saveFolderName/$timestamp")
        backupDir.mkdirs()

        for ((filename, data) in files) {
            File(backupDir, filename).writeBytes(data)
        }

        pruneBackups(saveFolderName)
        return backupDir
    }

    /**
     * List available backups for a save, newest first.
     */
    fun listBackups(saveFolderName: String): List<File> {
        val saveBackupDir = File(backupRoot, saveFolderName)
        if (!saveBackupDir.exists()) return emptyList()

        return saveBackupDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    /**
     * Restore a backup to a target directory.
     */
    fun restoreBackup(backupDir: File, targetDir: File): Boolean {
        if (!backupDir.exists()) return false

        targetDir.mkdirs()

        // Clear existing files in target
        targetDir.listFiles()?.forEach { it.delete() }

        // Copy backup files to target
        backupDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.copyTo(File(targetDir, file.name), overwrite = true)
            }
        }

        return true
    }

    private fun pruneBackups(saveFolderName: String) {
        val backups = listBackups(saveFolderName)
        if (backups.size > MAX_BACKUPS) {
            backups.drop(MAX_BACKUPS).forEach { dir ->
                dir.deleteRecursively()
            }
        }
    }
}
