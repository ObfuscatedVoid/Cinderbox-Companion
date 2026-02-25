package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDownloadManager
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.mods.models.ModDownloadProgress
import com.sdvsync.mods.models.RemoteMod
import com.sdvsync.mods.models.RemoteModFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModDetailState(
    val mod: RemoteMod? = null,
    val files: List<RemoteModFile> = emptyList(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val downloadProgress: ModDownloadProgress = ModDownloadProgress(),
    val installedUniqueIds: Set<String> = emptySet(),
)

class ModDetailViewModel(
    private val nexusSource: NexusModSource,
    private val downloadManager: ModDownloadManager,
    private val fileManager: ModFileManager,
    private val modId: String,
    private val source: String,
) : ViewModel() {

    companion object {
        private const val TAG = "ModDetailVM"
    }

    private val _state = MutableStateFlow(ModDetailState())
    val state: StateFlow<ModDetailState> = _state.asStateFlow()

    init {
        loadModDetails()
        observeDownloadProgress()
        loadInstalledIds()
    }

    private fun loadModDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val mod = nexusSource.getModDetails(modId)
                val files = nexusSource.getModFiles(modId)
                _state.value = _state.value.copy(
                    mod = mod,
                    files = files,
                    isLoading = false,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load mod details", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load mod details",
                )
            }
        }
    }

    private fun loadInstalledIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val mods = fileManager.listInstalledMods()
            _state.value = _state.value.copy(
                installedUniqueIds = mods.map { it.manifest.uniqueID.lowercase() }.toSet(),
            )
        }
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            downloadManager.progress.collect { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
            }
        }
    }

    fun installFile(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isDownloading = true)
                val url = nexusSource.getDownloadUrl(modId, fileId)
                val modName = _state.value.mod?.name ?: "Unknown"
                downloadManager.startDownload(url, modName, modId, source)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get download URL", e)
                val friendlyMessage = mapDownloadError(e.message)
                _state.value = _state.value.copy(
                    isDownloading = false,
                    error = friendlyMessage,
                )
            }
        }
    }

    private fun mapDownloadError(message: String?): String {
        if (message == null) return "Failed to start download"
        return when {
            message.contains("No File found", ignoreCase = true) ->
                "This file is no longer available for download"
            message.contains("Not Premium", ignoreCase = true) ||
            message.contains("premium", ignoreCase = true) ->
                "Nexus Premium required for direct downloads"
            message.contains("403") ->
                "Access denied. Check your API key permissions."
            message.contains("429") ->
                "Rate limit reached. Please try again later."
            else -> message
        }
    }
}
