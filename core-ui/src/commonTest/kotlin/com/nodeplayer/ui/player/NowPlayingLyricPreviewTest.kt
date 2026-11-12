package com.nodeplayer.ui.player

import com.nodeplayer.model.LyricSyllable
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowPlayingLyricPreviewTest {

    @Test
    fun testActiveLineSelectionMatchingTimestamp() {
        val line1 = LyricsLine(timestampMs = 1000L, text = "First line", endTimeMs = 3000L)
        val line2 = LyricsLine(timestampMs = 3000L, text = "Second line", endTimeMs = 6000L)
        val line3 = LyricsLine(timestampMs = 6000L, text = "Third line", endTimeMs = 9000L)
        val lines = listOf(line1, line2, line3)

        // Before first line
        assertNull(findActiveLyricLine(lines, 500L))

        // On exactly first line timestamp
        assertEquals(line1, findActiveLyricLine(lines, 1000L))
        assertEquals("First line", findActiveLyricText(lines, 1000L))

        // Mid first line
        assertEquals(line1, findActiveLyricLine(lines, 2500L))

        // Exactly second line timestamp
        assertEquals(line2, findActiveLyricLine(lines, 3000L))

        // During third line
        assertEquals(line3, findActiveLyricLine(lines, 7500L))

        // Past last line
        assertEquals(line3, findActiveLyricLine(lines, 12000L))
    }

    @Test
    fun testEmptyLyricsFallback() {
        val emptyLines = emptyList<LyricsLine>()
        assertNull(findActiveLyricLine(emptyLines, 5000L))
        assertNull(findActiveLyricText(emptyLines, 5000L))
    }

    @Test
    fun testCharsSungAtProgressCalculationAcrossPreviewWords() {
        // Line with 2 words: "Hey" (3 chars, 1000-2000ms), "Jude" (4 chars, 2000-3000ms) -> total 7 chars
        val word1 = LyricWord(text = "Hey", startMs = 1000L, endMs = 2000L)
        val word2 = LyricWord(text = "Jude", startMs = 2000L, endMs = 3000L)
        val line = LyricsLine(
            timestampMs = 1000L,
            text = "Hey Jude",
            endTimeMs = 3000L,
            words = listOf(word1, word2)
        )

        // Before start
        assertEquals(0f, calculateLyricLineCharsSungProgress(line, 500L))
        assertEquals(0f, calculateLyricLineCharsSungProgress(line, 1000L))

        // Halfway through "Hey" (at 1500ms): 1.5 chars sung / 7 chars = ~0.214f
        val midWord1 = calculateLyricLineCharsSungProgress(line, 1500L)
        assertTrue(midWord1 in 0.21f..0.22f)

        // At end of "Hey" / start of "Jude" (at 2000ms): 3 chars sung / 7 chars = ~0.428f
        val endWord1 = calculateLyricLineCharsSungProgress(line, 2000L)
        assertTrue(endWord1 in 0.42f..0.43f)

        // Halfway through "Jude" (at 2500ms): 3 + 2 = 5 chars sung / 7 chars = ~0.714f
        val midWord2 = calculateLyricLineCharsSungProgress(line, 2500L)
        assertTrue(midWord2 in 0.71f..0.72f)

        // At end of line (at 3000ms): 7 chars sung / 7 chars = 1.0f
        assertEquals(1f, calculateLyricLineCharsSungProgress(line, 3000L))

        // Beyond end of line
        assertEquals(1f, calculateLyricLineCharsSungProgress(line, 4000L))
    }

    @Test
    fun testCharsSungAtWithSyllables() {
        val syllables = listOf(
            LyricSyllable(startMs = 1000L, endMs = 1500L, startChar = 0, endChar = 2), // "Hel"
            LyricSyllable(startMs = 1500L, endMs = 2000L, startChar = 2, endChar = 5)  // "lo"
        )
        val word = LyricWord(text = "Hello", startMs = 1000L, endMs = 2000L, syllables = syllables)
        val line = LyricsLine(
            timestampMs = 1000L,
            text = "Hello",
            endTimeMs = 2000L,
            words = listOf(word)
        )

        // Midway through first syllable (1250ms -> 1 char out of 5 = 0.2f)
        val progress1 = calculateLyricLineCharsSungProgress(line, 1250L)
        assertEquals(0.2f, progress1, 0.01f)

        // Midway through second syllable (1750ms -> 2 + 1.5 = 3.5 chars out of 5 = 0.7f)
        val progress2 = calculateLyricLineCharsSungProgress(line, 1750L)
        assertEquals(0.7f, progress2, 0.01f)

        // Completed
        assertEquals(1f, calculateLyricLineCharsSungProgress(line, 2000L))
    }

    @Test
    fun testLineSyncedProgressCalculationWithoutWords() {
        val line = LyricsLine(
            timestampMs = 2000L,
            text = "Standard LRC line",
            endTimeMs = 6000L,
            words = emptyList()
        )

        assertEquals(0f, calculateLyricLineCharsSungProgress(line, 1000L))
        assertEquals(0f, calculateLyricLineCharsSungProgress(line, 2000L))
        assertEquals(0.5f, calculateLyricLineCharsSungProgress(line, 4000L), 0.01f)
        assertEquals(1f, calculateLyricLineCharsSungProgress(line, 6000L))
        assertEquals(1f, calculateLyricLineCharsSungProgress(line, 7000L))
    }
}
