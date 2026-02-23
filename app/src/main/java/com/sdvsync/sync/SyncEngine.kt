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
    data class Success(val message: String, val warning: String? = null) : SyncResult()
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
     * Pull a save from Steam Cloud to a local device.
     */
    suspend fun pullSave(
        saveFolderName: String,
        force: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): SyncResult {
        try {
            onProgress?.invoke(context.getString(R.string.sync_progress_downloading))

            // Download save files from the cloud
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

            // Check version compatibility (warn but don't block)
            val localSaves = saveFileManager.listLocalSaves()
            val existingLocal = localSaves.find { it.folderName == saveFolderName }
            val versionWarning = if (cloudMeta != null) {
                conflictResolver.checkVersionCompatibility(
                    cloudMeta.gameVersion,
                    existingLocal?.metadata?.gameVersion,
                )
            } else null

            // Backup existing local save
            onProgress?.invoke(context.getString(R.string.sync_progress_backing_up))
            if (existingLocal != null) {
                val localFiles = saveFileManager.readLocalSave(saveFolderName)
                if (localFiles.isNotEmpty()) {
                    backupManager.backupSaveData(saveFolderName, localFiles)
                }
            }

            // Write to the device
            onProgress?.invoke(context.getString(R.string.sync_progress_writing))
            val writeSuccess = saveFileManager.writeLocalSave(saveFolderName, cloudFiles)
            if (!writeSuccess) {
                return SyncResult.Error(context.getString(R.string.sync_error_write_failed))
            }

            val dayInfo = cloudMeta?.let { formatDisplayDate(it) }
            val successMsg = if (dayInfo != null) {
                context.getString(R.string.sync_pull_success_with_day, dayInfo)
            } else {
                context.getString(R.string.sync_pull_success)
            }
            return SyncResult.Success(successMsg, warning = versionWarning)

        } catch (e: Exception) {
            Log.e(TAG, "Pull failed for $saveFolderName", e)
            return SyncResult.Error(context.getString(R.string.sync_error_pull_failed, e.message ?: "Unknown error"))
        }
    }

    /**
     * Push a save from local device to Steam Cloud.
     * Always backs up the existing cloud save before overwriting.
     */
    suspend fun pushSave(
        saveFolderName: String,
        force: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): SyncResult {
        try {
            onProgress?.invoke(context.getString(R.string.sync_progress_reading))

            // Step 1: Read and validate local save files
            val localFiles = saveFileManager.readLocalSave(saveFolderName)
            if (localFiles.isEmpty()) {
                return SyncResult.Error(context.getString(R.string.sync_error_no_local_files, saveFolderName))
            }

            // Check for in-progress saves (temp files from game still writing)
            val tempFiles = localFiles.keys.filter { it.contains("_STARDEWVALLEYSAVETMP") }
            if (tempFiles.isNotEmpty()) {
                return SyncResult.Error(context.getString(R.string.sync_error_save_in_progress))
            }

            val localInfoData = localFiles["SaveGameInfo"]
            val localMeta = localInfoData?.let { metadataParser.parseFromBytes(it) }

            val mainSaveData = localFiles[saveFolderName]
            val validation = saveValidator.validateSaveData(mainSaveData, localInfoData)
            if (!validation.valid) {
                return SyncResult.Error(
                    context.getString(R.string.sync_error_invalid_local, validation.errors.joinToString(", "))
                )
            }

            // Step 2: Check cloud state (always — we need to know if there's something to back up)
            onProgress?.invoke(context.getString(R.string.sync_progress_checking_cloud))
            val cloudSaves = cloudService.listCloudSaves()
            val cloudFileList = cloudSaves[saveFolderName]

            // Step 3: Conflict check (only when !force)
            if (!force && cloudFileList != null) {
                val cloudInfoFile = cloudFileList.find { it.baseName == "SaveGameInfo" }
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

            // Step 4: Back up existing cloud save before overwriting
            if (cloudFileList != null && cloudFileList.isNotEmpty()) {
                try {
                    onProgress?.invoke(context.getString(R.string.sync_progress_backing_up_cloud))

                    val cloudFiles = mutableMapOf<String, ByteArray>()
                    cloudFileList.forEachIndexed { index, file ->
                        onProgress?.invoke(
                            context.getString(
                                R.string.sync_progress_backing_up_cloud_file,
                                index + 1,
                                cloudFileList.size,
                            )
                        )
                        val data = cloudService.downloadFile(file.fullPath)
                        cloudFiles[file.baseName] = data
                    }

                    backupManager.backupSaveData(saveFolderName, cloudFiles)
                    Log.d(TAG, "pushSave: backed up ${cloudFiles.size} cloud files for $saveFolderName")
                } catch (e: Exception) {
                    Log.e(TAG, "pushSave: cloud backup failed for $saveFolderName, aborting push", e)
                    return SyncResult.Error(
                        context.getString(R.string.sync_error_backup_failed, e.message ?: "Unknown error")
                    )
                }
            }

            // Step 5: Upload local files to cloud
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
