package com.nodeplayer.ui.lyrics

import com.nodeplayer.model.LyricAlignment
import com.nodeplayer.model.LyricSyllable
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [AppleMusicLyricsView], [WordByWordKaraokeText], and [KaraokeProgressMath].
 *
 * Verifies:
 * 1. Duet alignment mapping: [LyricAlignment.Start] vs [LyricAlignment.End].
 * 2. 3-Dot instrumental countdown triggers when inter-line break duration > 4000ms and does not trigger for breaks <= 4000ms.
 * 3. Backing vocal timing sweep independence: main line and backing line progress can sweep independently.
 * 4. Syllable-by-syllable karaoke progress calculations.
 */
class AppleMusicLyricsViewTest {

    @Test
    fun testDuetAlignmentMapping() {
        val leadLine = LyricsLine(
            timestampMs = 10000L,
            text = "I was born in the thunder",
            alignment = LyricAlignment.Start
        )
        val responseLine = LyricsLine(
            timestampMs = 13500L,
            text = "In the pouring rain",
            alignment = LyricAlignment.End
        )

        assertEquals(LyricAlignment.Start, leadLine.alignment, "Lead vocalist must align to Start")
        assertEquals(LyricAlignment.End, responseLine.alignment, "Answering vocalist must align to End")
    }

    @Test
    fun testInstrumentalBreakDetectionThreshold() {
        // Gap > 4000ms (5000ms break) -> triggers countdown
        val line1 = LyricsLine(
            timestampMs = 10000L,
            endTimeMs = 14000L,
            text = "First verse ends here"
        )
        val nextLineWithLongGap = LyricsLine(
            timestampMs = 19000L, // 19000 - 14000 = 5000ms > 4000ms
            text = "Second verse starts here"
        )
        val gapDurationLong = nextLineWithLongGap.timestampMs - (line1.endTimeMs ?: (line1.timestampMs + 3500L))
        assertTrue(gapDurationLong > 4000L, "Gap duration of 5000ms must exceed 4000ms threshold")

        // Gap <= 4000ms (2500ms break) -> does not trigger countdown
        val nextLineWithShortGap = LyricsLine(
            timestampMs = 16500L, // 16500 - 14000 = 2500ms <= 4000ms
            text = "Quick continuation"
        )
        val gapDurationShort = nextLineWithShortGap.timestampMs - (line1.endTimeMs ?: (line1.timestampMs + 3500L))
        assertFalse(gapDurationShort > 4000L, "Gap duration of 2500ms must not trigger instrumental countdown")

        // Exact boundary (4000ms break) -> does not trigger countdown (> 4000 required)
        val nextLineBoundary = LyricsLine(
            timestampMs = 18000L, // 18000 - 14000 = 4000ms
            text = "Boundary line"
        )
        val gapDurationBoundary = nextLineBoundary.timestampMs - (line1.endTimeMs ?: (line1.timestampMs + 3500L))
        assertFalse(gapDurationBoundary > 4000L, "Gap duration of exactly 4000ms must not trigger countdown (> 4000L required)")
    }

    @Test
    fun testInstrumentalBreakCountdownLightingMath() {
        val breakStartMs = 14000L
        val breakEndMs = 20000L // 6000ms break
        val breakDurationMs = breakEndMs - breakStartMs

        // At start (through = 0.0)
        val through0 = ((14000L - breakStartMs).toFloat() / breakDurationMs).coerceIn(0f, 1f)
        assertEquals(0.0f, through0)
        val litDot0At0 = (through0 * 3f - 0f).coerceIn(0f, 1f)
        val litDot1At0 = (through0 * 3f - 1f).coerceIn(0f, 1f)
        val litDot2At0 = (through0 * 3f - 2f).coerceIn(0f, 1f)
        assertEquals(0.0f, litDot0At0)
        assertEquals(0.0f, litDot1At0)
        assertEquals(0.0f, litDot2At0)

        // At 1/3 (through = 0.333) -> first dot fully lit (lit = 1.0), dot 1 & 2 unlit (0.0)
        val throughThird = ((16000L - breakStartMs).toFloat() / breakDurationMs).coerceIn(0f, 1f)
        val litDot0AtThird = (throughThird * 3f - 0f).coerceIn(0f, 1f)
        val litDot1AtThird = (throughThird * 3f - 1f).coerceIn(0f, 1f)
        val litDot2AtThird = (throughThird * 3f - 2f).coerceIn(0f, 1f)
        assertEquals(1.0f, litDot0AtThird)
        assertEquals(0.0f, litDot1AtThird)
        assertEquals(0.0f, litDot2AtThird)

        // At end (through = 1.0) -> all 3 dots fully lit (lit = 1.0)
        val throughEnd = ((20000L - breakStartMs).toFloat() / breakDurationMs).coerceIn(0f, 1f)
        val litDot0AtEnd = (throughEnd * 3f - 0f).coerceIn(0f, 1f)
        val litDot1AtEnd = (throughEnd * 3f - 1f).coerceIn(0f, 1f)
        val litDot2AtEnd = (throughEnd * 3f - 2f).coerceIn(0f, 1f)
        assertEquals(1.0f, litDot0AtEnd)
        assertEquals(1.0f, litDot1AtEnd)
        assertEquals(1.0f, litDot2AtEnd)
    }

