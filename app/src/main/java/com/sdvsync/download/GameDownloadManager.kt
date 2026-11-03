package com.sdvsync.download

import android.content.Context
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.local.M23CinderboxFirstPolicy
import com.sdvsync.local.CinderboxDownloadDestination
import com.sdvsync.ui.policy.DeveloperFeaturePolicy
import com.sdvsync.local.M23InstallationDiscovery
import com.sdvsync.local.InstallationScanner
import com.sdvsync.local.InstallHealth
import com.sdvsync.local.StoragePreflight
import com.sdvsync.local.StoragePlanner
import com.sdvsync.logging.AppLogger
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Steam's percentages refer to individual depots, not the entire game.
    val currentDepotId: Int? = null,
    val currentDepotPercent: Float = 0f,
    val depotPhase: String = "",
    val filesDownloaded: Int = 0,
    val errorMessage: String? = null,
    val verificationPhase: String = "",
    val verifiedFiles: Int = 0,
    val totalFilesToVerify: Int = 0,
    val verificationPassed: Boolean = true,
    val integrityScope: String = "NOT_VERIFIED",
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

    private val recovery = GameDownloadRecovery(context.filesDir)

    fun pendingDownload(): GameDownloadCheckpoint? = recovery.pending()

    fun canResume(directory: File, branch: String, os: String, verify: Boolean): Boolean =
        recovery.pending()?.matches(directory, branch, os, verify) == true

    /** Read-only view of journal-owned promotion; not standalone write authority. */
    fun isPublishedRecovery(directory: File): Boolean =
        runCatching { recovery.isPromotedStage(directory) }.getOrDefault(false)

    /** Explicit discard ONLY forgets metadata, never deletes downloaded game bytes. */
    fun discardPendingDownload() {
        recovery.discard()
        _progress.value = DownloadProgress()
    }

    fun restoreInterruptedProgress() {
        if (recovery.pending() != null && _progress.value.state == DownloadState.IDLE) {
            _progress.value = DownloadProgress(
                state = DownloadState.ERROR,
                errorMessage = "Interrupted Steam download detected. Sign in and choose Resume interrupted download."
            )
        }
    }

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
        verifyAfterDownload: Boolean = true,
        branchBuildId: Long = 0L
    ) {
        require(verifyAfterDownload && branchBuildId > 0L) {
            "M24 requires an authenticated PICS build and local verification"
        }
        check(_progress.value.state !in setOf(DownloadState.PREPARING,
            DownloadState.DOWNLOADING, DownloadState.VERIFYING, DownloadState.COPYING)) {
            "Another game download is already active"
        }
        val directory = File(installDirectory)
        // UI preflight does not authorize writing. Recheck the fixed
        // production destination even for callers that bypass the ViewModel.
        val debugAlternative = DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context) &&
            !CinderboxDownloadDestination.isExactDestination(directory.path)
        val scope = M23CinderboxFirstPolicy.assess(directory, File(CinderboxPaths.ROOT_DIR))
        if (debugAlternative) {
            check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
                scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
                scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
                "Debug destination is protected, unsafe or unreadable"
            }
        } else {
            CinderboxDownloadDestination.requireWritableForInstall(
                directory, isPublishedRecovery(directory))
        }
        StoragePreflight.requireWritable(directory)
        GameDownloadRecovery.safeDestination(directory)
        val checkpoint = recovery.pending()
        if (checkpoint == null) {
            if (debugAlternative) {
                check(scope.separateEmptyDestination) {
                    "Debug destination must be an independent empty folder"
                }
            } else {
                check(CinderboxDownloadDestination.inspect(directory).canStartNewDownload) {
                    "Cinderbox's GameFiles destination is no longer available for a new download"
                }
            }
            val health = InstallationScanner.game(directory).health
            check(health != InstallHealth.INCOMPLETE && health != InstallHealth.INACCESSIBLE &&
                health != InstallHealth.DETECTED && health != InstallHealth.STRUCTURALLY_VALID) {
                "Selected folder already contains files; choose an empty destination"
            }
            // Journal before starting the service. A process death leaves a recoverable download.
            recovery.beginStaged(directory, branch, os, verifyAfterDownload)
            recovery.bindSteamBuildId(branchBuildId)
        } else {
            // Existing game bytes are usable ONLY if this exact session created them.
            recovery.requireMatching(directory, branch, os, verifyAfterDownload)
            recovery.requireSteamBuildId(branchBuildId)
            check(checkpoint.stagingDirectory != null) {
                "Legacy direct-write checkpoint cannot authorize M24 staging; preserve bytes for review"
            }
        }
        GameDownloadService.start(
            context = context,
            branch = branch,
            password = branchPassword,
            installDir = installDirectory,
            os = os,
            verify = verifyAfterDownload,
            expectedBuildId = branchBuildId
        )
    }

    fun cancelDownload() {
        GameDownloadService.stop(context)
    }

    suspend fun downloadCinderboxApk() {
        _cinderboxProgress.value = CinderboxDownloadProgress(isDownloading = true)
        withContext(Dispatchers.IO) {
            try {
                // APK download only writes app-private cache, not the installed game.
                StoragePreflight.requireWritable(context.cacheDir)
                val releaseInfo = checkNotNull(releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.CINDERBOX_REPO, GitHubReleaseChecker.CINDERBOX_ASSET_PATTERN
                )) { "Could not find Cinderbox release" }
                val dest = File(context.cacheDir, releaseInfo.assetName)
                VerifiedReleaseAsset.download(
                    httpClient, releaseInfo, GitHubReleaseChecker.CINDERBOX_REPO, dest
                ) { done, total ->
                    _cinderboxProgress.value = CinderboxDownloadProgress(
                        isDownloading = true,
                        percent = done.toFloat() / total,
                        downloadedBytes = done,
                        totalBytes = total
                    )
                }
                // Do not offer a differently packaged APK to Android's installer.
                @Suppress("DEPRECATION")
                val packageInfo = context.packageManager.getPackageArchiveInfo(dest.absolutePath, 0)
                if (packageInfo?.packageName != "com.game.cinderbox") {
                    dest.delete()
                    error("Unexpected Cinderbox APK package identity")
                }
                _cinderboxProgress.value = CinderboxDownloadProgress(
                    completed = true, percent = 1f,
                    downloadedBytes = dest.length(), totalBytes = releaseInfo.assetSize,
                    apkFile = dest
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Cinderbox APK verification failed", e)
                _cinderboxProgress.value = CinderboxDownloadProgress(
                    errorMessage = e.message ?: "Untrusted Cinderbox download"
                )
            }
        }
    }

    suspend fun downloadSmapiZip(): File? = withContext(Dispatchers.IO) {
        _smapiProgress.value = SmapiSetupProgress(isDownloading = true)
        try {
            // Any denied target permission becomes visible in the SMAPI error card.
            StoragePreflight.requireWritable(File(CinderboxPaths.SMAPI_DIR))
            StoragePreflight.requireWritable(context.cacheDir)
            val releaseInfo = checkNotNull(releaseChecker.getLatestRelease(
                GitHubReleaseChecker.SMAPI_REPO, GitHubReleaseChecker.SMAPI_ASSET_PATTERN
            )) { "Could not find SMAPI release" }
            val dest = File(context.cacheDir, SMAPI_CACHE_FILENAME)
            _smapiProgress.value = SmapiSetupProgress(
                isDownloading = true, totalDownloadBytes = releaseInfo.assetSize
            )
            VerifiedReleaseAsset.download(
                httpClient, releaseInfo, GitHubReleaseChecker.SMAPI_REPO, dest
            ) { done, total ->
                _smapiProgress.value = SmapiSetupProgress(
                    isDownloading = true,
                    downloadPercent = done.toFloat() / total,
                    downloadedBytes = done, totalDownloadBytes = total
                )
            }
            AppLogger.i(TAG, "Verified SMAPI ZIP: ${dest.length()} bytes")
            dest
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "SMAPI archive verification failed", e)
            _smapiProgress.value = SmapiSetupProgress(
                errorMessage = e.message ?: "Untrusted SMAPI archive"
            )
            null
        }
    }

    suspend fun extractSmapiFromCache() = withContext(Dispatchers.IO) {
        val dest = File(SMAPI_DEST)
        val cached = File(context.cacheDir, SMAPI_CACHE_FILENAME)
        _smapiProgress.value = SmapiSetupProgress(isRunning = true, currentFile = "Recovering installation")
        try {
            // Perform recovery before touching a new archive; works offline after a crash.
            SmapiTransactionalInstaller.recover(dest)
            check(cached.isFile) { "SMAPI zip not found. Download it first." }
            val release = checkNotNull(releaseChecker.getLatestRelease(
                GitHubReleaseChecker.SMAPI_REPO, GitHubReleaseChecker.SMAPI_ASSET_PATTERN
            )) { "SMAPI release metadata unavailable" }
            VerifiedReleaseAsset.verifyCached(cached, release, GitHubReleaseChecker.SMAPI_REPO)
            val activeContext = coroutineContext
            val count = SmapiTransactionalInstaller.install(
                cached, dest, checkActive = { activeContext.ensureActive() }
            ) { name, completed ->
                _smapiProgress.value = _smapiProgress.value.copy(
                    currentFile = "smapi-internal/$name",
                    extractedFiles = completed
                )
            }
            Files.createDirectories(File(MODS_DIR).toPath())
            _smapiProgress.value = SmapiSetupProgress(
                completed = true, percent = 1f,
                extractedFiles = count + 1, totalFiles = count + 1
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e(TAG, "SMAPI transaction failed", e)
            _smapiProgress.value = SmapiSetupProgress(
                completed = true, errorMessage = e.message ?: "SMAPI installation failed"
            )
        }
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

    private fun countZipEntries(archive: File): Int = ZipFile(archive).use { zip ->
        require(zip.size() <= 8000) { "SMAPI archive has too many entries" }
        zip.size()
    }

    suspend fun copyToCinderbox(installDir: String) = withContext(Dispatchers.IO) {
        val srcDir = File(installDir)
        val destDir = File(CINDERBOX_DEST)
        // Never clobber the primary app's imported game during an explicit copy.
        // Restoring/updating an existing Cinderbox installation requires a
        // separate reviewed and backed-up transaction, not this setup helper.
        val sourceSafe = GameDownloadRecovery.safeDestination(srcDir)
        val targetSafe = GameDownloadRecovery.safeDestination(destDir)
        check(!sourceSafe.toPath().startsWith(targetSafe.toPath()) &&
            !targetSafe.toPath().startsWith(sourceSafe.toPath())) {
            "Copy source and primary Cinderbox game tree must be separate"
        }
        check(M23InstallationDiscovery.game(destDir).status == M23InstallationDiscovery.Status.MISSING) {
            "Primary Cinderbox game already contains files; copying would overwrite imported data"
        }

        val missing = CINDERBOX_DLLS.filter { !File(srcDir, it).isFile }.toMutableList()
        val content = File(srcDir, CINDERBOX_CONTENT_DIR)
        if (!content.isDirectory || content.walk().none { it.isFile }) missing += CINDERBOX_CONTENT_DIR
        if (missing.isNotEmpty()) {
            setCopyError("Missing game files: ${missing.joinToString(", ")}")
            return@withContext
        }

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

        // Existing source files provide an exact minimum copy-size budget, unlike a
        // not-yet-queried Steam depot. Refuse a copy when available space is insufficient.
        val copyBudget = StoragePlanner.copyPlan(destDir, filesToCopy.map { it.first })
        if (copyBudget.sufficient != true) {
            setCopyError("Game copy blocked: ${copyBudget.detail}")
            return@withContext
        }

        val smapiCacheFile = File(context.cacheDir, SMAPI_CACHE_FILENAME)
        val smapiEntryCount = if (smapiCacheFile.exists()) {
            runCatching { countZipEntries(smapiCacheFile) }.getOrDefault(0)
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

        // SMAPI installation is an isolated, verified directory transaction.
        val smapiDestDir = File(SMAPI_DEST)
        if (smapiCacheFile.exists()) {
            try {
                val release = checkNotNull(releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.SMAPI_REPO, GitHubReleaseChecker.SMAPI_ASSET_PATTERN
                )) { "SMAPI release metadata unavailable" }
                VerifiedReleaseAsset.verifyCached(smapiCacheFile, release, GitHubReleaseChecker.SMAPI_REPO)
                val activeContext = coroutineContext
                val count = SmapiTransactionalInstaller.install(
                    smapiCacheFile, smapiDestDir,
                    checkActive = { activeContext.ensureActive() }
                ) { name, completed ->
                    _progress.value = _progress.value.copy(
                        currentFile = "smapi-internal/$name",
                        copiedFiles = copied + completed,
                        overallPercent = if (totalFiles > 0) {
                            (copied + completed).toFloat() / totalFiles
                        } else 0f
                    )
                }
                copied += count
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Transactional SMAPI install failed", e)
                errors.add("SMAPI: ${e.message ?: "verification failed"}")
            }
        } else {
            // Even without a cache, repair any previous interrupted SMAPI swap.
            try { SmapiTransactionalInstaller.recover(smapiDestDir) }
            catch (e: Exception) { errors.add("SMAPI recovery: ${e.message}") }
        }

    // Create Mods/ directory
        _progress.value =
            _progress.value.copy(
                currentFile = "Mods/",
                copiedFiles = copied,
                overallPercent = if (totalFiles > 0) copied.toFloat() / totalFiles else 0f
            )
        try {
            Files.createDirectories(File(MODS_DIR).toPath())
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
