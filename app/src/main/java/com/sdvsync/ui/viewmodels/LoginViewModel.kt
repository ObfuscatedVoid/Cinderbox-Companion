package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.steam.AuthEvent
import com.sdvsync.steam.AuthState
import com.sdvsync.steam.SteamAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authenticator: SteamAuthenticator,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authenticator.authState

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _twoFactorCode = MutableStateFlow("")
    val twoFactorCode: StateFlow<String> = _twoFactorCode.asStateFlow()

    init {
        authenticator.registerCallbacks()

        viewModelScope.launch {
            authenticator.events.collect { event ->
                when (event) {
                    is AuthEvent.LoginSuccess -> { /* Navigation handled by UI */ }
                    is AuthEvent.LoginFailed -> { /* Error shown via authState */ }
                    is AuthEvent.Disconnected -> { /* Shown via authState */ }
                }
            }
        }
    }

    fun updateUsername(value: String) { _username.value = value }
    fun updatePassword(value: String) { _password.value = value }
    fun updateTwoFactorCode(value: String) { _twoFactorCode.value = value }

    fun login() {
        viewModelScope.launch {
            authenticator.login(_username.value, _password.value)
        }
    }

    fun submitCredentials() {
        viewModelScope.launch {
            authenticator.authenticateWithCredentials(_username.value, _password.value)
        }
    }

    fun submit2FA() {
        authenticator.submit2FACode(_twoFactorCode.value)
        _twoFactorCode.value = ""
    }

    fun tryResumeSession() {
        viewModelScope.launch {
            authenticator.loginWithSavedSession()
        }
    }

    override fun onCleared() {
        super.onCleared()
        authenticator.destroy()
    }
}
