package com.sdvsync.ui.viewmodels

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.download.DownloadProgress
import com.sdvsync.download.DownloadState
import com.sdvsync.download.GameDownloadManager
import com.sdvsync.logging.AppLogger
import com.sdvsync.steam.SteamContentService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameDownloadState(
    val isLoadingBranches: Boolean = false,
    val branches: List<SteamContentService.BranchInfo> = emptyList(),
    val selectedBranch: String = "public",
    val branchPassword: String = "",
    val selectedOs: String = "windows",
    val installDirectory: String = "",
    val verifyAfterDownload: Boolean = true,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val error: String? = null,
)

class GameDownloadViewModel(
    private val context: Context,
    private val contentService: SteamContentService,
    private val downloadManager: GameDownloadManager,
) : ViewModel() {

    companion object {
        private const val TAG = "GameDownloadVM"
    }

    private val _state = MutableStateFlow(GameDownloadState())
    val state: StateFlow<GameDownloadState> = _state.asStateFlow()

    init {
        val defaultDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).resolve("StardewValley").absolutePath
        _state.value = _state.value.copy(installDirectory = defaultDir)

        viewModelScope.launch {
            downloadManager.progress.collect { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
            }
        }
    }

    fun loadBranches() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBranches = true, error = null)
            try {
                val info = contentService.getAppBranches()
                _state.value = _state.value.copy(
                    branches = info.branches,
                    isLoadingBranches = false,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load branches", e)
                _state.value = _state.value.copy(
                    isLoadingBranches = false,
                    error = context.getString(R.string.download_error_load_branches, e.message ?: ""),
                )
            }
        }
    }

    fun selectBranch(name: String) {
        _state.value = _state.value.copy(selectedBranch = name, branchPassword = "")
    }

    fun selectOs(os: String) {
        _state.value = _state.value.copy(selectedOs = os)
    }

    fun updateInstallDirectory(path: String) {
        _state.value = _state.value.copy(installDirectory = path)
    }

    fun updateBranchPassword(password: String) {
        _state.value = _state.value.copy(branchPassword = password)
    }

    fun toggleVerification() {
        _state.value = _state.value.copy(verifyAfterDownload = !_state.value.verifyAfterDownload)
    }

    fun startDownload() {
        val current = _state.value
        if (current.installDirectory.isBlank()) {
            _state.value = current.copy(error = context.getString(R.string.download_error_no_directory))
            return
        }

        val selectedBranchInfo = current.branches.find { it.name == current.selectedBranch }
        if (selectedBranchInfo?.passwordRequired == true && current.branchPassword.isBlank()) {
            _state.value = current.copy(error = context.getString(R.string.download_error_password_required))
            return
        }

        _state.value = current.copy(error = null)

        downloadManager.startDownload(
            branch = current.selectedBranch,
            branchPassword = current.branchPassword.ifBlank { null },
            installDirectory = current.installDirectory,
            os = current.selectedOs,
            verifyAfterDownload = current.verifyAfterDownload,
        )
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun copyCinderbox() {
        viewModelScope.launch {
            try {
                downloadManager.copyToCinderbox(_state.value.installDirectory)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cinderbox copy failed", e)
            }
        }
    }

    fun resetDownload() {
        _state.value = _state.value.copy(
            downloadProgress = DownloadProgress(),
        )
    }
}
