package com.nodeplayer.ui.lyrics

import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsActiveLineCharacterizationTest {

    @Test
    fun testFindActiveLyricIndexMatchesIndexOfLastAcrossEdgeCases() {
        // Empty list
        assertEquals(-1, findActiveLyricIndex(emptyList(), 0L))
        assertEquals(-1, findActiveLyricIndex(emptyList(), 5000L))

        val lines = listOf(
            LyricsLine(timestampMs = 1000L, text = "Line 1", endTimeMs = 3000L),
            LyricsLine(timestampMs = 3000L, text = "Line 2", endTimeMs = 5000L),
            LyricsLine(timestampMs = 3000L, text = "Line 2 Dup", endTimeMs = 6000L),
            LyricsLine(timestampMs = 6000L, text = "Line 3", endTimeMs = 9000L),
            LyricsLine(timestampMs = 10000L, text = "Line 4", endTimeMs = 13500L)
        )

        val testPositions = listOf(
            -100L, 0L, 999L, 1000L, 1500L, 2999L,
            3000L, 4500L, 5999L, 6000L, 8500L, 10000L, 25000L
        )
        for (pos in testPositions) {
            val expected = lines.indexOfLast { it.timestampMs <= pos }
            assertEquals(expected, findActiveLyricIndex(lines, pos), "Mismatch at pos=$pos")
        }
    }

    @Test
    fun testKaraokeSweepProgressForActivePastAndFuturePositions() {
        val words = listOf(
            LyricWord("Hello", 2000L, 2500L),
            LyricWord("liquid", 2500L, 3200L),
            LyricWord("world", 3200L, 4000L)
        )

        // Future (before timestamp)
        assertEquals(0f, KaraokeProgressMath.calculateLineSweepProgress(1500L, 2000L, 4000L, words), 0.0001f)
        // Exact start
        assertEquals(0f, KaraokeProgressMath.calculateLineSweepProgress(2000L, 2000L, 4000L, words), 0.0001f)
        // Mid active
        assertEquals(0.5f, KaraokeProgressMath.calculateLineSweepProgress(3000L, 2000L, 4000L, words), 0.0001f)
        // End / past
        assertEquals(1f, KaraokeProgressMath.calculateLineSweepProgress(4000L, 2000L, 4000L, words), 0.0001f)
        assertEquals(1f, KaraokeProgressMath.calculateLineSweepProgress(5500L, 2000L, 4000L, words), 0.0001f)
    }
}
