package com.sdvsync.saves

import android.content.Context
import com.sdvsync.logging.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        const val DEFAULT_MAX_BACKUPS = 7
        const val MIN_MAX_BACKUPS = 1
        const val MAX_MAX_BACKUPS = 20
        private const val PREF_NAME = "backup_prefs"
        private const val KEY_MAX_BACKUPS = "max_backups"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }

    var maxBackups: Int = DEFAULT_MAX_BACKUPS
        private set

    init {
        maxBackups = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_BACKUPS, DEFAULT_MAX_BACKUPS)
    }

    private val backupRoot: File
        get() = File(context.filesDir, "backups").also { it.mkdirs() }

    /**
     * Update the max backup count and immediately prune all save folders.
     */
    fun setMaxBackupsAndPrune(count: Int) {
        maxBackups = count.coerceIn(MIN_MAX_BACKUPS, MAX_MAX_BACKUPS)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MAX_BACKUPS, maxBackups).apply()
        pruneAllSaves()
    }

    /**
     * Prune backups across all save folders to the current maxBackups limit.
     */
    fun pruneAllSaves() {
        backupRoot.listFiles()?.filter { it.isDirectory }?.forEach { saveDir ->
            pruneBackups(saveDir.name)
        }
    }

    /**
     * Create a backup of a local save folder.
     * Returns the backup directory path.
     */
    fun backupSave(saveDir: File): File? {
        if (!saveDir.exists() || !saveDir.isDirectory) return null

        AppLogger.d(TAG, "backupSave: starting backup of ${saveDir.name}")
        val timestamp = DATE_FORMAT.format(Date())
        val backupDir = File(backupRoot, "${saveDir.name}/$timestamp")
        backupDir.mkdirs()

        try {
            saveDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.copyTo(File(backupDir, file.name), overwrite = true)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "backupSave: failed for ${saveDir.name}", e)
            backupDir.deleteRecursively()
            throw e
        }

        pruneBackups(saveDir.name)
        AppLogger.d(TAG, "backupSave: completed at ${backupDir.absolutePath}")
        return backupDir
    }

    /**
     * Create a backup from in-memory save data.
     * Cleans up partial writes on failure.
     */
    fun backupSaveData(saveFolderName: String, files: Map<String, ByteArray>): File {
        AppLogger.d(TAG, "backupSaveData: starting backup of $saveFolderName (${files.size} files)")
        val timestamp = DATE_FORMAT.format(Date())
        val backupDir = File(backupRoot, "$saveFolderName/$timestamp")
        backupDir.mkdirs()

        try {
            for ((filename, data) in files) {
                File(backupDir, filename).writeBytes(data)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "backupSaveData: failed for $saveFolderName", e)
            backupDir.deleteRecursively()
            throw e
        }

        pruneBackups(saveFolderName)
        AppLogger.d(TAG, "backupSaveData: completed at ${backupDir.absolutePath}")
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
        if (backups.size > maxBackups) {
            val toPrune = backups.drop(maxBackups)
            AppLogger.d(TAG, "pruneBackups($saveFolderName): removing ${toPrune.size} old backups")
            toPrune.forEach { dir -> dir.deleteRecursively() }
        }
    }
}
