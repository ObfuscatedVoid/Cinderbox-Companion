package com.sdvsync.ui.viewmodels

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.R
import com.sdvsync.download.CinderboxDownloadProgress
import com.sdvsync.download.DownloadProgress
import com.sdvsync.download.DownloadState
import com.sdvsync.download.GameDownloadManager
import com.sdvsync.download.GitHubReleaseChecker
import com.sdvsync.download.SmapiSetupProgress
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.logging.AppLogger
import com.sdvsync.steam.SteamContentService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val verifyAfterDownload: Boolean = true,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val smapiSetupProgress: SmapiSetupProgress = SmapiSetupProgress(),
    val cinderboxDownloadProgress: CinderboxDownloadProgress = CinderboxDownloadProgress(),
    val error: String? = null,
    val cinderboxUpdateAvailable: Boolean = false,
    val latestCinderboxVersion: String? = null,
    val installedCinderboxVersion: String? = null,
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
    private val releaseChecker: GitHubReleaseChecker
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
    private var smapiJob: Job? = null
    private var cinderboxJob: Job? = null
    private var pendingSetupType: String? = null

    init {
        val defaultDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).resolve("StardewValley").absolutePath
        _state.value = _state.value.copy(
            installDirectory = defaultDir,
            isCinderboxSetup = fileAccessDetector.getSetupType() == SETUP_TYPE_CINDERBOX
        )

        viewModelScope.launch {
            downloadManager.progress.collect { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
            }
        }
        viewModelScope.launch {
            downloadManager.smapiProgress.collect { progress ->
                _state.value = _state.value.copy(smapiSetupProgress = progress)
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
                val installedCinderbox = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_CINDERBOX_VERSION)

                val smapiRelease = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.SMAPI_REPO,
                    GitHubReleaseChecker.SMAPI_ASSET_PATTERN
                )
                val installedSmapi = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION)

                _state.value = _state.value.copy(
                    latestCinderboxVersion = cinderboxRelease?.version,
                    installedCinderboxVersion = installedCinderbox,
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

    fun loadBranches() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBranches = true, error = null)
            try {
                val info = contentService.getAppBranches()
                _state.value = _state.value.copy(
                    branches = info.branches,
                    isLoadingBranches = false
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load branches", e)
                _state.value = _state.value.copy(
                    isLoadingBranches = false,
                    error = context.getString(R.string.download_error_load_branches, e.message ?: "")
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

        // Persist any pending setup choice now that validation passed
        pendingSetupType?.let { type ->
            fileAccessDetector.setSetupCompleted(type)
            pendingSetupType = null
        }

        _state.value = current.copy(error = null)

        downloadManager.startDownload(
            branch = current.selectedBranch,
            branchPassword = current.branchPassword.ifBlank { null },
            installDirectory = current.installDirectory,
            os = current.selectedOs,
            verifyAfterDownload = current.verifyAfterDownload
        )
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun copyCinderbox() {
        // Clear previous copy state before retrying
        GameDownloadManager._progress.value =
            GameDownloadManager._progress.value.copy(
                copyCompleted = false,
                copyErrors = emptyList(),
                copiedFiles = 0
            )
        viewModelScope.launch {
            try {
                downloadManager.copyToCinderbox(_state.value.installDirectory)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cinderbox copy failed", e)
                GameDownloadManager._progress.value =
                    GameDownloadManager._progress.value.copy(
                        state = DownloadState.COMPLETED,
                        copyCompleted = true,
                        copyErrors = listOf(e.message ?: "Copy failed unexpectedly")
                    )
            }
        }
    }

    fun resetDownload() {
        _state.value = _state.value.copy(
            downloadProgress = DownloadProgress()
        )
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
        GameDownloadManager._smapiProgress.value = SmapiSetupProgress()
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
        GameDownloadManager._cinderboxProgress.value = CinderboxDownloadProgress()
    }

    // ── Cinderbox Wizard ──────────────────────────────────────────────────

    fun onDownloadButtonClicked() {
        // Already completed setup before — skip wizard
        if (fileAccessDetector.isSetupCompleted()) {
            startDownload()
            return
        }

        // Cinderbox dir already exists — user clearly has it installed
        if (File(GameDownloadManager.CINDERBOX_BASE_DIR).isDirectory) {
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
        val dirExists = File(GameDownloadManager.CINDERBOX_BASE_DIR).isDirectory
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
        val exists = File(GameDownloadManager.CINDERBOX_BASE_DIR).isDirectory
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
