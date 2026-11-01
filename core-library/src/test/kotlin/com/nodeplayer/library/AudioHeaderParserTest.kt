package com.nodeplayer.library

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioHeaderParserTest {

    @Test
    fun testFlac24Bit96kHz() {
        val bytes = ByteArray(42)
        // Magic "fLaC"
        bytes[0] = 0x66.toByte()
        bytes[1] = 0x4C.toByte()
        bytes[2] = 0x61.toByte()
        bytes[3] = 0x43.toByte()
        // Block header: type 0 (STREAMINFO), length 34 (0x000022)
        bytes[4] = 0x00
        bytes[5] = 0x00
        bytes[6] = 0x00
        bytes[7] = 0x22

        // STREAMINFO offset 18 (file byte 26):
        // sampleRate = 96000 = 0x17700 -> b[26]=0x17, b[27]=0x70, b[28] upper 4 bits=0x0
        // channels = 2 -> (channels-1)=1 -> b[28] bits 1..3 = 001 -> 0x02
        // bitDepth = 24 -> (bitDepth-1)=23 = 0x17 -> bit 23 = 1 -> b[28] bit 0 = 1
        // so b[28] = 0x00 or 0x02 or 0x01 = 0x03
        // b[29] upper 4 bits = 0x70 (from 0x17)
        bytes[26] = 0x17.toByte()
        bytes[27] = 0x70.toByte()
        bytes[28] = 0x03.toByte()
        bytes[29] = 0x70.toByte()

        val info = AudioHeaderParser.parseBytes(bytes)
        assertNotNull(info)
        assertEquals("FLAC", info.formatName)
        assertEquals(96000, info.sampleRate)
        assertEquals(24, info.bitDepth)
        assertEquals(2, info.channels)
        assertTrue(info.isLossless)
        assertEquals("FLAC 24-bit / 96 kHz", info.formatDescription)
    }

    @Test
    fun testFlac16Bit44100Hz() {
        val bytes = ByteArray(42)
        // "fLaC"
        bytes[0] = 0x66.toByte()
        bytes[1] = 0x4C.toByte()
        bytes[2] = 0x61.toByte()
        bytes[3] = 0x43.toByte()
        // STREAMINFO header
        bytes[4] = 0x00
        bytes[5] = 0x00
        bytes[6] = 0x00
        bytes[7] = 0x22

        // sampleRate = 44100 = 0x0AC44 -> b[26]=0x0A, b[27]=0xC4, b[28] upper 4 bits = 0x40
        // channels = 2 -> b[28] bits 1..3 = 0x02
        // bitDepth = 16 -> (16-1)=15 = 0x0F -> bit 23 = 0 -> b[28] bit 0 = 0
        // so b[28] = 0x42
        // b[29] upper 4 bits = 0xF0
        bytes[26] = 0x0A.toByte()
        bytes[27] = 0xC4.toByte()
        bytes[28] = 0x42.toByte()
        bytes[29] = 0xF0.toByte()

        val info = AudioHeaderParser.parseBytes(bytes)
        assertNotNull(info)
        assertEquals("FLAC", info.formatName)
        assertEquals(44100, info.sampleRate)
        assertEquals(16, info.bitDepth)
        assertEquals(2, info.channels)
        assertTrue(info.isLossless)
        assertEquals("FLAC 16-bit / 44.1 kHz", info.formatDescription)
    }

    @Test
    fun testWav24Bit96kHz() {
        val bytes = ByteArray(44)
        // RIFF header
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()

        // fmt chunk (at byte 12)
        bytes[12] = 'f'.code.toByte()
        bytes[13] = 'm'.code.toByte()
        bytes[14] = 't'.code.toByte()
        bytes[15] = ' '.code.toByte()
        bytes[16] = 16 // chunk size = 16 (little-endian)
        bytes[17] = 0
        bytes[18] = 0
        bytes[19] = 0

        // audio format PCM = 1
        bytes[20] = 1
        bytes[21] = 0
        // num channels = 2
        bytes[22] = 2
        bytes[23] = 0
        // sample rate = 96000 (0x00017700)
        bytes[24] = 0x00.toByte()
        bytes[25] = 0x77.toByte()
        bytes[26] = 0x01.toByte()
        bytes[27] = 0x00.toByte()
        // bits per sample = 24 at offset 34
        bytes[34] = 24
        bytes[35] = 0

        val info = AudioHeaderParser.parseBytes(bytes)
        assertNotNull(info)
        assertEquals("WAV", info.formatName)
        assertEquals(96000, info.sampleRate)
        assertEquals(24, info.bitDepth)
        assertTrue(info.isLossless)
        assertEquals("WAV 24-bit / 96 kHz", info.formatDescription)
    }

    @Test
    fun testMp3320kbps() {
        val bytes = ByteArray(64)
        // Frame sync: 0xFF 0xFB (MPEG 1 Layer 3, no CRC)
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xFB.toByte()
        // b[2]: bitrate index 14 (320 kbps = 1110 -> 0xE0), sample rate 44.1 kHz (00 -> 0x00) -> 0xE0
        bytes[2] = 0xE0.toByte()
        // b[3]: channel mode joint stereo (01 -> 0x40)
        bytes[3] = 0x40.toByte()

        val info = AudioHeaderParser.parseBytes(bytes)
        assertNotNull(info)
        assertEquals("MP3", info.formatName)
        assertEquals(44100, info.sampleRate)
        assertEquals(0, info.bitDepth) // Lossy MUST NOT have bit depth
        assertEquals(320, info.bitrateKbps)
        assertFalse(info.isLossless)
        assertEquals("MP3 320 kbps", info.formatDescription)
    }
}
