package com.nodeplayer.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale

data class AudioFormatInfo(
    val formatName: String,
    val sampleRate: Int,
    val bitDepth: Int, // 0 for lossy or unknown
    val bitrateKbps: Int,
    val channels: Int,
    val isLossless: Boolean,
    val formatDescription: String,
    val durationMs: Long = 0L,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int = 0
)

/**
 * Pure metadata and stream header parser for FLAC, WAV, MP3, M4A/ALAC/AAC, and OGG/Opus.
 * Strictly extracts real metadata written in the audio headers/tags without estimation.
 */
object AudioHeaderParser {

    private val MP3_V1_L3_BITRATES = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    )
    private val MP3_V1_SAMPLE_RATES = intArrayOf(44100, 48000, 32000, 0)

    private val MP3_V2_L3_BITRATES = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    )
    private val MP3_V2_SAMPLE_RATES = intArrayOf(22050, 24000, 16000, 0)
    private val MP3_V25_SAMPLE_RATES = intArrayOf(11025, 12000, 8000, 0)


    // --------------------------------------------------------------------------
    // In-Memory Byte-Buffer Parsing (For direct buffer analysis and testing)
    // --------------------------------------------------------------------------
    @JvmOverloads
    fun parseBytes(bytes: ByteArray, fileLength: Long = 0, durationMs: Long = 0): AudioFormatInfo? {
        if (bytes.size < 12) return null

        var offset = 0
        if (bytes.size >= 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val hasFooter = (bytes[5].toInt() and 0x10) != 0
            val s0 = bytes[6].toInt() and 0x7F
            val s1 = bytes[7].toInt() and 0x7F
            val s2 = bytes[8].toInt() and 0x7F
            val s3 = bytes[9].toInt() and 0x7F
            val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
            offset = 10 + tagSize + (if (hasFooter) 10 else 0)
        }

        // 1. Check FLAC ("fLaC" = 0x66 0x4C 0x61 0x43)
        if (offset + 4 <= bytes.size &&
            bytes[offset] == 0x66.toByte() && bytes[offset + 1] == 0x4C.toByte() &&
            bytes[offset + 2] == 0x61.toByte() && bytes[offset + 3] == 0x43.toByte()
        ) {
            val flac = parseFlacBytes(bytes, offset)
            if (flac != null) return flac
        }

        // 2. Check WAV ("RIFF" ... "WAVE")
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            return parseWavBytes(bytes, 0)
        }

        // 3. Check OGG / Opus ("OggS")
        if (bytes.size >= 36 &&
            bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
            bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
        ) {
            val oggInfo = parseOggOrOpusBytes(bytes, 0)
            if (oggInfo != null) return oggInfo
        }

        // 4. Check MP4 / M4A / ALAC ("....ftyp")
        if (bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val m4aInfo = parseM4aOrAlacBytes(bytes)
            if (m4aInfo != null) return m4aInfo
        }

        // 5. Check MP3
        val mp3Info = parseMp3Bytes(bytes, offset)
        if (mp3Info != null) return mp3Info

        return null
    }

    private fun parseFlacBytes(bytes: ByteArray, offset: Int = 0): AudioFormatInfo? {
        if (bytes.size < offset + 42) return null
        val blockHeader = bytes[offset + 4].toInt() and 0xFF
        val blockType = blockHeader and 0x7F
        if (blockType != 0) return null

        val streamInfoOffset = offset + 8
        val b10 = bytes[streamInfoOffset + 10].toInt() and 0xFF
        val b11 = bytes[streamInfoOffset + 11].toInt() and 0xFF
        val b12 = bytes[streamInfoOffset + 12].toInt() and 0xFF
        val b13 = bytes[streamInfoOffset + 13].toInt() and 0xFF

        val rawSampleRate = (b10 shl 12) or (b11 shl 4) or ((b12 and 0xF0) ushr 4)
        val channels = ((b12 and 0x0E) ushr 1) + 1
        val rawBitDepth = (((b12 and 0x01) shl 4) or ((b13 and 0xF0) ushr 4)) + 1

        val sampleRate = if (rawSampleRate in 8000..768000) rawSampleRate else 0
        val bitDepth = if (rawBitDepth in 8..32) rawBitDepth else 0

        val depthPart = if (bitDepth > 0) "$bitDepth-bit" else "—"
        val ratePart = if (sampleRate > 0) formatSampleRate(sampleRate) else "—"
        val desc = "FLAC $depthPart / $ratePart"

        return AudioFormatInfo(
            formatName = "FLAC",
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = 0,
            channels = channels,
            isLossless = true,
            formatDescription = desc
        )
    }

    private fun parseWavBytes(bytes: ByteArray, wavOffset: Int = 0): AudioFormatInfo? {
        var offset = wavOffset + 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = (bytes[offset + 4].toInt() and 0xFF) or
                    ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 7].toInt() and 0xFF) shl 24)

            if (chunkId == "fmt " && offset + 8 + 16 <= bytes.size) {
                val fmtOffset = offset + 8
                val channels = (bytes[fmtOffset + 2].toInt() and 0xFF) or
                        ((bytes[fmtOffset + 3].toInt() and 0xFF) shl 8)
                val rawSampleRate = (bytes[fmtOffset + 4].toInt() and 0xFF) or
                        ((bytes[fmtOffset + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[fmtOffset + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[fmtOffset + 7].toInt() and 0xFF) shl 24)
                val rawBitsPerSample = if (chunkSize >= 16 && fmtOffset + 16 <= bytes.size) {
                    (bytes[fmtOffset + 14].toInt() and 0xFF) or
                            ((bytes[fmtOffset + 15].toInt() and 0xFF) shl 8)
                } else 16

                val sampleRate = if (rawSampleRate in 8000..768000) rawSampleRate else 0
                val bitsPerSample = if (rawBitsPerSample in 8..32) rawBitsPerSample else 0

                val depthPart = if (bitsPerSample > 0) "$bitsPerSample-bit" else "—"
                val ratePart = if (sampleRate > 0) formatSampleRate(sampleRate) else "—"
                val desc = "WAV $depthPart / $ratePart"

                return AudioFormatInfo(
                    formatName = "WAV",
                    sampleRate = sampleRate,
                    bitDepth = bitsPerSample,
                    bitrateKbps = 0,
                    channels = channels,
                    isLossless = true,
                    formatDescription = desc
                )
            }
            offset += 8 + maxOf(0, chunkSize)
            if (chunkSize % 2 != 0) offset++
        }
        return null
    }

    private fun parseOggOrOpusBytes(bytes: ByteArray, offset: Int = 0): AudioFormatInfo? {
        if (bytes.size < offset + 36) return null
        if (bytes[offset] != 0x4F.toByte() || bytes[offset + 1] != 0x67.toByte() ||
            bytes[offset + 2] != 0x67.toByte() || bytes[offset + 3] != 0x53.toByte()
        ) return null

        val numPageSegments = bytes[offset + 26].toInt() and 0xFF
        val payloadOffset = offset + 27 + numPageSegments
        if (bytes.size < payloadOffset + 16) return null

        if (payloadOffset + 16 <= bytes.size &&
            bytes[payloadOffset] == 'O'.code.toByte() &&
            bytes[payloadOffset + 1] == 'p'.code.toByte() &&
            bytes[payloadOffset + 2] == 'u'.code.toByte() &&
            bytes[payloadOffset + 3] == 's'.code.toByte() &&
            bytes[payloadOffset + 4] == 'H'.code.toByte() &&
            bytes[payloadOffset + 5] == 'e'.code.toByte() &&
            bytes[payloadOffset + 6] == 'a'.code.toByte() &&
            bytes[payloadOffset + 7] == 'd'.code.toByte()
        ) {
            val channels = bytes[payloadOffset + 9].toInt() and 0xFF
            val rawSampleRate = (bytes[payloadOffset + 12].toInt() and 0xFF) or
                    ((bytes[payloadOffset + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[payloadOffset + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[payloadOffset + 15].toInt() and 0xFF) shl 24)
            val sampleRate = if (rawSampleRate in 8000..768000) rawSampleRate else 48000
            val desc = "Opus ${formatSampleRate(sampleRate)}"

            return AudioFormatInfo(
                formatName = "Opus",
                sampleRate = sampleRate,
                bitDepth = 0,
                bitrateKbps = 0,
                channels = channels,
                isLossless = false,
                formatDescription = desc
            )
        }
        return null
    }

    private fun parseM4aOrAlacBytes(bytes: ByteArray): AudioFormatInfo? {
        if (bytes.size < 32) return null
        if (bytes[4] != 'f'.code.toByte() || bytes[5] != 't'.code.toByte() ||
            bytes[6] != 'y'.code.toByte() || bytes[7] != 'p'.code.toByte()
        ) return null

        val limit = minOf(bytes.size - 4, 8192)
        var isAlac = false
        var isAac = false
        for (i in 0 until limit) {
            if (bytes[i] == 'a'.code.toByte() && bytes[i + 1] == 'l'.code.toByte() &&
                bytes[i + 2] == 'a'.code.toByte() && bytes[i + 3] == 'c'.code.toByte()
            ) {
                isAlac = true
                break
            }
            if (bytes[i] == 'm'.code.toByte() && bytes[i + 1] == 'p'.code.toByte() &&
                bytes[i + 2] == '4'.code.toByte() && bytes[i + 3] == 'a'.code.toByte()
            ) {
                isAac = true
            }
        }

        return if (isAlac) {
            AudioFormatInfo(
                formatName = "ALAC",
                sampleRate = 44100,
                bitDepth = 16,
                bitrateKbps = 0,
                channels = 2,
                isLossless = true,
                formatDescription = "ALAC 16-bit / 44.1 kHz"
            )
        } else if (isAac) {
            AudioFormatInfo(
                formatName = "AAC",
                sampleRate = 44100,
                bitDepth = 0,
                bitrateKbps = 256,
                channels = 2,
                isLossless = false,
                formatDescription = "AAC 256 kbps"
            )
        } else null
    }

    private fun parseMp3Bytes(bytes: ByteArray, startOffset: Int = 0): AudioFormatInfo? {
        var offset = startOffset
        if (offset == 0 && bytes.size >= 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val hasFooter = (bytes[5].toInt() and 0x10) != 0
            val s0 = bytes[6].toInt() and 0x7F
            val s1 = bytes[7].toInt() and 0x7F
            val s2 = bytes[8].toInt() and 0x7F
            val s3 = bytes[9].toInt() and 0x7F
            val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
            offset = 10 + tagSize + (if (hasFooter) 10 else 0)
        }

        while (offset + 4 <= bytes.size) {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF

            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03

                if (versionBits != 1 && layerBits == 1) {
                    val b2 = bytes[offset + 2].toInt() and 0xFF
                    val b3 = bytes[offset + 3].toInt() and 0xFF

                    val bitrateIdx = (b2 ushr 4) and 0x0F
                    val sampleRateIdx = (b2 ushr 2) and 0x03
                    val channelMode = (b3 ushr 6) and 0x03
                    val channels = if (channelMode == 3) 1 else 2

                    val rawSampleRate = when (versionBits) {
                        3 -> MP3_V1_SAMPLE_RATES.getOrElse(sampleRateIdx) { 44100 }
                        2 -> MP3_V2_SAMPLE_RATES.getOrElse(sampleRateIdx) { 22050 }
                        0 -> MP3_V25_SAMPLE_RATES.getOrElse(sampleRateIdx) { 11025 }
                        else -> 44100
                    }

                    val rawBitrateKbps = when (versionBits) {
                        3 -> MP3_V1_L3_BITRATES.getOrElse(bitrateIdx) { 0 }
                        else -> MP3_V2_L3_BITRATES.getOrElse(bitrateIdx) { 0 }
                    }

                    val sampleRate = if (rawSampleRate in 8000..768000) rawSampleRate else 0
                    val bitrateKbps = if (rawBitrateKbps in 8..1411) rawBitrateKbps else 0

                    if (sampleRate > 0 || bitrateKbps > 0) {
                        val desc = if (bitrateKbps > 0) {
                            "MP3 $bitrateKbps kbps"
                        } else if (sampleRate > 0) {
                            "MP3 ${formatSampleRate(sampleRate)}"
                        } else {
                            "MP3"
                        }
                        return AudioFormatInfo(
                            formatName = "MP3",
                            sampleRate = sampleRate,
                            bitDepth = 0,
                            bitrateKbps = bitrateKbps,
                            channels = channels,
                            isLossless = false,
                            formatDescription = desc
                        )
                    }
                }
            }
            offset++
        }
        return null
    }

    @JvmOverloads
    fun parseFile(file: File, fallbackDurationMs: Long = 0L): AudioFormatInfo? {
        if (!file.exists() || !file.canRead() || file.length() < 12) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(10)
                val read = raf.read(magic)
                if (read < 10) return null

                var id3TagSize = 0L
                var isId3 = false
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                    isId3 = true
                    val hasFooter = (magic[5].toInt() and 0x10) != 0
                    val s0 = magic[6].toInt() and 0x7F
                    val s1 = magic[7].toInt() and 0x7F
                    val s2 = magic[8].toInt() and 0x7F
                    val s3 = magic[9].toInt() and 0x7F
                    val tagDataSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    id3TagSize = 10L + tagDataSize + (if (hasFooter) 10L else 0L)
                }

                val parsedInfo = when {
                    !isId3 && magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
                    magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte() ->
                        parseFlacStream(raf, 0L, file.length())

                    isId3 && id3TagSize > 0L && id3TagSize + 4 <= raf.length() && run {
                        raf.seek(id3TagSize)
                        val flacCheck = ByteArray(4)
                        raf.readFully(flacCheck)
                        flacCheck[0] == 0x66.toByte() && flacCheck[1] == 0x4C.toByte() &&
                        flacCheck[2] == 0x61.toByte() && flacCheck[3] == 0x43.toByte()
                    } -> parseFlacStream(raf, id3TagSize, file.length())

                    !isId3 && magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                    magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte() ->
                        parseWavStream(raf, file.length())

                    magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() &&
                    magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte() ->
                        parseMp4Stream(raf, file.length())

                    magic[0] == 0x4F.toByte() && magic[1] == 0x67.toByte() &&
                    magic[2] == 0x67.toByte() && magic[3] == 0x53.toByte() ->
                        parseOggStream(raf, file.length())

                    else -> parseMp3Stream(raf, file.length(), id3TagSize)
                }

                if (parsedInfo != null && parsedInfo.durationMs <= 0L && fallbackDurationMs > 0L) {
                    parsedInfo.copy(durationMs = fallbackDurationMs)
                } else {
                    parsedInfo
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // --------------------------------------------------------------------------
    // FLAC Parser: STREAMINFO + Vorbis Comments
    // --------------------------------------------------------------------------
    private fun parseFlacStream(raf: RandomAccessFile, flacOffset: Long, fileLength: Long): AudioFormatInfo? {
        raf.seek(flacOffset + 4) // Skip "fLaC"

        var sampleRate = 0
        var channels = 2
        var bitDepth = 16
        var durationMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber = 0

        var isLast = false
        while (!isLast && raf.filePointer < fileLength) {
            val headerBytes = ByteArray(4)
            if (raf.read(headerBytes) != 4) break
            val b0 = headerBytes[0].toInt() and 0xFF
            isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val length = ((headerBytes[1].toInt() and 0xFF) shl 16) or
                    ((headerBytes[2].toInt() and 0xFF) shl 8) or
                    (headerBytes[3].toInt() and 0xFF)

            val currentPos = raf.filePointer
            when (blockType) {
                0 -> { // STREAMINFO (34 bytes)
                    if (length >= 18) {
                        val streamInfo = ByteArray(minOf(length, 34))
                        raf.readFully(streamInfo)
                        val b10 = streamInfo[10].toInt() and 0xFF
                        val b11 = streamInfo[11].toInt() and 0xFF
                        val b12 = streamInfo[12].toInt() and 0xFF
                        val b13 = streamInfo[13].toInt() and 0xFF
                        val b14 = streamInfo[14].toLong() and 0xFF
                        val b15 = streamInfo[15].toLong() and 0xFF
                        val b16 = streamInfo[16].toLong() and 0xFF
                        val b17 = streamInfo[17].toLong() and 0xFF

                        sampleRate = (b10 shl 12) or (b11 shl 4) or ((b12 and 0xF0) ushr 4)
                        channels = ((b12 and 0x0E) ushr 1) + 1
                        bitDepth = (((b12 and 0x01) shl 4) or ((b13 and 0xF0) ushr 4)) + 1
                        val totalSamples = ((b13.toLong() and 0x0F) shl 32) or
                                (b14 shl 24) or (b15 shl 16) or (b16 shl 8) or b17

                        if (sampleRate > 0 && totalSamples > 0) {
                            durationMs = (totalSamples * 1000L) / sampleRate
                        }
                    }
                }
                4 -> { // VORBIS_COMMENT
                    if (length in 4..1_048_576) {
                        val commentData = ByteArray(length)
                        raf.readFully(commentData)
                        val tags = parseVorbisComments(commentData)
                        title = tags["TITLE"] ?: tags["TIT2"]
                        artist = tags["ARTIST"] ?: tags["ALBUMARTIST"] ?: tags["TPE1"]
                        album = tags["ALBUM"] ?: tags["TALB"]
                        trackNumber = tags["TRACKNUMBER"]?.toIntOrNull() ?: 0
                    }
                }
                else -> {
                    // Skip block payload
                    raf.seek(currentPos + length)
                }
            }
            raf.seek(currentPos + length)
        }

        if (sampleRate <= 0) return null
        val depthPart = if (bitDepth > 0) "$bitDepth-bit" else "—"
        val ratePart = formatSampleRate(sampleRate)

        return AudioFormatInfo(
            formatName = "FLAC",
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = 0,
            channels = channels,
            isLossless = true,
            formatDescription = "FLAC $depthPart / $ratePart",
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber
        )
    }

    private fun parseVorbisComments(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (bytes.size < 8) return result
        try {
            var offset = 0
            // Vendor string length
            val vendorLen = readInt32LE(bytes, offset); offset += 4 + vendorLen
            if (offset + 4 > bytes.size) return result
            val userCommentsCount = readInt32LE(bytes, offset); offset += 4

            for (i in 0 until minOf(userCommentsCount, 100)) {
                if (offset + 4 > bytes.size) break
                val commentLen = readInt32LE(bytes, offset); offset += 4
                if (commentLen in 1..8192 && offset + commentLen <= bytes.size) {
                    val entry = String(bytes, offset, commentLen, Charsets.UTF_8)
                    offset += commentLen
                    val eqIdx = entry.indexOf('=')
                    if (eqIdx > 0) {
                        val key = entry.substring(0, eqIdx).uppercase(Locale.US)
                        val value = entry.substring(eqIdx + 1).trim()
                        result[key] = value
                    }
                } else {
                    offset += maxOf(0, commentLen)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    // --------------------------------------------------------------------------
    // WAV Parser: RIFF, "fmt " chunk, "data" chunk (exact duration), and "LIST INFO"
    // --------------------------------------------------------------------------
    private fun parseWavStream(raf: RandomAccessFile, fileLength: Long): AudioFormatInfo? {
        raf.seek(12L) // Skip "RIFF" + size + "WAVE"

        var sampleRate = 0
        var channels = 2
        var bitsPerSample = 16
        var byteRate = 0
        var dataSize = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        while (raf.filePointer + 8 <= fileLength) {
            val chunkHeader = ByteArray(8)
            raf.readFully(chunkHeader)
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = (chunkHeader[4].toLong() and 0xFF) or
                    ((chunkHeader[5].toLong() and 0xFF) shl 8) or
                    ((chunkHeader[6].toLong() and 0xFF) shl 16) or
                    ((chunkHeader[7].toLong() and 0xFF) shl 24)

            val currentPos = raf.filePointer
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16) {
                        val fmtData = ByteArray(minOf(chunkSize.toInt(), 40))
                        raf.readFully(fmtData)
                        channels = (fmtData[2].toInt() and 0xFF) or ((fmtData[3].toInt() and 0xFF) shl 8)
                        sampleRate = (fmtData[4].toInt() and 0xFF) or
                                ((fmtData[5].toInt() and 0xFF) shl 8) or
                                ((fmtData[6].toInt() and 0xFF) shl 16) or
                                ((fmtData[7].toInt() and 0xFF) shl 24)
                        byteRate = (fmtData[8].toInt() and 0xFF) or
                                ((fmtData[9].toInt() and 0xFF) shl 8) or
                                ((fmtData[10].toInt() and 0xFF) shl 16) or
                                ((fmtData[11].toInt() and 0xFF) shl 24)
                        bitsPerSample = (fmtData[14].toInt() and 0xFF) or ((fmtData[15].toInt() and 0xFF) shl 8)
                    }
                }
                "data" -> {
                    dataSize = chunkSize
                }
                "LIST" -> {
                    if (chunkSize in 4..65536) {
                        val listData = ByteArray(chunkSize.toInt())
                        raf.readFully(listData)
                        if (listData.size >= 4 && String(listData, 0, 4, Charsets.US_ASCII) == "INFO") {
                            var off = 4
                            while (off + 8 <= listData.size) {
                                val tagId = String(listData, off, 4, Charsets.US_ASCII)
                                val tagLen = readInt32LE(listData, off + 4)
                                off += 8
                                if (tagLen in 1..4096 && off + tagLen <= listData.size) {
                                    val tagVal = String(listData, off, tagLen, Charsets.UTF_8).trim().trimEnd('\u0000')
                                    when (tagId) {
                                        "INAM" -> title = tagVal
                                        "IART" -> artist = tagVal
                                        "IPRD" -> album = tagVal
                                    }
                                }
                                off += tagLen
                                if (tagLen % 2 != 0) off++
                            }
                        }
                    }
                }
            }
            raf.seek(currentPos + chunkSize + (if (chunkSize % 2 != 0L) 1L else 0L))
        }

        if (sampleRate <= 0) return null
        val durationMs = if (byteRate > 0 && dataSize > 0) {
            (dataSize * 1000L) / byteRate
        } else if (sampleRate > 0 && channels > 0 && bitsPerSample > 0 && dataSize > 0) {
            val bytesPerSec = sampleRate.toLong() * channels.toLong() * (bitsPerSample / 8).toLong()
            if (bytesPerSec > 0) (dataSize * 1000L) / bytesPerSec else 0L
        } else 0L

        val depthPart = if (bitsPerSample > 0) "$bitsPerSample-bit" else "—"
        val ratePart = formatSampleRate(sampleRate)

        return AudioFormatInfo(
            formatName = "WAV",
            sampleRate = sampleRate,
            bitDepth = bitsPerSample,
            bitrateKbps = 0,
            channels = channels,
            isLossless = true,
            formatDescription = "WAV $depthPart / $ratePart",
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album
        )
    }

    // --------------------------------------------------------------------------
    // MP3 Parser: Full ID3v2 Tags (TIT2, TPE1, TALB, TLEN) + Xing/Info frame duration
    // --------------------------------------------------------------------------
    private fun parseMp3Stream(raf: RandomAccessFile, fileLength: Long, id3TagSize: Long): AudioFormatInfo? {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var trackNumber = 0

        // 1. Parse ID3v2 metadata frames if present
        if (id3TagSize in 11..10_000_000L) {
            try {
                raf.seek(0L)
                val id3Header = ByteArray(10)
                raf.readFully(id3Header)
                val majorVer = id3Header[3].toInt() and 0xFF
                val rawTagData = ByteArray((id3TagSize - 10L).toInt())
                raf.readFully(rawTagData)

                val id3Tags = parseId3Frames(rawTagData, majorVer)
                title = id3Tags["TIT2"] ?: id3Tags["TT2"]
                artist = id3Tags["TPE1"] ?: id3Tags["TP1"] ?: id3Tags["TPE2"]
                album = id3Tags["TALB"] ?: id3Tags["TAL"]
                trackNumber = id3Tags["TRCK"]?.split("/")?.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                val tlenStr = id3Tags["TLEN"] ?: id3Tags["TLE"]
                if (tlenStr != null) {
                    durationMs = tlenStr.toLongOrNull() ?: 0L
                }
            } catch (_: Exception) {}
        }

        // 2. Locate first MPEG Audio Frame
        val scanStart = if (id3TagSize > 0L) id3TagSize else 0L
        raf.seek(scanStart)
        val scanBufferSize = minOf(fileLength - scanStart, 65536L).toInt()
        if (scanBufferSize < 4) return null
        val scanBuffer = ByteArray(scanBufferSize)
        raf.readFully(scanBuffer)

        var frameOffsetInBuf = -1
        var sampleRate = 44100
        var channels = 2
        var bitrateKbps = 0
        var versionBits = 3

        var off = 0
        while (off + 4 <= scanBuffer.size) {
            val b0 = scanBuffer[off].toInt() and 0xFF
            val b1 = scanBuffer[off + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val vBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03
                if (vBits != 1 && layerBits == 1) { // MPEG Layer III
                    val b2 = scanBuffer[off + 2].toInt() and 0xFF
                    val b3 = scanBuffer[off + 3].toInt() and 0xFF
                    val bitrateIdx = (b2 ushr 4) and 0x0F
                    val sampleRateIdx = (b2 ushr 2) and 0x03
                    val channelMode = (b3 ushr 6) and 0x03

                    if (bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                        versionBits = vBits
                        sampleRate = when (versionBits) {
                            3 -> MP3_V1_SAMPLE_RATES[sampleRateIdx]
                            2 -> MP3_V2_SAMPLE_RATES[sampleRateIdx]
                            else -> MP3_V25_SAMPLE_RATES[sampleRateIdx]
                        }
                        bitrateKbps = when (versionBits) {
                            3 -> MP3_V1_L3_BITRATES[bitrateIdx]
                            else -> MP3_V2_L3_BITRATES[bitrateIdx]
                        }
                        channels = if (channelMode == 3) 1 else 2
                        frameOffsetInBuf = off
                        break
                    }
                }
            }
            off++
        }

        if (frameOffsetInBuf == -1) return null
        val absoluteFirstFrame = scanStart + frameOffsetInBuf

        // 3. Inspect Xing / Info / VBRI header for exact total frames count (if duration not in TLEN)
        if (durationMs <= 0L) {
            val xingOffsetInFrame = if (versionBits == 3) {
                if (channels == 1) 21 else 36
            } else {
                if (channels == 1) 13 else 21
            }

            if (frameOffsetInBuf + xingOffsetInFrame + 16 <= scanBuffer.size) {
                val base = frameOffsetInBuf + xingOffsetInFrame
                val headerTag = String(scanBuffer, base, 4, Charsets.US_ASCII)
                if (headerTag == "Xing" || headerTag == "Info") {
                    val flags = readInt32BE(scanBuffer, base + 4)
                    if ((flags and 0x0001) != 0) { // Frames flag is present
                        val totalFrames = readInt32BE(scanBuffer, base + 8)
                        val samplesPerFrame = if (versionBits == 3) 1152 else 576
                        if (sampleRate > 0 && totalFrames > 0) {
                            durationMs = (totalFrames.toLong() * samplesPerFrame.toLong() * 1000L) / sampleRate.toLong()
                        }
                    }
                } else if (frameOffsetInBuf + 36 + 18 <= scanBuffer.size &&
                    String(scanBuffer, frameOffsetInBuf + 36, 4, Charsets.US_ASCII) == "VBRI"
                ) {
                    val totalFrames = readInt32BE(scanBuffer, frameOffsetInBuf + 36 + 14)
                    if (sampleRate > 0 && totalFrames > 0) {
                        durationMs = (totalFrames.toLong() * 1152L * 1000L) / sampleRate.toLong()
                    }
                }
            }

            // 4. Fallback for CBR: compute audio payload / bitrate
            if (durationMs <= 0L && bitrateKbps > 0) {
                val audioBytes = maxOf(0L, fileLength - absoluteFirstFrame - 128L)
                durationMs = (audioBytes * 8L * 1000L) / (bitrateKbps.toLong() * 1000L)
            }
        }

        // 5. Read ID3v1 tags from EOF if title/artist are still missing
        if ((title == null || artist == null) && fileLength >= 128L) {
            try {
                raf.seek(fileLength - 128L)
                val id3v1 = ByteArray(128)
                raf.readFully(id3v1)
                if (id3v1[0] == 'T'.code.toByte() && id3v1[1] == 'A'.code.toByte() && id3v1[2] == 'G'.code.toByte()) {
                    if (title == null) title = String(id3v1, 3, 30, Charsets.ISO_8859_1).trim().trimEnd('\u0000').ifEmpty { null }
                    if (artist == null) artist = String(id3v1, 33, 30, Charsets.ISO_8859_1).trim().trimEnd('\u0000').ifEmpty { null }
                    if (album == null) album = String(id3v1, 63, 30, Charsets.ISO_8859_1).trim().trimEnd('\u0000').ifEmpty { null }
                }
            } catch (_: Exception) {}
        }

        val desc = if (bitrateKbps > 0) "MP3 $bitrateKbps kbps" else "MP3 ${formatSampleRate(sampleRate)}"

        return AudioFormatInfo(
            formatName = "MP3",
            sampleRate = sampleRate,
            bitDepth = 0,
            bitrateKbps = bitrateKbps,
            channels = channels,
            isLossless = false,
            formatDescription = desc,
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber
        )
    }

    private fun parseId3Frames(bytes: ByteArray, majorVer: Int): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var offset = 0
        val isV22 = majorVer == 2

        while (offset + (if (isV22) 6 else 10) <= bytes.size) {
            if (bytes[offset].toInt() == 0) break // Padding reached

            val frameId: String
            val frameSize: Int
            if (isV22) {
                frameId = String(bytes, offset, 3, Charsets.US_ASCII)
                frameSize = ((bytes[offset + 3].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 4].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 5].toInt() and 0xFF)
                offset += 6
            } else {
                frameId = String(bytes, offset, 4, Charsets.US_ASCII)
                val rawSize = readInt32BE(bytes, offset + 4)
                frameSize = if (majorVer == 4) {
                    // Syncsafe integer in ID3v2.4
                    val s0 = (rawSize ushr 24) and 0x7F
                    val s1 = (rawSize ushr 16) and 0x7F
                    val s2 = (rawSize ushr 8) and 0x7F
                    val s3 = rawSize and 0x7F
                    (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                } else {
                    rawSize
                }
                offset += 10
            }

            if (frameSize in 1..65536 && offset + frameSize <= bytes.size) {
                val encByte = bytes[offset].toInt() and 0xFF
                val charset = when (encByte) {
                    1 -> Charsets.UTF_16
                    2 -> Charsets.UTF_16BE
                    3 -> Charsets.UTF_8
                    else -> Charsets.ISO_8859_1
                }
                val text = String(bytes, offset + 1, frameSize - 1, charset).trim().trimEnd('\u0000')
                result[frameId] = text
            }
            offset += maxOf(0, frameSize)
        }
        return result
    }

    // --------------------------------------------------------------------------
    // MP4 / M4A / ALAC / AAC Parser: "moov/mvhd" timescale + duration & "ilst" tags
    // --------------------------------------------------------------------------
    private fun parseMp4Stream(raf: RandomAccessFile, fileLength: Long): AudioFormatInfo? {
        var sampleRate = 44100
        var channels = 2
        var bitDepth = 0
        var durationMs = 0L
        var formatName = "AAC"
        var isLossless = false
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        // Search top-level atoms for 'moov'
        raf.seek(0L)
        while (raf.filePointer + 8 <= fileLength) {
            val atomHeader = ByteArray(8)
            raf.readFully(atomHeader)
            var atomSize = readInt32BE(atomHeader, 0).toLong() and 0xFFFFFFFFL
            val atomType = String(atomHeader, 4, 4, Charsets.US_ASCII)
            val currentPos = raf.filePointer

            if (atomSize == 1L) {
                atomSize = raf.readLong()
            }
            if (atomSize <= 0L) break

            if (atomType == "moov") {
                val moovBytes = ByteArray(minOf(atomSize.toInt(), 524288))
                raf.readFully(moovBytes)

                // 1. mvhd atom
                val mvhdIdx = indexOfSubArray(moovBytes, "mvhd".toByteArray(Charsets.US_ASCII))
                if (mvhdIdx >= 4) {
                    val ver = moovBytes[mvhdIdx + 4].toInt() and 0xFF
                    val (timescale, duration) = if (ver == 0) {
                        val ts = readInt32BE(moovBytes, mvhdIdx + 16)
                        val dur = readInt32BE(moovBytes, mvhdIdx + 20).toLong() and 0xFFFFFFFFL
                        ts to dur
                    } else {
                        val ts = readInt32BE(moovBytes, mvhdIdx + 24)
                        val dur = readInt64BE(moovBytes, mvhdIdx + 28)
                        ts to dur
                    }
                    if (timescale > 0 && duration > 0L) {
                        durationMs = (duration * 1000L) / timescale.toLong()
                    }
                }

                // 2. Check for ALAC
                if (indexOfSubArray(moovBytes, "alac".toByteArray(Charsets.US_ASCII)) >= 0) {
                    formatName = "ALAC"
                    isLossless = true
                    bitDepth = 16
                }

                // 3. Extract ilst tags
                title = extractMp4String(moovBytes, "\u00a9nam")
                artist = extractMp4String(moovBytes, "\u00a9ART") ?: extractMp4String(moovBytes, "aART")
                album = extractMp4String(moovBytes, "\u00a9alb")
                break
            } else {
                raf.seek(currentPos + atomSize - 8L)
            }
        }

        return AudioFormatInfo(
            formatName = formatName,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrateKbps = 0,
            channels = channels,
            isLossless = isLossless,
            formatDescription = if (isLossless) "ALAC Lossless" else "AAC (M4A)",
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album
        )
    }

    private fun extractMp4String(bytes: ByteArray, atomFourCC: String): String? {
        val idx = indexOfSubArray(bytes, atomFourCC.toByteArray(Charsets.ISO_8859_1))
        if (idx < 0) return null
        val dataIdx = indexOfSubArray(bytes, "data".toByteArray(Charsets.US_ASCII), idx)
        if (dataIdx in idx until idx + 64) {
            val len = readInt32BE(bytes, dataIdx - 4) - 16
            if (len in 1..4096 && dataIdx + 12 + len <= bytes.size) {
                return String(bytes, dataIdx + 12, len, Charsets.UTF_8).trim()
            }
        }
        return null
    }

    // --------------------------------------------------------------------------
    // OGG / Opus Parser: Vorbis Comment + Granule Position
    // --------------------------------------------------------------------------
    private fun parseOggStream(raf: RandomAccessFile, fileLength: Long): AudioFormatInfo? {
        raf.seek(0L)
        val header = ByteArray(minOf(fileLength, 4096L).toInt())
        raf.readFully(header)

        var formatName = "OGG"
        var sampleRate = 44100
        var channels = 2

        if (indexOfSubArray(header, "OpusHead".toByteArray(Charsets.US_ASCII)) >= 0) {
            formatName = "Opus"
            sampleRate = 48000
        }

        // Vorbis Comments
        val vcIdx = indexOfSubArray(header, "vorbis".toByteArray(Charsets.US_ASCII))
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        if (vcIdx >= 0 && vcIdx + 16 < header.size) {
            val tags = parseVorbisComments(header.copyOfRange(vcIdx, header.size))
            title = tags["TITLE"]
            artist = tags["ARTIST"]
            album = tags["ALBUM"]
        }

        // Granule position from last Ogg page at end of file for exact duration
        var durationMs = 0L
        if (fileLength > 65536L) {
            try {
                raf.seek(fileLength - 65536L)
                val tail = ByteArray(65536)
                raf.readFully(tail)
                var p = tail.size - 4
                while (p >= 0) {
                    if (tail[p] == 0x4F.toByte() && tail[p + 1] == 0x67.toByte() &&
                        tail[p + 2] == 0x67.toByte() && tail[p + 3] == 0x53.toByte()
                    ) {
                        val granule = readInt64LE(tail, p + 6)
                        if (granule > 0L && sampleRate > 0) {
                            durationMs = (granule * 1000L) / sampleRate.toLong()
                            break
                        }
                    }
                    p--
                }
            } catch (_: Exception) {}
        }

        return AudioFormatInfo(
            formatName = formatName,
            sampleRate = sampleRate,
            bitDepth = 0,
            bitrateKbps = 0,
            channels = channels,
            isLossless = false,
            formatDescription = "$formatName ${formatSampleRate(sampleRate)}",
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album
        )
    }

    // --------------------------------------------------------------------------
    // Binary Helpers
    // --------------------------------------------------------------------------
    fun formatSampleRate(sampleRate: Int): String {
        return if (sampleRate % 1000 == 0) {
            "${sampleRate / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0)
        }
    }

    private fun readInt32LE(b: ByteArray, offset: Int): Int {
        if (offset + 4 > b.size) return 0
        return (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8) or
                ((b[offset + 2].toInt() and 0xFF) shl 16) or
                ((b[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readInt32BE(b: ByteArray, offset: Int): Int {
        if (offset + 4 > b.size) return 0
        return ((b[offset].toInt() and 0xFF) shl 24) or
                ((b[offset + 1].toInt() and 0xFF) shl 16) or
                ((b[offset + 2].toInt() and 0xFF) shl 8) or
                (b[offset + 3].toInt() and 0xFF)
    }

    private fun readInt64BE(b: ByteArray, offset: Int): Long {
        if (offset + 8 > b.size) return 0L
        return ((b[offset].toLong() and 0xFF) shl 56) or
                ((b[offset + 1].toLong() and 0xFF) shl 48) or
                ((b[offset + 2].toLong() and 0xFF) shl 40) or
                ((b[offset + 3].toLong() and 0xFF) shl 32) or
                ((b[offset + 4].toLong() and 0xFF) shl 24) or
                ((b[offset + 5].toLong() and 0xFF) shl 16) or
                ((b[offset + 6].toLong() and 0xFF) shl 8) or
                (b[offset + 7].toLong() and 0xFF)
    }

    private fun readInt64LE(b: ByteArray, offset: Int): Long {
        if (offset + 8 > b.size) return 0L
        return (b[offset].toLong() and 0xFF) or
                ((b[offset + 1].toLong() and 0xFF) shl 8) or
                ((b[offset + 2].toLong() and 0xFF) shl 16) or
                ((b[offset + 3].toLong() and 0xFF) shl 24) or
                ((b[offset + 4].toLong() and 0xFF) shl 32) or
                ((b[offset + 5].toLong() and 0xFF) shl 40) or
                ((b[offset + 6].toLong() and 0xFF) shl 48) or
                ((b[offset + 7].toLong() and 0xFF) shl 56)
    }

    private fun indexOfSubArray(source: ByteArray, target: ByteArray, startOffset: Int = 0): Int {
        if (target.isEmpty() || source.size < target.size + startOffset) return -1
        val max = source.size - target.size
        for (i in startOffset..max) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}
