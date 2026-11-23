package com.nodeplayer.library.converter

import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

data class FlacPicture(
    val mimeType: String,
    val description: String,
    val width: Int,
    val height: Int,
    val colorDepth: Int,
    val imageBytes: ByteArray,
    val rawBlockPayload: ByteArray
) {
    val base64Payload: String
        get() = Base64.getEncoder().encodeToString(rawBlockPayload)
}

/**
 * Pure Kotlin reader to extract embedded artwork, Vorbis comment lyrics,
 * and companion files (.lrc, .jpg) from FLAC tracks without external framework dependencies.
 */
object FlacMetadataExtractor {

    /**
     * Extracts picture metadata block (type 6) from a FLAC file if present.
     */
    fun extractPicture(file: File): FlacPicture? {
        if (!file.exists() || file.length() < 8) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Skip ID3v2 header if prepended to FLAC
                val id3Check = ByteArray(3)
                if (raf.read(id3Check) == 3 && id3Check[0] == 'I'.code.toByte() && id3Check[1] == 'D'.code.toByte() && id3Check[2] == '3'.code.toByte()) {
                    raf.seek(6)
                    val s0 = raf.read() and 0x7F
                    val s1 = raf.read() and 0x7F
                    val s2 = raf.read() and 0x7F
                    val s3 = raf.read() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    raf.seek(10L + tagSize)
                } else {
                    raf.seek(0)
                }

                val magic = ByteArray(4)
                if (raf.read(magic) != 4) return null
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() || magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()) {
                    return null
                }

                var isLast = false
                while (!isLast && raf.filePointer < raf.length()) {
                    val header = ByteArray(4)
                    if (raf.read(header) != 4) break
                    val b0 = header[0].toInt() and 0xFF
                    isLast = (b0 and 0x80) != 0
                    val blockType = b0 and 0x7F
                    val length = ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)

                    val blockStart = raf.filePointer
                    if (blockType == 6) { // PICTURE
                        if (length in 32..35_000_000) {
                            val payload = ByteArray(length)
                            raf.readFully(payload)

                            var offset = 0
                            offset += 4 // Skip picture type (3 = Front Cover)
                            if (offset + 4 > payload.size) return null
                            val mimeLen = readInt32BE(payload, offset); offset += 4
                            if (offset + mimeLen > payload.size) return null
                            val mimeType = String(payload, offset, mimeLen, Charsets.US_ASCII)
                            offset += mimeLen

                            if (offset + 4 > payload.size) return null
                            val descLen = readInt32BE(payload, offset); offset += 4
                            if (offset + descLen > payload.size) return null
                            val desc = String(payload, offset, descLen, Charsets.UTF_8)
                            offset += descLen

                            if (offset + 16 > payload.size) return null
                            val width = readInt32BE(payload, offset)
                            val height = readInt32BE(payload, offset + 4)
                            val depth = readInt32BE(payload, offset + 8)
                            offset += 16

                            if (offset + 4 > payload.size) return null
                            val dataLen = readInt32BE(payload, offset); offset += 4
                            if (offset + dataLen > payload.size) return null
                            val imageBytes = payload.copyOfRange(offset, offset + dataLen)

                            return FlacPicture(
                                mimeType = mimeType,
                                description = desc,
                                width = width,
                                height = height,
                                colorDepth = depth,
                                imageBytes = imageBytes,
                                rawBlockPayload = payload
                            )
                        }
                    }
                    raf.seek(blockStart + length)
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extracts lyrics from Vorbis comments (block type 4) if present.
     */
    fun extractLyrics(file: File): String? {
        if (!file.exists() || file.length() < 8) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val id3Check = ByteArray(3)
                if (raf.read(id3Check) == 3 && id3Check[0] == 'I'.code.toByte() && id3Check[1] == 'D'.code.toByte() && id3Check[2] == '3'.code.toByte()) {
                    raf.seek(6)
                    val s0 = raf.read() and 0x7F
                    val s1 = raf.read() and 0x7F
                    val s2 = raf.read() and 0x7F
                    val s3 = raf.read() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    raf.seek(10L + tagSize)
                } else {
                    raf.seek(0)
                }

                val magic = ByteArray(4)
                if (raf.read(magic) != 4) return null
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() || magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()) {
                    return null
                }

                var isLast = false
                while (!isLast && raf.filePointer < raf.length()) {
                    val header = ByteArray(4)
                    if (raf.read(header) != 4) break
                    val b0 = header[0].toInt() and 0xFF
                    isLast = (b0 and 0x80) != 0
                    val blockType = b0 and 0x7F
                    val length = ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)

                    val blockStart = raf.filePointer
                    if (blockType == 4) { // VORBIS_COMMENT
                        if (length in 4..4_000_000) {
                            val commentData = ByteArray(length)
                            raf.readFully(commentData)
                            val lyrics = findLyricsInVorbisComment(commentData)
                            if (!lyrics.isNullOrBlank()) return lyrics
                        }
                    }
                    raf.seek(blockStart + length)
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Finds companion cover file in the same directory as the source file.
     */
    fun findCompanionCover(sourceFile: File): File? {
        val parent = sourceFile.parentFile ?: return null
        val candidates = listOf(
            File(parent, "${sourceFile.nameWithoutExtension}.jpg"),
            File(parent, "${sourceFile.nameWithoutExtension}.jpeg"),
            File(parent, "${sourceFile.nameWithoutExtension}.png"),
            File(parent, "cover.jpg"),
            File(parent, "cover.png"),
            File(parent, "folder.jpg"),
            File(parent, "folder.png"),
            File(parent, "front.jpg"),
            File(parent, "front.png")
        )
        return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }

    /**
     * Finds companion .lrc file in the same directory as the source file.
     */
    fun findCompanionLrc(sourceFile: File): File? {
        val parent = sourceFile.parentFile ?: return null
        val candidates = listOf(
            File(parent, "${sourceFile.nameWithoutExtension}.lrc"),
            File(parent, "${sourceFile.nameWithoutExtension}.LRC")
        )
        return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun findLyricsInVorbisComment(bytes: ByteArray): String? {
        if (bytes.size < 8) return null
        return try {
            var offset = 0
            val vendorLen = readInt32LE(bytes, offset); offset += 4 + vendorLen
            if (offset + 4 > bytes.size) return null
            val count = readInt32LE(bytes, offset); offset += 4

            for (i in 0 until minOf(count, 500)) {
                if (offset + 4 > bytes.size) break
                val len = readInt32LE(bytes, offset); offset += 4
                if (len < 0 || offset + len > bytes.size) break
                val comment = String(bytes, offset, len, Charsets.UTF_8)
                offset += len
                val eqIdx = comment.indexOf('=')
                if (eqIdx > 0) {
                    val key = comment.substring(0, eqIdx).trim().uppercase()
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "UNSYNCED LYRICS" || key == "SYNCED LYRICS") {
                        val value = comment.substring(eqIdx + 1).trim()
                        if (value.isNotBlank()) return value
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun readInt32LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}
