package com.sdvsync.steam

import android.content.Context
import com.sdvsync.logging.AppLogger
import com.sdvsync.steam.transport.StableWebSocketConnection
import `in`.dragonbra.javasteam.networking.steam3.IConnectionFactory
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
import java.util.ArrayDeque
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGED_IN,
    DISCONNECTING
}

internal val steamProtocolTypes: EnumSet<ProtocolTypes> =
    EnumSet.of(ProtocolTypes.TCP, ProtocolTypes.WEB_SOCKET)

internal val stableSteamConnectionFactory: IConnectionFactory = IConnectionFactory { configuration, protocolTypes ->
    when {
        protocolTypes.contains(ProtocolTypes.TCP) -> IConnectionFactory.DEFAULT.createConnection(
            configuration,
            EnumSet.of(ProtocolTypes.TCP)
        )

        protocolTypes.contains(ProtocolTypes.WEB_SOCKET) -> StableWebSocketConnection()
        else -> IConnectionFactory.DEFAULT.createConnection(configuration, protocolTypes)
    }
}

internal fun buildSteamConnectionCandidates(
    servers: List<ServerRecord>,
    defaultTcp: ServerRecord? = ServerRecord.tryCreateSocketServer(SmartCMServerList.defaultServerNetFilter),
    defaultWebSocket: ServerRecord = ServerRecord.createWebSocketServer(
        SmartCMServerList.defaultServerWebSocket
    )
): List<ServerRecord> {
    val uniqueServers = servers.distinct()
    val preferredTcp = uniqueServers.firstOrNull {
        it.protocolTypes.contains(ProtocolTypes.TCP)
    } ?: defaultTcp
    val preferredWebSocket = uniqueServers.firstOrNull {
        it != preferredTcp && it.protocolTypes.contains(ProtocolTypes.WEB_SOCKET)
    } ?: defaultWebSocket

    return buildList(2) {
        preferredTcp?.let(::add)
        add(preferredWebSocket)
    }.distinct()
}

internal class SteamConnectionPlan {
    private val lock = Any()
    private val remaining = ArrayDeque<ServerRecord>()

    fun begin(servers: List<ServerRecord>): ServerRecord? = synchronized(lock) {
        remaining.clear()
        remaining.addAll(buildSteamConnectionCandidates(servers))
        takeNext()
    }

    fun retry(): ServerRecord? = synchronized(lock) { takeNext() }

    fun hasRetry(): Boolean = synchronized(lock) { remaining.isNotEmpty() }

    fun clear() = synchronized(lock) { remaining.clear() }

    private fun takeNext(): ServerRecord? = if (remaining.isEmpty()) null else remaining.removeFirst()
}

internal class TransportDisconnectLatch {
    private val _pending = MutableStateFlow(false)

    val isPending: Boolean
        get() = _pending.value

    fun expect() {
        _pending.value = true
    }

    fun acknowledge() {
        _pending.value = false
    }

    suspend fun awaitAcknowledgement() {
        _pending.first { pending -> !pending }
    }
}

internal enum class DisconnectDisposition {
    TERMINAL,
    RETRY_PENDING
}

class SteamClientManager(context: Context, sessionStore: SteamSessionStore) {

    companion object {
        private const val TAG = "SteamClientManager"
        private const val TRANSPORT_SHUTDOWN_TIMEOUT_MS = 5_000L
        private const val TRANSPORT_SHUTDOWN_POLL_MS = 50L
    }

    private val configuration: SteamConfiguration
    val client: SteamClient
    val callbackMgr: CallbackManager
    private var callbackJob: Job? = null
    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectMutex = Mutex()
    private val connectionPlan = SteamConnectionPlan()
    private val transportLifecycleLock = Any()
    private val disconnectLatch = TransportDisconnectLatch()
    private var retryAwaitingClaim = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val isRunning = AtomicBoolean(false)
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
            builder.withProtocolTypes(steamProtocolTypes)
            builder.withConnectionFactory(stableSteamConnectionFactory)
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
            "Initialized with TCP-first and stable WebSocket fallback, FileServerListProvider, " +
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

    suspend fun connect() = connectMutex.withLock {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.LOGGED_IN
        ) {
            return@withLock
        }

        ensureCallbackLoop()

