package com.sdvsync.sync

import android.content.Context
import android.util.Log
import com.sdvsync.R
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.saves.SaveValidator
import com.sdvsync.steam.SteamCloudService

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class NeedsConflictResolution(val comparison: SyncComparison) : SyncResult()
}

class SyncEngine(
    private val context: Context,
    private val cloudService: SteamCloudService,
    private val saveFileManager: SaveFileManager,
    private val saveValidator: SaveValidator,
    private val backupManager: SaveBackupManager,
    private val metadataParser: SaveMetadataParser,
    private val conflictResolver: ConflictResolver,
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    /**
     * Pull a save from Steam Cloud to local device.
     */
    suspend fun pullSave(
        saveFolderName: String,
        force: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): SyncResult {
        try {
            onProgress?.invoke(context.getString(R.string.sync_progress_downloading))

            // Download save files from cloud
            val cloudFiles = cloudService.downloadSave(saveFolderName) { downloaded, total ->
                onProgress?.invoke(context.getString(R.string.sync_progress_downloading_file, downloaded, total))
            }

            if (cloudFiles.isEmpty()) {
                return SyncResult.Error(context.getString(R.string.sync_error_no_cloud_files, saveFolderName))
            }

            Log.d(TAG, "pullSave: downloaded ${cloudFiles.size} files: ${cloudFiles.keys}")

            // Parse cloud metadata
            val cloudInfoData = cloudFiles["SaveGameInfo"]
            Log.d(TAG, "pullSave: SaveGameInfo data size=${cloudInfoData?.size ?: 0}")
            val cloudMeta = cloudInfoData?.let { metadataParser.parseFromBytes(it) }
            Log.d(TAG, "pullSave: cloudMeta=$cloudMeta")

            // Validate downloaded data
            val mainSaveData = cloudFiles[saveFolderName]
            Log.d(TAG, "pullSave: main save data key='$saveFolderName', size=${mainSaveData?.size ?: 0}")
            val validation = saveValidator.validateSaveData(mainSaveData, cloudInfoData)
            Log.d(TAG, "pullSave: validation=${validation.valid}, errors=${validation.errors}")
            if (!validation.valid) {
                return SyncResult.Error(
                    context.getString(R.string.sync_error_invalid_download, validation.errors.joinToString(", "))
                )
            }

            if (!force) {
                // Check local save for conflicts
                val localSaves = saveFileManager.listLocalSaves()
                val localSave = localSaves.find { it.folderName == saveFolderName }
                val localMeta = localSave?.metadata

                if (localMeta != null && cloudMeta != null) {
                    val comparison = conflictResolver.compare(cloudMeta, localMeta)
                    if (comparison.direction == SyncDirection.CONFLICT) {
                        return SyncResult.NeedsConflictResolution(comparison)
                    }
                    if (comparison.direction == SyncDirection.PUSH) {
                        return SyncResult.Error(context.getString(R.string.sync_error_local_newer))
                    }
                    if (comparison.direction == SyncDirection.SKIP) {
                        return SyncResult.Success(context.getString(R.string.sync_already_in_sync))
                    }
                }
            }

            // Backup existing local save
            onProgress?.invoke(context.getString(R.string.sync_progress_backing_up))
            val localSaves = saveFileManager.listLocalSaves()
            val existingLocal = localSaves.find { it.folderName == saveFolderName }
            if (existingLocal != null) {
                val localFiles = saveFileManager.readLocalSave(saveFolderName)
                if (localFiles.isNotEmpty()) {
                    backupManager.backupSaveData(saveFolderName, localFiles)
                }
            }

            // Write to device
            onProgress?.invoke(context.getString(R.string.sync_progress_writing))
            val writeSuccess = saveFileManager.writeLocalSave(saveFolderName, cloudFiles)
            if (!writeSuccess) {
                return SyncResult.Error(context.getString(R.string.sync_error_write_failed))
            }

            val dayInfo = cloudMeta?.let { formatDisplayDate(it) }
            return if (dayInfo != null) {
                SyncResult.Success(context.getString(R.string.sync_pull_success_with_day, dayInfo))
            } else {
                SyncResult.Success(context.getString(R.string.sync_pull_success))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pull failed for $saveFolderName", e)
            return SyncResult.Error(context.getString(R.string.sync_error_pull_failed, e.message ?: "Unknown error"))
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
            onProgress?.invoke(context.getString(R.string.sync_progress_reading))

            // Read local save files
            val localFiles = saveFileManager.readLocalSave(saveFolderName)
            if (localFiles.isEmpty()) {
                return SyncResult.Error(context.getString(R.string.sync_error_no_local_files, saveFolderName))
            }

            // Parse local metadata
            val localInfoData = localFiles["SaveGameInfo"]
            val localMeta = localInfoData?.let { metadataParser.parseFromBytes(it) }

            // Validate local save
            val mainSaveData = localFiles[saveFolderName]
            val validation = saveValidator.validateSaveData(mainSaveData, localInfoData)
            if (!validation.valid) {
                return SyncResult.Error(
                    context.getString(R.string.sync_error_invalid_local, validation.errors.joinToString(", "))
                )
            }

            if (!force) {
                // Compare with cloud
                onProgress?.invoke(context.getString(R.string.sync_progress_checking_cloud))
                val cloudSaves = cloudService.listCloudSaves()
                val cloudFiles = cloudSaves[saveFolderName]

                if (cloudFiles != null) {
                    // Download just the SaveGameInfo to compare
                    val cloudInfoFile = cloudFiles.find { it.baseName == "SaveGameInfo" }
                    if (cloudInfoFile != null) {
                        val cloudInfoData = cloudService.downloadFile(cloudInfoFile.fullPath)
                        val cloudMeta = metadataParser.parseFromBytes(cloudInfoData)

                        if (cloudMeta != null && localMeta != null) {
                            val comparison = conflictResolver.compare(cloudMeta, localMeta)
                            if (comparison.direction == SyncDirection.CONFLICT) {
                                return SyncResult.NeedsConflictResolution(comparison)
                            }
                            if (comparison.direction == SyncDirection.PULL) {
                                return SyncResult.Error(context.getString(R.string.sync_error_cloud_newer))
                            }
                            if (comparison.direction == SyncDirection.SKIP) {
                                return SyncResult.Success(context.getString(R.string.sync_already_in_sync))
                            }
                        }
                    }
                }
            }

            // Upload to Steam Cloud
            onProgress?.invoke(context.getString(R.string.sync_progress_uploading))
            cloudService.uploadSave(saveFolderName, localFiles) { uploaded, total ->
                onProgress?.invoke(context.getString(R.string.sync_progress_uploading_file, uploaded, total))
            }

            val dayInfo = localMeta?.let { formatDisplayDate(it) }
            return if (dayInfo != null) {
                SyncResult.Success(context.getString(R.string.sync_push_success_with_day, dayInfo))
            } else {
                SyncResult.Success(context.getString(R.string.sync_push_success))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Push failed for $saveFolderName", e)
            return SyncResult.Error(context.getString(R.string.sync_error_push_failed, e.message ?: "Unknown error"))
        }
    }

    private fun formatDisplayDate(meta: SaveMetadata): String {
        val seasonName = when (meta.season) {
            0 -> context.getString(R.string.save_season_spring)
            1 -> context.getString(R.string.save_season_summer)
            2 -> context.getString(R.string.save_season_fall)
            3 -> context.getString(R.string.save_season_winter)
            else -> context.getString(R.string.save_season_unknown)
        }
        return context.getString(R.string.save_display_date, seasonName, meta.dayOfMonth, meta.year)
    }
}
