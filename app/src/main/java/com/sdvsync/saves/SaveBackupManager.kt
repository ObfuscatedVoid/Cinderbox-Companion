package com.sdvsync.saves

import android.content.Context
import androidx.core.content.edit
import com.sdvsync.logging.AppLogger
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SaveBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        const val DEFAULT_MAX_BACKUPS = 7
        const val MIN_MAX_BACKUPS = 1
        const val MAX_MAX_BACKUPS = 20
        private const val PREF_NAME = "backup_prefs"
        private const val KEY_MAX_BACKUPS = "max_backups"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSSSS")
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
    @Synchronized
    fun setMaxBackupsAndPrune(count: Int) {
        maxBackups = count.coerceIn(MIN_MAX_BACKUPS, MAX_MAX_BACKUPS)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_MAX_BACKUPS, maxBackups) }
        pruneAllSaves()
    }

    /**
     * Prune backups across all save folders to the current maxBackups limit.
     */
    @Synchronized
    fun pruneAllSaves() {
        backupRoot.listFiles()?.filter { it.isDirectory }?.forEach { saveDir ->
            pruneBackups(saveDir.name)
        }
    }

    /**
     * Create a backup from in-memory save data.
     * Cleans up partial writes on failure.
     */
    @Synchronized
    fun backupSaveData(saveFolderName: String, files: Map<String, ByteArray>): File {
        AppLogger.d(TAG, "backupSaveData: starting backup of $saveFolderName (${files.size} files)")
        require(files.isNotEmpty()) { "Cannot back up an empty save" }
        requireSafeSaveName(saveFolderName)
        files.keys.forEach(::requireSafeSaveName)
        val backupDir = createBackupDirectory(saveFolderName)

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

    private fun createBackupDirectory(saveFolderName: String): File {
        val directory = File(backupRoot, saveFolderName)
        Files.createDirectories(directory.toPath())
        val timestamp = LocalDateTime.now().format(DATE_FORMAT)
        return Files.createTempDirectory(directory.toPath(), "$timestamp-").toFile()
    }

    /**
     * List available backups for a save, newest first.
     */
    @Synchronized
    fun listBackups(saveFolderName: String): List<File> {
        requireSafeSaveName(saveFolderName)
        val saveBackupDir = File(backupRoot, saveFolderName)
        if (!saveBackupDir.exists()) return emptyList()

        return saveBackupDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?: emptyList()
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
