package com.nodeplayer.music

import com.nodeplayer.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBadgeLogicTest {

    @Test
    fun testLosslessFlac16Bit44k() {
        val track = Track(
            id = 1,
            title = "505",
            artist = "Arctic Monkeys",
            album = "Favourite Worst Nightmare",
            durationMs = 253000,
            path = "/sdcard/Music/Shit but/Arctic Monkeys - 505.flac",
            mimeType = "audio/flac",
            sampleRate = 44100,
            bitDepth = 16,
            isLossless = true,
            formatDescription = "FLAC 16-bit / 44.1 kHz"
        )
        assertEquals("FLAC 16-bit / 44.1 kHz", track.formatBadgeText)
    }

    @Test
    fun testLosslessFlac24Bit96k() {
        val track = Track(
            id = 2,
            title = "HiRes Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180000,
            path = "/sdcard/Music/hires.flac",
            mimeType = "audio/flac",
            sampleRate = 96000,
            bitDepth = 24,
            isLossless = true,
            formatDescription = "FLAC 24-bit / 96 kHz"
        )
        assertEquals("FLAC 24-bit / 96 kHz", track.formatBadgeText)
    }

    @Test
    fun testLossyMp3320kbps() {
        val track = Track(
            id = 3,
            title = "Lossy Track",
            artist = "Artist",
            album = "Album",
            durationMs = 200000,
            path = "/sdcard/Music/song.mp3",
            mimeType = "audio/mpeg",
            sampleRate = 44100,
            bitDepth = 0, // Lossy files have NO bit depth
            bitrateKbps = 320,
            isLossless = false,
            formatDescription = "MP3 320 kbps"
        )
        assertEquals("MP3 320 kbps", track.formatBadgeText)
        assertEquals(0, track.bitDepth)
    }

    @Test
    fun testFallbackDashWhenPlausibilityFails() {
        // Track with empty description and unplausible sample rate / bit depth
        val track = Track(
            id = 4,
            title = "Unplausible Track",
            artist = "Artist",
            album = "Album",
            durationMs = 200000,
            path = "/sdcard/Music/bad.flac",
            mimeType = "audio/flac",
            sampleRate = 0, // invalid (outside 8..768 kHz)
            bitDepth = 3,   // invalid (outside 8..32 bit)
            isLossless = true,
            formatDescription = ""
        )
        assertEquals("FLAC — / —", track.formatBadgeText)
    }

    @Test
    fun testFallbackBitDepthOnlyDash() {
        val track = Track(
            id = 5,
            title = "Missing Bit Depth",
            artist = "Artist",
            album = "Album",
            durationMs = 200000,
            path = "/sdcard/Music/test.flac",
            mimeType = "audio/flac",
            sampleRate = 44100,
            bitDepth = 0,
            isLossless = true,
            formatDescription = ""
        )
        assertEquals("FLAC — / 44.1 kHz", track.formatBadgeText)
    }
}
