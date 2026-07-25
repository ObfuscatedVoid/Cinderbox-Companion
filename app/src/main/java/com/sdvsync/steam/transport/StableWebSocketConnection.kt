/*
 * Adapted from JavaSteam's WebSocketConnection.
 *
 * MIT License
 *
 * Copyright (c) 2018 Long Tran
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.sdvsync.steam.transport

import `in`.dragonbra.javasteam.networking.steam3.Connection
import `in`.dragonbra.javasteam.networking.steam3.NetMsgEventArgs
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal class StableWebSocketConnection : Connection() {
    companion object {
        private val logger = LogManager.getLogger<StableWebSocketConnection>()
        private val PING_INTERVAL = 5.toDuration(DurationUnit.SECONDS)
        private val UNSPECIFIED_ADDRESS = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleLock = Any()
    private var lifecycleGeneration = 0L
    private var disconnecting = false

    @Volatile
    private var client: HttpClient? = null

    @Volatile
    private var session: WebSocketSession? = null

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var endpoint: InetSocketAddress? = null

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        val generation = synchronized(lifecycleLock) {
            disconnecting = false
            ++lifecycleGeneration
        }

        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            logger.debug("Trying stable WebSocket connection to ${endPoint.hostName}:${endPoint.port}")
            endpoint = endPoint

            try {
                val newClient = HttpClient(CIO) {
                    install(WebSockets) {
                        pingInterval = PING_INTERVAL
                    }
                }
                if (!publishClient(generation, newClient)) {
                    newClient.close()
                    return@launch
                }

                val newSession = withTimeout(timeout.toLong().coerceAtLeast(1L)) {
                    newClient.webSocketSession {
                        url {
                            host = endPoint.hostName
                            port = endPoint.port
                            protocol = URLProtocol.WSS
                            path("cmsocket/")
                        }
                    }
                }
                if (!publishConnectedSession(generation, newClient, newSession)) {
                    newSession.close()
                    newClient.close()
                    return@launch
                }

                logger.debug("Stable WebSocket connected to ${endPoint.hostName}:${endPoint.port}")

                // Steam Guard can legitimately produce no CM payload while the user approves it.
                // Ktor's ping/pong timeout owns liveness instead of a payload-inactivity watchdog.
                newSession.incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Binary -> onNetMsgReceived(
                            NetMsgEventArgs(frame.readBytes(), currentEndPoint)
                        )

                        is Frame.Close -> disconnectInternal(userInitiated = false)
                        is Frame.Ping -> logger.debug("Received WebSocket ping")
                        is Frame.Pong -> logger.debug("Received WebSocket pong")
                        is Frame.Text -> logger.debug(
                            "Received WebSocket text frame (${frame.data.size} bytes)"
                        )
                    }
                }

                disconnectInternal(userInitiated = false, expectedGeneration = generation)
            } catch (e: TimeoutCancellationException) {
                logger.error("Stable WebSocket connection timed out", e)
                disconnectInternal(userInitiated = false, expectedGeneration = generation)
            } catch (e: CancellationException) {
                if (isCurrentGeneration(generation)) {
                    disconnectInternal(userInitiated = false, expectedGeneration = generation)
                }
                throw e
            } catch (e: Exception) {
                logger.error("Stable WebSocket connection failed", e)
                disconnectInternal(userInitiated = false, expectedGeneration = generation)
            }
        }

        val shouldStart = synchronized(lifecycleLock) {
            if (disconnecting || lifecycleGeneration != generation) {
                false
            } else {
                connectionJob = newJob
                true
            }
        }
        if (shouldStart) {
            newJob.start()
        } else {
            newJob.cancel()
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        disconnectInternal(userInitiated)
    }

    private fun disconnectInternal(userInitiated: Boolean, expectedGeneration: Long? = null) {
        val resources = synchronized(lifecycleLock) {
            if ((expectedGeneration != null && expectedGeneration != lifecycleGeneration) || disconnecting) {
                return
            }

            disconnecting = true
            lifecycleGeneration++
            TransportResources(connectionJob, session, client).also {
                connectionJob = null
                session = null
                client = null
            }
        }
        resources.job?.cancel()

        scope.launch {
            try {
                resources.session?.close()
            } catch (e: Exception) {
                logger.debug("Error closing stable WebSocket session: ${e.message}")
            }
            try {
                resources.client?.close()
            } catch (e: Exception) {
                logger.debug("Error closing stable WebSocket client: ${e.message}")
            }

            resources.job?.join()
            onDisconnected(userInitiated)
        }
    }

    override fun send(data: ByteArray) {
        val currentSession = synchronized(lifecycleLock) {
            session.takeUnless { disconnecting }
        }
        scope.launch {
            try {
                currentSession?.send(Frame.Binary(fin = true, data = data))
            } catch (e: Exception) {
                logger.error("Stable WebSocket send failed", e)
                disconnectInternal(userInitiated = false)
            }
        }
    }

    override fun getLocalIP(): InetAddress = UNSPECIFIED_ADDRESS

    override fun getCurrentEndPoint(): InetSocketAddress? = endpoint

    override fun getProtocolTypes(): ProtocolTypes = ProtocolTypes.WEB_SOCKET

    private fun publishClient(generation: Long, newClient: HttpClient): Boolean = synchronized(lifecycleLock) {
        if (disconnecting || lifecycleGeneration != generation) {
            false
        } else {
            client = newClient
            true
        }
    }

    private fun publishConnectedSession(
        generation: Long,
        newClient: HttpClient,
        newSession: WebSocketSession
    ): Boolean = synchronized(lifecycleLock) {
        if (disconnecting || lifecycleGeneration != generation || client !== newClient) {
            false
        } else {
            session = newSession
            onConnected()
            true
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean = synchronized(lifecycleLock) {
        !disconnecting && lifecycleGeneration == generation
    }

    private data class TransportResources(val job: Job?, val session: WebSocketSession?, val client: HttpClient?)
}
