package com.nodeplayer.network.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Binary Framing Protocol for Node Connect.
 * Magic: "NODE" (4 bytes)
 * Version: 1 byte
 * Message Type: 2 bytes
 * Flags: 1 byte
 * Sequence ID: 8 bytes
 * Payload Length: 4 bytes
 * Payload: N bytes (max 4 MB)
 * Checksum: 4 bytes CRC32
 */
object NodeFraming {

    val MAGIC = byteArrayOf('N'.code.toByte(), 'O'.code.toByte(), 'D'.code.toByte(), 'E'.code.toByte())
    const val PROTOCOL_VERSION: Byte = 1
    const val MAX_PAYLOAD_SIZE = 4 * 1024 * 1024 // 4 MB

    const val FLAG_COMPRESSED: Byte = 0x01
    const val FLAG_PRIORITY: Byte = 0x02

    data class Frame(
        val messageType: Short,
        val flags: Byte = 0,
        val sequenceId: Long = 0L,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            if (messageType != other.messageType) return false
            if (flags != other.flags) return false
            if (sequenceId != other.sequenceId) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = messageType.toInt()
            result = 31 * result + flags.toInt()
            result = 31 * result + sequenceId.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Serializes a Frame and writes it to an OutputStream.
     */
    fun writeFrame(frame: Frame, output: OutputStream) {
        val dos = DataOutputStream(output)
        dos.write(MAGIC)
        dos.writeByte(PROTOCOL_VERSION.toInt())
        dos.writeShort(frame.messageType.toInt())
        dos.writeByte(frame.flags.toInt())
        dos.writeLong(frame.sequenceId)
        dos.writeInt(frame.payload.size)
        if (frame.payload.isNotEmpty()) {
            dos.write(frame.payload)
        }

        val crc = CRC32()
        crc.update(frame.payload)
        dos.writeInt(crc.value.toInt())
        dos.flush()
    }

    /**
     * Reads a single Frame from an InputStream.
     * Throws EOFException if stream ends cleanly, or IOException if corrupt / invalid.
     */
    fun readFrame(input: InputStream): Frame {
        val dis = DataInputStream(input)

        // Verify Magic 4 bytes
        for (i in 0 until 4) {
            val b = dis.read()
            if (b == -1) throw EOFException("End of stream while reading magic header")
            if (b.toByte() != MAGIC[i]) {
                throw ProtocolException("Invalid Node Connect magic byte at index $i: expected ${MAGIC[i]}, got $b")
            }
        }

        val version = dis.readByte()
        if (version != PROTOCOL_VERSION) {
            throw ProtocolException("Unsupported protocol version: $version (expected $PROTOCOL_VERSION)")
        }

        val messageType = dis.readShort()
        val flags = dis.readByte()
        val sequenceId = dis.readLong()
        val payloadLength = dis.readInt()

        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw ProtocolException("Invalid payload length: $payloadLength bytes (max $MAX_PAYLOAD_SIZE)")
        }

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            dis.readFully(payload)
        }

        val expectedCrc = dis.readInt()
        val crc = CRC32()
        crc.update(payload)
        val computedCrc = crc.value.toInt()

        if (expectedCrc != computedCrc) {
            throw ProtocolException("CRC32 mismatch in Node Connect frame: expected $expectedCrc, computed $computedCrc")
        }

        return Frame(
            messageType = messageType,
            flags = flags,
            sequenceId = sequenceId,
            payload = payload
        )
    }
}

class ProtocolException(message: String) : java.io.IOException(message)
