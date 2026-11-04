package com.nodeplayer.network

import com.nodeplayer.network.pairing.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeSimpleTest {

    @Test
    fun testSimpleQrPayloadGenerationAndEncoding() {
        val ips = listOf("10.222.143.172", "192.168.43.1", "127.0.0.1")
        val port = 54820
        val deviceId = "test_dev_123"
        val deviceName = "Satya PC"

        val payload = "nodeconnect://${ips.joinToString(",")}:$port?dev=$deviceId&name=${java.net.URLEncoder.encode(deviceName, "UTF-8")}"
        assertTrue("Payload should be compact (< 120 bytes)", payload.length < 120)

        val matrix = QrCodeGenerator.encode(payload)
        assertNotNull(matrix)
        assertTrue("QR matrix should have valid dimensions", matrix.size >= 21)

        // Parse payload
        assertTrue(payload.startsWith("nodeconnect://"))
        val content = payload.removePrefix("nodeconnect://")
        val addressesPart = content.substringBefore("?")
        val hostPart = addressesPart.substringBefore(":")
        val portPart = addressesPart.substringAfter(":").toInt()
        assertEquals(port, portPart)

        val candidateList = hostPart.split(",")
        assertEquals(3, candidateList.size)
        assertEquals("10.222.143.172", candidateList[0])
    }
}
