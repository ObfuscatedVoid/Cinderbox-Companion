package com.sdvsync.steam

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.*
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    data object Idle : AuthState()
    data object Connecting : AuthState()
    data object WaitingForCredentials : AuthState()
    data class WaitingFor2FA(val is2FACode: Boolean) : AuthState()
    data object Authenticating : AuthState()
    data object LoggingIn : AuthState()
    data object LoggedIn : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthEvent {
    data object LoginSuccess : AuthEvent()
    data class LoginFailed(val message: String) : AuthEvent()
    data object Disconnected : AuthEvent()
}

class SteamAuthenticator(
    private val clientManager: SteamClientManager,
    private val sessionStore: SteamSessionStore,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var credentialsSession: CredentialsAuthSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun registerCallbacks() {
        val cbMgr = clientManager.callbackMgr

        cbMgr.subscribe(ConnectedCallback::class.java) { onConnected() }
        cbMgr.subscribe(DisconnectedCallback::class.java) { onDisconnected(it) }
        cbMgr.subscribe(LoggedOnCallback::class.java) { onLoggedOn(it) }
        cbMgr.subscribe(LoggedOffCallback::class.java) { onLoggedOff(it) }
    }

    suspend fun login(username: String, password: String) {
        _authState.value = AuthState.Connecting
        clientManager.connect()
    }

    suspend fun loginWithSavedSession() {
        val username = sessionStore.username
        val refreshToken = sessionStore.refreshToken
        if (username == null || refreshToken == null) {
            _authState.value = AuthState.Error("No saved session")
            return
        }

        _authState.value = AuthState.Connecting
        clientManager.connect()
    }

    private fun onConnected() {
        clientManager.onConnected()
        scope.launch {
            try {
                val savedRefreshToken = sessionStore.refreshToken
                val savedUsername = sessionStore.username

                if (savedRefreshToken != null && savedUsername != null) {
                    logOnWithToken(savedUsername, savedRefreshToken)
                } else {
                    _authState.value = AuthState.WaitingForCredentials
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Connection error: ${e.message}")
            }
        }
    }

    suspend fun authenticateWithCredentials(username: String, password: String) {
        _authState.value = AuthState.Authenticating

        try {
            val authDetails = AuthSessionDetails().apply {
                this.username = username
                this.password = password
                this.persistentSession = true
                this.authenticator = object : IAuthenticator {
                    override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> {
                        return CompletableFuture.completedFuture(false)
                    }

                    override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> {
                        val future = CompletableFuture<String>()
                        scope.launch {
                            _authState.value = AuthState.WaitingFor2FA(is2FACode = true)
                            pending2FAFuture = future
                        }
                        return future
                    }

                    override fun getEmailCode(
                        email: String?,
                        previousCodeWasIncorrect: Boolean,
                    ): CompletableFuture<String> {
                        val future = CompletableFuture<String>()
                        scope.launch {
                            _authState.value = AuthState.WaitingFor2FA(is2FACode = false)
                            pending2FAFuture = future
                        }
                        return future
                    }
                }
            }

            // beginAuthSessionViaCredentials returns CompletableFuture<CredentialsAuthSession>
            val session = SteamAuthentication(clientManager.client)
                .beginAuthSessionViaCredentials(authDetails)
                .get()

            credentialsSession = session

            // pollingWaitForResult returns CompletableFuture<AuthPollResult>
            val pollResponse = session.pollingWaitForResult().get()

            // Save session tokens
            sessionStore.username = username
            sessionStore.accessToken = pollResponse.accessToken
            sessionStore.refreshToken = pollResponse.refreshToken

            // Log on with the new tokens
            logOnWithToken(username, pollResponse.refreshToken)

        } catch (e: AuthenticationException) {
            _authState.value = AuthState.Error("Authentication failed: ${e.message}")
            scope.launch { _events.emit(AuthEvent.LoginFailed(e.message ?: "Unknown error")) }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Error: ${e.message}")
            scope.launch { _events.emit(AuthEvent.LoginFailed(e.message ?: "Unknown error")) }
        }
    }

    @Volatile
    private var pending2FAFuture: CompletableFuture<String>? = null

    fun submit2FACode(code: String) {
        pending2FAFuture?.complete(code)
        pending2FAFuture = null
    }

    private fun logOnWithToken(username: String, refreshToken: String) {
        _authState.value = AuthState.LoggingIn

        val details = LogOnDetails().apply {
            this.username = username
            this.accessToken = refreshToken
            this.shouldRememberPassword = true
        }

        clientManager.user.logOn(details)
    }

    private fun onLoggedOn(callback: LoggedOnCallback) {
        if (callback.result == EResult.OK) {
            callback.clientSteamID?.let { steamId ->
                sessionStore.steamId = steamId.convertToUInt64()
            }
            sessionStore.cellId = callback.cellID

            clientManager.onLoggedIn()
            _authState.value = AuthState.LoggedIn
            scope.launch { _events.emit(AuthEvent.LoginSuccess) }
        } else {
            val msg = when (callback.result) {
                EResult.AccountLogonDenied -> "Steam Guard email code required"
                EResult.AccountLoginDeniedNeedTwoFactor -> "Steam Guard mobile code required"
                EResult.InvalidPassword -> "Invalid password"
                EResult.TwoFactorCodeMismatch -> "Invalid 2FA code"
                EResult.Expired -> "Session expired, please log in again"
                else -> "Login failed: ${callback.result}"
            }
            _authState.value = AuthState.Error(msg)
            scope.launch { _events.emit(AuthEvent.LoginFailed(msg)) }

            if (callback.result == EResult.InvalidPassword ||
                callback.result == EResult.Expired
            ) {
                sessionStore.accessToken = null
                sessionStore.refreshToken = null
            }
        }
    }

    private fun onLoggedOff(callback: LoggedOffCallback) {
        _authState.value = AuthState.Idle
        clientManager.onDisconnected()
        scope.launch { _events.emit(AuthEvent.Disconnected) }
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        clientManager.onDisconnected()
        if (_authState.value is AuthState.LoggedIn) {
            _authState.value = AuthState.Idle
            scope.launch { _events.emit(AuthEvent.Disconnected) }
        }
    }

    fun logout() {
        sessionStore.clear()
        _authState.value = AuthState.Idle
        clientManager.disconnect()
    }

    fun destroy() {
        scope.cancel()
        clientManager.destroy()
    }
}
