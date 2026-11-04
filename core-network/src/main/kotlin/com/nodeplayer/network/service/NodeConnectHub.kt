package com.nodeplayer.network.service

import com.nodeplayer.library.repository.TrackRepository
import com.nodeplayer.network.crypto.NodeDeviceIdentity
import com.nodeplayer.network.crypto.SecureKeyStorage
import com.nodeplayer.network.discovery.DiscoveredNodePeer
import com.nodeplayer.network.discovery.NodeDiscovery
import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.pairing.PairedDevice
import com.nodeplayer.network.pairing.PairingManager
import com.nodeplayer.network.pairing.PairingSession
import com.nodeplayer.network.protocol.ControlStateEventMessage
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
 * Integrates Discovery, mTLS Server, Client, Registry, Pairing, Library, and Control planes.
 */
class NodeConnectHub(
    private val keyStorage: SecureKeyStorage = SecureKeyStorage(),
    private val registry: DeviceRegistry = DeviceRegistry(),
    private val trackRepository: TrackRepository? = null,
    private val localController: LocalPlayerController? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    val pairingManager = PairingManager(registry)
    val libraryService = NodeConnectLibraryService(trackRepository)
    val controlService = NodeConnectControlService(localController, registry)
    val mirrorService = NodeConnectMirrorService()

    private var identity: NodeDeviceIdentity? = null
    private var server: NodeServer? = null
    private var discovery: NodeDiscovery? = null

    val activeConnections = CopyOnWriteArrayList<NodeConnection>()

    private val _connectionState = MutableStateFlow(ConnectState.OFF)
    val connectionState: StateFlow<ConnectState> = _connectionState.asStateFlow()

    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredNodePeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredNodePeer>> = _discoveredPeers.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    @Synchronized
    fun start(deviceName: String, platform: String) {
        if (_connectionState.value != ConnectState.OFF) return

        val id = keyStorage.getOrCreateIdentity(deviceName)
        identity = id

        refreshPairedDevices()

        // 1. Start mTLS Server
        val s = NodeServer(
            identity = id,
            isTrustedFingerprint = { fp -> registry.isTrustedFingerprint(fp) },
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
        return pairingManager.startPairingSession(id.certificateFingerprintSha256, s.activePort)
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

    private fun refreshPairedDevices() {
        _pairedDevices.value = registry.getAllPairedDevices()
    }

    private fun handleIncomingConnection(conn: NodeConnection) {
        activeConnections.add(conn)
        controlService.registerController(conn)
        updateConnectionStatus()

        scope.launch {
            try {
                for (frame in conn.incomingFrames) {
                    val handled = libraryService.handleFrame(frame, conn) ||
                            controlService.handleFrame(frame, conn) ||
                            mirrorService.handleFrame(frame)
                }
            } finally {
                activeConnections.remove(conn)
                controlService.unregisterController(conn)
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
