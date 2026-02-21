package com.sdvsync.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.steam.SteamAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val fileAccessMode: String = "auto",
    val availableModes: List<String> = emptyList(),
    val autoSyncEnabled: Boolean = false,
    val isLoggedIn: Boolean = false,
    val steamUsername: String? = null,
)

class SettingsViewModel(
    private val context: Context,
    private val fileAccessDetector: FileAccessDetector,
    private val authenticator: SteamAuthenticator,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun load() {
        _state.value = SettingsState(
            availableModes = fileAccessDetector.availableMethods(),
            fileAccessMode = fileAccessDetector.detectBestStrategy().name,
            isLoggedIn = authenticator.authState.value is com.sdvsync.steam.AuthState.LoggedIn,
        )
    }

    fun logout() {
        authenticator.logout()
    }
}
