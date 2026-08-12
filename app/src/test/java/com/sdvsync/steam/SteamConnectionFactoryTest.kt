package com.sdvsync.steam

import com.sdvsync.steam.transport.StableWebSocketConnection
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamConnectionFactoryTest {
    @Test
    fun `Steam supports TCP with stable WebSocket fallback`() {
        assertTrue(steamProtocolTypes.contains(ProtocolTypes.TCP))
        assertTrue(steamProtocolTypes.contains(ProtocolTypes.WEB_SOCKET))
    }

    @Test
    fun `websocket connections use fresh stable transport instances`() {
        val configuration = SteamConfiguration.createDefault()
        val protocols = EnumSet.of(ProtocolTypes.WEB_SOCKET)

        val first = stableSteamConnectionFactory.createConnection(configuration, protocols)
        val second = stableSteamConnectionFactory.createConnection(configuration, protocols)

        assertTrue(first is StableWebSocketConnection)
        assertTrue(second is StableWebSocketConnection)
        assertNotSame(first, second)
    }

    @Test
    fun `non websocket connections retain JavaSteam transport support`() {
        val configuration = SteamConfiguration.createDefault()
        val protocols = EnumSet.of(ProtocolTypes.TCP)

        val connection = stableSteamConnectionFactory.createConnection(configuration, protocols)

        assertTrue(connection?.protocolTypes == ProtocolTypes.TCP)
    }

    @Test
    fun `mixed protocol candidates prefer TCP`() {
        val configuration = SteamConfiguration.createDefault()
        val protocols = EnumSet.of(ProtocolTypes.TCP, ProtocolTypes.WEB_SOCKET)

        val connection = stableSteamConnectionFactory.createConnection(configuration, protocols)

        assertTrue(connection !is StableWebSocketConnection)
        assertTrue(connection?.protocolTypes == ProtocolTypes.TCP)
    }

    @Test
    fun `connection plan falls back from TCP to websocket once`() {
        val webSocketOne = ServerRecord.createServer("wss-one.example", 443, ProtocolTypes.WEB_SOCKET)
        val webSocketTwo = ServerRecord.createServer("wss-two.example", 443, ProtocolTypes.WEB_SOCKET)
        val tcpOne = ServerRecord.createServer("tcp-one.example", 27017, ProtocolTypes.TCP)
        val tcpTwo = ServerRecord.createServer("tcp-two.example", 27017, ProtocolTypes.TCP)
        val plan = SteamConnectionPlan()

        assertEquals(tcpOne, plan.begin(listOf(tcpOne, tcpTwo, webSocketOne, webSocketTwo)))
        assertEquals(webSocketOne, plan.retry())
        assertNull(plan.retry())
    }

    @Test
    fun `missing protocol receives a default fallback candidate`() {
        val tcp = ServerRecord.createServer("tcp.example", 27017, ProtocolTypes.TCP)
        val webSocket = ServerRecord.createServer("wss.example", 443, ProtocolTypes.WEB_SOCKET)
        val defaultTcp = ServerRecord.createServer("default-tcp.example", 27017, ProtocolTypes.TCP)
        val defaultWebSocket = ServerRecord.createServer(
            "default-wss.example",
            443,
            ProtocolTypes.WEB_SOCKET
        )

        assertEquals(
            listOf(tcp, defaultWebSocket),
            buildSteamConnectionCandidates(listOf(tcp), defaultTcp, defaultWebSocket)
        )
        assertEquals(
            listOf(defaultTcp, webSocket),
            buildSteamConnectionCandidates(listOf(webSocket), defaultTcp, defaultWebSocket)
        )
    }

    @Test
    fun `clearing a connection plan prevents a queued retry`() {
        val tcp = ServerRecord.createServer("tcp.example", 27017, ProtocolTypes.TCP)
        val webSocket = ServerRecord.createServer("wss.example", 443, ProtocolTypes.WEB_SOCKET)
        val plan = SteamConnectionPlan()

        assertEquals(tcp, plan.begin(listOf(tcp, webSocket)))
        plan.clear()

        assertNull(plan.retry())
    }

    @Test
    fun `disconnect latch waits for the matching callback acknowledgement`() {
        val latch = TransportDisconnectLatch()

        latch.expect()

        assertTrue(latch.isPending)
        latch.acknowledge()
        assertFalse(latch.isPending)
    }
}
