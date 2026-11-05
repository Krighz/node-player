package com.nodeplayer.network.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*

/**
 * Node Connect Device Identity & Cryptographic Key Management.
 * Generates and stores EC-256 (secp256r1) keypairs with self-signed X.509 certificates.
 * Zero external dependencies — pure Kotlin ASN.1 DER certificate serialization.
 */
data class NodeDeviceIdentity(
    val deviceId: String,
    val keyPair: KeyPair,
    val certificate: X509Certificate,
    val certificateFingerprintSha256: String
)

object KeyManager {

    private const val EC_CURVE = "secp256r1"
    private const val KEY_ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Computes the colon-separated uppercase hex SHA-256 fingerprint of an X.509 certificate.
     * Example: "4A:8B:12:F3:..."
     */
    fun computeSha256Fingerprint(cert: X509Certificate): String {
        return computeSha256Fingerprint(cert.encoded)
    }

    fun computeSha256Fingerprint(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Generates a new fresh Node Connect device identity.
     */
    fun generateIdentity(deviceId: String = UUID.randomUUID().toString(), commonName: String = "NodePlayer-$deviceId"): NodeDeviceIdentity {
        val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        kpg.initialize(256, SecureRandom())
        val keyPair = kpg.generateKeyPair()
        val cert = generateSelfSignedCertificate(keyPair, commonName)
        val fp = computeSha256Fingerprint(cert)
        return NodeDeviceIdentity(
            deviceId = deviceId,
            keyPair = keyPair,
            certificate = cert,
            certificateFingerprintSha256 = fp
        )
    }

    /**
     * Generates a standard X.509 v3 certificate for an EC keypair in pure DER format.
     */
    fun generateSelfSignedCertificate(keyPair: KeyPair, commonName: String): X509Certificate {
        val ecdsaWithSha256Oid = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02)
        val cnOid = byteArrayOf(0x55, 0x04, 0x03)

        val sigAlg = seq(oid(ecdsaWithSha256Oid))
        val version = tagged(0, integer(BigInteger.valueOf(2))) // X.509 v3
        val serial = integer(BigInteger.valueOf(Math.abs(SecureRandom().nextLong()) + 1L))

        // Subject / Issuer name: Sequence of Set of Sequence(OID, UTF8String)
        val setOut = ByteArrayOutputStream()
        setOut.write(0x31)
        val cnSeq = seq(oid(cnOid), utf8(commonName))
        setOut.write(encodeLength(cnSeq.size))
        setOut.write(cnSeq)
        val name = seq(setOut.toByteArray())

        val now = System.currentTimeMillis()
        val notBefore = utcTime(Date(now - 86400000L)) // 1 day in past for clock skew
        val notAfter = utcTime(Date(now + 10L * 365 * 86400000L)) // 10 years validity
        val validity = seq(notBefore, notAfter)

        // SubjectPublicKeyInfo directly from Java EC public key
        val spki = keyPair.public.encoded

        val tbs = seq(version, serial, sigAlg, name, validity, name, spki)

        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initSign(keyPair.private)
        sig.update(tbs)
        val signatureBytes = sig.sign()

        val certDer = seq(tbs, sigAlg, bitString(signatureBytes))

        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    // --- ASN.1 DER primitives ---

    private fun encodeLength(len: Int): ByteArray {
        return when {
            len < 128 -> byteArrayOf(len.toByte())
            len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
            len < 65536 -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x83.toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
    }

    private fun seq(vararg items: ByteArray): ByteArray {
        val inner = ByteArrayOutputStream()
        for (item in items) inner.write(item)
        val inBytes = inner.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(0x30)
        out.write(encodeLength(inBytes.size))
        out.write(inBytes)
        return out.toByteArray()
    }

    private fun tagged(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xA0 or tag)
        out.write(encodeLength(value.size))
        out.write(value)
        return out.toByteArray()
    }

    private fun integer(bi: BigInteger): ByteArray {
        val bytes = bi.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(0x02)
        out.write(encodeLength(bytes.size))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun oid(oidBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x06)
        out.write(encodeLength(oidBytes.size))
        out.write(oidBytes)
        return out.toByteArray()
    }

    private fun bitString(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x03)
        out.write(encodeLength(data.size + 1))
        out.write(0x00) // 0 unused bits
        out.write(data)
        return out.toByteArray()
    }

    private fun utf8(text: String): ByteArray {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(0x0C)
        out.write(encodeLength(bytes.size))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun utcTime(date: Date): ByteArray {
        val sdf = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val bytes = sdf.format(date).toByteArray(StandardCharsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(0x17)
        out.write(encodeLength(bytes.size))
        out.write(bytes)
        return out.toByteArray()
    }
}
