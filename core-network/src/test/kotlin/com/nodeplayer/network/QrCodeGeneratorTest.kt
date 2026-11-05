package com.nodeplayer.network

import com.nodeplayer.network.pairing.QrCodeGenerator
import org.junit.Assert.*
import org.junit.Test

class QrCodeGeneratorTest {

    @Test
    fun testQrCodeGeneration() {
        val payload = "nodeconnect://10.222.143.172:54820?pin=123456"
        val matrix = QrCodeGenerator.encode(payload)

        assertTrue(matrix.size >= 21)
        // Finder patterns should be non-empty
        var blackCount = 0
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.get(x, y)) blackCount++
            }
        }
        assertTrue("QR code should contain black modules", blackCount > 50)
    }
}
