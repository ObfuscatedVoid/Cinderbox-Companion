package com.sdvsync.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadState {
    IDLE,
    PREPARING,
    DOWNLOADING,
    COMPLETED,
    ERROR,
    CANCELLED,
}

data class DownloadProgress(
    val state: DownloadState = DownloadState.IDLE,
    val overallPercent: Float = 0f,
    val currentFile: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val errorMessage: String? = null,
)

class GameDownloadManager(
    private val context: Context,
) {
    companion object {
        internal val _progress = MutableStateFlow(DownloadProgress())
    }

    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    fun startDownload(
        branch: String = "public",
        branchPassword: String? = null,
        installDirectory: String,
        os: String = "windows",
    ) {
        GameDownloadService.start(
            context = context,
            branch = branch,
            password = branchPassword,
            installDir = installDirectory,
            os = os,
        )
    }

    fun cancelDownload() {
        GameDownloadService.stop(context)
    }
}
