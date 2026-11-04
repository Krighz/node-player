package com.nodeplayer.network

import com.nodeplayer.network.protocol.NodeFraming
import com.nodeplayer.network.protocol.ProtocolException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FramingTest {

    @Test
    fun testFrameWriteAndReadRoundtrip() {
        val payload = "Hello Node Connect Audio Streaming Engine".toByteArray()
        val originalFrame = NodeFraming.Frame(
            messageType = 0x0010,
            flags = NodeFraming.FLAG_PRIORITY,
            sequenceId = 42L,
            payload = payload
        )

        val bos = ByteArrayOutputStream()
        NodeFraming.writeFrame(originalFrame, bos)

        val bytes = bos.toByteArray()
        assertTrue(bytes.size > payload.size)

        val bis = ByteArrayInputStream(bytes)
        val readFrame = NodeFraming.readFrame(bis)

        assertEquals(originalFrame.messageType, readFrame.messageType)
        assertEquals(originalFrame.flags, readFrame.flags)
        assertEquals(originalFrame.sequenceId, readFrame.sequenceId)
        assertArrayEquals(originalFrame.payload, readFrame.payload)
    }

    @Test(expected = ProtocolException::class)
    fun testCorruptedChecksumThrowsProtocolException() {
        val payload = "Important Audio Payload".toByteArray()
        val originalFrame = NodeFraming.Frame(
            messageType = 0x0031,
            flags = 0,
            sequenceId = 100L,
            payload = payload
        )

        val bos = ByteArrayOutputStream()
        NodeFraming.writeFrame(originalFrame, bos)
        val bytes = bos.toByteArray()

        // Corrupt one byte inside the payload
        bytes[24] = (bytes[24].toInt() xor 0xFF).toByte()

        val bis = ByteArrayInputStream(bytes)
        NodeFraming.readFrame(bis)
    }

    @Test(expected = ProtocolException::class)
    fun testInvalidMagicThrowsProtocolException() {
        val corruptBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x01, 0x00, 0x00, 0x00)
        val bis = ByteArrayInputStream(corruptBytes)
        NodeFraming.readFrame(bis)
    }
}
