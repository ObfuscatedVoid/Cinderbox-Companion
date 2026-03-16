package com.sdvsync.download

import android.content.Context
import com.sdvsync.logging.AppLogger
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DownloadState {
    IDLE,
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    COPYING,
    ERROR,
    CANCELLED
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
    val copyErrors: List<String> = emptyList()
)

data class CinderboxDownloadProgress(
    val isDownloading: Boolean = false,
    val percent: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val completed: Boolean = false,
    val apkFile: File? = null,
    val errorMessage: String? = null
)

data class SmapiSetupProgress(
    val isRunning: Boolean = false,
    val percent: Float = 0f,
    val currentFile: String = "",
    val extractedFiles: Int = 0,
    val totalFiles: Int = 0,
    val completed: Boolean = false,
    val errorMessage: String? = null,
    val isDownloading: Boolean = false,
    val downloadPercent: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalDownloadBytes: Long = 0
)

class GameDownloadManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val releaseChecker: GitHubReleaseChecker
) {
    companion object {
        private const val TAG = "GameDownloadManager"

        internal val _progress = MutableStateFlow(DownloadProgress())
        internal val _smapiProgress = MutableStateFlow(SmapiSetupProgress())
        internal val _cinderboxProgress = MutableStateFlow(CinderboxDownloadProgress())

        val CINDERBOX_DLLS =
            listOf(
                "Stardew Valley.dll",
                "StardewValley.GameData.dll",
                "BmFont.dll",
                "xTile.dll",
                "Lidgren.Network.dll"
            )
        const val CINDERBOX_CONTENT_DIR = "Content"
        const val CINDERBOX_BASE_DIR = "/storage/emulated/0/StardewValley"
        const val CINDERBOX_DEST = "$CINDERBOX_BASE_DIR/GameFiles"

        const val SMAPI_CACHE_FILENAME = "smapi-internal.zip"
        const val SMAPI_DEST = "/storage/emulated/0/StardewValley/smapi-internal"
        const val MODS_DIR = "/storage/emulated/0/StardewValley/Mods"
    }

    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()
    val smapiProgress: StateFlow<SmapiSetupProgress> = _smapiProgress.asStateFlow()
    val cinderboxProgress: StateFlow<CinderboxDownloadProgress> = _cinderboxProgress.asStateFlow()

    fun startDownload(
        branch: String = "public",
        branchPassword: String? = null,
        installDirectory: String,
        os: String = "windows",
        verifyAfterDownload: Boolean = true
    ) {
        GameDownloadService.start(
            context = context,
            branch = branch,
            password = branchPassword,
            installDir = installDirectory,
            os = os,
            verify = verifyAfterDownload
        )
    }

    fun cancelDownload() {
        GameDownloadService.stop(context)
    }

    suspend fun downloadCinderboxApk() {
        _cinderboxProgress.value = CinderboxDownloadProgress(isDownloading = true)

        withContext(Dispatchers.IO) {
            try {
                val releaseInfo = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.CINDERBOX_REPO,
                    GitHubReleaseChecker.CINDERBOX_ASSET_PATTERN
                )
                if (releaseInfo == null) {
                    _cinderboxProgress.value =
                        CinderboxDownloadProgress(errorMessage = "Could not find Cinderbox release")
                    return@withContext
                }

                val destFile = File(context.cacheDir, releaseInfo.assetName)
                val request = Request.Builder().url(releaseInfo.assetUrl).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    _cinderboxProgress.value =
                        CinderboxDownloadProgress(errorMessage = "HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body
                    ?: run {
                        response.close()
                        _cinderboxProgress.value =
                            CinderboxDownloadProgress(errorMessage = "Empty response body")
                        return@withContext
                    }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(64 * 1024)

                destFile.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            _cinderboxProgress.value =
                                CinderboxDownloadProgress(
                                    isDownloading = true,
                                    percent =
                                    if (totalBytes > 0) {
                                        downloadedBytes.toFloat() / totalBytes
                                    } else {
                                        0f
                                    },
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                        }
                    }
                }

                AppLogger.i(TAG, "Cinderbox APK downloaded: ${destFile.length()} bytes")

                releaseChecker.setInstalledVersion(
                    GitHubReleaseChecker.KEY_CINDERBOX_VERSION,
                    releaseInfo.version
                )

                _cinderboxProgress.value =
                    CinderboxDownloadProgress(
                        completed = true,
                        percent = 1f,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        apkFile = destFile
                    )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Cinderbox APK download failed", e)
                _cinderboxProgress.value =
                    CinderboxDownloadProgress(errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    suspend fun downloadSmapiZip(): File? {
        return withContext(Dispatchers.IO) {
            val destFile = File(context.cacheDir, SMAPI_CACHE_FILENAME)
            try {
                val releaseInfo = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.SMAPI_REPO,
                    GitHubReleaseChecker.SMAPI_ASSET_PATTERN
                )
                if (releaseInfo == null) {
                    _smapiProgress.value =
                        SmapiSetupProgress(errorMessage = "Could not find SMAPI release")
                    return@withContext null
                }

                _smapiProgress.value = SmapiSetupProgress(
                    isDownloading = true,
                    totalDownloadBytes = releaseInfo.assetSize
                )

                val request = Request.Builder().url(releaseInfo.assetUrl).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    _smapiProgress.value =
                        SmapiSetupProgress(errorMessage = "HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: run {
                    response.close()
                    _smapiProgress.value =
                        SmapiSetupProgress(errorMessage = "Empty response body")
                    return@withContext null
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(64 * 1024)

                destFile.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            _smapiProgress.value = SmapiSetupProgress(
                                isDownloading = true,
                                downloadPercent = if (totalBytes > 0) {
                                    downloadedBytes.toFloat() / totalBytes
                                } else {
                                    0f
                                },
                                downloadedBytes = downloadedBytes,
                                totalDownloadBytes = totalBytes
                            )
                        }
                    }
                }

                AppLogger.i(TAG, "SMAPI zip downloaded: ${destFile.length()} bytes")

                destFile
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "SMAPI zip download failed", e)
                destFile.delete()
                _smapiProgress.value =
                    SmapiSetupProgress(errorMessage = e.message ?: "Download failed")
                null
            }
        }
    }

    suspend fun extractSmapiFromCache() {
        val cacheFile = File(context.cacheDir, SMAPI_CACHE_FILENAME)
        if (!cacheFile.exists()) {
            _smapiProgress.value =
                SmapiSetupProgress(errorMessage = "SMAPI zip not found. Download it first.")
            return
        }

        _smapiProgress.value = SmapiSetupProgress(isRunning = true, currentFile = "Counting files…")

        val buffer = ByteArray(256 * 1024)
        val smapiDestDir = File(SMAPI_DEST)
        val errors = mutableListOf<String>()
        var extracted = 0

        val totalEntries =
            try {
                cacheFile.inputStream().use { countZipEntries(it) } + 1 // +1 for Mods/
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Failed to count SMAPI entries", e)
                _smapiProgress.value =
                    SmapiSetupProgress(
                        completed = true,
                        errorMessage = "Failed to open SMAPI zip: ${e.message}"
                    )
                return
            }

        _smapiProgress.value = SmapiSetupProgress(isRunning = true, totalFiles = totalEntries)

        try {
            cacheFile.inputStream().use { fileStream ->
                ZipInputStream(fileStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()

                        val entryName = entry.name
                        val displayName = "smapi-internal/$entryName"

                        _smapiProgress.value =
                            _smapiProgress.value.copy(
                                currentFile = displayName,
                                extractedFiles = extracted,
                                percent =
                                if (totalEntries > 0) {
                                    extracted.toFloat() / totalEntries
                                } else {
                                    0f
                                }
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
                                if (e is kotlinx.coroutines.CancellationException) {
                                    throw e
                                }
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
            AppLogger.e(TAG, "Failed to open SMAPI zip", e)
            errors.add(SMAPI_CACHE_FILENAME)
        }

        // Create Mods/ directory
        _smapiProgress.value =
            _smapiProgress.value.copy(
                currentFile = "Mods/",
                extractedFiles = extracted,
                percent =
                if (totalEntries > 0) {
                    extracted.toFloat() / totalEntries
                } else {
                    0f
                }
            )
        try {
            File(MODS_DIR).mkdirs()
            extracted++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to create Mods directory", e)
            errors.add("Mods/")
        }

        AppLogger.i(
            TAG,
            "SMAPI setup complete: $extracted/$totalEntries files, ${errors.size} errors"
        )

        _smapiProgress.value =
            SmapiSetupProgress(
                isRunning = false,
                percent = 1f,
                extractedFiles = extracted,
                totalFiles = totalEntries,
                completed = true,
                errorMessage =
                if (errors.isNotEmpty()) {
                    errors.joinToString(", ")
                } else {
                    null
                }
            )
    }

    suspend fun downloadAndExtractSmapi() {
        val file = downloadSmapiZip() ?: return
        extractSmapiFromCache()
        // Only record installed version after successful extraction
        val smapiProgress = _smapiProgress.value
        if (smapiProgress.completed && smapiProgress.errorMessage == null) {
            val releaseInfo = releaseChecker.getLatestRelease(
                GitHubReleaseChecker.SMAPI_REPO,
                GitHubReleaseChecker.SMAPI_ASSET_PATTERN
            )
            releaseInfo?.let {
                releaseChecker.setInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION, it.version)
            }
        }
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

        val smapiCacheFile = File(context.cacheDir, SMAPI_CACHE_FILENAME)
        val smapiEntryCount = if (smapiCacheFile.exists()) {
            smapiCacheFile.inputStream().use { countZipEntries(it) }
        } else {
            0
        }
        val totalFiles = filesToCopy.size + smapiEntryCount + 1 // +1 for Mods/ dir

        AppLogger.i(
            TAG,
            "Cinderbox setup: ${filesToCopy.size} game files + $smapiEntryCount SMAPI files + Mods dir = $totalFiles total"
        )

        _progress.value =
            _progress.value.copy(
                state = DownloadState.COPYING,
                currentFile = "",
                copiedFiles = 0,
                totalFilesToCopy = totalFiles,
                overallPercent = 0f
            )

        val errors = mutableListOf<String>()
        var copied = 0
        val buffer = ByteArray(256 * 1024)

        // Copy DLLs + Content
        for ((src, dest) in filesToCopy) {
            coroutineContext.ensureActive()

            val relativeName = src.relativeTo(srcDir).path
            _progress.value =
                _progress.value.copy(
                    currentFile = relativeName,
                    copiedFiles = copied,
                    overallPercent =
                    if (totalFiles > 0) {
                        copied.toFloat() / totalFiles
                    } else {
                        0f
                    }
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

        // Extract SMAPI zip from cache
        val smapiDestDir = File(SMAPI_DEST)
        if (smapiCacheFile.exists()) {
            try {
                smapiCacheFile.inputStream().use { fileStream ->
                    ZipInputStream(fileStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()

                            val entryName = entry.name
                            val displayName = "smapi-internal/$entryName"

                            _progress.value =
                                _progress.value.copy(
                                    currentFile = displayName,
                                    copiedFiles = copied,
                                    overallPercent =
                                    if (totalFiles > 0) {
                                        copied.toFloat() / totalFiles
                                    } else {
                                        0f
                                    }
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
                                    if (e is kotlinx.coroutines.CancellationException) {
                                        throw e
                                    }
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
                AppLogger.e(TAG, "Failed to open SMAPI zip", e)
                errors.add(SMAPI_CACHE_FILENAME)
            }
        } else {
            AppLogger.w(TAG, "SMAPI cache file not found, skipping SMAPI extraction in copy")
        }

        // Create Mods/ directory
        _progress.value =
            _progress.value.copy(
                currentFile = "Mods/",
                copiedFiles = copied,
                overallPercent = if (totalFiles > 0) copied.toFloat() / totalFiles else 0f
            )
        try {
            File(MODS_DIR).mkdirs()
            copied++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "Failed to create Mods directory", e)
            errors.add("Mods/")
        }

        AppLogger.i(
            TAG,
            "Cinderbox setup complete: $copied/$totalFiles files, ${errors.size} errors"
        )

        _progress.value =
            _progress.value.copy(
                state = DownloadState.COMPLETED,
                currentFile = "",
                copiedFiles = copied,
                totalFilesToCopy = totalFiles,
                overallPercent = 1f,
                copyCompleted = true,
                copyErrors = errors
            )
    }
}
