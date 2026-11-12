package com.nodeplayer.ui.lyrics

import com.nodeplayer.model.LyricWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaraokeFillMathTest {

    @Test
    fun testLineSweepProgressWithoutWords() {
        // Line starts at 1000ms, ends at 5000ms (duration 4000ms)
        val timestampMs = 1000L
        val endTimeMs = 5000L

        // Before start
        assertEquals(0f, KaraokeProgressMath.calculateLineSweepProgress(500L, timestampMs, endTimeMs, emptyList()))

        // Exactly at start
        assertEquals(0f, KaraokeProgressMath.calculateLineSweepProgress(1000L, timestampMs, endTimeMs, emptyList()))

        // Halfway
        val halfway = KaraokeProgressMath.calculateLineSweepProgress(3000L, timestampMs, endTimeMs, emptyList())
        assertEquals(0.5f, halfway, 0.01f)

        // At end
        assertEquals(1f, KaraokeProgressMath.calculateLineSweepProgress(5000L, timestampMs, endTimeMs, emptyList()))

        // Past end
        assertEquals(1f, KaraokeProgressMath.calculateLineSweepProgress(6000L, timestampMs, endTimeMs, emptyList()))
    }

    @Test
    fun testLineSweepProgressWithWords() {
        val words = listOf(
            LyricWord("I", 1000L, 1500L),
            LyricWord("been", 1500L, 2000L),
            LyricWord("tryna", 2000L, 2800L),
            LyricWord("call", 2800L, 3500L)
        )

        // Halfway through entire sequence (1000ms to 3500ms = 2500ms duration, mid = 2250ms)
        val progress = KaraokeProgressMath.calculateLineSweepProgress(2250L, 1000L, 3500L, words)
        assertEquals(0.5f, progress, 0.02f)
    }

    @Test
    fun testDistanceLadder() {
        // Distance 0 (Active)
        assertEquals(1.0f, KaraokeProgressMath.alphaForDistance(0))
        assertEquals(1.0f, KaraokeProgressMath.scaleForDistance(0))
        assertEquals(0f, KaraokeProgressMath.blurForDistance(0).value)

        // Distance 1
        assertTrue(KaraokeProgressMath.alphaForDistance(1) < 1.0f)
        assertTrue(KaraokeProgressMath.scaleForDistance(1) < 1.0f)
        assertTrue(KaraokeProgressMath.blurForDistance(1).value > 0f)

        // Distance 3
        assertTrue(KaraokeProgressMath.alphaForDistance(3) < KaraokeProgressMath.alphaForDistance(1))
    }
}
