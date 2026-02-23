package com.sdvsync.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.sdvsync.autosync.AutoSyncService
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.fileaccess.RootFileAccess
import com.sdvsync.fileaccess.SAFFileAccess
import com.sdvsync.fileaccess.ShizukuFileAccess
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.steam.SteamAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val fileAccessMode: String = "auto",
    val availableModes: List<String> = emptyList(),
    val autoSyncEnabled: Boolean = false,
    val autoSyncAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val safEligible: Boolean = false,
    val safConfigured: Boolean = false,
    val safIsStaging: Boolean = false,
    val isLoggedIn: Boolean = false,
    val steamUsername: String? = null,
    val maxBackups: Int = SaveBackupManager.DEFAULT_MAX_BACKUPS,
)

class SettingsViewModel(
    private val context: Context,
    private val fileAccessDetector: FileAccessDetector,
    private val authenticator: SteamAuthenticator,
    private val backupManager: SaveBackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun load() {
        val hasRoot = RootFileAccess.isAvailable()
        val shizukuInstalled = ShizukuFileAccess.isInstalled(context.packageManager)
        val shizukuRunning = ShizukuFileAccess.isRunning()
        val shizukuPermission = ShizukuFileAccess.isPermissionGranted()

        _state.value = SettingsState(
            availableModes = fileAccessDetector.availableMethods(),
            fileAccessMode = fileAccessDetector.detectBestStrategy().name,
            autoSyncAvailable = hasRoot,
            shizukuInstalled = shizukuInstalled,
            shizukuRunning = shizukuRunning,
            shizukuPermissionGranted = shizukuPermission,
            safEligible = SAFFileAccess.isDeviceEligible(),
            safConfigured = SAFFileAccess.isAvailable(context),
            safIsStaging = SAFFileAccess.isStaging(context),
            isLoggedIn = authenticator.authState.value is com.sdvsync.steam.AuthState.LoggedIn,
            maxBackups = backupManager.maxBackups,
        )
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
        _state.value = _state.value.copy(autoSyncEnabled = enabled)
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
        _state.value = _state.value.copy(maxBackups = backupManager.maxBackups)
    }

    fun logout() {
        authenticator.logout()
    }
}
