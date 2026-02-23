package com.sdvsync.sync

import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.saves.SaveValidator
import com.sdvsync.steam.SteamCloudService

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class NeedsConflictResolution(val comparison: SyncComparison) : SyncResult()
}

class SyncEngine(
    private val cloudService: SteamCloudService,
    private val saveFileManager: SaveFileManager,
    private val saveValidator: SaveValidator,
    private val backupManager: SaveBackupManager,
    private val metadataParser: SaveMetadataParser,
) {
    /**
     * Pull a save from Steam Cloud to local device.
     */
    suspend fun pullSave(
        saveFolderName: String,
        force: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): SyncResult {
        try {
            onProgress?.invoke("Downloading from Steam Cloud...")

            // Download save files from cloud
            val cloudFiles = cloudService.downloadSave(saveFolderName) { downloaded, total ->
                onProgress?.invoke("Downloading file $downloaded of $total...")
            }

            if (cloudFiles.isEmpty()) {
                return SyncResult.Error("No files found on Steam Cloud for $saveFolderName")
            }

            // Parse cloud metadata
            val cloudInfoData = cloudFiles["SaveGameInfo"]
            val cloudMeta = cloudInfoData?.let { metadataParser.parseFromBytes(it) }

            // Validate downloaded data
            val mainSaveData = cloudFiles[saveFolderName]
            val validation = saveValidator.validateSaveData(mainSaveData, cloudInfoData)
            if (!validation.valid) {
                return SyncResult.Error(
                    "Downloaded save is invalid: ${validation.errors.joinToString(", ")}"
                )
            }

            if (!force) {
                // Check local save for conflicts
                val localSaves = saveFileManager.listLocalSaves()
                val localSave = localSaves.find { it.folderName == saveFolderName }
                val localMeta = localSave?.metadata

                if (localMeta != null && cloudMeta != null) {
                    val comparison = ConflictResolver().compare(cloudMeta, localMeta)
                    if (comparison.direction == SyncDirection.CONFLICT) {
                        return SyncResult.NeedsConflictResolution(comparison)
                    }
                    if (comparison.direction == SyncDirection.PUSH) {
                        return SyncResult.Error(
                            "Local save is newer than cloud. Push first or use force pull."
                        )
                    }
                    if (comparison.direction == SyncDirection.SKIP) {
                        return SyncResult.Success("Already in sync")
                    }
                }
            }

            // Backup existing local save
            onProgress?.invoke("Backing up local save...")
            val localSaves = saveFileManager.listLocalSaves()
            val existingLocal = localSaves.find { it.folderName == saveFolderName }
            if (existingLocal != null) {
                val localFiles = saveFileManager.readLocalSave(saveFolderName)
                if (localFiles.isNotEmpty()) {
                    backupManager.backupSaveData(saveFolderName, localFiles)
                }
            }

            // Write to device
            onProgress?.invoke("Writing save to device...")
            val writeSuccess = saveFileManager.writeLocalSave(saveFolderName, cloudFiles)
            if (!writeSuccess) {
                return SyncResult.Error("Failed to write save files to device")
            }

            val dayInfo = cloudMeta?.let { " (${it.displayDate})" } ?: ""
            return SyncResult.Success("Save pulled successfully$dayInfo")

        } catch (e: Exception) {
            return SyncResult.Error("Pull failed: ${e.message}")
        }
    }

    /**
     * Push a save from local device to Steam Cloud.
     */
    suspend fun pushSave(
        saveFolderName: String,
        force: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): SyncResult {
        try {
            onProgress?.invoke("Reading local save...")

            // Read local save files
            val localFiles = saveFileManager.readLocalSave(saveFolderName)
            if (localFiles.isEmpty()) {
                return SyncResult.Error("No local save files found for $saveFolderName")
            }

            // Parse local metadata
            val localInfoData = localFiles["SaveGameInfo"]
            val localMeta = localInfoData?.let { metadataParser.parseFromBytes(it) }

            // Validate local save
            val mainSaveData = localFiles[saveFolderName]
            val validation = saveValidator.validateSaveData(mainSaveData, localInfoData)
            if (!validation.valid) {
                return SyncResult.Error(
                    "Local save is invalid: ${validation.errors.joinToString(", ")}"
                )
            }

            if (!force) {
                // Compare with cloud
                onProgress?.invoke("Checking Steam Cloud...")
                val cloudSaves = cloudService.listCloudSaves()
                val cloudFiles = cloudSaves[saveFolderName]

                if (cloudFiles != null) {
                    // Download just the SaveGameInfo to compare
                    val cloudInfoFile = cloudFiles.find { it.baseName == "SaveGameInfo" }
                    if (cloudInfoFile != null) {
                        val cloudInfoData = cloudService.downloadFile(cloudInfoFile.fullPath)
                        val cloudMeta = metadataParser.parseFromBytes(cloudInfoData)

                        if (cloudMeta != null && localMeta != null) {
                            val comparison = ConflictResolver().compare(cloudMeta, localMeta)
                            if (comparison.direction == SyncDirection.CONFLICT) {
                                return SyncResult.NeedsConflictResolution(comparison)
                            }
                            if (comparison.direction == SyncDirection.PULL) {
                                return SyncResult.Error(
                                    "Cloud save is newer. Pull first or use force push."
                                )
                            }
                            if (comparison.direction == SyncDirection.SKIP) {
                                return SyncResult.Success("Already in sync")
                            }
                        }
                    }
                }
            }

            // Upload to Steam Cloud
            onProgress?.invoke("Uploading to Steam Cloud...")
            cloudService.uploadSave(saveFolderName, localFiles) { uploaded, total ->
                onProgress?.invoke("Uploading file $uploaded of $total...")
            }

            val dayInfo = localMeta?.let { " (${it.displayDate})" } ?: ""
            return SyncResult.Success("Save pushed successfully$dayInfo")

        } catch (e: Exception) {
            return SyncResult.Error("Push failed: ${e.message}")
        }
    }
}
