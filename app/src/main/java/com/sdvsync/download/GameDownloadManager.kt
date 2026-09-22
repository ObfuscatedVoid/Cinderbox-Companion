package com.sdvsync.download

import android.content.Context
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.logging.AppLogger
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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
        const val CINDERBOX_BASE_DIR = CinderboxPaths.DESKTOP_DIR
        const val CINDERBOX_DEST = CinderboxPaths.GAME_FILES_DIR

        const val SMAPI_CACHE_FILENAME = "smapi-internal.zip"
        const val SMAPI_DEST = CinderboxPaths.SMAPI_DIR
        const val MODS_DIR = CinderboxPaths.MODS_DIR
    }

    private val installationMutex = Mutex()
    private var smapiArchiveVersion: String? = null

    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()
    val smapiProgress: StateFlow<SmapiSetupProgress> = _smapiProgress.asStateFlow()
    val cinderboxProgress: StateFlow<CinderboxDownloadProgress> = _cinderboxProgress.asStateFlow()

    fun resetProgress() {
        _progress.value = DownloadProgress()
    }

    fun resetCopyState() {
        _progress.value = _progress.value.copy(
            copyCompleted = false,
            copyErrors = emptyList(),
            copiedFiles = 0
        )
    }

    fun setCopyError(message: String) {
        _progress.value = _progress.value.copy(
            state = DownloadState.COMPLETED,
            copyCompleted = true,
            copyErrors = listOf(message)
        )
    }

    fun resetSmapiProgress() {
        _smapiProgress.value = SmapiSetupProgress()
    }

    fun resetCinderboxProgress() {
        _cinderboxProgress.value = CinderboxDownloadProgress()
    }

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

                com.sdvsync.saves.requireSafeSaveName(releaseInfo.assetName)
                val destFile =
                    downloadToFile(httpClient, releaseInfo.assetUrl, File(context.cacheDir, releaseInfo.assetName)) {
                            bytes,
                            total
                        ->
                        _cinderboxProgress.value = CinderboxDownloadProgress(
                            isDownloading = true,
                            percent = if (total > 0) bytes.toFloat() / total else 0f,
                            downloadedBytes = bytes,
                            totalBytes = total.coerceAtLeast(0)
                        )
                    }
                _cinderboxProgress.value = CinderboxDownloadProgress(
                    completed = true,
                    percent = 1f,
                    downloadedBytes = destFile.length(),
                    totalBytes = destFile.length(),
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

    private suspend fun downloadSmapiZip(): File? {
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

                downloadToFile(httpClient, releaseInfo.assetUrl, destFile) { bytes, total ->
                    _smapiProgress.value = SmapiSetupProgress(
                        isDownloading = true,
                        downloadPercent = if (total > 0) bytes.toFloat() / total else 0f,
                        downloadedBytes = bytes,
                        totalDownloadBytes = total.coerceAtLeast(0)
                    )
                }.also { smapiArchiveVersion = releaseInfo.version }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "SMAPI zip download failed", e)
                _smapiProgress.value =
                    SmapiSetupProgress(errorMessage = e.message ?: "Download failed")
                null
            }
        }
    }

    private suspend fun extractSmapiFromCache() {
        val archive = File(context.cacheDir, SMAPI_CACHE_FILENAME)
        try {
            val total = smapiFileCount(archive)
            _smapiProgress.value = SmapiSetupProgress(isRunning = true, totalFiles = total)
            var extracted = 0
            StagedInstallation(File(SMAPI_DEST)).use { stage ->
                extractSmapi(archive, stage.directory) { name ->
                    extracted++
                    _smapiProgress.value = SmapiSetupProgress(
                        isRunning = true,
                        currentFile = name,
                        extractedFiles = extracted,
                        totalFiles = total,
                        percent = extracted.toFloat() / total
                    )
                }
                Files.createDirectories(File(MODS_DIR).toPath())
                coroutineContext.ensureActive()
                publishInstallations(listOf(stage))
            }
            _smapiProgress.value = SmapiSetupProgress(
                completed = true,
                percent = 1f,
                extractedFiles = total,
                totalFiles = total
            )
        } catch (e: CancellationException) {
            _smapiProgress.value = SmapiSetupProgress(errorMessage = "SMAPI setup cancelled")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "SMAPI setup failed", e)
            _smapiProgress.value = SmapiSetupProgress(errorMessage = e.message ?: "SMAPI setup failed")
        }
    }

    suspend fun downloadAndExtractSmapi() = withContext(Dispatchers.IO) {
        installationMutex.withLock {
            downloadSmapiZip() ?: return@withLock
            extractSmapiFromCache()
            if (_smapiProgress.value.completed) {
                smapiArchiveVersion?.let {
                    releaseChecker.setInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION, it)
                }
            }
        }
    }

    suspend fun copyToCinderbox(installDir: String) = withContext(Dispatchers.IO) {
        installationMutex.withLock {
            try {
                val source = File(installDir).canonicalFile
                val content = File(source, CINDERBOX_CONTENT_DIR)
                require(CINDERBOX_DLLS.all { File(source, it).isFile }) { "Required game DLLs are missing" }
                require(content.isDirectory) { "Game Content is missing" }
                val contentFiles = Files.walk(content.toPath()).use { paths ->
                    paths.peek { require(!Files.isSymbolicLink(it)) { "Game Content contains a symbolic link" } }
                        .filter { Files.isRegularFile(it) }
                        .map { it.toFile() }.toArray().map { it as File }
                }
                val files = CINDERBOX_DLLS.map { File(source, it) } + contentFiles
                require(content.isDirectory && files.size > CINDERBOX_DLLS.size) { "Game Content is missing" }
                val sourceRoot = source.path + File.separator
                require(
                    files.all {
                        it.canonicalPath.startsWith(sourceRoot)
                    }
                ) { "Game files point outside the source directory" }
                val archive = File(context.cacheDir, SMAPI_CACHE_FILENAME).takeIf { it.isFile }
                val total = files.size + (archive?.let(::smapiFileCount) ?: 0)
                var copied = 0
                fun progress(name: String) {
                    _progress.value = _progress.value.copy(
                        state = DownloadState.COPYING,
                        currentFile = name,
                        copiedFiles = copied,
                        totalFilesToCopy = total,
                        overallPercent = copied.toFloat() / total,
                        copyCompleted = false,
                        copyErrors = emptyList()
                    )
                }
                progress("")
                StagedInstallation(File(CINDERBOX_DEST)).use { gameStage ->
                    for (file in files) {
                        val relative = file.relativeTo(source).path
                        copyGameFile(file, File(gameStage.directory, relative))
                        copied++
                        progress(relative)
                    }
                    Files.createDirectories(File(MODS_DIR).toPath())
                    if (archive != null) {
                        StagedInstallation(File(SMAPI_DEST)).use { smapiStage ->
                            extractSmapi(archive, smapiStage.directory) { name ->
                                copied++
                                progress("smapi-internal/$name")
                            }
                            coroutineContext.ensureActive()
                            publishInstallations(listOf(gameStage, smapiStage))
                        }
                    } else {
                        coroutineContext.ensureActive()
                        publishInstallations(listOf(gameStage))
                    }
                }
                _progress.value = _progress.value.copy(
                    state = DownloadState.COMPLETED,
                    currentFile = "",
                    copiedFiles = copied,
                    totalFilesToCopy = total,
                    overallPercent = 1f,
                    copyCompleted = true,
                    copyErrors = emptyList()
                )
            } catch (e: CancellationException) {
                setCopyError("Game setup cancelled")
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cinderbox setup failed", e)
                setCopyError(e.message ?: "Game setup failed")
            }
        }
    }
}
