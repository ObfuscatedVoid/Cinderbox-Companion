package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.download.AppUpdateManager
import com.sdvsync.download.GitHubReleaseChecker
import com.sdvsync.download.GitHubReleaseInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateState(
    val updateInfo: GitHubReleaseInfo? = null,
    val showDialog: Boolean = false,
    val showInstallPermissionPrompt: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApk: File? = null,
    val downloadError: String? = null
)

class AppUpdateViewModel(
    private val updateManager: AppUpdateManager,
    private val releaseChecker: GitHubReleaseChecker
) : ViewModel() {

    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.APP_REPO,
                    GitHubReleaseChecker.APP_ASSET_PATTERN
                ) ?: return@launch

                if (updateManager.shouldShowUpdate(info.version)) {
                    _state.update { it.copy(updateInfo = info, showDialog = true) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    fun dismiss() {
        _state.update { it.copy(showDialog = false, downloadedApk = null) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(isDownloading = false, downloadProgress = 0f, downloadedApk = null, showDialog = false)
        }
    }

    fun skipVersion() {
        val version = _state.value.updateInfo?.version ?: return
        updateManager.setSkippedVersion(version)
        _state.update { it.copy(showDialog = false) }
    }

    fun startDownload() {
        if (!updateManager.canInstallPackages()) {
            _state.update { it.copy(showInstallPermissionPrompt = true) }
            return
        }
        val info = _state.value.updateInfo ?: return
        _state.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadError = null,
                downloadedApk = null,
                showInstallPermissionPrompt = false
            )
        }
        downloadJob = viewModelScope.launch {
            val apk = updateManager.downloadUpdate(info.assetUrl, info.assetName) { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
            if (apk != null) {
                _state.update { it.copy(isDownloading = false, downloadedApk = apk) }
            } else {
                _state.update { it.copy(isDownloading = false, downloadError = "Download failed. Tap to retry.") }
            }
        }
    }

    fun retryDownload() {
        _state.update { it.copy(downloadError = null) }
        startDownload()
    }

    fun onInstallPermissionGranted() {
        _state.update { it.copy(showInstallPermissionPrompt = false) }
        if (updateManager.canInstallPackages()) {
            startDownload()
        }
    }
}
