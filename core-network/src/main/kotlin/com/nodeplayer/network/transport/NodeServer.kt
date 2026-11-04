package com.nodeplayer.network.transport

import com.nodeplayer.network.crypto.KeyManager
import com.nodeplayer.network.crypto.NodeDeviceIdentity
import com.nodeplayer.network.crypto.NodeSslContext
import com.nodeplayer.network.crypto.PinningTrustManager
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * mTLS Server Socket for Node Connect.
 * Listens on a local port, performs mutual TLS handshake, enforces certificate pinning,
 * and yields established NodeConnection instances to an incoming connection listener.
 */
class NodeServer(
    val identity: NodeDeviceIdentity,
    private val isTrustedFingerprint: (String) -> Boolean,
    private val port: Int = DEFAULT_PORT,
    private val scope: CoroutineScope
) {
    companion object {
        const val DEFAULT_PORT = 54820
    }

    private var serverSocket: SSLServerSocket? = null
    private var listenJob: Job? = null
    var activePort: Int = port
        private set

    fun start(onNewConnection: (NodeConnection) -> Unit) {
        val trustManager = PinningTrustManager(isTrustedFingerprint)
        val sslContext = NodeSslContext.create(identity, trustManager)

        val factory = sslContext.serverSocketFactory
        var boundSocket: SSLServerSocket? = null
        var currentPort = port
        var lastEx: Exception? = null

        // Try requested port first, then fall back to next 10 ports
        for (attempt in 0..10) {
            try {
                val candidate = factory.createServerSocket() as SSLServerSocket
                candidate.needClientAuth = true // Enforce Mutual TLS: client MUST provide cert
                candidate.reuseAddress = true
                candidate.bind(InetSocketAddress(currentPort))
                boundSocket = candidate
                activePort = candidate.localPort
                break
            } catch (e: Exception) {
                lastEx = e
                currentPort = port + attempt + 1
            }
        }

        val ss = boundSocket ?: throw (lastEx ?: IOException("Failed to bind SSLServerSocket on $port..${port + 10}"))
        serverSocket = ss

        listenJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !ss.isClosed) {
                    val socket = ss.accept() as SSLSocket
                    launch {
                        try {
                            socket.startHandshake()
                            val peerCerts = socket.session.peerCertificates
                            val peerCert = peerCerts[0] as X509Certificate
                            val fp = KeyManager.computeSha256Fingerprint(peerCert)
                            val peerId = peerCert.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")

                            val conn = NodeConnection(socket, peerId, fp, scope)
                            conn.start()
                            onNewConnection(conn)
                        } catch (e: Exception) {
                            try { socket.close() } catch (ignored: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                // Server closed or network reset
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
    }

    val isRunning: Boolean get() = serverSocket?.isClosed == false
}

/**
 * mTLS Outgoing Client for Node Connect.
 */
class NodeClient(
    val identity: NodeDeviceIdentity,
    private val isTrustedFingerprint: (String) -> Boolean,
    private val scope: CoroutineScope
) {
    suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Int = 5000
    ): NodeConnection = withContext(Dispatchers.IO) {
        val trustManager = PinningTrustManager(isTrustedFingerprint)
        val sslContext = NodeSslContext.create(identity, trustManager)

        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = 30000
        socket.startHandshake()

        val peerCerts = socket.session.peerCertificates
        val peerCert = peerCerts[0] as X509Certificate
        val fp = KeyManager.computeSha256Fingerprint(peerCert)
        val peerId = peerCert.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")

        val conn = NodeConnection(socket, peerId, fp, scope)
        conn.start()
        conn
    }
}
