package com.nodeplayer.network.transport

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.network.crypto.KeyManager
import com.nodeplayer.network.crypto.NodeDeviceIdentity
import com.nodeplayer.network.crypto.NodeSslContext
import com.nodeplayer.network.crypto.PinningTrustManager
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

enum class SecurityLevel {
    SIMPLE, STRICT
}

/**
 * Server Socket for Node Connect.
 * Supports Simple Mode (unauthenticated TLS or plain TCP for instant setup)
 * and Strict Mode (Mutual TLS with cryptographic certificate pinning).
 * Automatically binds across all network interfaces (0.0.0.0) with dynamic port fallback.
 */
class NodeServer(
    val identity: NodeDeviceIdentity,
    private val isTrustedFingerprint: (String) -> Boolean,
    private val port: Int = DEFAULT_PORT,
    val securityLevel: SecurityLevel = SecurityLevel.SIMPLE,
    val encryptionEnabled: Boolean = true,
    private val scope: CoroutineScope
) {
    companion object {
        const val DEFAULT_PORT = 54820
    }

    private var serverSocket: ServerSocket? = null
    private var listenJob: Job? = null
    var activePort: Int = port
        private set

    fun start(onNewConnection: (NodeConnection) -> Unit) {
        var boundSocket: ServerSocket? = null
        var currentPort = port
        var lastEx: Exception? = null

        NodeLog.i(
            "nodeconnect/server",
            "Starting NodeServer (securityLevel=$securityLevel, encryption=$encryptionEnabled) on base port $port..."
        )

        // Try requested port first, then fall back through next 10 ports
        for (attempt in 0..10) {
            try {
                if (encryptionEnabled) {
                    val sslContext = if (securityLevel == SecurityLevel.SIMPLE) {
                        NodeSslContext.createPermissive(identity)
                    } else {
                        val trustManager = PinningTrustManager(isTrustedFingerprint)
                        NodeSslContext.create(identity, trustManager)
                    }
                    val factory = sslContext.serverSocketFactory
                    val candidate = factory.createServerSocket() as SSLServerSocket
                    candidate.needClientAuth = (securityLevel == SecurityLevel.STRICT)
                    candidate.reuseAddress = true
                    candidate.bind(InetSocketAddress(currentPort))
                    boundSocket = candidate
                } else {
                    val candidate = ServerSocket()
                    candidate.reuseAddress = true
                    candidate.bind(InetSocketAddress(currentPort))
                    boundSocket = candidate
                }
                activePort = boundSocket.localPort
                NodeLog.i("nodeconnect/server", "Server successfully bound to 0.0.0.0:$activePort")
                break
            } catch (e: Exception) {
                lastEx = e
                NodeLog.w("nodeconnect/server", "Port $currentPort unavailable (${e.message}), trying next...")
                currentPort = port + attempt + 1
            }
        }

        val ss = boundSocket ?: throw (lastEx ?: IOException("Failed to bind ServerSocket on $port..${port + 10}"))
        serverSocket = ss

        listenJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !ss.isClosed) {
                    val clientSocket = try {
                        ss.accept()
                    } catch (e: Exception) {
                        if (!ss.isClosed) {
                            NodeLog.e("nodeconnect/server", "Exception while accepting socket: ${e.message}", e)
                        }
                        break
                    }

                    NodeLog.i(
                        "nodeconnect/server",
                        "Accepted incoming connection from ${clientSocket.remoteSocketAddress}"
                    )

                    launch {
                        try {
                            val (peerId, fp) = if (clientSocket is SSLSocket) {
                                clientSocket.startHandshake()
                                val peerCerts = try { clientSocket.session.peerCertificates } catch (_: Exception) { null }
                                if (!peerCerts.isNullOrEmpty()) {
                                    val peerCert = peerCerts[0] as X509Certificate
                                    val f = KeyManager.computeSha256Fingerprint(peerCert)
                                    val id = peerCert.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")
                                    id to f
                                } else {
                                    "anonymous" to "unauthenticated"
                                }
                            } else {
                                "plain_client" to "none"
                            }

                            NodeLog.i("nodeconnect/server", "Connection established with peerId=$peerId, fp=$fp")
                            val conn = NodeConnection(clientSocket, peerId, fp, scope)
                            conn.start()
                            onNewConnection(conn)
                        } catch (e: Exception) {
                            NodeLog.e(
                                "nodeconnect/server",
                                "Handshake failure from ${clientSocket.remoteSocketAddress}: ${e.message}",
                                e
                            )
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    NodeLog.e("nodeconnect/server", "Server listener crashed: ${e.message}", e)
                }
            }
        }
    }

    fun stop() {
        NodeLog.i("nodeconnect/server", "Stopping NodeServer on port $activePort...")
        listenJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/server", "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }

    val isRunning: Boolean get() = serverSocket?.isClosed == false
}

/**
 * Outgoing Client for Node Connect.
 */
class NodeClient(
    val identity: NodeDeviceIdentity,
    private val isTrustedFingerprint: (String) -> Boolean,
    val securityLevel: SecurityLevel = SecurityLevel.SIMPLE,
    val encryptionEnabled: Boolean = true,
    private val scope: CoroutineScope
) {
    suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Int = 4000
    ): NodeConnection = withContext(Dispatchers.IO) {
        NodeLog.i("nodeconnect/connect", "Initiating connection to $host:$port (security=$securityLevel, enc=$encryptionEnabled)...")
        val socket: Socket

        if (encryptionEnabled) {
            val sslContext = if (securityLevel == SecurityLevel.SIMPLE) {
                NodeSslContext.createPermissive(identity)
            } else {
                val trustManager = PinningTrustManager(isTrustedFingerprint)
                NodeSslContext.create(identity, trustManager)
            }
            val sslSocket = sslContext.socketFactory.createSocket() as SSLSocket
            sslSocket.connect(InetSocketAddress(host, port), timeoutMs)
            sslSocket.soTimeout = 30000
            sslSocket.startHandshake()

            val peerCerts = try { sslSocket.session.peerCertificates } catch (_: Exception) { null }
            val (peerId, fp) = if (!peerCerts.isNullOrEmpty()) {
                val peerCert = peerCerts[0] as X509Certificate
                val f = KeyManager.computeSha256Fingerprint(peerCert)
                val id = peerCert.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")
                id to f
            } else {
                "server" to "unauthenticated"
            }
            NodeLog.i("nodeconnect/connect", "TLS handshake success with $host:$port (peerId=$peerId)")
            val conn = NodeConnection(sslSocket, peerId, fp, scope)
            conn.start()
            conn
        } else {
            val plainSocket = Socket()
            plainSocket.connect(InetSocketAddress(host, port), timeoutMs)
            plainSocket.soTimeout = 30000
            NodeLog.i("nodeconnect/connect", "Plain TCP socket connected to $host:$port")
            val conn = NodeConnection(plainSocket, "plain_peer", "none", scope)
            conn.start()
            conn
        }
    }
}
