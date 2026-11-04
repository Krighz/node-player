package com.nodeplayer.network.crypto

import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * mTLS Certificate Pinning Trust Manager for Node Connect.
 * Enforces zero-trust peer certificate authentication against known/paired SHA-256 fingerprints.
 * If a certificate fingerprint does not match the pinned peer registry, the TLS handshake is aborted immediately.
 */
class PinningTrustManager(
    private val isTrustedFingerprint: (String) -> Boolean
) : X509ExtendedTrustManager() {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyChain(chain)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        verifyChain(chain)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        verifyChain(chain)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyChain(chain)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        verifyChain(chain)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        verifyChain(chain)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }

    private fun verifyChain(chain: Array<out X509Certificate>?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Node Connect mTLS: Peer provided empty certificate chain")
        }
        val peerCert = chain[0]
        val fingerprint = KeyManager.computeSha256Fingerprint(peerCert)
        if (!isTrustedFingerprint(fingerprint)) {
            throw CertificateException("Node Connect mTLS: Peer certificate fingerprint rejected (not paired or revoked): $fingerprint")
        }
    }
}

/**
 * Permissive Trust Manager for Simple Security Mode.
 * Encrypts communication over TLS with local self-signed certificates,
 * but allows any peer certificate without requiring upfront pinning or PIN exchange.
 */
class PermissiveTrustManager : X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Creates and configures standard TLSv1.3 / TLSv1.2 mTLS SSLContext.
 */
object NodeSslContext {

    const val TLS_PROTOCOL = "TLSv1.3"

    fun create(
        identity: NodeDeviceIdentity,
        trustManager: X509ExtendedTrustManager
    ): SSLContext {
        // Build In-Memory KeyStore containing local device EC key and self-signed certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        val password = "node-connect-local".toCharArray()
        keyStore.setKeyEntry("local-identity", identity.keyPair.private, password, arrayOf(identity.certificate))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val sslContext = try {
            SSLContext.getInstance(TLS_PROTOCOL)
        } catch (e: Exception) {
            SSLContext.getInstance("TLS")
        }

        sslContext.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())
        return sslContext
    }

    fun createPermissive(identity: NodeDeviceIdentity): SSLContext {
        return create(identity, PermissiveTrustManager())
    }
}
