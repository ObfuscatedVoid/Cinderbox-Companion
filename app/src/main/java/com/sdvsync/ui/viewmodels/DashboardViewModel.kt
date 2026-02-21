package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.saves.LocalSave
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadata
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.steam.CloudFile
import com.sdvsync.steam.SteamCloudService
import com.sdvsync.sync.ConflictResolver
import com.sdvsync.sync.SyncDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SaveEntry(
    val folderName: String,
    val cloudMeta: SaveMetadata?,
    val localMeta: SaveMetadata?,
    val syncDirection: SyncDirection,
    val statusMessage: String,
    val hasCloud: Boolean,
    val hasLocal: Boolean,
)

data class DashboardState(
    val saves: List<SaveEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class DashboardViewModel(
    private val cloudService: SteamCloudService,
    private val saveFileManager: SaveFileManager,
    private val metadataParser: SaveMetadataParser,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val conflictResolver = ConflictResolver()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Fetch cloud saves
                val cloudSaves = cloudService.listCloudSaves()
                val cloudMetadata = mutableMapOf<String, SaveMetadata>()

                for ((folderName, files) in cloudSaves) {
                    val infoFile = files.find { it.filename == "SaveGameInfo" }
                    if (infoFile != null) {
                        try {
                            val data = cloudService.downloadFile(infoFile.fullPath)
                            metadataParser.parseFromBytes(data)?.let {
                                cloudMetadata[folderName] = it
                            }
                        } catch (_: Exception) {
                            // Skip saves we can't parse
                        }
                    }
                }

                // Fetch local saves
                val localSaves = try {
                    saveFileManager.listLocalSaves()
                } catch (_: Exception) {
                    emptyList()
                }
                val localMap = localSaves.associateBy { it.folderName }

                // Merge into unified list
                val allFolderNames = (cloudMetadata.keys + localMap.keys).distinct()
                val entries = allFolderNames.map { folderName ->
                    val cloudMeta = cloudMetadata[folderName]
                    val localMeta = localMap[folderName]?.metadata

                    val comparison = conflictResolver.compare(cloudMeta, localMeta)

                    SaveEntry(
                        folderName = folderName,
                        cloudMeta = cloudMeta,
                        localMeta = localMeta,
                        syncDirection = comparison.direction,
                        statusMessage = comparison.message,
                        hasCloud = cloudMeta != null,
                        hasLocal = localMeta != null,
                    )
                }.sortedByDescending {
                    // Sort by most recent activity
                    maxOf(
                        it.cloudMeta?.daysPlayed ?: 0,
                        it.localMeta?.daysPlayed ?: 0,
                    )
                }

                _state.value = DashboardState(saves = entries, isLoading = false)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load saves: ${e.message}",
                )
            }
        }
    }
}
