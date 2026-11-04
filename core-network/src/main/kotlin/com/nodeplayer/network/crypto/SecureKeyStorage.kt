package com.nodeplayer.network.crypto

import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Persistent secure storage for Node Connect device identity (EC private key and self-signed certificate).
 * On Windows, defaults to %APPDATA%\NodePlayer\keys.
 * On Android, defaults to app-private directory.
 */
class SecureKeyStorage(
    private val storageDir: File = getDefaultStorageDir()
) {

    companion object {
        fun getDefaultStorageDir(): File {
            val appData = System.getenv("APPDATA")
            return if (!appData.isNullOrBlank()) {
                File(appData, "NodePlayer/keys")
            } else {
                val userHome = System.getProperty("user.home", ".")
                File(userHome, ".nodeplayer/keys")
            }
        }
    }

    private val privateKeyFile = File(storageDir, "device_private_key.pk8")
    private val publicKeyFile = File(storageDir, "device_public_key.x509")
    private val certFile = File(storageDir, "device_cert.der")
    private val idFile = File(storageDir, "device_id.txt")

    init {
        storageDir.mkdirs()
    }

    /**
     * Loads existing identity or generates and saves a new one.
     */
    @Synchronized
    fun getOrCreateIdentity(deviceName: String = "NodePlayer-${System.getProperty("user.name", "User")}"): NodeDeviceIdentity {
        val existing = loadIdentity()
        if (existing != null) {
            return existing
        }

        val newId = UUID.randomUUID().toString()
        val identity = KeyManager.generateIdentity(deviceId = newId, commonName = "$deviceName ($newId)")
        saveIdentity(identity)
        return identity
    }

    @Synchronized
    fun loadIdentity(): NodeDeviceIdentity? {
        if (!privateKeyFile.exists() || !certFile.exists() || !idFile.exists() || !publicKeyFile.exists()) {
            return null
        }

        return try {
            val deviceId = idFile.readText(Charsets.UTF_8).trim()
            val privBytes = privateKeyFile.readBytes()
            val pubBytes = publicKeyFile.readBytes()
            val certBytes = certFile.readBytes()

            val kf = KeyFactory.getInstance("EC")
            val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
            val keyPair = KeyPair(pubKey, privKey)

            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(certBytes.inputStream()) as X509Certificate
            val fp = KeyManager.computeSha256Fingerprint(cert)

            NodeDeviceIdentity(
                deviceId = deviceId,
                keyPair = keyPair,
                certificate = cert,
                certificateFingerprintSha256 = fp
            )
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun saveIdentity(identity: NodeDeviceIdentity) {
        storageDir.mkdirs()
        idFile.writeText(identity.deviceId, Charsets.UTF_8)
        privateKeyFile.writeBytes(identity.keyPair.private.encoded)
        publicKeyFile.writeBytes(identity.keyPair.public.encoded)
        certFile.writeBytes(identity.certificate.encoded)
    }

    @Synchronized
    fun clearIdentity() {
        privateKeyFile.delete()
        publicKeyFile.delete()
        certFile.delete()
        idFile.delete()
    }
}
