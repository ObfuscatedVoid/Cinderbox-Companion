package com.sdvsync.steam

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.CMClient
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGED_IN,
    DISCONNECTING
}

class SteamClientManager {

    private var steamClient: SteamClient? = null
    private var callbackManager: CallbackManager? = null
    private var callbackJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val isRunning = AtomicBoolean(false)

    val client: SteamClient
        get() = steamClient ?: throw IllegalStateException("Steam client not initialized")

    val user: SteamUser
        get() = client.getHandler(SteamUser::class.java)
            ?: throw IllegalStateException("SteamUser handler not available")

    val cloud: SteamCloud
        get() = client.getHandler(SteamCloud::class.java)
            ?: throw IllegalStateException("SteamCloud handler not available")

    val callbackMgr: CallbackManager
        get() = callbackManager ?: throw IllegalStateException("Callback manager not initialized")

    val isLoggedIn: Boolean
        get() = _connectionState.value == ConnectionState.LOGGED_IN

    fun initialize() {
        if (steamClient != null) return

        steamClient = SteamClient().also { client ->
            callbackManager = CallbackManager(client)
        }
    }

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.LOGGED_IN
        ) return

        initialize()
        _connectionState.value = ConnectionState.CONNECTING

        withContext(Dispatchers.IO) {
            client.connect()
            startCallbackLoop()
        }
    }

    fun onConnected() {
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onLoggedIn() {
        _connectionState.value = ConnectionState.LOGGED_IN
    }

    fun onDisconnected() {
        _connectionState.value = ConnectionState.DISCONNECTED
        stopCallbackLoop()
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        stopCallbackLoop()
        try {
            steamClient?.disconnect()
        } catch (_: Exception) {
            // Ignore disconnect errors
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun startCallbackLoop() {
        if (isRunning.getAndSet(true)) return

        callbackJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning.get()) {
                try {
                    callbackManager?.runWaitCallbacks(1000)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Log but don't crash on callback errors
                }
            }
        }
    }

    private fun stopCallbackLoop() {
        isRunning.set(false)
        callbackJob?.cancel()
        callbackJob = null
    }

    fun destroy() {
        disconnect()
        steamClient = null
        callbackManager = null
    }
}
