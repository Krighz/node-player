package com.nodeplayer.network.discord

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Little-endian frame encoder and decoder for Discord IPC named pipe transport.
 * Header format:
 * - Opcode: Int32 (4 bytes, Little-Endian)
 * - Length: Int32 (4 bytes, Little-Endian)
 * - Body: UTF-8 encoded JSON payload
 */
object DiscordFrameCodec {

    const val HEADER_SIZE: Int = 8
    const val MAX_FRAME_SIZE: Int = 1_048_576 // 1MB safety cap

    /**
     * Encodes an opcode and JSON payload into a Discord IPC wire frame.
     */
    fun encodeFrame(opcode: Int, jsonPayload: String): ByteArray {
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payloadBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode)
        buffer.putInt(payloadBytes.size)
        buffer.put(payloadBytes)
        return buffer.array()
    }

    /**
     * Decodes the 8-byte header into (opcode, length).
     * @throws IllegalArgumentException if header size is invalid or payload length is negative/oversized.
     */
    fun decodeHeader(headerBytes: ByteArray): Pair<Int, Int> {
        require(headerBytes.size == HEADER_SIZE) { "Header must be exactly $HEADER_SIZE bytes, got ${headerBytes.size}" }
        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val opcode = buffer.int
        val length = buffer.int
        require(length in 0..MAX_FRAME_SIZE) { "Payload length $length exceeds allowed range (0..$MAX_FRAME_SIZE)" }
        return Pair(opcode, length)
    }

    /**
     * Decodes a complete frame given header bytes and payload bytes.
     */
    fun decodeFrame(headerBytes: ByteArray, payloadBytes: ByteArray): Pair<Int, String> {
        val (opcode, length) = decodeHeader(headerBytes)
        require(payloadBytes.size == length) {
            "Payload byte size ${payloadBytes.size} does not match header length $length"
        }
        val payloadString = String(payloadBytes, Charsets.UTF_8)
        return Pair(opcode, payloadString)
    }
}
