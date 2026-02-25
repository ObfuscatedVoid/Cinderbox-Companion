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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModDetailState(
    val mod: RemoteMod? = null,
    val files: List<RemoteModFile> = emptyList(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val downloadProgress: ModDownloadProgress = ModDownloadProgress(),
    val installedUniqueIds: Set<String> = emptySet(),
    val downloadErrorUrl: String? = null,
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
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val mod = nexusSource.getModDetails(modId)
                val files = nexusSource.getModFiles(modId)
                _state.update { it.copy(mod = mod, files = files, isLoading = false) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load mod details", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load mod details")
                }
            }
        }
    }

    private fun loadInstalledIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val mods = fileManager.listInstalledMods()
            _state.update {
                it.copy(installedUniqueIds = mods.map { m -> m.manifest.uniqueID.lowercase() }.toSet())
            }
        }
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            downloadManager.progress.collect { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
        }
    }

    fun installFile(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(isDownloading = true, error = null, downloadErrorUrl = null) }
                val url = nexusSource.getDownloadUrl(modId, fileId)
                val modName = _state.value.mod?.name ?: "Unknown"
                downloadManager.startDownload(url, modName, modId, source)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to get download URL", e)
                val (friendlyMessage, browserUrl) = mapDownloadError(e.message)
                _state.update {
                    it.copy(
                        isDownloading = false,
                        error = friendlyMessage,
                        downloadErrorUrl = browserUrl,
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null, downloadErrorUrl = null) }
    }

    private fun mapDownloadError(message: String?): Pair<String, String?> {
        val nexusUrl = "https://www.nexusmods.com/stardewvalley/mods/$modId?tab=files"
        if (message == null) return "Failed to start download" to null
        return when {
            message.contains("No File found", ignoreCase = true) ->
                "This file is no longer available for download" to nexusUrl
            message.contains("Not Premium", ignoreCase = true) ||
            message.contains("premium", ignoreCase = true) ->
                "Nexus Premium required for API downloads. Use the browser to download manually." to nexusUrl
            message.contains("403") ->
                "Access denied. Free Nexus accounts must download via browser." to nexusUrl
            message.contains("429") ->
                "Rate limit reached. Please try again later." to null
            else -> message to nexusUrl
        }
    }
}
