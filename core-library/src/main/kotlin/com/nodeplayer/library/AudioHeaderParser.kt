package com.nodeplayer.library

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

data class AudioFormatInfo(
    val formatName: String,
    val sampleRate: Int,
    val bitDepth: Int, // 0 for lossy or unknown
    val bitrateKbps: Int,
    val channels: Int,
    val isLossless: Boolean,
    val formatDescription: String
)

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

    fun parseFile(file: File, durationMs: Long = 0): AudioFormatInfo? {
        if (!file.exists() || !file.canRead() || file.length() < 12) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(10)
                val read = raf.read(magic)
                if (read < 10) return null

                var audioStartOffset = 0L
                var isId3 = false
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                    isId3 = true
                    val hasFooter = (magic[5].toInt() and 0x10) != 0
                    val s0 = magic[6].toInt() and 0x7F
                    val s1 = magic[7].toInt() and 0x7F
                    val s2 = magic[8].toInt() and 0x7F
                    val s3 = magic[9].toInt() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    audioStartOffset = 10L + tagSize + (if (hasFooter) 10L else 0L)
                }

                // 1. Check FLAC ("fLaC"): at offset 0, or after ID3 tag
                if (!isId3) {
                    if (magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
                        magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte()
                    ) {
                        raf.seek(0L)
                        val buffer = ByteArray(minOf(raf.length(), 4096L).toInt())
                        raf.readFully(buffer)
                        return parseFlac(buffer, 0)
                    }
                } else if (audioStartOffset > 0L && audioStartOffset + 4 <= raf.length()) {
                    raf.seek(audioStartOffset)
                    val flacCheck = ByteArray(4)
                    raf.readFully(flacCheck)
                    if (flacCheck[0] == 0x66.toByte() && flacCheck[1] == 0x4C.toByte() &&
                        flacCheck[2] == 0x61.toByte() && flacCheck[3] == 0x43.toByte()
                    ) {
                        raf.seek(audioStartOffset)
                        val buffer = ByteArray(minOf(raf.length() - audioStartOffset, 4096L).toInt())
                        raf.readFully(buffer)
                        return parseFlac(buffer, 0)
                    }
                }

                // 2. Check WAV ("RIFF" ... "WAVE")
                if (!isId3 && magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                    magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()
                ) {
                    raf.seek(0L)
                    val buffer = ByteArray(minOf(raf.length(), 4096L).toInt())
                    raf.readFully(buffer)
                    return parseWav(buffer, 0)
                }

                // 3. MP3: Check after ID3v2 tag (or start)
                if (audioStartOffset > 0L && audioStartOffset < raf.length()) {
                    raf.seek(audioStartOffset)
                    val buffer = ByteArray(minOf(raf.length() - audioStartOffset, 16384L).toInt())
                    raf.readFully(buffer)
                    val mp3Info = parseMp3(buffer, 0)
                    if (mp3Info != null) return mp3Info
                }

                // Fallback: search initial 64 KB
                raf.seek(0L)
                val buffer = ByteArray(minOf(raf.length(), 65536L).toInt())
                raf.readFully(buffer)
                parseBytes(buffer, file.length(), durationMs)
            }
        } catch (_: Exception) {
            null
        }
    }

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
            val flac = parseFlac(bytes, offset)
            if (flac != null) return flac
        }

        // 2. Check WAV ("RIFF" ... "WAVE")
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            return parseWav(bytes, 0)
        }

        // 3. Check OGG / Opus ("OggS")
        if (bytes.size >= 36 &&
            bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
            bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
        ) {
            val oggInfo = parseOggOrOpus(bytes, 0)
            if (oggInfo != null) return oggInfo
        }

        // 4. Check MP4 / M4A / ALAC ("....ftyp")
        if (bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val m4aInfo = parseM4aOrAlac(bytes)
            if (m4aInfo != null) return m4aInfo
        }

        // 5. Check MP3
        val mp3Info = parseMp3(bytes, offset)
        if (mp3Info != null) return mp3Info

        return null
    }

    private fun parseOggOrOpus(bytes: ByteArray, offset: Int = 0): AudioFormatInfo? {
        if (bytes.size < offset + 36) return null
        if (bytes[offset] != 0x4F.toByte() || bytes[offset + 1] != 0x67.toByte() ||
            bytes[offset + 2] != 0x67.toByte() || bytes[offset + 3] != 0x53.toByte()
        ) return null

        val numPageSegments = bytes[offset + 26].toInt() and 0xFF
        val payloadOffset = offset + 27 + numPageSegments
        if (bytes.size < payloadOffset + 16) return null

        // Check Opus ("OpusHead")
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
            val rawRate = (bytes[payloadOffset + 12].toInt() and 0xFF) or
                    ((bytes[payloadOffset + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[payloadOffset + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[payloadOffset + 15].toInt() and 0xFF) shl 24)
            val sampleRate = if (rawRate in 8000..768000) rawRate else 48000
            return AudioFormatInfo(
                formatName = "Opus",
                sampleRate = sampleRate,
                bitDepth = 0,
                bitrateKbps = 0,
                channels = maxOf(1, channels),
                isLossless = false,
                formatDescription = "Opus ${formatSampleRate(sampleRate)}"
            )
        }

        // Check Ogg Vorbis (\x01vorbis)
        if (payloadOffset + 16 <= bytes.size &&
            bytes[payloadOffset] == 1.toByte() &&
            bytes[payloadOffset + 1] == 'v'.code.toByte() &&
            bytes[payloadOffset + 2] == 'o'.code.toByte() &&
            bytes[payloadOffset + 3] == 'r'.code.toByte() &&
            bytes[payloadOffset + 4] == 'b'.code.toByte() &&
            bytes[payloadOffset + 5] == 'i'.code.toByte() &&
            bytes[payloadOffset + 6] == 's'.code.toByte()
        ) {
            val channels = bytes[payloadOffset + 11].toInt() and 0xFF
            val rawRate = (bytes[payloadOffset + 12].toInt() and 0xFF) or
                    ((bytes[payloadOffset + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[payloadOffset + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[payloadOffset + 15].toInt() and 0xFF) shl 24)
            val sampleRate = if (rawRate in 8000..768000) rawRate else 44100
            return AudioFormatInfo(
                formatName = "OGG",
                sampleRate = sampleRate,
                bitDepth = 0,
                bitrateKbps = 0,
                channels = maxOf(1, channels),
                isLossless = false,
                formatDescription = "OGG ${formatSampleRate(sampleRate)}"
            )
        }

        return null
    }

    private fun parseM4aOrAlac(bytes: ByteArray): AudioFormatInfo? {
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
                formatDescription = "ALAC Lossless"
            )
        } else if (isAac) {
            AudioFormatInfo(
                formatName = "AAC",
                sampleRate = 44100,
                bitDepth = 0,
                bitrateKbps = 0,
                channels = 2,
                isLossless = false,
                formatDescription = "AAC (M4A)"
            )
        } else null
    }

    private fun parseFlac(bytes: ByteArray, offset: Int = 0): AudioFormatInfo? {
        // "fLaC" starts at offset (4 bytes)
        // Block header starts at offset + 4 (4 bytes)
        // STREAMINFO block payload starts at offset + 8 (34 bytes)
        if (bytes.size < offset + 42) return null
        val blockHeader = bytes[offset + 4].toInt() and 0xFF
        val blockType = blockHeader and 0x7F
        if (blockType != 0) return null

        val streamInfoOffset = offset + 8
        // In STREAMINFO:
        // Offsets 0..9: min/max block size and frame size (10 bytes)
        // Offsets 10..13: sample rate (20 bits), channels (3 bits), bits-per-sample (5 bits)
        // Offsets 18..33: MD5 checksum
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

    private fun parseWav(bytes: ByteArray, wavOffset: Int = 0): AudioFormatInfo? {
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
            if (chunkSize % 2 != 0) offset++ // Word aligned
        }
        return null
    }

    private fun parseMp3(bytes: ByteArray, startOffset: Int = 0): AudioFormatInfo? {
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
                            bitDepth = 0, // Lossy has NO bit depth
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

    fun formatSampleRate(sampleRate: Int): String {
        return if (sampleRate % 1000 == 0) {
            "${sampleRate / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0)
        }
    }
}
