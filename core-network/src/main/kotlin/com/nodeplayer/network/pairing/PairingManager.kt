package com.nodeplayer.network.pairing

import java.security.SecureRandom
import java.util.UUID

data class PairingSession(
    val sessionToken: String,
    val sixDigitPin: String,
    val localFingerprint: String,
    val port: Int,
    val expiresAtTimestampMs: Long
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtTimestampMs

    fun toQrPayload(localIp: String, deviceId: String, deviceName: String): String {
        val sanitizedName = deviceName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(16)
        return "nodeconnect://$localIp:$port?ott=$sessionToken&pin=$sixDigitPin&dev=$sanitizedName"
    }
}

class PairingManager(
    private val registry: DeviceRegistry
) {
    private var currentSession: PairingSession? = null

    /**
     * Creates a new pairing session with a 6-digit PIN and a cryptographically secure OTT.
     * Valid for 3 minutes (180 seconds).
     */
    @Synchronized
    fun startPairingSession(localFingerprint: String, port: Int): PairingSession {
        val pin = "%06d".format(SecureRandom().nextInt(1_000_000))
        val token = UUID.randomUUID().toString()
        val session = PairingSession(
            sessionToken = token,
            sixDigitPin = pin,
            localFingerprint = localFingerprint,
            port = port,
            expiresAtTimestampMs = System.currentTimeMillis() + 180_000L
        )
        currentSession = session
        return session
    }

    @Synchronized
    fun getCurrentSession(): PairingSession? {
        val session = currentSession ?: return null
        if (session.isExpired) {
            currentSession = null
            return null
        }
        return session
    }

    @Synchronized
    fun verifyAndCompletePairing(
        tokenOrPin: String,
        remoteDeviceId: String,
        remoteDeviceName: String,
        remotePlatform: String,
        remoteFingerprint: String,
        initialPermissions: DevicePermissions = DevicePermissions()
    ): Boolean {
        val session = currentSession ?: return false
        if (session.isExpired) {
            currentSession = null
            return false
        }

        val matches = tokenOrPin == session.sessionToken || tokenOrPin == session.sixDigitPin
        if (!matches) {
            return false
        }

        // Successfully verified: register peer
        val paired = PairedDevice(
            deviceId = remoteDeviceId,
            deviceName = remoteDeviceName,
            platform = remotePlatform,
            certificateFingerprintSha256 = remoteFingerprint,
            permissions = initialPermissions,
            pairedTimestamp = System.currentTimeMillis(),
            lastSeenTimestamp = System.currentTimeMillis()
        )
        registry.registerDevice(paired)

        // Expire session
        currentSession = null
        return true
    }

    @Synchronized
    fun cancelSession() {
        currentSession = null
    }
}
