package com.sdvsync.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.autosync.AutoSyncService
import com.sdvsync.download.AppUpdateManager
import com.sdvsync.download.GitHubReleaseChecker
import com.sdvsync.fileaccess.AllFilesAccess
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.fileaccess.RootFileAccess
import com.sdvsync.fileaccess.SAFFileAccess
import com.sdvsync.fileaccess.ShizukuFileAccess
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.steam.SteamAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val cinderboxMode: Boolean = true,
    val fileAccessMode: String = "auto",
    val availableModes: List<String> = emptyList(),
    val hasRoot: Boolean = false,
    val preferredStrategy: String? = null,
    val autoSyncEnabled: Boolean = false,
    val autoSyncAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val allFilesEligible: Boolean = false,
    val allFilesPermissionGranted: Boolean = false,
    val allFilesAccessWorking: Boolean = false,
    val safEligible: Boolean = false,
    val safConfigured: Boolean = false,
    val safIsStaging: Boolean = false,
    val isLoggedIn: Boolean = false,
    val steamUsername: String? = null,
    val maxBackups: Int = SaveBackupManager.DEFAULT_MAX_BACKUPS,
    val hasNexusApiKey: Boolean = false,
    val nexusApiKeyMasked: String? = null,
    val isValidatingApiKey: Boolean = false,
    val apiKeyError: String? = null,
    val installedCinderboxVersion: String? = null,
    val installedSmapiVersion: String? = null,
    val latestCinderboxVersion: String? = null,
    val latestSmapiVersion: String? = null,
    val cinderboxUpdateAvailable: Boolean = false,
    val smapiUpdateAvailable: Boolean = false,
    val updateCheckEnabled: Boolean = true,
    val showStoragePermissionPrompt: Boolean = false
)

class SettingsViewModel(
    private val context: Context,
    private val fileAccessDetector: FileAccessDetector,
    private val authenticator: SteamAuthenticator,
    private val backupManager: SaveBackupManager,
    private val modDataStore: ModDataStore,
    private val nexusSource: NexusModSource,
    private val releaseChecker: GitHubReleaseChecker,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasRoot = RootFileAccess.isAvailable()
            val shizukuInstalled = ShizukuFileAccess.isInstalled(context.packageManager)
            val shizukuRunning = ShizukuFileAccess.isRunning()
            val shizukuPermission = ShizukuFileAccess.isPermissionGranted()

            val apiKey = modDataStore.getNexusApiKey()
            val maskedKey = apiKey?.let {
                if (it.length > 4) "****${it.takeLast(4)}" else "****"
            }

            val installedCinderbox = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_CINDERBOX_VERSION)
            val installedSmapi = releaseChecker.getInstalledVersion(GitHubReleaseChecker.KEY_SMAPI_VERSION)

            _state.value = SettingsState(
                cinderboxMode = fileAccessDetector.isCinderboxMode(),
                availableModes = fileAccessDetector.availableMethods(),
                fileAccessMode = fileAccessDetector.resolveStrategy().name,
                hasRoot = hasRoot,
                preferredStrategy = fileAccessDetector.getPreferredStrategy(),
                autoSyncAvailable = hasRoot,
                shizukuInstalled = shizukuInstalled,
                shizukuRunning = shizukuRunning,
                shizukuPermissionGranted = shizukuPermission,
                allFilesEligible = android.os.Build.VERSION.SDK_INT >= 30,
                allFilesPermissionGranted = AllFilesAccess.isPermissionGranted(),
                allFilesAccessWorking = AllFilesAccess.isAvailable(),
                safEligible = SAFFileAccess.isDeviceEligible(),
                safConfigured = SAFFileAccess.isAvailable(context),
                safIsStaging = SAFFileAccess.isStaging(context),
                isLoggedIn = authenticator.authState.value is com.sdvsync.steam.AuthState.LoggedIn,
                maxBackups = backupManager.maxBackups,
                hasNexusApiKey = apiKey != null,
                nexusApiKeyMasked = maskedKey,
                installedCinderboxVersion = installedCinderbox,
                installedSmapiVersion = installedSmapi,
                updateCheckEnabled = appUpdateManager.isUpdateCheckEnabled()
            )

            // Load latest versions
            try {
                val cinderboxRelease = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.CINDERBOX_REPO,
                    GitHubReleaseChecker.CINDERBOX_ASSET_PATTERN
                )
                val smapiRelease = releaseChecker.getLatestRelease(
                    GitHubReleaseChecker.SMAPI_REPO,
                    GitHubReleaseChecker.SMAPI_ASSET_PATTERN
                )
                _state.update {
                    it.copy(
                        latestCinderboxVersion = cinderboxRelease?.version,
                        latestSmapiVersion = smapiRelease?.version,
                        cinderboxUpdateAvailable = releaseChecker.isUpdateAvailable(
                            it.installedCinderboxVersion,
                            cinderboxRelease?.version
                        ),
                        smapiUpdateAvailable = releaseChecker.isUpdateAvailable(
                            it.installedSmapiVersion,
                            smapiRelease?.version
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    fun onSafDirectorySelected(uri: Uri) {
        SAFFileAccess.persistUri(context, uri)
        load()
    }

    fun clearSafAccess() {
        SAFFileAccess.clearPersistedUri(context)
        load()
    }

    fun toggleAutoSync(enabled: Boolean) {
        _state.update { it.copy(autoSyncEnabled = enabled) }
        if (enabled) {
            AutoSyncService.start(context)
        } else {
            AutoSyncService.stop(context)
        }
    }

    fun requestShizukuPermission() {
        ShizukuFileAccess.requestPermission()
    }

    fun bindShizukuService() {
        ShizukuFileAccess.bindService()
    }

    fun setMaxBackups(count: Int) {
        backupManager.setMaxBackupsAndPrune(count)
        _state.update { it.copy(maxBackups = backupManager.maxBackups) }
    }

    fun toggleCinderboxMode(enabled: Boolean) {
        fileAccessDetector.setCinderboxMode(enabled)
        if (enabled && fileAccessDetector.needsStoragePermission()) {
            _state.update { it.copy(cinderboxMode = true, showStoragePermissionPrompt = true) }
            return
        }
        load()
    }

    fun onStoragePermissionResult() {
        _state.update { it.copy(showStoragePermissionPrompt = false) }
        load()
    }

    fun dismissStoragePermissionPrompt() {
        _state.update { it.copy(showStoragePermissionPrompt = false) }
        if (fileAccessDetector.needsStoragePermission()) {
            fileAccessDetector.setCinderboxMode(false)
            load()
        }
    }

    fun setFileAccessMode(name: String?) {
        fileAccessDetector.setPreferredStrategy(name)
        load()
    }

    fun logout() {
        authenticator.logout()
    }

    fun validateAndSaveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isValidatingApiKey = true, apiKeyError = null) }
            try {
                val valid = nexusSource.validateApiKey(key.trim())
                if (valid) {
                    modDataStore.setNexusApiKey(key.trim())
                    load()
                } else {
                    _state.update {
                        it.copy(isValidatingApiKey = false, apiKeyError = "Invalid API key")
                    }
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(isValidatingApiKey = false, apiKeyError = "Validation failed. Check your connection.")
                }
            }
        }
    }

    fun removeNexusApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            modDataStore.setNexusApiKey(null)
            load()
        }
    }

    fun clearApiKeyError() {
        _state.update { it.copy(apiKeyError = null) }
    }

    fun toggleUpdateCheck(enabled: Boolean) {
        appUpdateManager.setUpdateCheckEnabled(enabled)
        _state.update { it.copy(updateCheckEnabled = enabled) }
    }
}
