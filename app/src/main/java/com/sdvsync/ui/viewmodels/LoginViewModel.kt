package com.sdvsync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdvsync.steam.AuthState
import com.sdvsync.steam.SteamAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authenticator: SteamAuthenticator) : ViewModel() {

    val authState: StateFlow<AuthState> = authenticator.authState

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _twoFactorCode = MutableStateFlow("")
    val twoFactorCode: StateFlow<String> = _twoFactorCode.asStateFlow()

    fun updateUsername(value: String) {
        _username.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun updateTwoFactorCode(value: String) {
        _twoFactorCode.value = value
    }

    fun login() {
        viewModelScope.launch {
            authenticator.login(_username.value, _password.value)
        }
    }

    fun loginWithQR() {
        viewModelScope.launch {
            authenticator.loginWithQR()
        }
    }

    fun cancelQRLogin() {
        authenticator.cancelQRLogin()
    }

    fun confirmDeviceApproval() {
        authenticator.submitDeviceConfirmation(useMobileApproval = true)
    }

    fun useSteamGuardCode() {
        authenticator.submitDeviceConfirmation(useMobileApproval = false)
    }

    fun submit2FA(): Boolean {
        val submitted = authenticator.submit2FACode(_twoFactorCode.value)
        if (submitted) {
            _twoFactorCode.value = ""
        }
        return submitted
    }

    fun tryResumeSession() {
        viewModelScope.launch {
            authenticator.loginWithSavedSession()
        }
    }
}
