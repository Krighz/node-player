package com.nodeplayer.network.discovery

import kotlinx.coroutines.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredNodePeer(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val hostAddress: String,
    val port: Int,
    val certificateFingerprintSha256: String,
    val canReceiveCast: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Local Network Discovery for Node Connect.
 * Uses local multicast UDP broadcast (239.255.77.88 : 54821) on Wi-Fi/LAN.
 * Zero external servers, zero telemetry.
 */
class NodeDiscovery(
    val localDeviceId: String,
    val localDeviceName: String,
    val localPlatform: String,
    val localPort: Int,
    val localFingerprint: String,
    private val scope: CoroutineScope
) {
    companion object {
        const val MULTICAST_GROUP = "239.255.77.88"
        const val DISCOVERY_PORT = 54821
        const val ANNOUNCE_INTERVAL_MS = 3000L
    }

    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredNodePeer>()
    private var announceJob: Job? = null
    private var listenJob: Job? = null
    private var multicastSocket: MulticastSocket? = null

    fun start(onPeerFoundOrUpdated: (DiscoveredNodePeer) -> Unit) {
        val group = InetAddress.getByName(MULTICAST_GROUP)

        // 1. Multicast Receiver Loop
        listenJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = MulticastSocket(DISCOVERY_PORT)
                socket.reuseAddress = true
                socket.joinGroup(group)
                multicastSocket = socket

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && !socket.isClosed) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val senderIp = packet.address.hostAddress

                        val peer = parseAnnouncement(text, senderIp)
                        if (peer != null && peer.deviceId != localDeviceId) {
                            discoveredPeers[peer.deviceId] = peer
                            onPeerFoundOrUpdated(peer)
                        }
                    } catch (e: Exception) {
                        // socket timeout or network flip
                    }
                }
            } catch (e: Exception) {
                // Multicast not supported on interface or permission blocked
            }
        }

        // 2. Multicast Announcement Loop (Every 3 seconds)
        announceJob = scope.launch(Dispatchers.IO) {
            val announcementPayload = buildAnnouncement()
            val socket = DatagramSocket()
            try {
                while (isActive) {
                    val packet = DatagramPacket(
                        announcementPayload,
                        announcementPayload.size,
                        group,
                        DISCOVERY_PORT
                    )
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        // Ignore broadcast send drop
                    }
                    delay(ANNOUNCE_INTERVAL_MS)
                }
            } finally {
                socket.close()
            }
        }
    }

    fun stop() {
        announceJob?.cancel()
        listenJob?.cancel()
        try {
            multicastSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        discoveredPeers.clear()
    }

    fun getActivePeers(): List<DiscoveredNodePeer> {
        val now = System.currentTimeMillis()
        // Filter out peers not seen in 15 seconds
        return discoveredPeers.values.filter { now - it.lastSeenTimestamp < 15_000L }
    }

    private fun buildAnnouncement(): ByteArray {
        val line = listOf(
            "NODEDISCOVER_v1",
            localDeviceId,
            localDeviceName.replace("|", "_"),
            localPlatform,
            localPort.toString(),
            localFingerprint,
            "1" // canReceiveCast
        ).joinToString("|")
        return line.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parseAnnouncement(text: String, senderIp: String): DiscoveredNodePeer? {
        val parts = text.split("|")
        if (parts.size < 7 || parts[0] != "NODEDISCOVER_v1") return null
        return DiscoveredNodePeer(
            deviceId = parts[1],
            deviceName = parts[2],
            platform = parts[3],
            hostAddress = senderIp,
            port = parts[4].toIntOrNull() ?: localPort,
            certificateFingerprintSha256 = parts[5],
            canReceiveCast = parts[6] == "1",
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }
}
