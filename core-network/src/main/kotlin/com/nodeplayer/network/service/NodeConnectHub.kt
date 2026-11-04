package com.nodeplayer.network.service

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.repository.TrackRepository
import com.nodeplayer.network.crypto.NodeDeviceIdentity
import com.nodeplayer.network.crypto.SecureKeyStorage
import com.nodeplayer.network.discovery.DiscoveredNodePeer
import com.nodeplayer.network.discovery.NodeDiscovery
import com.nodeplayer.network.cast.ActiveCastSession
import com.nodeplayer.network.cast.JitterBuffer
import com.nodeplayer.network.cast.NodeConnectCastService
import com.nodeplayer.network.handoff.HandoffPayload
import com.nodeplayer.network.handoff.NodeConnectHandoffService
import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.pairing.PairedDevice
import com.nodeplayer.network.pairing.PairingManager
import com.nodeplayer.network.pairing.PairingSession
import com.nodeplayer.network.protocol.ControlStateEventMessage
import com.nodeplayer.network.protocol.HandshakeMessage
import com.nodeplayer.network.protocol.NodeFraming
import com.nodeplayer.network.protocol.NodeMessageTypes
import com.nodeplayer.network.protocol.PairingRequestMessage
import com.nodeplayer.network.protocol.PairingResponseMessage
import com.nodeplayer.network.pairing.DevicePermissions
import java.io.IOException
import com.nodeplayer.network.session.ExternalSessionService
import com.nodeplayer.network.session.PlatformMediaSessionBridge
import com.nodeplayer.network.sync.NodeConnectTransferService
import com.nodeplayer.network.sync.SyncEngine
import com.nodeplayer.network.transport.NodeClient
import com.nodeplayer.network.transport.NodeConnection
import com.nodeplayer.network.transport.NodeServer
import com.nodeplayer.network.transport.SecurityLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

enum class ConnectState {
    OFF,
    DISCOVERING,
    CONNECTED
}

/**
 * NodeConnectHub: Main coordination hub for Node Connect.
 * Integrates Discovery, Server, Client, Registry, Pairing, Library, Control, Cast, Handoff, Sync, and External Session planes.
 * Defaults to Simple Mode (unauthenticated TLS / zero PIN) with instant pairing and parallel multi-address connection.
 */
