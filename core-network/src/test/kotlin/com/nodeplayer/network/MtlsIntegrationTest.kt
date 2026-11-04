package com.nodeplayer.network

import com.nodeplayer.network.protocol.ControlAction
import com.nodeplayer.network.protocol.ControlCommandMessage
import com.nodeplayer.network.protocol.NodeMessageTypes
import com.nodeplayer.network.test.HeadlessTestPeer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class MtlsIntegrationTest {

    private var peerA: HeadlessTestPeer? = null
    private var peerB: HeadlessTestPeer? = null
    private var peerUntrusted: HeadlessTestPeer? = null

    @After
    fun teardown() {
        peerA?.stop()
        peerB?.stop()
        peerUntrusted?.stop()
    }

    @Test
    fun testMutualTlsHandshakeAndControlCommand() = runBlocking {
        val a = HeadlessTestPeer("Node-PC", "Windows")
        val b = HeadlessTestPeer("Node-Phone", "Android")
        peerA = a
        peerB = b

        // Mutually trust certificates
        a.trustPeer(b)
        b.trustPeer(a)

        val port = a.startServer(0)
        assertTrue("Port should be assigned", port > 0)

        // Peer B connects to Peer A over mTLS
        val connB = b.connectTo("127.0.0.1", port)
        assertTrue("Connection should be open", connB.isOpen)

        delay(100)
        assertEquals("Server should have 1 active connection", 1, a.activeConnections.size)

        // Peer B sends a Play/Pause command to Peer A
        val cmd = ControlCommandMessage(action = ControlAction.PLAY, seekPositionMs = 12500L, volume = 0.85f)
        connB.sendPayload(NodeMessageTypes.CONTROL_CMD, cmd.toBytes())

        delay(150)
        assertEquals(1, a.receivedControlCommands.size)
        val received = a.receivedControlCommands[0]
        assertEquals(ControlAction.PLAY, received.action)
        assertEquals(12500L, received.seekPositionMs)
        assertEquals(0.85f, received.volume, 0.001f)

        // Peer B sends audio PCM frames
        val dummyPcm1 = ByteArray(1024) { (it % 127).toByte() }
        val dummyPcm2 = ByteArray(1024) { ((it + 1) % 127).toByte() }
        connB.sendPayload(NodeMessageTypes.CAST_PCM_CHUNK, dummyPcm1)
        connB.sendPayload(NodeMessageTypes.CAST_PCM_CHUNK, dummyPcm2)

        delay(150)
        assertEquals(2, a.receivedAudioChunkCount.get())
        assertArrayEquals(dummyPcm1, a.receivedAudioChunks[0])
        assertArrayEquals(dummyPcm2, a.receivedAudioChunks[1])

        connB.close()
        a.stop()
        b.stop()
    }

    @Test
    fun testUntrustedPeerIsRejectedImmediately() = runBlocking {
        val a = HeadlessTestPeer("Secure-Node-PC", "Windows")
        val rogue = HeadlessTestPeer("Rogue-Device", "Unknown")
        peerA = a
        peerUntrusted = rogue

        // Peer A does NOT trust Rogue-Device
        val port = a.startServer(0)

        var handshakeFailed = false
        try {
            rogue.connectTo("127.0.0.1", port)
        } catch (e: Exception) {
            handshakeFailed = true
        }

        assertTrue("Untrusted mTLS connection must be rejected during handshake", handshakeFailed)
        assertEquals("Peer A should have 0 accepted connections", 0, a.activeConnections.size)

        a.stop()
        rogue.stop()
    }
}
