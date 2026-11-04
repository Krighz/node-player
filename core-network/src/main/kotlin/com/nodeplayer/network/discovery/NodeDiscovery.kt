package com.nodeplayer.network.discovery

import com.nodeplayer.common.logging.NodeLog
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
    val candidateAddresses: List<String> = listOf(hostAddress),
    val discoveryMethod: String = "Broadcast",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Multi-Method Local Network Discovery for Node Connect.
 * Supports:
 *  1. Subnet & Global UDP Broadcast Beacons (255.255.255.255 + interface broadcasts : 54821)
 *  2. Multicast UDP (239.255.77.88 : 54821)
 *  3. Hotspot Gateway Probing (probes default gateway)
 *  4. Parallel Subnet Sweep (/24 fallback)
 *  5. Manual IP & USB ADB link
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
        const val ANNOUNCE_INTERVAL_MS = 2500L
    }

    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredNodePeer>()
    private var announceJob: Job? = null
    private var listenJob: Job? = null
    private var multicastSocket: MulticastSocket? = null
    private var datagramSocket: DatagramSocket? = null

    fun getLocalCandidateIps(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ips
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        ips.add(addr.hostAddress)
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/discovery", "Error enumerating local IPs: ${e.message}")
        }
        return ips.distinct()
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val broadcasts = mutableListOf<InetAddress>()
        try {
            // Add global broadcast
            broadcasts.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return broadcasts
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast
                    if (bcast != null) {
                        broadcasts.add(bcast)
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/discovery", "Error getting broadcast addresses: ${e.message}")
        }
        return broadcasts.distinct()
    }

    fun start(onPeerFoundOrUpdated: (DiscoveredNodePeer) -> Unit) {
        val candidateIps = getLocalCandidateIps()
        NodeLog.i(
            "nodeconnect/discovery",
            "Starting discovery for '$localDeviceName' on port $localPort. Candidate IPs: $candidateIps"
        )

        // 1. Unified UDP Listener (Multicast + Broadcast)
        listenJob = scope.launch(Dispatchers.IO) {
            val group = try { InetAddress.getByName(MULTICAST_GROUP) } catch (_: Exception) { null }
            try {
                val socket = MulticastSocket(DISCOVERY_PORT)
                socket.reuseAddress = true
                if (group != null) {
                    try {
                        socket.joinGroup(group)
                    } catch (e: Exception) {
                        NodeLog.d("nodeconnect/discovery", "Multicast joinGroup skipped or not supported: ${e.message}")
                    }
                }
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
                            val existing = discoveredPeers[peer.deviceId]
                            val mergedCandidates = (existing?.candidateAddresses.orEmpty() + peer.candidateAddresses).distinct()
                            val updated = peer.copy(candidateAddresses = mergedCandidates)
                            discoveredPeers[peer.deviceId] = updated
                            NodeLog.i(
                                "nodeconnect/discovery",
                                "Discovered peer '${updated.deviceName}' (${updated.deviceId}) at ${updated.hostAddress}:${updated.port} via ${updated.discoveryMethod}"
                            )
                            onPeerFoundOrUpdated(updated)
                        }
                    } catch (e: Exception) {
                        if (!socket.isClosed) {
                            NodeLog.v("nodeconnect/discovery", "UDP receive loop notice: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                NodeLog.e("nodeconnect/discovery", "Failed to bind UDP discovery listener on port $DISCOVERY_PORT: ${e.message}", e)
            }
        }

        // 2. Multi-Destination UDP Announcement Loop (Every 2.5s)
        announceJob = scope.launch(Dispatchers.IO) {
            val payload = buildAnnouncement()
            val socket = DatagramSocket()
            socket.broadcast = true
            datagramSocket = socket

            try {
                val group = try { InetAddress.getByName(MULTICAST_GROUP) } catch (_: Exception) { null }
                while (isActive) {
                    val targets = getBroadcastAddresses()
                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(payload, payload.size, target, DISCOVERY_PORT)
                            socket.send(packet)
                        } catch (e: Exception) {
                            NodeLog.v("nodeconnect/discovery", "Broadcast send to $target failed: ${e.message}")
                        }
                    }
                    if (group != null) {
                        try {
                            val mcastPacket = DatagramPacket(payload, payload.size, group, DISCOVERY_PORT)
                            socket.send(mcastPacket)
                        } catch (_: Exception) {}
                    }
                    delay(ANNOUNCE_INTERVAL_MS)
                }
            } catch (e: Exception) {
                if (isActive) {
                    NodeLog.w("nodeconnect/discovery", "Announcement sender loop error: ${e.message}")
                }
            } finally {
                socket.close()
            }
        }
    }

    /**
     * Probes the default gateway on local subnets (Crucial when connected to phone hotspot).
     */
    fun probeGateway(onPeerFound: (DiscoveredNodePeer) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val localIps = getLocalCandidateIps()
            val gatewayCandidates = localIps.mapNotNull { ip ->
                val idx = ip.lastIndexOf('.')
                if (idx != -1) ip.substring(0, idx) + ".1" else null
            }.distinct()

            NodeLog.i("nodeconnect/discovery", "Probing default gateway candidates: $gatewayCandidates")
            for (gw in gatewayCandidates) {
                for (p in localPort..(localPort + 5)) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(gw, p), 300)
                        socket.close()
                        NodeLog.i("nodeconnect/discovery", "Gateway probe succeeded at $gw:$p")
                        val peer = DiscoveredNodePeer(
                            deviceId = "gw_$gw",
                            deviceName = "Hotspot Host ($gw)",
                            platform = "Mobile/Gateway",
                            hostAddress = gw,
                            port = p,
                            certificateFingerprintSha256 = "",
                            discoveryMethod = "Gateway Probe",
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        discoveredPeers[peer.deviceId] = peer
                        onPeerFound(peer)
                        break
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Sweeps the local /24 subnet in parallel with 200ms connection timeouts.
     */
    fun scanSubnet(onPeerFound: (DiscoveredNodePeer) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val localIps = getLocalCandidateIps()
            val subnets = localIps.mapNotNull { ip ->
                val idx = ip.lastIndexOf('.')
                if (idx != -1) ip.substring(0, idx) else null
            }.distinct()

            NodeLog.i("nodeconnect/discovery", "Starting parallel subnet scan across subnets: $subnets")

            for (subnet in subnets) {
                val jobs = (1..254).map { hostNum ->
                    val targetIp = "$subnet.$hostNum"
                    async(Dispatchers.IO) {
                        for (p in localPort..(localPort + 3)) {
                            try {
                                val s = Socket()
                                s.connect(InetSocketAddress(targetIp, p), 150)
                                s.close()
                                NodeLog.i("nodeconnect/discovery", "Subnet scan found active peer at $targetIp:$p")
                                val peer = DiscoveredNodePeer(
                                    deviceId = "peer_$targetIp",
                                    deviceName = "Node Peer ($targetIp)",
                                    platform = "Discovered",
                                    hostAddress = targetIp,
                                    port = p,
                                    certificateFingerprintSha256 = "",
                                    discoveryMethod = "Subnet Scan",
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                                discoveredPeers[peer.deviceId] = peer
                                onPeerFound(peer)
                                break
                            } catch (_: Exception) {}
                        }
                    }
                }
                jobs.awaitAll()
            }
            NodeLog.i("nodeconnect/discovery", "Parallel subnet scan complete.")
        }
    }

    fun addManualPeer(host: String, port: Int, onPeerFound: (DiscoveredNodePeer) -> Unit) {
        val peer = DiscoveredNodePeer(
            deviceId = "manual_${host.replace('.', '_')}",
            deviceName = "Manual Peer ($host)",
            platform = "Direct",
            hostAddress = host,
            port = port,
            certificateFingerprintSha256 = "",
            discoveryMethod = "Manual IP",
            lastSeenTimestamp = System.currentTimeMillis()
        )
        discoveredPeers[peer.deviceId] = peer
        onPeerFound(peer)
    }

    fun stop() {
        NodeLog.i("nodeconnect/discovery", "Stopping discovery...")
        announceJob?.cancel()
        listenJob?.cancel()
        try { multicastSocket?.close() } catch (_: Exception) {}
        try { datagramSocket?.close() } catch (_: Exception) {}
        discoveredPeers.clear()
    }

    fun getActivePeers(): List<DiscoveredNodePeer> {
        val now = System.currentTimeMillis()
        return discoveredPeers.values.filter { now - it.lastSeenTimestamp < 30_000L }
    }

    private fun buildAnnouncement(): ByteArray {
        val candidateIps = getLocalCandidateIps().joinToString(",")
        val line = listOf(
            "NODEDISCOVER_v2",
            localDeviceId,
            localDeviceName.replace("|", "_"),
            localPlatform,
            localPort.toString(),
            localFingerprint,
            "1", // canReceiveCast
            candidateIps
        ).joinToString("|")
        return line.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parseAnnouncement(text: String, senderIp: String): DiscoveredNodePeer? {
        val parts = text.split("|")
        if (parts.size < 7) return null
        val version = parts[0]
        if (version != "NODEDISCOVER_v1" && version != "NODEDISCOVER_v2") return null

        val candidateIps = if (parts.size >= 8 && parts[7].isNotBlank()) {
            (parts[7].split(",") + senderIp).distinct()
        } else {
            listOf(senderIp)
        }

        return DiscoveredNodePeer(
            deviceId = parts[1],
            deviceName = parts[2],
            platform = parts[3],
            hostAddress = senderIp,
            port = parts[4].toIntOrNull() ?: localPort,
            certificateFingerprintSha256 = parts[5],
            canReceiveCast = parts[6] == "1",
            candidateAddresses = candidateIps,
            discoveryMethod = if (version == "NODEDISCOVER_v2") "Broadcast" else "Legacy",
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }
}
