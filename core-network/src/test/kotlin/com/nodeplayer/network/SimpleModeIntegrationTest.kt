package com.nodeplayer.network

import com.nodeplayer.network.protocol.ControlAction
import com.nodeplayer.network.protocol.ControlCommandMessage
import com.nodeplayer.network.protocol.NodeMessageTypes
import com.nodeplayer.network.test.HeadlessTestPeer
import com.nodeplayer.network.transport.SecurityLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleModeIntegrationTest {

    private var peerA: HeadlessTestPeer? = null
    private var peerB: HeadlessTestPeer? = null

    @After
    fun tearDown() {
        peerA?.server?.stop()
        peerB?.server?.stop()
        peerA?.activeConnections?.forEach { it.close() }
        peerB?.activeConnections?.forEach { it.close() }
    }

    @Test
    fun testSimpleModeEncryptedTlsHandshakeAndControl() = runBlocking {
        // Peer A and Peer B have NO upfront cert pinning or pre-shared PIN
        val a = HeadlessTestPeer("PC-Desktop", "Windows")
        val b = HeadlessTestPeer("Phone-Android", "Android")
        peerA = a
        peerB = b

        // Start Peer A server in SIMPLE security mode (unauthenticated TLS)
        val portA = a.startServer(0, securityLevel = SecurityLevel.SIMPLE)
        assertTrue(portA > 0)

        // Peer B connects to Peer A directly in SIMPLE security mode
        val connB = b.connectTo("127.0.0.1", portA, securityLevel = SecurityLevel.SIMPLE)
        assertTrue(connB.isOpen)

        delay(100)
        assertEquals(1, a.activeConnections.size)

        // Peer B sends a PLAY command over the unpinned connection
        val cmd = ControlCommandMessage(
            action = ControlAction.PLAY,
            seekPositionMs = 5000L,
            volume = 0.9f
        )
        connB.sendPayload(NodeMessageTypes.CONTROL_CMD, cmd.toBytes())

        delay(150)
        assertEquals(1, a.receivedControlCommands.size)
        val received = a.receivedControlCommands[0]
        assertEquals(ControlAction.PLAY, received.action)
        assertEquals(5000L, received.seekPositionMs)
        assertEquals(0.9f, received.volume, 0.001f)

        connB.close()
    }
}
