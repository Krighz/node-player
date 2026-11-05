package com.nodeplayer.network

import com.nodeplayer.network.pairing.DevicePermissions
import com.nodeplayer.network.pairing.DeviceRegistry
import com.nodeplayer.network.pairing.PairingManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PairingTest {

    @Test
    fun testPairingWorkflowAndRegistryPersistence() {
        val tempFile = File.createTempFile("reg_test_", ".txt").apply { deleteOnExit() }
        val registry = DeviceRegistry(tempFile)
        val manager = PairingManager(registry)

        val localFp = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        val session = manager.startPairingSession(localFp, 54820)

        assertNotNull(session.sixDigitPin)
        assertEquals(6, session.sixDigitPin.length)
        assertFalse(session.isExpired)

        val remoteFp = "11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF"

        // False PIN fails
        val badResult = manager.verifyAndCompletePairing(
            tokenOrPin = "000000",
            remoteDeviceId = "phone-1",
            remoteDeviceName = "Realme GT 6T",
            remotePlatform = "Android",
            remoteFingerprint = remoteFp
        )
        assertFalse(badResult)
        assertFalse(registry.isTrustedFingerprint(remoteFp))

        // Correct PIN succeeds
        val goodResult = manager.verifyAndCompletePairing(
            tokenOrPin = session.sixDigitPin,
            remoteDeviceId = "phone-1",
            remoteDeviceName = "Realme GT 6T",
            remotePlatform = "Android",
            remoteFingerprint = remoteFp,
            initialPermissions = DevicePermissions(canReceiveCast = true, canBrowseLibrary = true)
        )
        assertTrue(goodResult)
        assertTrue(registry.isTrustedFingerprint(remoteFp))

        val paired = registry.getPairedDeviceById("phone-1")
        assertNotNull(paired)
        assertEquals("Realme GT 6T", paired!!.deviceName)
        assertTrue(paired.permissions.canReceiveCast)

        // Verify instant revocation
        val revoked = registry.revokeDevice("phone-1")
        assertTrue(revoked)
        assertFalse(registry.isTrustedFingerprint(remoteFp))
    }
}
