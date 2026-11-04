package com.nodeplayer.network

import com.nodeplayer.network.crypto.KeyManager
import com.nodeplayer.network.crypto.SecureKeyStorage
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CryptoTest {

    @Test
    fun testGenerateIdentityAndVerifyCertificate() {
        val identity = KeyManager.generateIdentity(deviceId = "test-node-1", commonName = "NodePlayer-PC")
        assertNotNull(identity.keyPair)
        assertNotNull(identity.certificate)
        assertEquals("test-node-1", identity.deviceId)

        // Verify X.509 signature using self public key
        identity.certificate.verify(identity.keyPair.public)

        // Verify SHA-256 fingerprint format (AA:BB:CC:... with 32 bytes = 95 chars)
        val fp = identity.certificateFingerprintSha256
        assertNotNull(fp)
        assertTrue("Fingerprint should contain colons", fp.contains(":"))
        val parts = fp.split(":")
        assertEquals(32, parts.size)
        parts.forEach {
            assertEquals(2, it.length)
            assertTrue("Hex char check", it.matches(Regex("[0-9A-F]{2}")))
        }
    }

    @Test
    fun testSecureKeyStoragePersistence() {
        val tempDir = File.createTempFile("node_test_keys_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val storage1 = SecureKeyStorage(tempDir)
            val id1 = storage1.getOrCreateIdentity("MyTestPC")
            assertNotNull(id1)

            // Re-instantiate storage and reload
            val storage2 = SecureKeyStorage(tempDir)
            val id2 = storage2.loadIdentity()
            assertNotNull(id2)

            assertEquals(id1.deviceId, id2!!.deviceId)
            assertEquals(id1.certificateFingerprintSha256, id2.certificateFingerprintSha256)
            assertEquals(id1.keyPair.public, id2.keyPair.public)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