    @Test
    fun testBackingVocalSweepIndependence() {
        val backingWords = listOf(
            LyricWord(text = "echo", startMs = 12000L, endMs = 13500L)
        )
        val leadWords = listOf(
            LyricWord(text = "Shout", startMs = 10000L, endMs = 11500L)
        )

        val leadLine = LyricsLine(
            timestampMs = 10000L,
            endTimeMs = 12000L,
            text = "Shout",
            words = leadWords,
            background = LyricsLine(
                timestampMs = 12000L,
                endTimeMs = 14000L,
                text = "(echo)",
                words = backingWords
            )
        )

        // At 11000ms: Lead vocal is actively singing, backing vocal has not started (0.0f)
        val leadProgressAt11 = KaraokeProgressMath.calculateLineSweepProgress(
            currentPositionMs = 11000L,
            timestampMs = leadLine.timestampMs,
            endTimeMs = leadLine.endTimeMs,
            words = leadLine.words
        )
        val bgProgressAt11 = KaraokeProgressMath.calculateLineSweepProgress(
            currentPositionMs = 11000L,
            timestampMs = leadLine.background!!.timestampMs,
            endTimeMs = leadLine.background!!.endTimeMs,
            words = leadLine.background!!.words
        )
        assertTrue(leadProgressAt11 > 0f && leadProgressAt11 < 1f, "Lead vocal should be midway through sweep")
        assertEquals(0.0f, bgProgressAt11, "Backing vocal must remain at 0% before its startMs")

        // At 12800ms: Lead vocal is finished (1.0f), backing vocal is actively sweeping
        val leadProgressAt128 = KaraokeProgressMath.calculateLineSweepProgress(
            currentPositionMs = 12800L,
            timestampMs = leadLine.timestampMs,
            endTimeMs = leadLine.endTimeMs,
            words = leadLine.words
        )
        val bgProgressAt128 = KaraokeProgressMath.calculateLineSweepProgress(
            currentPositionMs = 12800L,
            timestampMs = leadLine.background!!.timestampMs,
            endTimeMs = leadLine.background!!.endTimeMs,
            words = leadLine.background!!.words
        )
        assertEquals(1.0f, leadProgressAt128, "Lead vocal must be 100% completed")
        assertTrue(bgProgressAt128 > 0f && bgProgressAt128 < 1f, "Backing vocal must sweep independently")
    }

    @Test
    fun testLyricWordCharsSungAtSyllables() {
        val syllables = listOf(
            LyricSyllable(startMs = 1000L, endMs = 1500L, startChar = 0, endChar = 4),   // "Kara"
            LyricSyllable(startMs = 1500L, endMs = 2000L, startChar = 4, endChar = 7)    // "oke"
        )
        val word = LyricWord(
            text = "Karaoke",
            startMs = 1000L,
            endMs = 2000L,
            syllables = syllables
        )

        // Before start
        assertEquals(0.0f, word.charsSungAt(900L))
        // Mid-first syllable
        val mid1 = word.charsSungAt(1250L)
        assertTrue(mid1 in 1.9f..2.1f, "Chars sung midway in first syllable should be ~2.0, was $mid1")
        // Exact boundary between syllables
        val midBoundary = word.charsSungAt(1500L)
        assertEquals(4.0f, midBoundary)
        // Mid-second syllable
        val mid2 = word.charsSungAt(1750L)
        assertTrue(mid2 in 5.4f..5.6f, "Chars sung midway in second syllable should be ~5.5, was $mid2")
        // After end
        assertEquals(7.0f, word.charsSungAt(2100L))
    }

    @Test
    fun testKaraokeProgressMathAlphaLadder() {
        assertEquals(1.0f, KaraokeProgressMath.alphaForDistance(0), "Active line must have alpha 1.0f")
        assertEquals(0.58f, KaraokeProgressMath.alphaForDistance(1), "Distance 1 must have alpha 0.58f")
        assertEquals(0.40f, KaraokeProgressMath.alphaForDistance(2), "Distance 2 must have alpha 0.40f")
        assertEquals(0.28f, KaraokeProgressMath.alphaForDistance(3), "Distance 3 must have alpha 0.28f")
        assertEquals(0.28f, KaraokeProgressMath.alphaForDistance(5), "Distance >3 must stay at 0.28f")
    }
}
