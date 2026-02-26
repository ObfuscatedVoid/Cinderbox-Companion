package com.sdvsync.download

import android.content.Context
import com.sdvsync.logging.AppLogger
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
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

data class SmapiSetupProgress(
    val isRunning: Boolean = false,
    val percent: Float = 0f,
    val currentFile: String = "",
    val extractedFiles: Int = 0,
    val totalFiles: Int = 0,
    val completed: Boolean = false,
    val errorMessage: String? = null,
)

class GameDownloadManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GameDownloadManager"

        internal val _progress = MutableStateFlow(DownloadProgress())
        internal val _smapiProgress = MutableStateFlow(SmapiSetupProgress())

        val CINDERBOX_DLLS = listOf(
            "Stardew Valley.dll",
            "StardewValley.GameData.dll",
            "BmFont.dll",
            "xTile.dll",
            "Lidgren.Network.dll",
        )
        const val CINDERBOX_CONTENT_DIR = "Content"
        const val CINDERBOX_DEST = "/storage/emulated/0/StardewValley/GameFiles"
        const val SMAPI_ASSET_NAME = "smapi-internal.zip"
        const val SMAPI_DEST = "/storage/emulated/0/StardewValley/smapi-internal"
        const val MODS_DIR = "/storage/emulated/0/StardewValley/Mods"
    }

    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()
    val smapiProgress: StateFlow<SmapiSetupProgress> = _smapiProgress.asStateFlow()

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

    suspend fun extractSmapiAsset() {
        _smapiProgress.value = SmapiSetupProgress(isRunning = true, currentFile = "Counting files…")

        val buffer = ByteArray(256 * 1024)
        val smapiDestDir = File(SMAPI_DEST)
        val errors = mutableListOf<String>()
        var extracted = 0

        // Count entries first
        val totalEntries = try {
            context.assets.open(SMAPI_ASSET_NAME).use { countZipEntries(it) } + 1 // +1 for Mods/
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to count SMAPI entries", e)
            _smapiProgress.value = SmapiSetupProgress(
                completed = true,
                errorMessage = "Failed to open SMAPI asset: ${e.message}",
            )
            return
        }

        _smapiProgress.value = SmapiSetupProgress(
            isRunning = true,
            totalFiles = totalEntries,
        )

        // Extract zip from assets
        try {
            context.assets.open(SMAPI_ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()

                        val entryName = entry.name
                        val displayName = "smapi-internal/$entryName"

                        _smapiProgress.value = _smapiProgress.value.copy(
                            currentFile = displayName,
                            extractedFiles = extracted,
                            percent = if (totalEntries > 0) extracted.toFloat() / totalEntries else 0f,
                        )

                        if (!entry.isDirectory) {
                            try {
                                val destFile = File(smapiDestDir, entryName)
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().buffered().use { output ->
                                    var bytesRead: Int
                                    while (zip.read(buffer).also { bytesRead = it } != -1) {
                                        coroutineContext.ensureActive()
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                                extracted++
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                AppLogger.e(TAG, "Failed to extract $displayName", e)
                                errors.add(displayName)
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to open SMAPI asset", e)
            errors.add(SMAPI_ASSET_NAME)
        }

        // Create Mods/ directory
        _smapiProgress.value = _smapiProgress.value.copy(
            currentFile = "Mods/",
            extractedFiles = extracted,
            percent = if (totalEntries > 0) extracted.toFloat() / totalEntries else 0f,
        )
        try {
            File(MODS_DIR).mkdirs()
            extracted++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to create Mods directory", e)
            errors.add("Mods/")
        }

        AppLogger.i(TAG, "SMAPI setup complete: $extracted/$totalEntries files, ${errors.size} errors")

        _smapiProgress.value = SmapiSetupProgress(
            isRunning = false,
            percent = 1f,
            extractedFiles = extracted,
            totalFiles = totalEntries,
            completed = true,
            errorMessage = if (errors.isNotEmpty()) errors.joinToString(", ") else null,
        )
    }

    private fun countZipEntries(inputStream: InputStream): Int {
        var count = 0
        ZipInputStream(inputStream).use { zip ->
            while (zip.nextEntry != null) {
                count++
                zip.closeEntry()
            }
        }
        return count
    }

    suspend fun copyToCinderbox(installDir: String) {
        val srcDir = File(installDir)
        val destDir = File(CINDERBOX_DEST)

        // --- Count total files up front ---
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

        val smapiEntryCount = context.assets.open(SMAPI_ASSET_NAME).use { countZipEntries(it) }
        val totalFiles = filesToCopy.size + smapiEntryCount + 1 // +1 for Mods/ dir

        AppLogger.i(TAG, "Cinderbox setup: ${filesToCopy.size} game files + $smapiEntryCount SMAPI files + Mods dir = $totalFiles total")

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

        // --- Phase 1: Copy DLLs + Content ---
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

        // --- Phase 2: Extract SMAPI zip ---
        val smapiDestDir = File(SMAPI_DEST)
        try {
            context.assets.open(SMAPI_ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()

                        val entryName = entry.name
                        val displayName = "smapi-internal/$entryName"

                        _progress.value = _progress.value.copy(
                            currentFile = displayName,
                            copiedFiles = copied,
                            overallPercent = if (totalFiles > 0) copied.toFloat() / totalFiles else 0f,
                        )

                        if (!entry.isDirectory) {
                            try {
                                val destFile = File(smapiDestDir, entryName)
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().buffered().use { output ->
                                    var bytesRead: Int
                                    while (zip.read(buffer).also { bytesRead = it } != -1) {
                                        coroutineContext.ensureActive()
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                                copied++
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                AppLogger.e(TAG, "Failed to extract $displayName", e)
                                errors.add(displayName)
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to open SMAPI asset", e)
            errors.add(SMAPI_ASSET_NAME)
        }

        // --- Phase 3: Create Mods/ directory ---
        _progress.value = _progress.value.copy(
            currentFile = "Mods/",
            copiedFiles = copied,
            overallPercent = if (totalFiles > 0) copied.toFloat() / totalFiles else 0f,
        )
        try {
            File(MODS_DIR).mkdirs()
            copied++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to create Mods directory", e)
            errors.add("Mods/")
        }

        AppLogger.i(TAG, "Cinderbox setup complete: $copied/$totalFiles files, ${errors.size} errors")

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
