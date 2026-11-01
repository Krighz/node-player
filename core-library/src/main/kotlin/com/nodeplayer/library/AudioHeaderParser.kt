package com.nodeplayer.library

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

data class AudioFormatInfo(
    val formatName: String,
    val sampleRate: Int,
    val bitDepth: Int, // 0 for lossy
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

                // 1. FLAC ("fLaC")
                if (magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
                    magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte()
                ) {
                    raf.seek(0L)
                    val buffer = ByteArray(minOf(raf.length(), 4096L).toInt())
                    raf.readFully(buffer)
                    return parseFlac(buffer)
                }

                // 2. WAV ("RIFF" ... "WAVE")
                if (magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                    magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()
                ) {
                    raf.seek(0L)
                    val buffer = ByteArray(minOf(raf.length(), 4096L).toInt())
                    raf.readFully(buffer)
                    return parseWav(buffer)
                }

                // 3. MP3: Check for ID3v2 tag and seek directly past it to find frame sync
                var audioStartOffset = 0L
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                    val s0 = magic[6].toInt() and 0x7F
                    val s1 = magic[7].toInt() and 0x7F
                    val s2 = magic[8].toInt() and 0x7F
                    val s3 = magic[9].toInt() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                    audioStartOffset = 10L + tagSize
                }

                if (audioStartOffset > 0L && audioStartOffset < raf.length()) {
                    raf.seek(audioStartOffset)
                    val buffer = ByteArray(minOf(raf.length() - audioStartOffset, 16384L).toInt())
                    raf.readFully(buffer)
                    val mp3Info = parseMp3(buffer, file.length(), durationMs)
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

        // 1. Check FLAC ("fLaC" = 0x66 0x4C 0x61 0x43)
        if (bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() &&
            bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
        ) {
            return parseFlac(bytes)
        }

        // 2. Check WAV ("RIFF" ... "WAVE")
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            return parseWav(bytes)
        }

        // 3. Check MP3 (handle optional ID3v2 tag at start)
        val mp3Info = parseMp3(bytes, fileLength, durationMs)
        if (mp3Info != null) return mp3Info

        return null
    }

    private fun parseFlac(bytes: ByteArray): AudioFormatInfo? {
        // STREAMINFO starts at byte 8 (4 bytes magic + 4 bytes block header)
        // STREAMINFO must be block type 0: (bytes[4] and 0x7F) == 0
        if (bytes.size < 26) return null
        val blockType = (bytes[4].toInt() and 0x7F)
        if (blockType != 0) return null

        // Bytes 18..25 of STREAMINFO (file bytes 26..33, or if header at offset 8):
        // File offset: 4 (magic) + 4 (block header) = 8.
        // STREAMINFO offset 18 = 8 + 18 = 26.
        if (bytes.size < 34) return null
        val b18 = bytes[26].toInt() and 0xFF
        val b19 = bytes[27].toInt() and 0xFF
        val b20 = bytes[28].toInt() and 0xFF
        val b21 = bytes[29].toInt() and 0xFF

        val sampleRate = (b18 shl 12) or (b19 shl 4) or ((b20 and 0xF0) ushr 4)
        val channels = ((b20 and 0x0E) ushr 1) + 1
        val bitDepth = (((b20 and 0x01) shl 4) or ((b21 and 0xF0) ushr 4)) + 1

        val rateFormatted = formatSampleRate(sampleRate)
        val desc = "FLAC $bitDepth-bit / $rateFormatted"

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

    private fun parseWav(bytes: ByteArray): AudioFormatInfo? {
        var offset = 12
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
                val sampleRate = (bytes[fmtOffset + 4].toInt() and 0xFF) or
                        ((bytes[fmtOffset + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[fmtOffset + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[fmtOffset + 7].toInt() and 0xFF) shl 24)
                val bitsPerSample = if (chunkSize >= 16 && fmtOffset + 16 <= bytes.size) {
                    (bytes[fmtOffset + 14].toInt() and 0xFF) or
                            ((bytes[fmtOffset + 15].toInt() and 0xFF) shl 8)
                } else 16

                val rateFormatted = formatSampleRate(sampleRate)
                val desc = "WAV $bitsPerSample-bit / $rateFormatted"

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

    private fun parseMp3(bytes: ByteArray, fileLength: Long, durationMs: Long): AudioFormatInfo? {
        var offset = 0

        // Skip ID3v2 if present
        if (bytes.size >= 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val s0 = bytes[6].toInt() and 0x7F
            val s1 = bytes[7].toInt() and 0x7F
            val s2 = bytes[8].toInt() and 0x7F
            val s3 = bytes[9].toInt() and 0x7F
            val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
            offset = 10 + tagSize
        }

        // Scan for MP3 frame sync (11 bits of 1s: 0xFF, 0xE0)
        while (offset + 4 <= bytes.size) {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF

            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03

                // Only valid MPEG versions: 00 = 2.5, 10 = 2, 11 = 1 (01 is reserved)
                // Layer: 01 = Layer 3 (MP3)
                if (versionBits != 1 && layerBits == 1) {
                    val b2 = bytes[offset + 2].toInt() and 0xFF
                    val b3 = bytes[offset + 3].toInt() and 0xFF

                    val bitrateIdx = (b2 ushr 4) and 0x0F
                    val sampleRateIdx = (b2 ushr 2) and 0x03
                    val channelMode = (b3 ushr 6) and 0x03
                    val channels = if (channelMode == 3) 1 else 2

                    val sampleRate = when (versionBits) {
                        3 -> MP3_V1_SAMPLE_RATES.getOrElse(sampleRateIdx) { 44100 }
                        2 -> MP3_V2_SAMPLE_RATES.getOrElse(sampleRateIdx) { 22050 }
                        0 -> MP3_V25_SAMPLE_RATES.getOrElse(sampleRateIdx) { 11025 }
                        else -> 44100
                    }

                    var bitrateKbps = when (versionBits) {
                        3 -> MP3_V1_L3_BITRATES.getOrElse(bitrateIdx) { 0 }
                        else -> MP3_V2_L3_BITRATES.getOrElse(bitrateIdx) { 0 }
                    }

                    // Fallback to average bitrate from duration if header bitrate is 0 (VBR/free)
                    if (bitrateKbps == 0 && fileLength > 0 && durationMs > 0) {
                        val durationSec = durationMs / 1000.0
                        bitrateKbps = ((fileLength * 8) / (durationSec * 1000.0)).toInt()
                    }

                    if (sampleRate > 0 && bitrateKbps > 0) {
                        val desc = "MP3 $bitrateKbps kbps"
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
