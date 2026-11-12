package com.nodeplayer.ui.lyrics

import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.LyricWord
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullScreenLyricsViewTest {

    @Test
    fun testActiveIndexDetermination() {
        val lines = listOf(
            LyricsLine(timestampMs = 1000L, text = "Line 1", endTimeMs = 3000L),
            LyricsLine(timestampMs = 3000L, text = "Line 2", endTimeMs = 6000L),
            LyricsLine(timestampMs = 6000L, text = "Line 3", endTimeMs = 9000L)
        )

        // Before first line
        val before = lines.indexOfLast { it.timestampMs <= 500L }.coerceAtLeast(0)
        assertEquals(0, before)

        // During second line
        val during = lines.indexOfLast { it.timestampMs <= 4500L }.coerceAtLeast(0)
        assertEquals(1, during)

        // During third line
        val third = lines.indexOfLast { it.timestampMs <= 8000L }.coerceAtLeast(0)
        assertEquals(2, third)
    }

    @Test
    fun testWordByWordProgressCalculation() {
        val line = LyricsLine(timestampMs = 2000L, text = "Word-by-word line", endTimeMs = 6000L)
        val duration = line.endTimeMs!! - line.timestampMs // 4000L

        // Start of line -> 0.0f
        val posStart = 2000L
        val progressStart = ((posStart - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.0f, progressStart)

        // Mid line -> 0.5f
        val posMid = 4000L
        val progressMid = ((posMid - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.5f, progressMid)

        // End of line -> 1.0f
        val posEnd = 6000L
        val progressEnd = ((posEnd - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, progressEnd)

        // Past end of line -> clamped to 1.0f
        val posPast = 7000L
        val progressPast = ((posPast - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, progressPast)
    }

    @Test
    fun testDistanceScaleAndAlphaLadder() {
        fun getTargetAlpha(distance: Int): Float = when (distance) {
            0 -> 1.0f
            1 -> 0.58f
            2 -> 0.40f
            else -> 0.28f
        }

        fun getTargetScale(distance: Int): Float = when (distance) {
            0 -> 1.00f
            1 -> 0.94f
            2 -> 0.90f
            else -> 0.88f
        }

        assertEquals(1.0f, getTargetAlpha(0))
        assertEquals(0.58f, getTargetAlpha(1))
        assertEquals(0.40f, getTargetAlpha(2))
        assertEquals(0.28f, getTargetAlpha(3))
        assertEquals(0.28f, getTargetAlpha(5))

        assertEquals(1.00f, getTargetScale(0))
        assertEquals(0.94f, getTargetScale(1))
        assertEquals(0.90f, getTargetScale(2))
        assertEquals(0.88f, getTargetScale(3))
        assertEquals(0.88f, getTargetScale(8))
    }
}
