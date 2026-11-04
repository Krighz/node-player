package com.nodeplayer.network.test

import com.nodeplayer.network.crypto.KeyManager
import com.nodeplayer.network.crypto.NodeDeviceIdentity
import com.nodeplayer.network.pairing.DevicePermissions
import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.pairing.PairedDevice
import com.nodeplayer.network.protocol.*
import com.nodeplayer.network.transport.NodeClient
import com.nodeplayer.network.transport.NodeConnection
import com.nodeplayer.network.transport.NodeServer
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Headless Test Peer for automated testing and fault injection of Node Connect.
 * Runs in-process or via CLI without any GUI.
 */
class HeadlessTestPeer(
    val deviceName: String,
    val platform: String = "TestPeer"
) {
    val peerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val identity: NodeDeviceIdentity = KeyManager.generateIdentity(commonName = deviceName)
    val registryFile = File.createTempFile("node_test_reg_", ".txt").apply { deleteOnExit() }
    val registry = DeviceRegistry(registryFile)

    var server: NodeServer? = null
    val activeConnections = CopyOnWriteArrayList<NodeConnection>()

    val receivedControlCommands = CopyOnWriteArrayList<ControlCommandMessage>()
    val receivedAudioChunks = CopyOnWriteArrayList<ByteArray>()
    val receivedAudioChunkCount = AtomicInteger(0)

    // Optional custom frame listener for service integration
    var onFrameReceived: (suspend (NodeFraming.Frame, NodeConnection) -> Boolean)? = null

    // Fault Injection Flags
    var dropProbability: Float = 0.0f
    var artificialDelayMs: Long = 0L

    fun startServer(port: Int = 0): Int {
        val s = NodeServer(
            identity = identity,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) },
            port = port,
            scope = peerScope
        )
        s.start { conn ->
            activeConnections.add(conn)
            setupFrameHandling(conn)
        }
        server = s
        return s.activePort
    }

    fun trustPeer(peer: HeadlessTestPeer, permissions: DevicePermissions = DevicePermissions()) {
        val paired = PairedDevice(
            deviceId = peer.identity.deviceId,
            deviceName = peer.deviceName,
            platform = peer.platform,
            certificateFingerprintSha256 = peer.identity.certificateFingerprintSha256,
            permissions = permissions
        )
        registry.registerDevice(paired)
    }

    suspend fun connectTo(host: String, port: Int): NodeConnection {
        val client = NodeClient(
            identity = identity,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) },
            scope = peerScope
        )
        val conn = client.connect(host, port)
        activeConnections.add(conn)
        setupFrameHandling(conn)
        return conn
    }

    private fun setupFrameHandling(conn: NodeConnection) {
        peerScope.launch {
            for (frame in conn.incomingFrames) {
                // Fault Injection: Drop simulation
                if (dropProbability > 0f && kotlin.random.Random.nextFloat() < dropProbability) {
                    continue
                }

                // Fault Injection: Delay simulation
                if (artificialDelayMs > 0L) {
                    delay(artificialDelayMs)
                }

                val handled = onFrameReceived?.invoke(frame, conn) ?: false
                if (!handled) {
                    when (frame.messageType) {
                        NodeMessageTypes.CONTROL_CMD -> {
                            val cmd = ControlCommandMessage.fromBytes(frame.payload)
                            receivedControlCommands.add(cmd)
                        }
                        NodeMessageTypes.CAST_PCM_CHUNK -> {
                            receivedAudioChunks.add(frame.payload)
                            receivedAudioChunkCount.incrementAndGet()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        for (c in activeConnections) {
            c.close()
        }
        activeConnections.clear()
        server?.stop()
        peerScope.cancel()
        registryFile.delete()
    }
}
