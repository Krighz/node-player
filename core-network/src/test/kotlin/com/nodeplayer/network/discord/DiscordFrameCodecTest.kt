package com.nodeplayer.network.discord

import com.nodeplayer.model.presence.DiscordIpcConstants
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DiscordFrameCodecTest {

    @Test
    fun testEncodeAndDecodeHandshakeFrame() {
        val payload = """{"v":1,"client_id":"1234567890"}"""
        val encoded = DiscordFrameCodec.encodeFrame(DiscordIpcConstants.OP_HANDSHAKE, payload)

        assertEquals(8 + payload.toByteArray(Charsets.UTF_8).size, encoded.size)

        // Read header bytes explicitly in Little Endian
        val buffer = ByteBuffer.wrap(encoded, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        val op = buffer.int
        val len = buffer.int

        assertEquals(DiscordIpcConstants.OP_HANDSHAKE, op)
        assertEquals(payload.toByteArray(Charsets.UTF_8).size, len)

        val headerBytes = encoded.copyOfRange(0, 8)
        val payloadBytes = encoded.copyOfRange(8, encoded.size)
        val (decodedOp, decodedPayload) = DiscordFrameCodec.decodeFrame(headerBytes, payloadBytes)

        assertEquals(DiscordIpcConstants.OP_HANDSHAKE, decodedOp)
        assertEquals(payload, decodedPayload)
    }

    @Test
    fun testFrameWithUtf8MultiByteCharacters() {
        val payload = """{"details":"Café & 🎵 音樂","state":"by Artist"}"""
        val encoded = DiscordFrameCodec.encodeFrame(DiscordIpcConstants.OP_FRAME, payload)

        val headerBytes = encoded.copyOfRange(0, 8)
        val payloadBytes = encoded.copyOfRange(8, encoded.size)
        val (decodedOp, decodedPayload) = DiscordFrameCodec.decodeFrame(headerBytes, payloadBytes)

        assertEquals(DiscordIpcConstants.OP_FRAME, decodedOp)
        assertEquals(payload, decodedPayload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOversizedPayloadHeaderThrowsException() {
        // Construct header with 2MB payload size (exceeding MAX_FRAME_SIZE 1MB)
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(DiscordIpcConstants.OP_FRAME)
        buffer.putInt(2_000_000)

        DiscordFrameCodec.decodeHeader(buffer.array())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativePayloadLengthThrowsException() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(DiscordIpcConstants.OP_FRAME)
        buffer.putInt(-1)

        DiscordFrameCodec.decodeHeader(buffer.array())
    }
}
