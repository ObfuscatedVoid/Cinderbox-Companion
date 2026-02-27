package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.BackupInfo
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.saves.SaveMetadataParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupListState(
    val saveFolderName: String = "",
    val backups: List<BackupInfo> = emptyList(),
    val currentMetadata: SaveMetadata? = null,
    val isLoading: Boolean = true,
    val isRestoring: Boolean = false,
    val restoreResult: String? = null,
    val error: String? = null
)

class BackupListViewModel(
    private val context: Context,
    private val backupManager: SaveBackupManager,
    private val saveFileManager: SaveFileManager,
    private val metadataParser: SaveMetadataParser
) : ViewModel() {

    companion object {
        private const val TAG = "BackupListVM"
        private val BACKUP_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        private val DISPLAY_DATE_FORMAT = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
    }

    private val _state = MutableStateFlow(BackupListState())
    val state: StateFlow<BackupListState> = _state.asStateFlow()

    fun loadBackups(saveFolderName: String) {
        _state.update { it.copy(saveFolderName = saveFolderName, isLoading = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get current local metadata
                val localSaves = saveFileManager.listLocalSaves()
                val currentMeta = localSaves.find { it.folderName == saveFolderName }?.metadata

                // List and parse backup info
                val backupDirs = backupManager.listBackups(saveFolderName)
                val backups = backupDirs.map { dir -> parseBackupInfo(dir) }

                _state.update {
                    it.copy(
                        backups = backups,
                        currentMetadata = currentMeta,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load backups", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load backups") }
            }
        }
    }

    fun restoreBackup(backupDir: File) {
        val saveFolderName = _state.value.saveFolderName
        _state.update { it.copy(isRestoring = true, restoreResult = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Backup current save first
                val currentFiles = saveFileManager.readLocalSave(saveFolderName)
                if (currentFiles.isNotEmpty()) {
                    backupManager.backupSaveData(saveFolderName, currentFiles)
                    AppLogger.d(TAG, "Backed up current save before restore")
                }

                // Read backup files
                val backupFiles = mutableMapOf<String, ByteArray>()
                backupDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    backupFiles[file.name] = file.readBytes()
                }

                if (backupFiles.isEmpty()) {
                    _state.update { it.copy(isRestoring = false, restoreResult = "Backup is empty") }
                    return@launch
                }

                // Write backup files to save directory
                val success = saveFileManager.writeLocalSave(saveFolderName, backupFiles)

                _state.update {
                    it.copy(
                        isRestoring = false,
                        restoreResult = if (success) "Restore complete" else "Restore failed"
                    )
                }

                if (success) {
                    // Reload backups to refresh current metadata
                    loadBackups(saveFolderName)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Restore failed", e)
                _state.update {
                    it.copy(isRestoring = false, restoreResult = "Restore failed: ${e.message}")
                }
            }
        }
    }

    fun clearRestoreResult() {
        _state.update { it.copy(restoreResult = null) }
    }

    private fun parseBackupInfo(dir: File): BackupInfo {
        val metadata = try {
            val infoFile = File(dir, "SaveGameInfo")
            if (infoFile.exists()) metadataParser.parseFromFile(infoFile) else null
        } catch (e: Exception) {
            null
        }

        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val totalSize = files.sumOf { it.length() }

        return BackupInfo(
            backupDir = dir,
            timestamp = formatTimestamp(dir.name),
            metadata = metadata,
            fileCount = files.size,
            totalSize = totalSize
        )
    }

    private fun formatTimestamp(dirName: String): String = try {
        val date = BACKUP_DATE_FORMAT.parse(dirName)
        if (date != null) DISPLAY_DATE_FORMAT.format(date) else dirName
    } catch (_: Exception) {
        dirName
    }
}
