package com.sdvsync.steam

import android.util.Log
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.*
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
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
    data class WaitingForQRScan(val challengeUrl: String) : AuthState()
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
    companion object {
        private const val TAG = "SteamAuth"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var credentialsSession: CredentialsAuthSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending credentials stored between connect() and onConnected()
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null
    private var pendingQRLogin = false
    private var qrPollJob: Job? = null

    // Track if we were logged in (for auto-reconnect)
    private var wasLoggedIn = false
    private var isUserDisconnect = false

    init {
        registerCallbacks()
    }

    private fun registerCallbacks() {
        val cbMgr = clientManager.callbackMgr

        cbMgr.subscribe(ConnectedCallback::class.java) { onConnected() }
        cbMgr.subscribe(DisconnectedCallback::class.java) { onDisconnected(it) }
        cbMgr.subscribe(LoggedOnCallback::class.java) { onLoggedOn(it) }
        cbMgr.subscribe(LoggedOffCallback::class.java) { onLoggedOff(it) }
    }

    suspend fun login(username: String, password: String) {
        pendingUsername = username
        pendingPassword = password
        pendingQRLogin = false
        isUserDisconnect = false
        _authState.value = AuthState.Connecting
        clientManager.connect()
    }

    suspend fun loginWithQR() {
        pendingUsername = null
        pendingPassword = null
        pendingQRLogin = true
        isUserDisconnect = false
        _authState.value = AuthState.Connecting
        clientManager.connect()
    }

    fun cancelQRLogin() {
        qrPollJob?.cancel()
        qrPollJob = null
        pendingQRLogin = false
        _authState.value = AuthState.Idle
    }

    suspend fun loginWithSavedSession() {
        val username = sessionStore.username
        val refreshToken = sessionStore.refreshToken
        if (username == null || refreshToken == null) {
            return
        }

        pendingUsername = null
        pendingPassword = null
        pendingQRLogin = false
        isUserDisconnect = false
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
                    Log.d(TAG, "Resuming session for $savedUsername")
                    logOnWithToken(savedUsername, savedRefreshToken)
                } else if (pendingQRLogin) {
                    pendingQRLogin = false
                    startQRAuthentication()
                } else if (pendingUsername != null && pendingPassword != null) {
                    authenticateWithCredentials(pendingUsername!!, pendingPassword!!)
                    pendingUsername = null
                    pendingPassword = null
                } else {
                    _authState.value = AuthState.WaitingForCredentials
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onConnected", e)
                _authState.value = AuthState.Error("Connection error: ${e.message}")
            }
        }
    }

    private suspend fun authenticateWithCredentials(username: String, password: String) {
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

            val session = SteamAuthentication(clientManager.client)
                .beginAuthSessionViaCredentials(authDetails)
                .await()

            credentialsSession = session

            val pollResponse = session.pollingWaitForResult().await()

            sessionStore.username = username
            sessionStore.accessToken = pollResponse.accessToken
            sessionStore.refreshToken = pollResponse.refreshToken

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

    private suspend fun startQRAuthentication() {
        try {
            val authDetails = AuthSessionDetails().apply {
                deviceFriendlyName = "SDV-Sync Android"
                persistentSession = true
            }

            val qrSession = SteamAuthentication(clientManager.client)
                .beginAuthSessionViaQR(authDetails)
                .await()

            _authState.value = AuthState.WaitingForQRScan(qrSession.challengeUrl)

            qrSession.challengeUrlChanged = IChallengeUrlChanged { session ->
                session?.let {
                    _authState.value = AuthState.WaitingForQRScan(it.challengeUrl)
                }
            }

            qrPollJob = scope.launch {
                var pollResult: AuthPollResult? = null
                while (isActive && pollResult == null) {
                    try {
                        pollResult = qrSession.pollAuthSessionStatus().await()
                    } catch (e: AuthenticationException) {
                        _authState.value = AuthState.Error("QR login expired. Try again.")
                        _events.emit(AuthEvent.LoginFailed("QR session expired"))
                        return@launch
                    }

                    if (pollResult == null) {
                        delay((qrSession.pollingInterval * 1000).toLong())
                    }
                }

                if (pollResult != null) {
                    sessionStore.username = pollResult.accountName
                    sessionStore.accessToken = pollResult.accessToken
                    sessionStore.refreshToken = pollResult.refreshToken

                    logOnWithToken(pollResult.accountName, pollResult.refreshToken)
                }
            }

        } catch (e: Exception) {
            _authState.value = AuthState.Error("QR login failed: ${e.message}")
            scope.launch { _events.emit(AuthEvent.LoginFailed(e.message ?: "Unknown error")) }
        }
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
            Log.d(TAG, "Logged on successfully")
            callback.clientSteamID?.let { steamId ->
                sessionStore.steamId = steamId.convertToUInt64()
            }
            sessionStore.cellId = callback.cellID

            wasLoggedIn = true
            clientManager.onLoggedIn()
            _authState.value = AuthState.LoggedIn
            scope.launch { _events.emit(AuthEvent.LoginSuccess) }
        } else {
            val msg = when (callback.result) {
                EResult.AccountLogonDenied -> "Steam Guard email code required"
                EResult.AccountLoginDeniedNeedTwoFactor -> "Steam Guard mobile code required"
                EResult.InvalidPassword -> "Invalid password or expired token"
                EResult.TwoFactorCodeMismatch -> "Invalid 2FA code"
                EResult.Expired -> "Session expired, please log in again"
                else -> "Login failed: ${callback.result}"
            }
            Log.w(TAG, "Logon failed: $msg")
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
        Log.d(TAG, "Logged off: ${callback.result}")
        wasLoggedIn = false
        _authState.value = AuthState.Idle
        clientManager.onDisconnected(userInitiated = false)
        scope.launch { _events.emit(AuthEvent.Disconnected) }
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        val userInitiated = isUserDisconnect
        val currentAuthState = _authState.value
        Log.d(TAG, "Disconnected (userInitiated=$userInitiated, wasLoggedIn=$wasLoggedIn, authState=${currentAuthState::class.simpleName})")
        clientManager.onDisconnected(userInitiated = userInitiated)

        if (!userInitiated && wasLoggedIn && sessionStore.hasSession()) {
            // Don't auto-reconnect if we're already in a connecting/authenticating state.
            // JavaSteam's client.connect() internally calls disconnect(), which fires this
            // callback again — without this guard we'd get an infinite reconnection cascade.
            if (currentAuthState is AuthState.Connecting ||
                currentAuthState is AuthState.Authenticating ||
                currentAuthState is AuthState.LoggingIn
            ) {
                Log.d(TAG, "Already connecting/authenticating, ignoring disconnect for auto-reconnect")
                return
            }

            Log.d(TAG, "Auto-reconnecting in 2s...")
            _authState.value = AuthState.Connecting
            scope.launch {
                delay(2000) // Let the client settle before reconnecting
                try {
                    clientManager.connect()
                    Log.d(TAG, "Auto-reconnect: connect() returned, waiting for callbacks...")
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-reconnect failed", e)
                    wasLoggedIn = false
                    _authState.value = AuthState.Error("Disconnected: ${e.message}")
                    _events.emit(AuthEvent.Disconnected)
                }
            }
        } else if (!userInitiated && currentAuthState is AuthState.LoggedIn) {
            _authState.value = AuthState.Idle
            scope.launch { _events.emit(AuthEvent.Disconnected) }
        }
    }

    fun logout() {
        isUserDisconnect = true
        wasLoggedIn = false
        sessionStore.clear()
        _authState.value = AuthState.Idle
        clientManager.disconnect()
    }

    fun destroy() {
        scope.cancel()
        clientManager.destroy()
    }
}