class NodeConnectHub(
    val keyStorage: SecureKeyStorage = SecureKeyStorage(),
    val registry: DeviceRegistry = DeviceRegistry(),
    val trackRepository: TrackRepository? = null,
    val localController: LocalPlayerController? = null,
    val onIncomingCastStream: ((ActiveCastSession, JitterBuffer) -> Unit)? = null,
    val onIncomingHandoff: ((HandoffPayload) -> Boolean)? = null,
    val platformMediaSessionBridge: PlatformMediaSessionBridge? = null,
    var onAllowPrompt: ((deviceName: String, deviceId: String, callback: (allow: Boolean, always: Boolean) -> Unit) -> Unit)? = null,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    val pairingManager = PairingManager(registry)
    val libraryService = NodeConnectLibraryService(trackRepository)
    val controlService = NodeConnectControlService(localController, registry)
    val mirrorService = NodeConnectMirrorService()
    val castService = NodeConnectCastService(
        registry = registry,
        onStreamStartedLocally = onIncomingCastStream
    )
    val handoffService = NodeConnectHandoffService(
        onHandoffReceived = onIncomingHandoff
    )
    val syncEngine = SyncEngine()
    val syncService = NodeConnectTransferService(syncEngine)
    val externalSessionService = ExternalSessionService(platformMediaSessionBridge, registry)

    var securityLevel: SecurityLevel = SecurityLevel.SIMPLE
    var encryptionEnabled: Boolean = true
    var trustAllOnNetwork: Boolean = false

    private var identity: NodeDeviceIdentity? = null
    private var server: NodeServer? = null
    private var discovery: NodeDiscovery? = null
    var localDeviceName: String = "Node Device"
        private set
    var localPlatform: String = "Unknown"
        private set

    val activeConnections = CopyOnWriteArrayList<NodeConnection>()

    private val _connectionState = MutableStateFlow(ConnectState.OFF)
    val connectionState: StateFlow<ConnectState> = _connectionState.asStateFlow()

    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredNodePeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredNodePeer>> = _discoveredPeers.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _activePairingSession = MutableStateFlow<PairingSession?>(null)
    val activePairingSession: StateFlow<PairingSession?> = _activePairingSession.asStateFlow()

    val activePort: Int get() = server?.activePort ?: NodeServer.DEFAULT_PORT
    val activeDeviceId: String? get() = identity?.deviceId

    @Synchronized
    fun start(deviceName: String, platform: String) {
        if (_connectionState.value != ConnectState.OFF) return

        localDeviceName = deviceName
        localPlatform = platform
        NodeLog.i("nodeconnect/hub", "Starting NodeConnectHub ($localDeviceName, $localPlatform)...")

        val id = keyStorage.getOrCreateIdentity(deviceName)
        identity = id

        refreshPairedDevices()

        // 1. Start Server
        val s = NodeServer(
            identity = id,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) || pairingManager.getCurrentSession() != null },
            port = NodeServer.DEFAULT_PORT,
            securityLevel = securityLevel,
            encryptionEnabled = encryptionEnabled,
            scope = scope
        )
        s.start { conn ->
            handleIncomingConnection(conn)
        }
        server = s

        // 2. Start Multi-Method Discovery
        val disc = NodeDiscovery(
            localDeviceId = id.deviceId,
            localDeviceName = deviceName,
            localPlatform = platform,
            localPort = s.activePort,
            localFingerprint = id.certificateFingerprintSha256,
            scope = scope
        )
        disc.start {
            _discoveredPeers.value = disc.getActivePeers()
        }
        discovery = disc

        _connectionState.value = ConnectState.DISCOVERING
        NodeLog.i("nodeconnect/hub", "NodeConnectHub active in DISCOVERING mode on port ${s.activePort}")
    }

    @Synchronized
    fun stop() {
        NodeLog.i("nodeconnect/hub", "Stopping NodeConnectHub...")
        discovery?.stop()
        discovery = null

        server?.stop()
        server = null

        pairingManager.cancelSession()
        _activePairingSession.value = null

        for (conn in activeConnections) {
            conn.close()
        }
        activeConnections.clear()

        _connectionState.value = ConnectState.OFF
        _connectedDeviceCount.value = 0
        _discoveredPeers.value = emptyList()
        NodeLog.i("nodeconnect/hub", "NodeConnectHub stopped.")
    }

    fun getLocalCandidateIps(): List<String> {
        return discovery?.getLocalCandidateIps().orEmpty()
    }

    fun triggerSubnetScan() {
        discovery?.scanSubnet {
            _discoveredPeers.value = discovery?.getActivePeers().orEmpty()
        }
    }

    fun triggerGatewayProbe() {
        discovery?.probeGateway {
            _discoveredPeers.value = discovery?.getActivePeers().orEmpty()
        }
    }

    fun addManualPeer(host: String, port: Int) {
        discovery?.addManualPeer(host, port) {
            _discoveredPeers.value = discovery?.getActivePeers().orEmpty()
        }
    }

    suspend fun testConnectionTo(host: String, port: Int): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 3000)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            socket.close()
            NodeLog.i("nodeconnect/doctor", "Ping test to $host:$port succeeded in ${latency}ms")
            Result.success(latency)
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/doctor", "Ping test to $host:$port failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Connects to a peer by trying all candidate addresses in parallel with a short timeout.
     * Keeps the first socket that connects and completes the Hello handshake.
     */
    suspend fun connectToPeer(peer: DiscoveredNodePeer): NodeConnection? = withContext(Dispatchers.IO) {
        val id = identity ?: return@withContext null
        val candidates = (peer.candidateAddresses + peer.hostAddress).distinct().filter { it.isNotBlank() }
        NodeLog.i("nodeconnect/connect", "Parallel connecting to peer '${peer.deviceName}' across candidates: $candidates (port: ${peer.port})")

        val client = NodeClient(
            identity = id,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) },
            securityLevel = securityLevel,
            encryptionEnabled = encryptionEnabled,
            scope = scope
        )

        // Try candidate addresses in parallel
        val deferredList = candidates.map { host ->
            async(Dispatchers.IO) {
                try {
                    client.connect(host, peer.port, timeoutMs = 3000)
                } catch (_: Exception) {
                    null
                }
            }
        }

        // Await the first responding connection
        var establishedConn: NodeConnection? = null
        for (deferred in deferredList) {
            val conn = deferred.await()
            if (conn != null && establishedConn == null) {
                establishedConn = conn
            } else {
                conn?.close()
            }
        }

        if (establishedConn == null) {
            NodeLog.w("nodeconnect/connect", "All candidate addresses failed for peer '${peer.deviceName}'")
            return@withContext null
        }

        val conn = establishedConn

        // Perform Client Hello handshake
        try {
            val hs = HandshakeMessage(
                deviceId = id.deviceId,
                deviceName = localDeviceName,
                platform = localPlatform,
                protocolVersion = 2
            )
            val hsFrame = NodeFraming.Frame(
                messageType = NodeMessageTypes.HANDSHAKE_REQ,
                sequenceId = 1L,
                payload = hs.toBytes()
            )
            conn.sendFrame(hsFrame)

            // Await Handshake Response
            val respFrame = withTimeoutOrNull(5000) {
                conn.incomingFrames.receive()
            }
            if (respFrame == null || respFrame.messageType != NodeMessageTypes.HANDSHAKE_RESP) {
                NodeLog.w("nodeconnect/connect", "Handshake timeout or invalid response from ${conn.peerHostAddress}")
                conn.close()
                return@withContext null
            }

            val peerHs = HandshakeMessage.fromBytes(respFrame.payload)
            conn.peerDeviceId = peerHs.deviceId
            NodeLog.i("nodeconnect/connect", "Handshake success! Connected to '${peerHs.deviceName}' (${peerHs.deviceId})")

            val pairedHost = PairedDevice(
                deviceId = peerHs.deviceId,
                deviceName = peerHs.deviceName,
                platform = peerHs.platform,
                certificateFingerprintSha256 = conn.peerFingerprint,
                permissions = DevicePermissions(),
                pairedTimestamp = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            registry.registerDevice(pairedHost)
            refreshPairedDevices()

            registerAndProcessConnection(conn)
            conn
        } catch (e: Exception) {
            NodeLog.e("nodeconnect/connect", "Exception during client handshake: ${e.message}", e)
            conn.close()
            null
        }
    }

    fun startPairingSession(): PairingSession? {
        val id = identity ?: return null
        val s = server ?: return null
        val session = pairingManager.startPairingSession(id.certificateFingerprintSha256, s.activePort)
        _activePairingSession.value = session
        return session
    }

    fun cancelPairingSession() {
        pairingManager.cancelSession()
        _activePairingSession.value = null
    }

    suspend fun pairWithHost(
        host: String,
        port: Int,
        pin: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val id = identity ?: return@withContext Result.failure(IllegalStateException("NodeConnect is not active"))
        try {
            val client = NodeClient(
                identity = id,
                isTrustedFingerprint = { _ -> true },
                securityLevel = securityLevel,
                encryptionEnabled = encryptionEnabled,
                scope = scope
            )
            val conn = client.connect(host, port, timeoutMs = 8000)

            val req = PairingRequestMessage(
                tokenOrPin = pin.trim(),
                deviceId = id.deviceId,
                deviceName = localDeviceName,
                platform = localPlatform
            )
            val reqFrame = NodeFraming.Frame(
                messageType = NodeMessageTypes.PAIRING_REQ,
                sequenceId = 1L,
                payload = req.toBytes()
            )
            conn.sendFrame(reqFrame)

            val respFrame = withTimeoutOrNull(8000) {
                conn.incomingFrames.receive()
            }
            if (respFrame == null || respFrame.messageType != NodeMessageTypes.PAIRING_RESP) {
                conn.close()
                return@withContext Result.failure(IOException("No response or invalid response from host ($host:$port)"))
            }

            val resp = PairingResponseMessage.fromBytes(respFrame.payload)
            if (!resp.success) {
                conn.close()
                return@withContext Result.failure(SecurityException(resp.message.ifEmpty { "PIN rejected by host" }))
            }

            val pairedHost = PairedDevice(
                deviceId = resp.serverDeviceId.ifEmpty { conn.peerDeviceId },
                deviceName = resp.serverDeviceName.ifEmpty { "Node Host ($host)" },
                platform = "Windows",
                certificateFingerprintSha256 = conn.peerFingerprint,
                permissions = DevicePermissions(),
                pairedTimestamp = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            registry.registerDevice(pairedHost)
            refreshPairedDevices()

            registerAndProcessConnection(conn)
            Result.success(pairedHost.deviceName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun revokeDevice(deviceId: String): Boolean {
        val res = registry.revokeDevice(deviceId)
        if (res) {
            refreshPairedDevices()
            for (conn in activeConnections) {
                if (conn.peerDeviceId == deviceId) {
                    conn.close()
                }
            }
        }
        return res
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = registry.getAllPairedDevices()
    }

    private fun registerAndProcessConnection(conn: NodeConnection) {
        scope.launch {
            try {
                // Register active connection
                activeConnections.add(conn)
                controlService.registerController(conn)
                updateConnectionStatus()
                NodeLog.i("nodeconnect/hub", "Peer ${conn.peerDeviceId} (${conn.peerHostAddress}) registered into active session")

                for (frame in conn.incomingFrames) {
                    val handled = libraryService.handleFrame(frame, conn) ||
                            controlService.handleFrame(frame, conn) ||
                            mirrorService.handleFrame(frame) ||
                            castService.handleFrame(frame, conn) ||
                            handoffService.handleFrame(frame, conn) ||
                            syncService.handleFrame(frame, conn) ||
                            externalSessionService.handleFrame(frame, conn)
                }
            } catch (e: Exception) {
                NodeLog.w("nodeconnect/hub", "Connection terminated for peer ${conn.peerDeviceId}: ${e.message}")
            } finally {
                activeConnections.remove(conn)
                controlService.unregisterController(conn)
                conn.close()
                updateConnectionStatus()
            }
        }
    }

    private fun handleIncomingConnection(conn: NodeConnection) {
        scope.launch {
            try {
                // If this is an incoming socket awaiting handshake:
                if (securityLevel == SecurityLevel.SIMPLE) {
                    handleSimpleIncomingHandshake(conn)
                } else {
                    handleStrictIncomingHandshake(conn)
                }

                registerAndProcessConnection(conn)
            } catch (e: Exception) {
                NodeLog.w("nodeconnect/hub", "Incoming connection rejected or handshake failed: ${e.message}")
                conn.close()
            }
        }
    }

    private suspend fun handleSimpleIncomingHandshake(conn: NodeConnection) {
        // In Simple Mode: read peer's HANDSHAKE_REQ or PAIRING_REQ
        val frame = withTimeoutOrNull(8000) {
            conn.incomingFrames.receive()
        } ?: throw IOException("Timeout waiting for peer handshake")

        if (frame.messageType == NodeMessageTypes.HANDSHAKE_REQ) {
            val req = HandshakeMessage.fromBytes(frame.payload)
            conn.peerDeviceId = req.deviceId
            NodeLog.i("nodeconnect/hub", "Received Handshake from '${req.deviceName}' (${req.deviceId}) on ${conn.peerHostAddress}")

            val isTrusted = registry.isTrustedDeviceId(req.deviceId) || trustAllOnNetwork

            if (!isTrusted) {
                // Trigger Allow prompt if callback provided
                val prompt = onAllowPrompt
                if (prompt != null) {
                    var allowed = false
                    val promptCompleted = CompletableDeferred<Boolean>()
                    prompt(req.deviceName, req.deviceId) { allow, always ->
                        allowed = allow
                        if (allow && always) {
                            val paired = PairedDevice(
                                deviceId = req.deviceId,
                                deviceName = req.deviceName,
                                platform = req.platform,
                                certificateFingerprintSha256 = conn.peerFingerprint,
                                permissions = DevicePermissions(),
                                pairedTimestamp = System.currentTimeMillis(),
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            registry.registerDevice(paired)
                            refreshPairedDevices()
                        }
                        promptCompleted.complete(allow)
                    }
                    val result = withTimeoutOrNull(20000) { promptCompleted.await() } ?: false
                    if (!result) {
                        conn.sendPayload(NodeMessageTypes.DISCONNECT, "Connection denied".toByteArray())
                        throw SecurityException("Connection rejected by user prompt")
                    }
                }
            }

            // Send Handshake response
            val resp = HandshakeMessage(
                deviceId = identity?.deviceId ?: "local_device",
                deviceName = localDeviceName,
                platform = localPlatform,
                protocolVersion = 2
            )
            val respFrame = NodeFraming.Frame(
                messageType = NodeMessageTypes.HANDSHAKE_RESP,
                sequenceId = frame.sequenceId,
                payload = resp.toBytes()
            )
            conn.sendFrame(respFrame)
            NodeLog.i("nodeconnect/hub", "Handshake accepted and responded to '${req.deviceName}'")

            val peerEntry = registry.getPairedDeviceById(req.deviceId) ?: PairedDevice(
                deviceId = req.deviceId,
                deviceName = req.deviceName,
                platform = req.platform,
                certificateFingerprintSha256 = conn.peerFingerprint,
                permissions = DevicePermissions(),
                pairedTimestamp = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            registry.registerDevice(peerEntry)
            refreshPairedDevices()
        }
    }

    private suspend fun handleStrictIncomingHandshake(conn: NodeConnection) {
        val isAlreadyTrusted = registry.isTrustedFingerprint(conn.peerFingerprint)
        if (!isAlreadyTrusted) {
            val frame = withTimeoutOrNull(15000) {
                conn.incomingFrames.receive()
            } ?: throw IOException("Timeout waiting for pairing request")

            if (frame.messageType != NodeMessageTypes.PAIRING_REQ) {
                throw SecurityException("Strict mode: unauthenticated connection attempted non-pairing frame")
            }

            val req = PairingRequestMessage.fromBytes(frame.payload)
            val ok = pairingManager.verifyAndCompletePairing(
                tokenOrPin = req.tokenOrPin,
                remoteDeviceId = req.deviceId,
                remoteDeviceName = req.deviceName,
                remotePlatform = req.platform,
                remoteFingerprint = conn.peerFingerprint
            )

            if (!ok) {
                val failResp = PairingResponseMessage(
                    success = false,
                    message = "Invalid or expired PIN",
                    serverDeviceId = "",
                    serverDeviceName = ""
                )
                conn.sendFrame(
                    NodeFraming.Frame(
                        messageType = NodeMessageTypes.PAIRING_RESP,
                        sequenceId = frame.sequenceId,
                        payload = failResp.toBytes()
                    )
                )
                delay(200)
                throw SecurityException("Invalid PIN in strict mode")
            }

            val successResp = PairingResponseMessage(
                success = true,
                message = "Pairing successful",
                serverDeviceId = identity?.deviceId ?: "",
                serverDeviceName = localDeviceName
            )
            conn.sendFrame(
                NodeFraming.Frame(
                    messageType = NodeMessageTypes.PAIRING_RESP,
                    sequenceId = frame.sequenceId,
                    payload = successResp.toBytes()
                )
            )
            _activePairingSession.value = null
            refreshPairedDevices()
        }
    }

    private fun updateConnectionStatus() {
        val count = activeConnections.size
        _connectedDeviceCount.value = count
        _connectionState.value = if (count > 0) ConnectState.CONNECTED else if (server != null) ConnectState.DISCOVERING else ConnectState.OFF
    }
}
