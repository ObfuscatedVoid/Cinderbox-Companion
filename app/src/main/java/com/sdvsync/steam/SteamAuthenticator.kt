package com.sdvsync.steam

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sdvsync.logging.AppLogger
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.*
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await

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
    private val context: Context,
    private val clientManager: SteamClientManager,
    private val sessionStore: SteamSessionStore
) {
    companion object {
        private const val TAG = "SteamAuth"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private enum class PendingAuthFlow {
        NONE,
        RESUME_SESSION,
        CREDENTIALS,
        QR
    }

    private var credentialsSession: CredentialsAuthSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending credentials stored between connect() and onConnected()
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null
    private var pendingQRLogin = false
    private var pendingAuthFlow = PendingAuthFlow.NONE
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
        cbMgr.subscribe(LicenseListCallback::class.java) { callback ->
            AppLogger.d(TAG, "Received ${callback.licenseList.size} licenses")
            clientManager.updateLicenses(callback.licenseList)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Connect to a CM server with timeout and one retry.
     * Throws TimeoutCancellationException if both attempts fail.
     */
    private suspend fun connectWithTimeout() {
        clientManager.reconnect() // Always force fresh — avoids stuck CONNECTING state
        try {
            withTimeout(15_000) {
                clientManager.connectionState.first { state ->
                    state == ConnectionState.CONNECTED ||
                        state == ConnectionState.LOGGED_IN
                }
            }
            AppLogger.d(TAG, "Connected to CM server")
        } catch (_: TimeoutCancellationException) {
            AppLogger.w(TAG, "Connection timed out after 15s, retrying with new CM server...")
            clientManager.reconnect()
            // Let this throw TimeoutCancellationException to caller
            withTimeout(15_000) {
                clientManager.connectionState.first { state ->
                    state == ConnectionState.CONNECTED ||
                        state == ConnectionState.LOGGED_IN
                }
            }
            AppLogger.d(TAG, "Connected to CM server on retry")
        }
    }

    private suspend fun connectOrFail() {
        if (!isNetworkAvailable()) {
            AppLogger.w(TAG, "No network available")
            _authState.value = AuthState.Error("No internet connection")
            _events.emit(AuthEvent.LoginFailed("No internet connection"))
            return
        }
        try {
            connectWithTimeout()
        } catch (_: TimeoutCancellationException) {
            AppLogger.e(TAG, "Connection timed out after retry")
            _authState.value = AuthState.Error("Could not connect to Steam. Please try again.")
            _events.emit(AuthEvent.LoginFailed("Connection timed out"))
        }
    }

    suspend fun login(username: String, password: String) {
        pendingUsername = username
        pendingPassword = password
        pendingQRLogin = false
        pendingAuthFlow = PendingAuthFlow.CREDENTIALS
        isUserDisconnect = false
        AppLogger.i(TAG, "Login attempt for user: $username")
        _authState.value = AuthState.Connecting
        connectOrFail()
    }

    suspend fun loginWithQR() {
        pendingUsername = null
        pendingPassword = null
        pendingQRLogin = true
        pendingAuthFlow = PendingAuthFlow.QR
        isUserDisconnect = false
        AppLogger.i(TAG, "QR login attempt")
        _authState.value = AuthState.Connecting
        connectOrFail()
    }

    fun cancelQRLogin() {
        qrPollJob?.cancel()
        qrPollJob = null
        pendingQRLogin = false
        pendingAuthFlow = PendingAuthFlow.NONE
        _authState.value = AuthState.Idle
    }

    suspend fun loginWithSavedSession() {
        val username = sessionStore.username
        val refreshToken = sessionStore.refreshToken
        if (username == null || refreshToken == null) {
            AppLogger.i(TAG, "No saved session to resume")
            return
        }

        pendingUsername = null
        pendingPassword = null
        pendingQRLogin = false
        pendingAuthFlow = PendingAuthFlow.RESUME_SESSION
        isUserDisconnect = false
        AppLogger.i(TAG, "Attempting saved session resume for $username")
        _authState.value = AuthState.Connecting
        connectOrFail()
    }

    private fun onConnected() {
        clientManager.onConnected()
        scope.launch {
            try {
                val savedRefreshToken = sessionStore.refreshToken
                val savedUsername = sessionStore.username
                AppLogger.d(TAG, "Auth state -> onConnected (pendingFlow=$pendingAuthFlow)")

                when (pendingAuthFlow) {
                    PendingAuthFlow.RESUME_SESSION -> {
                        pendingAuthFlow = PendingAuthFlow.NONE
                        if (savedRefreshToken != null && savedUsername != null) {
                            AppLogger.d(TAG, "Resuming session for $savedUsername")
                            _authState.value = AuthState.LoggingIn
                            logOnWithToken(savedUsername, savedRefreshToken)
                        } else {
                            AppLogger.w(TAG, "Resume session requested but no credentials stored")
                            _authState.value = AuthState.WaitingForCredentials
                        }
                    }

                    PendingAuthFlow.QR -> {
                        pendingQRLogin = false
                        pendingAuthFlow = PendingAuthFlow.NONE
                        AppLogger.d(TAG, "Auth state -> Starting QR authentication")
                        startQRAuthentication()
                    }

                    PendingAuthFlow.CREDENTIALS -> {
                        pendingAuthFlow = PendingAuthFlow.NONE
                        pendingQRLogin = false
                        val username = pendingUsername
                        val password = pendingPassword
                        pendingUsername = null
                        pendingPassword = null

                        if (username != null && password != null) {
                            authenticateWithCredentials(username, password)
                        } else {
                            AppLogger.w(TAG, "Credentials flow but username/password null")
                            _authState.value = AuthState.WaitingForCredentials
                        }
                    }

                    PendingAuthFlow.NONE -> if (savedRefreshToken != null && savedUsername != null) {
                        AppLogger.d(TAG, "Resuming session for $savedUsername (no pending flow)")
                        _authState.value = AuthState.LoggingIn
                        logOnWithToken(savedUsername, savedRefreshToken)
                    } else {
                        _authState.value = AuthState.WaitingForCredentials
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in onConnected", e)
                _authState.value = AuthState.Error("Connection error: ${e.message}")
            }
        }
    }

    private suspend fun authenticateWithCredentials(username: String, password: String) {
        AppLogger.d(TAG, "Authenticating with credentials for $username")
        _authState.value = AuthState.Authenticating

        try {
            val authDetails = AuthSessionDetails().apply {
                this.username = username
                this.password = password
                this.persistentSession = true
                this.authenticator = object : IAuthenticator {
                    override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> =
                        CompletableFuture.completedFuture(false)

                    override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> {
                        val future = CompletableFuture<String>()
                        scope.launch {
                            AppLogger.i(TAG, "2FA required: device code (incorrect=$previousCodeWasIncorrect)")
                            _authState.value = AuthState.WaitingFor2FA(is2FACode = true)
                            pending2FAFuture = future
                        }
                        return future
                    }

                    override fun getEmailCode(
                        email: String?,
                        previousCodeWasIncorrect: Boolean
                    ): CompletableFuture<String> {
                        val future = CompletableFuture<String>()
                        scope.launch {
                            AppLogger.i(TAG, "2FA required: email code (incorrect=$previousCodeWasIncorrect)")
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
        AppLogger.i(TAG, "Starting QR authentication")
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
        AppLogger.d(TAG, "Auth state -> LoggingIn (user=${username.take(2)}***)")
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
            AppLogger.i(TAG, "Logon OK (cellId=${callback.cellID})")
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
            AppLogger.w(TAG, "Logon failed: $msg")
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
        AppLogger.d(TAG, "Logged off: ${callback.result}")
        wasLoggedIn = false
        _authState.value = AuthState.Idle
        clientManager.onDisconnected(userInitiated = false)
        scope.launch { _events.emit(AuthEvent.Disconnected) }
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        val userInitiated = isUserDisconnect
        val currentAuthState = _authState.value
        AppLogger.d(
            TAG,
            "Disconnected (userInitiated=$userInitiated, wasLoggedIn=$wasLoggedIn, authState=${currentAuthState::class.simpleName})"
        )
        clientManager.onDisconnected(userInitiated = userInitiated)

        if (!userInitiated && wasLoggedIn && sessionStore.hasSession()) {
            // Don't auto-reconnect if we're already in a connecting/authenticating state.
            // JavaSteam's client.connect() internally calls disconnect(), which fires this
            // callback again — without this guard we'd get an infinite reconnection cascade.
            if (currentAuthState is AuthState.Connecting ||
                currentAuthState is AuthState.Authenticating ||
                currentAuthState is AuthState.LoggingIn
            ) {
                AppLogger.d(TAG, "Already connecting/authenticating, ignoring disconnect for auto-reconnect")
                return
            }

            AppLogger.d(TAG, "Auto-reconnecting in 2s...")
            _authState.value = AuthState.Connecting
            scope.launch {
                delay(2000) // Let the client settle before reconnecting
                try {
                    pendingAuthFlow = PendingAuthFlow.RESUME_SESSION
                    connectWithTimeout()
                    AppLogger.d(TAG, "Auto-reconnect succeeded")
                } catch (_: TimeoutCancellationException) {
                    AppLogger.e(TAG, "Auto-reconnect timed out")
                    wasLoggedIn = false
                    pendingAuthFlow = PendingAuthFlow.NONE
                    _authState.value = AuthState.Idle
                    _events.emit(AuthEvent.Disconnected)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Auto-reconnect failed", e)
                    wasLoggedIn = false
                    pendingAuthFlow = PendingAuthFlow.NONE
                    _authState.value = AuthState.Idle
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
        pendingAuthFlow = PendingAuthFlow.NONE
        sessionStore.clear()
        _authState.value = AuthState.Idle
        clientManager.disconnect()
    }

    fun destroy() {
        scope.cancel()
        clientManager.destroy()
    }
}
