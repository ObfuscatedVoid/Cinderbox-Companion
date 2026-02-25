package com.sdvsync.download

import android.content.Context
import com.sdvsync.logging.AppLogger
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.coroutines.coroutineContext

enum class DownloadState {
    IDLE,
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    COPYING,
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
    val verifiedFiles: Int = 0,
    val totalFilesToVerify: Int = 0,
    val verificationPassed: Boolean = true,
    val verificationErrors: List<String> = emptyList(),
    val copiedFiles: Int = 0,
    val totalFilesToCopy: Int = 0,
    val copyCompleted: Boolean = false,
    val copyErrors: List<String> = emptyList(),
)

class GameDownloadManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GameDownloadManager"

        internal val _progress = MutableStateFlow(DownloadProgress())

        val CINDERBOX_DLLS = listOf(
            "Stardew Valley.dll",
            "StardewValley.GameData.dll",
            "BmFont.dll",
            "xTile.dll",
            "Lidgren.Network.dll",
        )
        const val CINDERBOX_CONTENT_DIR = "Content"
        const val CINDERBOX_DEST = "/storage/emulated/0/StardewValley/GameFiles"
    }

    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    fun startDownload(
        branch: String = "public",
        branchPassword: String? = null,
        installDirectory: String,
        os: String = "windows",
        verifyAfterDownload: Boolean = true,
    ) {
        GameDownloadService.start(
            context = context,
            branch = branch,
            password = branchPassword,
            installDir = installDirectory,
            os = os,
            verify = verifyAfterDownload,
        )
    }

    fun cancelDownload() {
        GameDownloadService.stop(context)
    }

    suspend fun copyToCinderbox(installDir: String) {
        val srcDir = File(installDir)
        val destDir = File(CINDERBOX_DEST)

        // Enumerate source files: DLLs + Content/ tree
        val filesToCopy = mutableListOf<Pair<File, File>>() // source -> dest

        for (dll in CINDERBOX_DLLS) {
            val src = File(srcDir, dll)
            if (src.exists()) {
                filesToCopy.add(src to File(destDir, dll))
            }
        }

        val contentSrc = File(srcDir, CINDERBOX_CONTENT_DIR)
        if (contentSrc.exists() && contentSrc.isDirectory) {
            contentSrc.walk().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(srcDir).path
                filesToCopy.add(file to File(destDir, relativePath))
            }
        }

        val totalFiles = filesToCopy.size
        AppLogger.i(TAG, "Copying $totalFiles files to Cinderbox: $CINDERBOX_DEST")

        _progress.value = _progress.value.copy(
            state = DownloadState.COPYING,
            currentFile = "",
            copiedFiles = 0,
            totalFilesToCopy = totalFiles,
            overallPercent = 0f,
        )

        val errors = mutableListOf<String>()
        var copied = 0
        val buffer = ByteArray(256 * 1024)

        for ((src, dest) in filesToCopy) {
            coroutineContext.ensureActive()

            val relativeName = src.relativeTo(srcDir).path
            _progress.value = _progress.value.copy(
                currentFile = relativeName,
                copiedFiles = copied,
                overallPercent = if (totalFiles > 0) copied.toFloat() / totalFiles else 0f,
            )

            try {
                dest.parentFile?.mkdirs()
                src.inputStream().buffered().use { input ->
                    dest.outputStream().buffered().use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                copied++
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Failed to copy $relativeName", e)
                errors.add(relativeName)
            }
        }

        AppLogger.i(TAG, "Cinderbox copy complete: $copied/$totalFiles files, ${errors.size} errors")

        _progress.value = _progress.value.copy(
            state = DownloadState.COMPLETED,
            currentFile = "",
            copiedFiles = copied,
            totalFilesToCopy = totalFiles,
            overallPercent = 1f,
            copyCompleted = true,
            copyErrors = errors,
        )
    }
}
