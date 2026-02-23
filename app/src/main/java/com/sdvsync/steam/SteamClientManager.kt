package com.sdvsync.steam

import android.util.Log
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGED_IN,
    DISCONNECTING
}

class SteamClientManager {

    companion object {
        private const val TAG = "SteamClientManager"
    }

    val client: SteamClient = SteamClient()
    val callbackMgr: CallbackManager = CallbackManager(client)
    private var callbackJob: Job? = null
    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val isRunning = AtomicBoolean(false)

    val user: SteamUser
        get() = client.getHandler(SteamUser::class.java)
            ?: throw IllegalStateException("SteamUser handler not available")

    val cloud: SteamCloud
        get() = client.getHandler(SteamCloud::class.java)
            ?: throw IllegalStateException("SteamCloud handler not available")

    val isLoggedIn: Boolean
        get() = _connectionState.value == ConnectionState.LOGGED_IN

    /**
     * Ensure the callback loop is running. Safe to call multiple times.
     */
    private fun ensureCallbackLoop() {
        // If the previous callback job exited unexpectedly, reset so we can start a new one
        if (callbackJob?.isCompleted == true) {
            Log.w(TAG, "Callback loop had stopped, will restart")
            isRunning.set(false)
        }

        if (isRunning.getAndSet(true)) return

        Log.d(TAG, "Starting callback loop")
        callbackJob = callbackScope.launch {
            while (isRunning.get()) {
                try {
                    callbackMgr.runWaitCallbacks(1000)
                } catch (e: Exception) {
                    // Catch ALL exceptions including CancellationException.
                    // Steam's disconnect cancels pending async jobs, throwing
                    // j.u.c.CancellationException (which IS kotlinx.coroutines.CancellationException
                    // via typealias). Rethrowing would kill this coroutine and prevent reconnection.
                    // This follows the same pattern used by Pluvia (proven JavaSteam Android app).
                    if (!isRunning.get()) break
                    Log.w(TAG, "Callback loop caught ${e::class.simpleName}: ${e.message}")
                }
            }
            Log.d(TAG, "Callback loop stopped")
        }
    }

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.LOGGED_IN
        ) return

        _connectionState.value = ConnectionState.CONNECTING

        // Always ensure callback loop is running before connecting
        ensureCallbackLoop()

        withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to Steam CM servers...")
            client.connect()
        }
    }

    /**
     * Wait until the client is logged in, with a timeout.
     * Returns true if logged in, false if timed out.
     */
    suspend fun awaitLoggedIn(timeoutMs: Long = 30_000): Boolean {
        return try {
            withTimeout(timeoutMs) {
                connectionState.first { it == ConnectionState.LOGGED_IN }
                true
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    fun onConnected() {
        Log.d(TAG, "Connected to Steam")
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onLoggedIn() {
        Log.d(TAG, "Logged in to Steam")
        _connectionState.value = ConnectionState.LOGGED_IN
    }

    fun onDisconnected(userInitiated: Boolean) {
        Log.d(TAG, "Disconnected from Steam (userInitiated=$userInitiated, currentState=${_connectionState.value})")
        // Don't reset state if we're currently CONNECTING — JavaSteam's client.connect()
        // internally calls disconnect() which triggers this callback, and resetting to
        // DISCONNECTED would cause a reconnection cascade.
        if (_connectionState.value != ConnectionState.CONNECTING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * User-initiated disconnect (logout).
     */
    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        try {
            client.disconnect()
        } catch (_: Exception) {
            // Ignore disconnect errors
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun destroy() {
        isRunning.set(false)
        callbackJob?.cancel()
        callbackJob = null
        callbackScope.cancel()
        try {
            client.disconnect()
        } catch (_: Exception) {}
    }
}
