package com.sdvsync.steam

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.EAuthSessionGuardType
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSession
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.CredentialsAuthSession
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.steam.authentication.SteamAuthentication
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed class AuthState {
    data object Idle : AuthState()
    data object Connecting : AuthState()
    data class WaitingForDeviceConfirmation(val canUseCode: Boolean) : AuthState()
    data class WaitingFor2FA(val is2FACode: Boolean, val previousCodeWasIncorrect: Boolean = false) : AuthState()
    data class WaitingForQRScan(val challengeUrl: String) : AuthState()
    data object Authenticating : AuthState()
    data object LoggingIn : AuthState()
    data object LoggedIn : AuthState()
    data class Error(val message: String) : AuthState()
}

private fun AuthState.isTransientAttemptState(): Boolean = this is AuthState.Connecting ||
    this is AuthState.WaitingForDeviceConfirmation ||
    this is AuthState.WaitingFor2FA ||
    this is AuthState.WaitingForQRScan ||
    this is AuthState.Authenticating ||
    this is AuthState.LoggingIn

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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val authAttempts = AuthAttemptCoordinator()
    private val deviceConfirmationChoice = PendingDeviceConfirmationChoice()
    private val twoFactorChallenge = PendingTwoFactorChallenge()
    private val pendingLogOn = AtomicReference<PendingLogOn?>(null)
    private var autoReconnectJob: Job? = null

    @Volatile
    private var wasLoggedIn = false

    @Volatile
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

    private suspend fun connectWithTimeout() {
        clientManager.reconnect()
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
            withTimeout(15_000) {
                clientManager.connectionState.first { state ->
                    state == ConnectionState.CONNECTED ||
                        state == ConnectionState.LOGGED_IN
                }
            }
            AppLogger.d(TAG, "Connected to CM server on retry")
        }
    }

    private fun isClockValid(): Boolean {
        val now = System.currentTimeMillis()
        val minValid = java.util.Calendar.getInstance().apply {
            set(2025, java.util.Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis
        return now > minValid
    }

    private suspend fun connectOrFail(attempt: AuthAttemptToken): Boolean {
        if (!isNetworkAvailable()) {
            AppLogger.w(TAG, "No network available")
            setState(attempt, AuthState.Error(context.getString(R.string.error_no_internet)))
            return false
        }
        if (!isClockValid()) {
            AppLogger.w(TAG, "Device clock appears incorrect")
            setState(attempt, AuthState.Error(context.getString(R.string.error_clock_invalid)))
            return false
        }

        return try {
            connectWithTimeout()
            ensureActive(attempt)
            true
        } catch (_: TimeoutCancellationException) {
            AppLogger.e(TAG, "Connection timed out after retry")
            setState(attempt, AuthState.Error(context.getString(R.string.error_steam_connection)))
            clientManager.disconnect()
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Connection failed", e)
            setState(attempt, AuthState.Error(context.getString(R.string.error_steam_connection)))
            clientManager.disconnect()
            false
        }
    }

    private suspend fun runAuthAttempt(kind: AuthAttemptKind, action: suspend (AuthAttemptToken) -> Unit) {
        val job = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Authentication requires a coroutine job")
        val attempt = authAttempts.tryBegin(kind, job)
        if (attempt == null) {
            AppLogger.w(TAG, "Ignored inactive or overlapping ${kind.name.lowercase()} authentication attempt")
            return
        }

        isUserDisconnect = false
        try {
            ensureActive(attempt)
            action(attempt)
        } finally {
            cancelPendingInput(attempt.id)
            clearPendingLogOn(attempt.id)
            authAttempts.finish(attempt) {
                if (job.isCancelled && _authState.value.isTransientAttemptState()) {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    private fun setState(attempt: AuthAttemptToken, state: AuthState): Boolean = authAttempts.runIfActive(attempt.id) {
        _authState.value = state
    }

    private suspend fun ensureActive(attempt: AuthAttemptToken) {
        currentCoroutineContext().ensureActive()
        if (!authAttempts.isActive(attempt.id)) {
            throw CancellationException("Authentication attempt is no longer active")
        }
    }

    suspend fun login(username: String, password: String) {
        runAuthAttempt(AuthAttemptKind.CREDENTIALS) { attempt ->
            AppLogger.i(TAG, "Login attempt for user: ${username.take(2)}***")
            setState(attempt, AuthState.Connecting)
            if (connectOrFail(attempt)) {
                authenticateWithCredentials(attempt, username, password)
            }
        }
    }

    suspend fun loginWithQR() {
        runAuthAttempt(AuthAttemptKind.QR) { attempt ->
            AppLogger.i(TAG, "QR login attempt")
            setState(attempt, AuthState.Connecting)
            if (connectOrFail(attempt)) {
                startQRAuthentication(attempt)
            }
        }
    }

    fun cancelQRLogin() {
        val cancelled = authAttempts.cancelActive(AuthAttemptKind.QR) {
            _authState.value is AuthState.WaitingForQRScan
        } ?: return
        cancelPendingInput(cancelled.id)
        clearPendingLogOn(cancelled.id)
        _authState.value = AuthState.Idle
    }

    suspend fun loginWithSavedSession() {
        if (clientManager.isLoggedIn) {
            AppLogger.d(TAG, "Saved session resume skipped because Steam is already logged in")
            return
        }

        runAuthAttempt(AuthAttemptKind.RESUME_SESSION) { attempt ->
            val username = sessionStore.username
            val refreshToken = sessionStore.refreshToken
            if (username == null || refreshToken == null) {
                AppLogger.i(TAG, "No saved session to resume")
                setState(attempt, AuthState.Idle)
                return@runAuthAttempt
            }

            AppLogger.i(TAG, "Attempting saved session resume for ${username.take(2)}***")
            setState(attempt, AuthState.Connecting)
            if (connectOrFail(attempt)) {
                try {
                    logOnWithToken(attempt, username, refreshToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Saved session resume failed", e)
                    setState(attempt, AuthState.Error(context.getString(R.string.error_steam_connection)))
                }
            }
        }
    }

    private fun onConnected() {
        clientManager.onConnected()
        AppLogger.d(TAG, "Connected callback received")
    }

    private suspend fun authenticateWithCredentials(attempt: AuthAttemptToken, username: String, password: String) {
        AppLogger.d(TAG, "Authenticating with credentials for ${username.take(2)}***")
        setState(attempt, AuthState.Authenticating)

        try {
            val authDetails = AuthSessionDetails().apply {
                this.username = username
                this.password = password
                this.deviceFriendlyName = context.getString(R.string.steam_device_name)
                this.persistentSession = true
            }
            val attemptScope = CoroutineScope(currentCoroutineContext() + Dispatchers.IO)

            val session = SteamAuthentication(clientManager.client)
                .beginAuthSessionViaCredentials(authDetails, attemptScope)
                .await()
            ensureActive(attempt)

            val pollResponse = completeCredentialsAuthentication(attempt, session, attemptScope)
            ensureActive(attempt)

            logOnWithToken(attempt, username, pollResponse.refreshToken, pollResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthenticationException) {
            AppLogger.e(TAG, "Credential authentication failed", e)
            val message = when (e.result) {
                EResult.InvalidPassword -> context.getString(R.string.error_invalid_password)
                EResult.Expired -> context.getString(R.string.error_session_expired)
                else -> context.getString(R.string.error_auth_failed_generic)
            }
            setState(attempt, AuthState.Error(message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Credential authentication connection failure", e)
            setState(attempt, AuthState.Error(context.getString(R.string.error_steam_connection)))
        }
    }

    private suspend fun completeCredentialsAuthentication(
        attempt: AuthAttemptToken,
        session: CredentialsAuthSession,
        attemptScope: CoroutineScope
    ): AuthPollResult {
        var confirmation = session.allowedConfirmations.firstOrNull()
            ?: throw AuthenticationException("There are no allowed confirmations")

        if (confirmation.confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation) {
            val fallback = session.allowedConfirmations.getOrNull(1)?.takeIf {
                it.confirmationType.isCodeConfirmation()
            }
            val choice = deviceConfirmationChoice.awaitChoice(attempt.id)
            AppLogger.i(TAG, "Steam Guard requires device confirmation")
            setState(attempt, AuthState.WaitingForDeviceConfirmation(canUseCode = fallback != null))

            val useMobileApproval = choice.await()
            ensureActive(attempt)
            if (useMobileApproval) {
                setState(attempt, AuthState.Authenticating)
                return pollUntilResult(
                    attempt = attempt,
                    session = session,
                    attemptScope = attemptScope,
                    delayBeforeFirstPoll = true
                )
            }

            confirmation = fallback
                ?: throw AuthenticationException("No Steam Guard code fallback is available")
        }

        return when (val confirmationType = confirmation.confirmationType) {
            EAuthSessionGuardType.k_EAuthSessionGuardType_None -> {
                pollUntilResult(attempt, session, attemptScope, delayBeforeFirstPoll = false)
            }

            EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode,
            EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode -> {
                submitSteamGuardCode(attempt, session, confirmationType, attemptScope)
                pollUntilResult(attempt, session, attemptScope, delayBeforeFirstPoll = false)
            }

            EAuthSessionGuardType.k_EAuthSessionGuardType_Unknown -> {
                throw AuthenticationException("There are no allowed confirmations")
            }

            else -> throw AuthenticationException("Unsupported confirmation type $confirmationType")
        }
    }

    private suspend fun submitSteamGuardCode(
        attempt: AuthAttemptToken,
        session: CredentialsAuthSession,
        confirmationType: EAuthSessionGuardType,
        attemptScope: CoroutineScope
    ) {
        val isDeviceCode = confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode
        val invalidCodeResult = if (isDeviceCode) {
            EResult.TwoFactorCodeMismatch
        } else {
            EResult.InvalidLoginAuthCode
        }
        var previousCodeWasIncorrect = false

        while (true) {
            val challenge = twoFactorChallenge.awaitCode(attempt.id)
            AppLogger.i(
                TAG,
                "Steam Guard requires ${if (isDeviceCode) "device" else "email"} code " +
                    "(incorrect=$previousCodeWasIncorrect)"
            )
            setState(
                attempt,
                AuthState.WaitingFor2FA(
                    is2FACode = isDeviceCode,
                    previousCodeWasIncorrect = previousCodeWasIncorrect
                )
            )

            val code = challenge.await()
            ensureActive(attempt)
            setState(attempt, AuthState.Authenticating)

            try {
                session.sendSteamGuardCode(code, confirmationType, attemptScope).await()
                return
            } catch (e: AuthenticationException) {
                if (e.result != invalidCodeResult) {
                    throw e
                }
                previousCodeWasIncorrect = true
            }
        }
    }

    private suspend fun pollUntilResult(
        attempt: AuthAttemptToken,
        session: AuthSession,
        attemptScope: CoroutineScope,
        delayBeforeFirstPoll: Boolean
    ): AuthPollResult {
        var shouldDelay = delayBeforeFirstPoll
        while (true) {
            ensureActive(attempt)
            if (shouldDelay) {
                delay(pollingIntervalMillis(session.pollingInterval))
            }
            shouldDelay = true

            val result = session.pollAuthSessionStatus(attemptScope).await()
            ensureActive(attempt)
            if (result != null) {
                return result
            }
        }
    }

    fun submit2FACode(code: String): Boolean {
        val attempt = authAttempts.activeAttempt()
        if (attempt == null || _authState.value !is AuthState.WaitingFor2FA) {
            AppLogger.w(TAG, "Ignored unexpected 2FA code submission")
            return false
        }

        val submitted = twoFactorChallenge.submit(attempt.id, code) {
            setState(attempt, AuthState.Authenticating)
        }
        if (!submitted) {
            AppLogger.w(TAG, "Ignored empty or duplicate 2FA code submission")
        }
        return submitted
    }

    fun submitDeviceConfirmation(useMobileApproval: Boolean): Boolean {
        val attempt = authAttempts.activeAttempt()
        val state = _authState.value as? AuthState.WaitingForDeviceConfirmation
        if (attempt == null || state == null || (!useMobileApproval && !state.canUseCode)) {
            AppLogger.w(TAG, "Ignored unexpected device confirmation choice")
            return false
        }

        val submitted = deviceConfirmationChoice.submit(attempt.id, useMobileApproval) {
            setState(attempt, AuthState.Authenticating)
        }
        if (!submitted) {
            AppLogger.w(TAG, "Ignored duplicate device confirmation choice")
        }
        return submitted
    }

    private suspend fun startQRAuthentication(attempt: AuthAttemptToken) {
        AppLogger.i(TAG, "Starting QR authentication")
        var qrSession: QrAuthSession? = null
        try {
            val authDetails = AuthSessionDetails().apply {
                deviceFriendlyName = context.getString(R.string.steam_device_name)
                persistentSession = true
            }
            val attemptScope = CoroutineScope(currentCoroutineContext() + Dispatchers.IO)

            qrSession = SteamAuthentication(clientManager.client)
                .beginAuthSessionViaQR(authDetails, attemptScope)
                .await()
            ensureActive(attempt)

            qrSession.challengeUrlChanged = IChallengeUrlChanged { session ->
                if (session != null && authAttempts.isActive(attempt.id)) {
                    setState(attempt, AuthState.WaitingForQRScan(session.challengeUrl))
                }
            }
            setState(attempt, AuthState.WaitingForQRScan(qrSession.challengeUrl))

            val pollResult = pollUntilResult(
                attempt = attempt,
                session = qrSession,
                attemptScope = attemptScope,
                delayBeforeFirstPoll = false
            )
            ensureActive(attempt)

            logOnWithToken(attempt, pollResult.accountName, pollResult.refreshToken, pollResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthenticationException) {
            AppLogger.e(TAG, "QR authentication expired or was rejected", e)
            setState(attempt, AuthState.Error(context.getString(R.string.error_qr_expired)))
        } catch (e: Exception) {
            AppLogger.e(TAG, "QR authentication failed", e)
            setState(attempt, AuthState.Error(context.getString(R.string.error_qr_failed_generic)))
        } finally {
            qrSession?.challengeUrlChanged = null
        }
    }

    private suspend fun logOnWithToken(
        attempt: AuthAttemptToken,
        username: String,
        refreshToken: String,
        newAuthResult: AuthPollResult? = null
    ) {
        ensureActive(attempt)
        val callbackResult = CompletableDeferred<LoggedOnCallback>()
        val pending = PendingLogOn(attempt.id, callbackResult)
        val details = LogOnDetails().apply {
            this.username = username
            this.accessToken = refreshToken
            this.shouldRememberPassword = true
        }

        try {
            val initiated = authAttempts.runIfActive(attempt.id) {
                check(pendingLogOn.compareAndSet(null, pending)) {
                    "Another Steam logon callback is already pending"
                }
                AppLogger.d(TAG, "Auth state -> LoggingIn (user=${username.take(2)}***)")
                _authState.value = AuthState.LoggingIn
                clientManager.user.logOn(details)
            }
            if (!initiated) {
                throw CancellationException("Authentication ended before Steam logon started")
            }

            val callback = callbackResult.await()
            ensureActive(attempt)
            handleLoggedOn(attempt, username, newAuthResult, callback)
        } finally {
            pendingLogOn.compareAndSet(pending, null)
        }
    }

    private fun onLoggedOn(callback: LoggedOnCallback) {
        val pending = pendingLogOn.get()
        if (pending == null || !authAttempts.isActive(pending.attemptId)) {
            AppLogger.w(TAG, "Ignoring logon callback without an active authentication attempt")
            return
        }
        pending.result.complete(callback)
    }

    private fun handleLoggedOn(
        attempt: AuthAttemptToken,
        username: String,
        newAuthResult: AuthPollResult?,
        callback: LoggedOnCallback
    ) {
        authAttempts.runIfActive(attempt.id) {
            if (callback.result == EResult.OK) {
                AppLogger.i(TAG, "Logon OK (cellId=${callback.cellID})")
                if (newAuthResult != null) {
                    sessionStore.username = username
                    sessionStore.accessToken = newAuthResult.accessToken
                    sessionStore.refreshToken = newAuthResult.refreshToken
                }
                callback.clientSteamID?.let { steamId ->
                    sessionStore.steamId = steamId.convertToUInt64()
                }
                sessionStore.cellId = callback.cellID

                wasLoggedIn = true
                clientManager.onLoggedIn()
                _authState.value = AuthState.LoggedIn
            } else {
                val msg = when (callback.result) {
                    EResult.AccountLogonDenied -> context.getString(R.string.error_steamguard_email)
                    EResult.AccountLoginDeniedNeedTwoFactor -> context.getString(R.string.error_steamguard_mobile)
                    EResult.InvalidPassword -> context.getString(R.string.error_invalid_password)
                    EResult.TwoFactorCodeMismatch -> context.getString(R.string.error_2fa_mismatch)
                    EResult.Expired -> context.getString(R.string.error_session_expired)
                    else -> context.getString(R.string.error_auth_failed_generic)
                }
                AppLogger.w(TAG, "Logon failed (${callback.result}): $msg")
                _authState.value = AuthState.Error(msg)

                if (callback.result == EResult.InvalidPassword || callback.result == EResult.Expired) {
                    sessionStore.accessToken = null
                    sessionStore.refreshToken = null
                }
            }
        }
    }

    private fun onLoggedOff(callback: LoggedOffCallback) {
        AppLogger.d(TAG, "Logged off: ${callback.result}")
        wasLoggedIn = false
        clientManager.onDisconnected(userInitiated = false)
        if (handleActiveConnectionLoss(_authState.value)) {
            return
        }
        _authState.value = AuthState.Idle
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        val appDisconnect = isUserDisconnect
        if (shouldIgnoreTransportDisconnect(callback.isUserInitiated, appDisconnect)) {
            AppLogger.d(TAG, "Ignoring intentional disconnect used to replace the Steam transport")
            return
        }

        val userInitiated = appDisconnect || callback.isUserInitiated
        val currentAuthState = _authState.value
        AppLogger.d(
            TAG,
            "Disconnected (userInitiated=$userInitiated, wasLoggedIn=$wasLoggedIn, authState=${currentAuthState::class.simpleName})"
        )
        clientManager.onDisconnected(userInitiated = userInitiated)

        if (handleActiveConnectionLoss(currentAuthState)) {
            return
        }

        if (!userInitiated && wasLoggedIn && sessionStore.hasSession()) {
            AppLogger.d(TAG, "Auto-reconnecting in 2s...")
            _authState.value = AuthState.Connecting
            autoReconnectJob?.cancel()
            autoReconnectJob = scope.launch {
                delay(2000)
                if (clientManager.isLoggedIn) {
                    return@launch
                }
                loginWithSavedSession()
                if (_authState.value is AuthState.Error) {
                    wasLoggedIn = false
                }
            }
        } else if (!userInitiated && currentAuthState is AuthState.LoggedIn) {
            _authState.value = AuthState.Idle
        }
    }

    private fun handleActiveConnectionLoss(currentAuthState: AuthState): Boolean {
        if (authAttempts.activeAttempt() == null) {
            return false
        }
        if (currentAuthState is AuthState.Connecting || currentAuthState is AuthState.Error) {
            return true
        }
        if (currentAuthState is AuthState.LoggedIn) {
            return false
        }

        val cancelled = authAttempts.cancelActive() ?: return true
        cancelPendingInput(cancelled.id)
        clearPendingLogOn(cancelled.id)
        _authState.value = AuthState.Error(context.getString(R.string.error_steam_disconnected_login))
        return true
    }

    fun logout() {
        isUserDisconnect = true
        wasLoggedIn = false
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        authAttempts.cancelActive()?.let { cancelled ->
            cancelPendingInput(cancelled.id)
            clearPendingLogOn(cancelled.id)
        }
        sessionStore.clear()
        _authState.value = AuthState.Idle
        clientManager.disconnect()
    }

    fun destroy() {
        authAttempts.cancelActive()?.let { cancelled ->
            cancelPendingInput(cancelled.id)
            clearPendingLogOn(cancelled.id)
        }
        scope.cancel()
        clientManager.destroy()
    }

    private fun cancelPendingInput(attemptId: Long) {
        twoFactorChallenge.cancel(attemptId)
        deviceConfirmationChoice.cancel(attemptId)
    }

    private fun clearPendingLogOn(attemptId: Long) {
        while (true) {
            val pending = pendingLogOn.get() ?: return
            if (pending.attemptId != attemptId) {
                return
            }
            if (pendingLogOn.compareAndSet(pending, null)) {
                pending.result.cancel(CancellationException("Authentication attempt ended"))
                return
            }
        }
    }

    private data class PendingLogOn(val attemptId: Long, val result: CompletableDeferred<LoggedOnCallback>)
}
