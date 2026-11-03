package com.sdvsync.ui.viewmodels

import android.content.Context
import android.os.Environment
import android.net.Uri
import com.sdvsync.local.M24AndroidFolderPicker
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.download.CinderboxDownloadProgress
import com.sdvsync.download.DownloadProgress
import com.sdvsync.download.DownloadState
import com.sdvsync.download.GameDownloadManager
import com.sdvsync.local.InstallationScanner
import com.sdvsync.local.M23InstallationDiscovery
import com.sdvsync.local.M23CinderboxFirstPolicy
import com.sdvsync.local.InstallAssessment
import com.sdvsync.local.InstallHealth
import com.sdvsync.local.StoragePreflight
import java.io.File
import com.sdvsync.download.GitHubReleaseChecker
import com.sdvsync.download.SmapiSetupProgress
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.logging.AppLogger
import com.sdvsync.ui.policy.DeveloperFeaturePolicy
import com.sdvsync.ui.policy.DownloadPresentation
import com.sdvsync.steam.ConnectionState
import com.sdvsync.steam.SteamAuthenticator
import com.sdvsync.steam.SteamClientManager
import com.sdvsync.steam.SteamContentService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CinderboxWizardStep {
    SETUP_CHOICE,
    PERMISSIONS,
    DOWNLOAD_APK,
    INSTALL_APK,
    VERIFY_LAUNCHED,
    READY
}

data class CinderboxWizardState(
    val isVisible: Boolean = false,
    val currentStep: CinderboxWizardStep = CinderboxWizardStep.SETUP_CHOICE,
    val hasStoragePermission: Boolean = false,
    val hasInstallPermission: Boolean = false,
    val cinderboxDirExists: Boolean = false
)

data class GameDownloadState(
    val isLoadingBranches: Boolean = false,
    val branches: List<SteamContentService.BranchInfo> = emptyList(),
    val selectedBranch: String = "public",
    val branchPassword: String = "",
    val selectedOs: String = "windows",
    val installDirectory: String = "",
    val selectedInstallHealth: InstallAssessment? = null,
    val primaryGameStatus: M23InstallationDiscovery.Status = M23InstallationDiscovery.Status.MISSING,
    val primarySmapiStatus: M23InstallationDiscovery.Status = M23InstallationDiscovery.Status.MISSING,
    val destinationCode: M23CinderboxFirstPolicy.Code = M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE,
    val storageReady: Boolean = false,
    val storageDetail: String = "Storage not checked",
    val verifyAfterDownload: Boolean = true,
    val hasRecoverableDownload: Boolean = false,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val smapiSetupProgress: SmapiSetupProgress = SmapiSetupProgress(),
    val cinderboxDownloadProgress: CinderboxDownloadProgress = CinderboxDownloadProgress(),
    val error: String? = null,
    val branchLoadError: String? = null,
    val branchLoadNeedsReconnect: Boolean = false,
    val steamLoggedIn: Boolean = false,
    val cinderboxUpdateAvailable: Boolean = false,
    val latestCinderboxVersion: String? = null,
    val installedCinderboxVersion: String? = null,
    val cinderboxPackageInstalled: Boolean = false,
    val smapiUpdateAvailable: Boolean = false,
    val latestSmapiVersion: String? = null,
    val installedSmapiVersion: String? = null,
    val isCinderboxSetup: Boolean = false
)

