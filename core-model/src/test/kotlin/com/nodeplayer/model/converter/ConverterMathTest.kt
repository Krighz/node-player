package com.nodeplayer.model.converter

import org.junit.Assert.*
import org.junit.Test

class ConverterMathTest {

    @Test
    fun testEstimatedOutputBytesCalculation() {
        // 4 minutes (240,000 ms) at 128 kbps: 240,000 * 128 / 8 = 3,840,000 bytes (~3.84 MB)
        val estimated128 = ConverterMath.estimatedOutputBytes(durationMs = 240_000L, bitrateKbps = 128)
        assertEquals(3_840_000L, estimated128)

        // 4 minutes at 192 kbps: 240,000 * 192 / 8 = 5,760,000 bytes
        val estimated192 = ConverterMath.estimatedOutputBytes(durationMs = 240_000L, bitrateKbps = 192)
        assertEquals(5_760_000L, estimated192)

        // 4 minutes at 96 kbps: 240,000 * 96 / 8 = 2,880,000 bytes
        val estimated96 = ConverterMath.estimatedOutputBytes(durationMs = 240_000L, bitrateKbps = 96)
        assertEquals(2_880_000L, estimated96)

        // Edge case: zero duration or negative bitrate
        assertEquals(0L, ConverterMath.estimatedOutputBytes(0L, 128))
        assertEquals(0L, ConverterMath.estimatedOutputBytes(240_000L, 0))
    }

    @Test
    fun testReplayGainToR128Q78Conversion() {
        // 0.0 dB ReplayGain: (0.0 - 5.0) * 256 = -1280
        assertEquals(-1280, ConverterMath.replayGainToR128Q78(0.0))

        // +5.0 dB ReplayGain: (5.0 - 5.0) * 256 = 0
        assertEquals(0, ConverterMath.replayGainToR128Q78(5.0))

        // -2.5 dB ReplayGain: (-2.5 - 5.0) * 256 = -7.5 * 256 = -1920
        assertEquals(-1920, ConverterMath.replayGainToR128Q78(-2.5))
    }

    @Test
    fun testTranscodePresetResolution() {
        assertEquals(TranscodePreset.PRESET_192, TranscodePreset.fromId("192"))
        assertEquals(TranscodePreset.PRESET_128, TranscodePreset.fromId("128"))
        assertEquals(TranscodePreset.PRESET_96, TranscodePreset.fromId("96"))
        assertEquals(TranscodePreset.PRESET_CUSTOM, TranscodePreset.fromId("CUSTOM"))
        // Fallback for unknown ID
        assertEquals(TranscodePreset.PRESET_128, TranscodePreset.fromId("unknown_id"))
    }

    @Test
    fun testFormatDiskSize() {
        assertEquals("500 B", ConverterMath.formatDiskSize(500L))
        assertEquals("1.5 KB", ConverterMath.formatDiskSize(1536L))
        assertEquals("10.0 MB", ConverterMath.formatDiskSize(10 * 1024 * 1024L))
        assertEquals("1.5 GB", ConverterMath.formatDiskSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
