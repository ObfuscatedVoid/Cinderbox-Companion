package com.sdvsync.mods

import android.content.Context
import com.sdvsync.mods.models.ModDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages mod download progress state.
 * Acts as a shared state holder between the download service and UI.
 */
class ModDownloadManager(private val context: Context) {
    companion object {
        internal val _progress = MutableStateFlow(ModDownloadProgress())
    }

    val progress: StateFlow<ModDownloadProgress> = _progress.asStateFlow()

    fun startDownload(url: String, modName: String, modId: String, source: String) {
        ModDownloadService.start(context, url, modName, modId, source)
    }

    fun cancelDownload() {
        ModDownloadService.stop(context)
    }

    fun resetProgress() {
        _progress.value = ModDownloadProgress()
    }
}