class GameDownloadViewModel(
    private val context: Context,
    private val contentService: SteamContentService,
    private val downloadManager: GameDownloadManager,
    private val fileAccessDetector: FileAccessDetector,
    private val releaseChecker: GitHubReleaseChecker,
    private val steamClientManager: SteamClientManager,
    private val steamAuthenticator: SteamAuthenticator
) : ViewModel() {

    companion object {
        private const val TAG = "GameDownloadVM"
        const val SETUP_TYPE_CINDERBOX = "cinderbox"
        const val SETUP_TYPE_OTHER = "other"
    }

    private val _state = MutableStateFlow(GameDownloadState())
    val state: StateFlow<GameDownloadState> = _state.asStateFlow()
    private val _wizardState = MutableStateFlow(CinderboxWizardState())
    val wizardState: StateFlow<CinderboxWizardState> = _wizardState.asStateFlow()
    private var branchLoadJob: Job? = null
    private var smapiJob: Job? = null
    private var cinderboxJob: Job? = null
    private var pendingSetupType: String? = null

    /** Read-only Android PackageManager presence, not a preference or files on disk. */
    private fun installedCinderboxVersionFromPackage(): String? = try {
        context.packageManager.getPackageInfo("com.game.cinderbox", 0).versionName ?: "installed"
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    init {
        val defaultDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).resolve("StardewValley").absolutePath
        val savedDir = context.getSharedPreferences("m4_local_install", Context.MODE_PRIVATE)
            .getString("game_dir", null) ?: defaultDir
        val interrupted = runCatching { downloadManager.pendingDownload() }
        val pending = interrupted.getOrNull()
        val selectedDir = pending?.directory ?: savedDir
        _state.value = _state.value.copy(
            installDirectory = selectedDir,
            selectedBranch = pending?.branch ?: "public",
            selectedOs = pending?.os ?: "windows",
            verifyAfterDownload = pending?.verify ?: true,
            hasRecoverableDownload = pending != null,
            error = interrupted.exceptionOrNull()?.message,
            selectedInstallHealth = InstallationScanner.game(File(selectedDir)),
            primaryGameStatus = M23InstallationDiscovery.game(File(CinderboxPaths.GAME_FILES_DIR)).status,
            primarySmapiStatus = M23InstallationDiscovery.smapi(File(CinderboxPaths.SMAPI_DIR)).status,
            destinationCode = M23CinderboxFirstPolicy.assess(
                File(selectedDir), File(CinderboxPaths.ROOT_DIR)).code,
            storageReady = false,
            storageDetail = "Write access will be tested only when you start a download.",
            isCinderboxSetup = fileAccessDetector.getSetupType() == SETUP_TYPE_CINDERBOX,
            cinderboxPackageInstalled = installedCinderboxVersionFromPackage() != null
        )
        if (pending != null) downloadManager.restoreInterruptedProgress()

        // Keep download eligibility in sync with the live Steam transport state.
        viewModelScope.launch {
            steamClientManager.connectionState.collect { connection ->
                _state.update { it.copy(steamLoggedIn = connection == ConnectionState.LOGGED_IN) }
            }
        }
        viewModelScope.launch {
            downloadManager.progress.collect { progress ->
                val recoverable = when (progress.state) {
                    DownloadState.ERROR, DownloadState.CANCELLED, DownloadState.COMPLETED ->
                        runCatching { downloadManager.pendingDownload() != null }.getOrDefault(false)
                    else -> _state.value.hasRecoverableDownload
                }
                _state.update { it.copy(downloadProgress = progress,
                    hasRecoverableDownload = recoverable) }
            }
        }
        viewModelScope.launch {
            downloadManager.smapiProgress.collect { progress ->
                _state.value = _state.value.copy(smapiSetupProgress = progress)
                if (progress.completed) refreshInstalledVersions()
            }
        }
        viewModelScope.launch {
            downloadManager.cinderboxProgress.collect { progress ->
                _state.value = _state.value.copy(cinderboxDownloadProgress = progress)
            }
        }

        // Check for updates
        viewModelScope.launch {
            try {
                val cinderboxRelease = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.CINDERBOX_REPO,
                    GitHubReleaseChecker.CINDERBOX_ASSET_PATTERN
                )
                val installedCinderboxPackage = installedCinderboxVersionFromPackage()
                val installedCinderbox = installedCinderboxPackage
                    ?: releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_CINDERBOX_VERSION)

                val smapiRelease = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.SMAPI_REPO,
                    GitHubReleaseChecker.SMAPI_ASSET_PATTERN
                )
                val installedSmapi = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION)

                _state.value = _state.value.copy(
                    latestCinderboxVersion = cinderboxRelease?.version,
                    installedCinderboxVersion = installedCinderbox,
                    cinderboxPackageInstalled = installedCinderboxPackage != null,
                    cinderboxUpdateAvailable = releaseChecker.isUpdateAvailable(
                        installedCinderbox,
                        cinderboxRelease?.version
                    ),
                    latestSmapiVersion = smapiRelease?.version,
                    installedSmapiVersion = installedSmapi,
                    smapiUpdateAvailable = releaseChecker.isUpdateAvailable(
                        installedSmapi,
                        smapiRelease?.version
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to check for updates", e)
            }
        }
    }

    fun refreshInstalledVersions() {
        val installedCinderboxPackage = installedCinderboxVersionFromPackage()
        val cinderbox = installedCinderboxPackage
            ?: releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_CINDERBOX_VERSION)
        val smapi = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION)
        _state.update {
            it.copy(
                installedCinderboxVersion = cinderbox,
                cinderboxPackageInstalled = installedCinderboxPackage != null,
                installedSmapiVersion = smapi,
                cinderboxUpdateAvailable = releaseChecker.isUpdateAvailable(cinderbox, it.latestCinderboxVersion),
                smapiUpdateAvailable = releaseChecker.isUpdateAvailable(smapi, it.latestSmapiVersion)
            )
        }
    }

    /** Initial load is read-only; user Retry may resume the previously saved Steam session. */
    fun loadBranches(reconnect: Boolean = false) {
        // Prevent overlapping PICS jobs and competing saved-session reconnect attempts.
        if (branchLoadJob?.isActive == true) return
        branchLoadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoadingBranches = true,
                    branchLoadError = null,
                    branchLoadNeedsReconnect = false
                )
            }
            try {
                if (!steamClientManager.isLoggedIn) {
                    if (reconnect && steamClientManager.connectionState.value != ConnectionState.CONNECTING) {
                        AppLogger.i(TAG, "User requested saved-session Steam reconnect")
                        steamAuthenticator.loginWithSavedSession()
                    }
                    // Login and Steam transport callbacks are asynchronous. Never call PICS
                    // until SteamClientManager itself reports a completed Steam logon.
                    val timeoutMs = if (reconnect) 30_000L else 7_000L
                    if (!steamClientManager.awaitLoggedIn(timeoutMs)) {
                        _state.update {
                            it.copy(
                                isLoadingBranches = false,
                                branchLoadNeedsReconnect = true,
                                branchLoadError = context.getString(R.string.download_error_steam_offline)
                            )
                        }
                        return@launch
                    }
                }

                val info = contentService.getAppBranches()
                if (info.branches.isEmpty()) {
                    _state.update {
                        it.copy(
                            branches = emptyList(),
                            isLoadingBranches = false,
                            branchLoadError = context.getString(R.string.download_error_no_branches)
                        )
                    }
                } else {
                    _state.update { current ->
                        val selected = current.selectedBranch.takeIf { selected ->
                            info.branches.any { it.name == selected }
                        } ?: info.branches.firstOrNull { it.name == "public" }?.name
                            ?: info.branches.first().name
                        current.copy(
                            branches = info.branches,
                            selectedBranch = selected,
                            isLoadingBranches = false,
                            branchLoadError = null,
                            branchLoadNeedsReconnect = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load branches", e)
                val disconnected = !steamClientManager.isLoggedIn
                _state.update {
                    it.copy(
                        isLoadingBranches = false,
                        branchLoadNeedsReconnect = disconnected,
                        branchLoadError = if (disconnected) {
                            context.getString(R.string.download_error_steam_offline)
                        } else {
                            // Do not display raw Steam exceptions, which may include private data.
                            context.getString(R.string.download_error_load_branches, e.javaClass.simpleName)
                        }
                    )
                }
            }
        }
    }

    fun retryBranchLoad() = loadBranches(reconnect = true)

    fun selectBranch(name: String) {
        _state.value = _state.value.copy(selectedBranch = name, branchPassword = "")
    }

    fun selectOs(os: String) {
        _state.value = _state.value.copy(selectedOs = os)
    }

    fun updateInstallDirectory(path: String) {
        if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            _state.update { it.copy(error = "Custom download folders are available only in developer debug builds.") }
            return
        }
        context.getSharedPreferences("m4_local_install", Context.MODE_PRIVATE)
            .edit().putString("game_dir", path).apply()
        _state.value = _state.value.copy(
            installDirectory = path,
            selectedInstallHealth = InstallationScanner.game(File(path)),
            primaryGameStatus = M23InstallationDiscovery.game(File(CinderboxPaths.GAME_FILES_DIR)).status,
            primarySmapiStatus = M23InstallationDiscovery.smapi(File(CinderboxPaths.SMAPI_DIR)).status,
            destinationCode = M23CinderboxFirstPolicy.assess(
                File(path), File(CinderboxPaths.ROOT_DIR)).code,
            storageReady = false,
            storageDetail = "Write access will be tested only when you start a download."
        )
    }

    fun refreshSelectedDirectoryHealth() {
        val path = _state.value.installDirectory
        _state.value = _state.value.copy(
            selectedInstallHealth = InstallationScanner.game(File(path)),
            primaryGameStatus = M23InstallationDiscovery.game(File(CinderboxPaths.GAME_FILES_DIR)).status,
            primarySmapiStatus = M23InstallationDiscovery.smapi(File(CinderboxPaths.SMAPI_DIR)).status,
            destinationCode = M23CinderboxFirstPolicy.assess(
                File(path), File(CinderboxPaths.ROOT_DIR)).code,
            storageReady = false,
            storageDetail = "Write access will be tested only when you start a download."
        )
    }

    fun updateBranchPassword(password: String) {
        _state.value = _state.value.copy(branchPassword = password)
    }

    fun chooseAndroidFolder(uri: Uri) {
        if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            _state.update { it.copy(error = "Custom download folders are available only in developer debug builds.") }
            return
        }
        runCatching { M24AndroidFolderPicker.resolve(context, uri) }
            .onSuccess { directory ->
                updateInstallDirectory(directory.absolutePath)
                _state.update { it.copy(error = null) }
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Unsupported folder selection") }
            }
    }

    fun toggleVerification() {
        // M24: live transfers cannot disable local integrity verification.
        _state.value = _state.value.copy(verifyAfterDownload = true)
    }

    fun startDownload() {
        // A cached branch must never authorize a download after Steam disconnects.
        if (!steamClientManager.isLoggedIn) {
            _state.update {
                it.copy(
                    branchLoadNeedsReconnect = true,
                    branchLoadError = context.getString(R.string.download_error_steam_offline)
                )
            }
            return
        }
        val current = _state.value
        if (current.installDirectory.isBlank()) {
            _state.value = current.copy(error = context.getString(R.string.download_error_no_directory))
            return
        }

        val selectedBranchInfo = current.branches.find { it.name == current.selectedBranch }
        if (selectedBranchInfo == null || selectedBranchInfo.buildId <= 0L ||
            !current.verifyAfterDownload) {
            _state.value = current.copy(error =
                "Steam branch build information is missing or local verification is disabled. Refresh branches.")
            return
        }
        val selectedHealth = InstallationScanner.game(File(current.installDirectory))
        // Existing imported Cinderbox game/SMAPI are primary app assets,
        // not an authorization or a blanket blocker for another destination.
        val destination = M23CinderboxFirstPolicy.assess(
            File(current.installDirectory), File(CinderboxPaths.ROOT_DIR))
        val resume = try {
            downloadManager.canResume(File(current.installDirectory), current.selectedBranch,
                current.selectedOs, current.verifyAfterDownload)
        } catch (e: Exception) {
            _state.value = current.copy(error = e.message ?: "Invalid recovery journal")
            return
        }
        if (resume && downloadManager.pendingDownload()?.steamBuildId != selectedBranchInfo.buildId) {
            _state.value = current.copy(error =
                "Interrupted checkpoint uses a different or unknown Steam build. Files preserved; review before retry.")
            return
        }
        // A checkpoint never licenses writing inside the original Cinderbox tree.
        // For new downloads, the *selected* destination must be empty and separate.
        if ((!resume && !destination.separateEmptyDestination) ||
            destination.code == M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE ||
            destination.code == M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION ||
            destination.code == M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
            _state.value = current.copy(error =
                "Download destination rejected (${destination.code}). Cinderbox's imported " +
                "game, saves, mods and SMAPI were not modified.")
            return
        }
        // Only after rejecting protected/unsafe destinations may an explicitly
        // requested download test actual filesystem writability.
        val storage = StoragePreflight.check(File(current.installDirectory))
        if (!storage.writable || selectedHealth.health == InstallHealth.INACCESSIBLE ||
            (!resume && (selectedHealth.health == InstallHealth.INCOMPLETE ||
                selectedHealth.health == InstallHealth.STRUCTURALLY_VALID ||
                selectedHealth.health == InstallHealth.DETECTED))) {
            _state.value = current.copy(error = if (!storage.writable) storage.details else
                "Existing game files detected (${selectedHealth.health}). Only a matching interrupted session may resume them.")
            return
        }
        if (selectedBranchInfo.passwordRequired == true && current.branchPassword.isBlank()) {
            _state.value = current.copy(error = context.getString(R.string.download_error_password_required))
            return
        }

        // Persist any pending setup choice now that validation passed
        pendingSetupType?.let { type ->
            fileAccessDetector.setSetupCompleted(type)
            pendingSetupType = null
        }

        _state.value = current.copy(error = null)

        try {
            downloadManager.startDownload(
                branch = current.selectedBranch,
                branchPassword = current.branchPassword.ifBlank { null },
                installDirectory = current.installDirectory,
                os = current.selectedOs,
                verifyAfterDownload = current.verifyAfterDownload,
                branchBuildId = selectedBranchInfo.buildId
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Storage is unavailable")
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun discardInterruptedDownload() {
        runCatching { downloadManager.discardPendingDownload() }
            .onSuccess { _state.update { it.copy(hasRecoverableDownload = false,
                error = "Recovery forgotten. Existing files were kept; choose an empty folder for a new download.") } }
            .onFailure { failure -> _state.update {
                it.copy(error = "Cannot discard recovery: ${failure.message}")
            } }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun copyCinderbox() {
        downloadManager.resetCopyState()
        viewModelScope.launch {
            try {
                downloadManager.copyToCinderbox(_state.value.installDirectory)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cinderbox copy failed", e)
                downloadManager.setCopyError(e.message ?: "Copy failed unexpectedly")
            }
        }
    }

    fun resetDownload() {
        downloadManager.resetProgress()
    }

    fun extractSmapi() {
        if (smapiJob?.isActive == true) return
        smapiJob = viewModelScope.launch {
            try {
                downloadManager.downloadAndExtractSmapi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "SMAPI download/extraction failed", e)
            }
        }
    }

    fun resetSmapiSetup() {
        downloadManager.resetSmapiProgress()
    }

    fun downloadCinderbox() {
        if (cinderboxJob?.isActive == true) return
        cinderboxJob = viewModelScope.launch {
            try {
                downloadManager.downloadCinderboxApk()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cinderbox download failed", e)
            }
        }
    }

    fun resetCinderboxDownload() {
        downloadManager.resetCinderboxProgress()
    }

    // ── Cinderbox Wizard ──────────────────────────────────────────────────

    fun onDownloadButtonClicked() {
        // Already completed setup before — skip wizard
        if (fileAccessDetector.isSetupCompleted()) {
            startDownload()
            return
        }

        // Cinderbox dir already exists — user clearly has it installed
        if (CinderboxPaths.isInstalled()) {
            fileAccessDetector.setSetupCompleted(SETUP_TYPE_CINDERBOX)
            fileAccessDetector.setCinderboxMode(true)
            _state.value = _state.value.copy(isCinderboxSetup = true)
            startDownload()
            return
        }

        // Show wizard
        _wizardState.value = CinderboxWizardState(isVisible = true)
    }

    fun onWizardChooseCinderbox() {
        val dirExists = CinderboxPaths.isInstalled()
        if (dirExists) {
            // Already installed — jump to ready
            _wizardState.value = _wizardState.value.copy(
                currentStep = CinderboxWizardStep.READY,
                cinderboxDirExists = true
            )
        } else {
            // Need to check permissions first
            _wizardState.value = _wizardState.value.copy(
                currentStep = CinderboxWizardStep.PERMISSIONS
            )
        }
    }

    fun onWizardChooseOther() {
        pendingSetupType = SETUP_TYPE_OTHER
        _state.value = _state.value.copy(isCinderboxSetup = false)
        _wizardState.value = CinderboxWizardState()
        startDownload()
    }

    fun refreshPermissions(hasStorage: Boolean, hasInstall: Boolean) {
        val current = _wizardState.value
        val updated = current.copy(
            hasStoragePermission = hasStorage,
            hasInstallPermission = hasInstall
        )
        _wizardState.value = updated

        // Auto-advance when both permissions granted
        if (updated.hasStoragePermission &&
            updated.hasInstallPermission &&
            current.currentStep == CinderboxWizardStep.PERMISSIONS
        ) {
            _wizardState.value = updated.copy(
                currentStep = CinderboxWizardStep.DOWNLOAD_APK
            )
            // Auto-trigger download
            downloadCinderbox()
        }
    }

    fun onApkDownloadComplete() {
        val current = _wizardState.value
        if (current.currentStep == CinderboxWizardStep.DOWNLOAD_APK) {
            _wizardState.value = current.copy(
                currentStep = CinderboxWizardStep.INSTALL_APK
            )
        }
    }

    fun onApkInstalled() {
        _wizardState.value = _wizardState.value.copy(
            currentStep = CinderboxWizardStep.VERIFY_LAUNCHED
        )
    }

    fun checkCinderboxDirectory(): Boolean {
        val exists = CinderboxPaths.isInstalled()
        if (exists) {
            _wizardState.value = _wizardState.value.copy(
                currentStep = CinderboxWizardStep.READY,
                cinderboxDirExists = true
            )
        }
        return exists
    }

    fun skipVerification() {
        _wizardState.value = _wizardState.value.copy(
            currentStep = CinderboxWizardStep.READY
        )
    }

    fun onWizardStartDownload() {
        fileAccessDetector.setSetupCompleted(SETUP_TYPE_CINDERBOX)
        fileAccessDetector.setCinderboxMode(true)
        _state.value = _state.value.copy(isCinderboxSetup = true)
        _wizardState.value = CinderboxWizardState()
        startDownload()
    }

    fun dismissWizard() {
        cinderboxJob?.cancel()
        cinderboxJob = null
        resetCinderboxDownload()
        _wizardState.value = CinderboxWizardState()
    }
}
