package com.nodeplayer.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsModelTest {

    @Test
    fun testDefaultParameterCompatibilityForExistingCallers() {
        // Test existing LyricWord constructor callers without syllables
        val word = LyricWord("hello", 1000L, 2000L)
        assertEquals("hello", word.text)
        assertEquals(1000L, word.startMs)
        assertEquals(2000L, word.endMs)
        assertTrue(word.syllables.isEmpty())

        // Test existing LyricsLine constructor callers without background or alignment
        val line = LyricsLine(timestampMs = 1000L, text = "hello")
        assertEquals(1000L, line.timestampMs)
        assertEquals(1000L, line.timeMs)
        assertEquals("hello", line.text)
        assertNull(line.endTimeMs)
        assertTrue(line.words.isEmpty())
        assertFalse(line.isEstimatedWords)
        assertNull(line.background)
        assertEquals(LyricAlignment.Start, line.alignment)
        assertFalse(line.isWordSynced)

        val lineWithWords = LyricsLine(
            timestampMs = 1000L,
            text = "hello world",
            words = listOf(word)
        )
        assertTrue(lineWithWords.isWordSynced)
    }

    @Test
    fun testCharsSungAtWithoutSyllables() {
        val word = LyricWord(text = "freedom", startMs = 1000L, endMs = 2000L)

        // Before startMs
        assertEquals(0f, word.charsSungAt(500L))
        assertEquals(0f, word.charsSungAt(1000L))

        // After endMs
        assertEquals(7f, word.charsSungAt(2000L))
        assertEquals(7f, word.charsSungAt(2500L))

        // Midway (50% of 7 chars = 3.5f)
        assertEquals(3.5f, word.charsSungAt(1500L))

        // Quarter (25% of 7 chars = 1.75f)
        assertEquals(1.75f, word.charsSungAt(1250L))
    }

    @Test
    fun testCharsSungAtWithSyllables() {
        // "water" length 5. Syllable 1: "wa" (0..2), 1000..1400. Syllable 2: "ter" (2..5), 1400..2000.
        val s1 = LyricSyllable(startMs = 1000L, endMs = 1400L, startChar = 0, endChar = 2)
        val s2 = LyricSyllable(startMs = 1400L, endMs = 2000L, startChar = 2, endChar = 5)
        val word = LyricWord(
            text = "water",
            startMs = 1000L,
            endMs = 2000L,
            syllables = listOf(s1, s2)
        )

        // Before start
        assertEquals(0f, word.charsSungAt(900L))
        assertEquals(0f, word.charsSungAt(1000L))

        // Midway through syllable 1 (1200ms -> 50% between 0 and 2 = 1.0f)
        assertEquals(1.0f, word.charsSungAt(1200L))

        // Boundary between syllable 1 and 2 (1400ms)
        assertEquals(2.0f, word.charsSungAt(1400L))

        // Midway through syllable 2 (1700ms -> 50% between 2 and 5 = 3.5f)
        assertEquals(3.5f, word.charsSungAt(1700L))

        // End of word
        assertEquals(5.0f, word.charsSungAt(2000L))
        assertEquals(5.0f, word.charsSungAt(2500L))
    }

    @Test
    fun testZeroDurationDivisionGuard() {
        // Instantaneous word (endMs == startMs)
        val instantWord = LyricWord(text = "snap", startMs = 1000L, endMs = 1000L)
        // At or before startMs
        assertEquals(0f, instantWord.charsSungAt(999L))
        // At or after endMs
        assertEquals(4f, instantWord.charsSungAt(1000L))
        assertEquals(4f, instantWord.charsSungAt(1001L))

        // Instantaneous syllable inside word
        val zeroSyl = LyricSyllable(startMs = 1500L, endMs = 1500L, startChar = 0, endChar = 4)
        val wordWithZeroSyl = LyricWord(
            text = "snap",
            startMs = 1000L,
            endMs = 2000L,
            syllables = listOf(zeroSyl)
        )
        // Should not throw ArithmeticException
        val result = wordWithZeroSyl.charsSungAt(1500L)
        assertTrue(result >= 0f && result <= 4f)
    }

    @Test
    fun testDuetAlignmentAndBackgroundVocals() {
        val backingLine = LyricsLine(
            timestampMs = 1200L,
            text = "(in the background)",
            alignment = LyricAlignment.End
        )
        val leadLine = LyricsLine(
            timestampMs = 1000L,
            text = "Lead vocals here",
            background = backingLine,
            alignment = LyricAlignment.Start
        )

        assertEquals(LyricAlignment.Start, leadLine.alignment)
        assertEquals("(in the background)", leadLine.background?.text)
        assertEquals(LyricAlignment.End, leadLine.background?.alignment)
    }

    @Test
    fun testExploreModelsAndYouTubeAccountParity() {
        val mood = MoodGenre(
            title = "Chill",
            stripeColor = 0xFF123456L,
            browseId = "FEmusic_chill",
            params = "test_params",
            thumbnailUrl = "https://example.com/thumb.jpg"
        )
        val section = MoodGenreSection(
            title = "Moods",
            items = listOf(mood)
        )
        assertEquals("Chill", section.items.first().title)
        assertEquals(0xFF123456L, section.items.first().stripeColor)

        val account = YouTubeAccount(
            id = "user123",
            name = "Test User",
            handle = "@testuser",
            avatarUrl = "https://example.com/avatar.jpg"
        )
        assertEquals("@testuser", account.handle)
        assertEquals("@testuser", account.channelHandle)
        assertEquals("https://example.com/avatar.jpg", account.avatarUrl)
    }
}