        withContext(Dispatchers.IO) {
            awaitPreviousTransportShutdown()

            val candidates = nextConnectionCandidates()
            currentCoroutineContext().ensureActive()
            val candidate = connectionPlan.begin(candidates)
                ?: throw IllegalStateException("No Steam CM servers are available")
            if (!startConnection(candidate)) {
                connectionPlan.clear()
                throw CancellationException("Steam connection was cancelled")
            }
            AppLogger.d(TAG, "client.connect() returned")
        }
    }

    private suspend fun awaitPreviousTransportShutdown() {
        val waitingForCallback = synchronized(transportLifecycleLock) {
            when {
                disconnectLatch.isPending -> true
                client.isDisconnected -> false
                else -> {
                    AppLogger.d(TAG, "Waiting for previous Steam transport to shut down")
                    _connectionState.value = ConnectionState.DISCONNECTING
                    disconnectLatch.expect()
                    try {
                        client.disconnect()
                        true
                    } catch (e: Exception) {
                        disconnectLatch.acknowledge()
                        _connectionState.value = ConnectionState.DISCONNECTED
                        throw e
                    }
                }
            }
        }
        if (!waitingForCallback) {
            return
        }

        withTimeout(TRANSPORT_SHUTDOWN_TIMEOUT_MS) {
            disconnectLatch.awaitAcknowledgement()
            while (!client.isDisconnected) {
                delay(TRANSPORT_SHUTDOWN_POLL_MS)
            }
        }
    }

    private fun startConnection(candidate: ServerRecord): Boolean = synchronized(transportLifecycleLock) {
        if (disconnectLatch.isPending || _connectionState.value == ConnectionState.DISCONNECTING) {
            return@synchronized false
        }

        _connectionState.value = ConnectionState.CONNECTING
        retryAwaitingClaim = false
        AppLogger.d(TAG, "Connecting to Steam CM via ${candidate.protocolTypes}")
        client.connect(candidate)
        true
    }

    private fun nextConnectionCandidates(): List<ServerRecord> {
        val endpoints = configuration.serverList.getAllEndPoints()
        if (endpoints.isEmpty()) {
            AppLogger.d(TAG, "Server list empty, pre-populating with fallback servers")
            configuration.serverList.replaceList(
                listOfNotNull(
                    ServerRecord.tryCreateSocketServer(SmartCMServerList.defaultServerNetFilter),
                    ServerRecord.createWebSocketServer(SmartCMServerList.defaultServerWebSocket)
                ),
                writeProvider = false,
                Instant.now()
            )
        } else {
            AppLogger.d(TAG, "Server list has ${endpoints.size} servers from provider")
        }

        return buildSteamConnectionCandidates(
            listOfNotNull(
                configuration.serverList.getNextServerCandidate(ProtocolTypes.TCP),
                configuration.serverList.getNextServerCandidate(ProtocolTypes.WEB_SOCKET)
            )
        )
    }

    suspend fun awaitLoggedIn(timeoutMs: Long = 30_000): Boolean = try {
        withTimeout(timeoutMs) {
            connectionState.first { it == ConnectionState.LOGGED_IN }
            true
        }
    } catch (_: TimeoutCancellationException) {
        false
    }

    fun onConnected() = synchronized(transportLifecycleLock) {
        if (_connectionState.value != ConnectionState.CONNECTING) {
            AppLogger.w(TAG, "Ignoring a late Steam connected callback")
            return@synchronized
        }

        AppLogger.d(TAG, "Connected to Steam")
        retryAwaitingClaim = false
        connectionPlan.clear()
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onLoggedIn() {
        AppLogger.d(TAG, "Logged in to Steam")
        _connectionState.value = ConnectionState.LOGGED_IN
    }

    internal fun onLoggedOff() = synchronized(transportLifecycleLock) {
        AppLogger.d(TAG, "Logged off Steam")
        if (_connectionState.value == ConnectionState.LOGGED_IN) {
            _connectionState.value = ConnectionState.CONNECTED
        }
    }

    internal fun onDisconnected(userInitiated: Boolean): DisconnectDisposition = synchronized(transportLifecycleLock) {
        disconnectLatch.acknowledge()
        AppLogger.d(
            TAG,
            "Disconnected from Steam (userInitiated=$userInitiated, currentState=${_connectionState.value})"
        )

        if (!userInitiated &&
            _connectionState.value == ConnectionState.CONNECTING &&
            connectionPlan.hasRetry()
        ) {
            retryAwaitingClaim = true
            return@synchronized DisconnectDisposition.RETRY_PENDING
        }

        retryAwaitingClaim = false
        connectionPlan.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        DisconnectDisposition.TERMINAL
    }

    internal fun retryPendingConnection(): Boolean = synchronized(transportLifecycleLock) {
        if (_connectionState.value != ConnectionState.CONNECTING ||
            disconnectLatch.isPending ||
            !client.isDisconnected
        ) {
            return@synchronized false
        }

        val nextCandidate = connectionPlan.retry() ?: return@synchronized false
        retryAwaitingClaim = false
        AppLogger.w(TAG, "CM connection failed; retrying via ${nextCandidate.protocolTypes}")
        client.connect(nextCandidate)
        true
    }

    internal fun abandonPendingRetry() = synchronized(transportLifecycleLock) {
        retryAwaitingClaim = false
        disconnectLatch.acknowledge()
        connectionPlan.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    internal fun cancelConnectingAttempt() = synchronized(transportLifecycleLock) {
        if (_connectionState.value == ConnectionState.CONNECTING) {
            disconnectLocked()
        }
    }

    fun disconnect() = synchronized(transportLifecycleLock) {
        disconnectLocked()
    }

    private fun disconnectLocked() {
        connectionPlan.clear()
        if (retryAwaitingClaim && client.isDisconnected) {
            retryAwaitingClaim = false
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        if (_connectionState.value == ConnectionState.DISCONNECTED && client.isDisconnected) {
            return
        }
        if (disconnectLatch.isPending) {
            _connectionState.value = ConnectionState.DISCONNECTING
            return
        }

        _connectionState.value = ConnectionState.DISCONNECTING
        retryAwaitingClaim = false
        disconnectLatch.expect()
        try {
            if (!client.isDisconnected) {
                client.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Steam disconnect failed: ${e.message}")
            disconnectLatch.acknowledge()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun destroy() {
        connectionPlan.clear()
        isRunning.set(false)
        callbackJob?.cancel()
        callbackJob = null
        callbackScope.cancel()
        try {
            client.disconnect()
        } catch (_: Exception) {}
    }
}
