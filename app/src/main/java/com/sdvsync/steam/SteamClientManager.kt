package com.sdvsync.steam

import android.content.Context
import com.sdvsync.logging.AppLogger
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.discovery.FileServerListProvider
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.discovery.SmartCMServerList
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.SteamGameServer
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer
import `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.SteamScreenshots
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import java.io.File
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGED_IN,
    DISCONNECTING
}

class SteamClientManager(context: Context, sessionStore: SteamSessionStore) {

    companion object {
        private const val TAG = "SteamClientManager"
        private val PROTOCOL_TYPES = EnumSet.of(ProtocolTypes.WEB_SOCKET)
    }

    private val configuration: SteamConfiguration
    val client: SteamClient
    val callbackMgr: CallbackManager
    private var callbackJob: Job? = null
    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var fallbackServersPopulated = false

    val user: SteamUser
        get() = client.getHandler(SteamUser::class.java)
            ?: throw IllegalStateException("SteamUser handler not available")

    val cloud: SteamCloud
        get() = client.getHandler(SteamCloud::class.java)
            ?: throw IllegalStateException("SteamCloud handler not available")

    val apps: SteamApps
        get() = client.getHandler(SteamApps::class.java)
            ?: throw IllegalStateException("SteamApps handler not available")

    private val _licenses = MutableStateFlow<List<License>>(emptyList())
    val licenses: StateFlow<List<License>> = _licenses.asStateFlow()

    fun updateLicenses(licenseList: List<License>) {
        _licenses.value = licenseList
    }

    val isLoggedIn: Boolean
        get() = _connectionState.value == ConnectionState.LOGGED_IN

    init {
        val serverListFile = File(context.filesDir, "steam_server_list.bin")
        val savedCellId = sessionStore.cellId

        configuration = SteamConfiguration.create { builder ->
            builder.withProtocolTypes(PROTOCOL_TYPES)
            builder.withServerListProvider(FileServerListProvider(serverListFile))
            builder.withConnectionTimeout(30_000L)
            if (savedCellId > 0) {
                builder.withCellID(savedCellId)
            }
            builder.withHttpClient(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .pingInterval(15, TimeUnit.SECONDS)
                    .build()
            )
        }

        client = SteamClient(configuration).apply {
            removeHandler(SteamGameServer::class.java)
            removeHandler(SteamMasterServer::class.java)
            removeHandler(SteamWorkshop::class.java)
            removeHandler(SteamScreenshots::class.java)
        }

        callbackMgr = CallbackManager(client)

        AppLogger.d(
            TAG,
            "Initialized with WebSocket-only, FileServerListProvider, " +
                "cellId=$savedCellId"
        )
    }

    private fun ensureCallbackLoop() {
        if (callbackJob?.isCompleted == true) {
            AppLogger.w(TAG, "Callback loop had stopped, will restart")
            isRunning.set(false)
        }

        if (isRunning.getAndSet(true)) return

        AppLogger.d(TAG, "Starting callback loop")
        callbackJob = callbackScope.launch {
            while (isRunning.get()) {
                try {
                    callbackMgr.runWaitCallbacks(1000)
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                    AppLogger.w(TAG, "Callback loop caught ${e::class.simpleName}: ${e.message}")
                }
            }
            AppLogger.d(TAG, "Callback loop stopped")
        }
    }

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.LOGGED_IN
        ) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        ensureCallbackLoop()

        withContext(Dispatchers.IO) {
            ensureFallbackServers()
            AppLogger.d(TAG, "Connecting to Steam CM servers...")
            client.connect()
            AppLogger.d(TAG, "client.connect() returned")
        }
    }

    private fun ensureFallbackServers() {
        if (fallbackServersPopulated) return

        val endpoints = configuration.serverList.getAllEndPoints()
        if (endpoints.isNotEmpty()) {
            AppLogger.d(TAG, "Server list has ${endpoints.size} servers from provider")
            fallbackServersPopulated = true
            return
        }

        AppLogger.d(TAG, "Server list empty, pre-populating with fallback servers")
        configuration.serverList.replaceList(
            listOfNotNull(
                ServerRecord.createWebSocketServer(SmartCMServerList.defaultServerWebSocket),
                ServerRecord.tryCreateSocketServer(SmartCMServerList.defaultServerNetFilter)
            ),
            writeProvider = false,
            Instant.now()
        )
        fallbackServersPopulated = true
    }

    suspend fun reconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        connect()
    }

    suspend fun awaitLoggedIn(timeoutMs: Long = 30_000): Boolean = try {
        withTimeout(timeoutMs) {
            connectionState.first { it == ConnectionState.LOGGED_IN }
            true
        }
    } catch (_: TimeoutCancellationException) {
        false
    }

    fun onConnected() {
        AppLogger.d(TAG, "Connected to Steam")
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onLoggedIn() {
        AppLogger.d(TAG, "Logged in to Steam")
        _connectionState.value = ConnectionState.LOGGED_IN
    }

    fun onDisconnected(userInitiated: Boolean) {
        AppLogger.d(
            TAG,
            "Disconnected from Steam (userInitiated=$userInitiated, currentState=${_connectionState.value})"
        )
        if (_connectionState.value != ConnectionState.CONNECTING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        try {
            client.disconnect()
        } catch (_: Exception) {
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
