package com.nodeplayer.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NowPlayingTimelineTest {

    @Test
    fun testDurationFormatting() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:05", formatDuration(5000L))
        assertEquals("1:04", formatDuration(64000L))
        assertEquals("3:17", formatDuration(197000L))
        assertEquals("10:00", formatDuration(600000L))
    }

    @Test
    fun testRemainingTimeCalculation() {
        val durationMs = 200000L // 3:20
        val positionMs = 50000L  // 0:50
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L) // 150000L (2:30)

        assertEquals("0:50", formatDuration(positionMs))
        assertEquals("2:30", formatDuration(remainingMs))
        assertEquals("-2:30", "-${formatDuration(remainingMs)}")
    }

    @Test
    fun testDragClamping() {
        val totalMs = 180000L
        fun clampProgress(fraction: Float): Long = (fraction.coerceIn(0f, 1f) * totalMs).toLong()

        assertEquals(0L, clampProgress(-0.5f))
        assertEquals(90000L, clampProgress(0.5f))
        assertEquals(180000L, clampProgress(1.5f))
    }
}
