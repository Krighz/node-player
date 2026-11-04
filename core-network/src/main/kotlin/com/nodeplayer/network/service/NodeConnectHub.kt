package com.nodeplayer.network.service

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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

enum class ConnectState {
    OFF,
    DISCOVERING,
    CONNECTED
}

/**
 * NodeConnectHub: Main coordination hub for Node Connect.
 * Integrates Discovery, mTLS Server, Client, Registry, Pairing, Library, Control, Cast, Handoff, Sync, and External Session planes.
 */
class NodeConnectHub(
    val keyStorage: SecureKeyStorage = SecureKeyStorage(),
    val registry: DeviceRegistry = DeviceRegistry(),
    val trackRepository: TrackRepository? = null,
    val localController: LocalPlayerController? = null,
    val onIncomingCastStream: ((ActiveCastSession, JitterBuffer) -> Unit)? = null,
    val onIncomingHandoff: ((HandoffPayload) -> Boolean)? = null,
    val platformMediaSessionBridge: PlatformMediaSessionBridge? = null,
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

    private var identity: NodeDeviceIdentity? = null
    private var server: NodeServer? = null
    private var discovery: NodeDiscovery? = null
    private var localDeviceName: String = "Node Device"
    private var localPlatform: String = "Unknown"

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
        val id = keyStorage.getOrCreateIdentity(deviceName)
        identity = id

        refreshPairedDevices()

        // 1. Start mTLS Server
        val s = NodeServer(
            identity = id,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) || pairingManager.getCurrentSession() != null },
            port = NodeServer.DEFAULT_PORT,
            scope = scope
        )
        s.start { conn ->
            handleIncomingConnection(conn)
        }
        server = s

        // 2. Start Local Discovery
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
    }

    @Synchronized
    fun stop() {
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
    }

    suspend fun connectToPeer(peer: DiscoveredNodePeer): NodeConnection? {
        val id = identity ?: return null
        val client = NodeClient(
            identity = id,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) },
            scope = scope
        )

        return try {
            val conn = client.connect(peer.hostAddress, peer.port)
            handleIncomingConnection(conn)
            conn
        } catch (e: Exception) {
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
            // Handshake with host allowing certificate check during pairing flow
            val client = NodeClient(
                identity = id,
                isTrustedFingerprint = { _ -> true },
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

            // Wait for PAIRING_RESP frame
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

            // Successfully paired! Pin host cert in DeviceRegistry
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

            handleIncomingConnection(conn)
            Result.success(pairedHost.deviceName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun revokeDevice(deviceId: String): Boolean {
        val res = registry.revokeDevice(deviceId)
        if (res) {
            refreshPairedDevices()
            // Drop any active connection for this device
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

    private fun handleIncomingConnection(conn: NodeConnection) {
        val isAlreadyTrusted = registry.isTrustedFingerprint(conn.peerFingerprint)

        scope.launch {
            try {
                if (!isAlreadyTrusted) {
                    // Gate incoming untrusted connection: must perform pairing within 15 seconds
                    val frame = withTimeoutOrNull(15000) {
                        conn.incomingFrames.receive()
                    }
                    if (frame == null || frame.messageType != NodeMessageTypes.PAIRING_REQ) {
                        conn.close()
                        return@launch
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
                        conn.close()
                        return@launch
                    }

                    // Success
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

                // Connection is authenticated and trusted
                activeConnections.add(conn)
                controlService.registerController(conn)
                updateConnectionStatus()

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
                // Connection terminated
            } finally {
                activeConnections.remove(conn)
                controlService.unregisterController(conn)
                conn.close()
                updateConnectionStatus()
            }
        }
    }

    private fun updateConnectionStatus() {
        val count = activeConnections.size
        _connectedDeviceCount.value = count
        _connectionState.value = if (count > 0) ConnectState.CONNECTED else if (server != null) ConnectState.DISCOVERING else ConnectState.OFF
    }
}
