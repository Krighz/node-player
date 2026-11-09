package com.nodeplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testStandardLrcParsing() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [00:01.50]First line of lyrics
            [00:04.20]Second line of lyrics
            [00:08.00]Third line ending
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc, trackId = 123L)

        assertTrue(lyrics.isSynced)
        assertEquals(3, lyrics.lines.size)
        assertEquals(1500L, lyrics.lines[0].timestampMs)
        assertEquals("First line of lyrics", lyrics.lines[0].text)
        assertEquals(4200L, lyrics.lines[0].endTimeMs)
        assertTrue(lyrics.lines[0].isEstimatedWords)
        assertFalse(lyrics.hasWordTimings)
        assertEquals(123L, lyrics.trackId)
    }

    @Test
    fun testMultipleTimestampsSameLine() {
        val lrc = """
            [00:02.00][00:10.00]Chorus repeating line
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertEquals(2000L, lyrics.lines[0].timestampMs)
        assertEquals("Chorus repeating line", lyrics.lines[0].text)
        assertEquals(10000L, lyrics.lines[1].timestampMs)
        assertEquals("Chorus repeating line", lyrics.lines[1].text)
    }

    @Test
    fun testEnhancedLrcPerWordTimings() {
        val lrc = """
            [00:05.00]<00:05.00>Never <00:05.50>gonna <00:06.00>give <00:06.50>you <00:07.00>up
            [00:08.00]<00:08.00>Never <00:08.40>gonna <00:08.80>let <00:09.20>you <00:09.60>down
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertTrue(lyrics.isSynced)
        assertTrue(lyrics.hasWordTimings)
        assertEquals(2, lyrics.lines.size)

        val line1 = lyrics.lines[0]
        assertEquals(5000L, line1.timestampMs)
        assertFalse(line1.isEstimatedWords)
        assertEquals(5, line1.words.size)
        assertEquals("Never ", line1.words[0].text)
        assertEquals(5000L, line1.words[0].startMs)
        assertEquals(5500L, line1.words[0].endMs)

        assertEquals("gonna ", line1.words[1].text)
        assertEquals(5500L, line1.words[1].startMs)
        assertEquals(6000L, line1.words[1].endMs)
    }

    @Test
    fun testPlainTextWithoutTimestamps() {
        val plain = """
            Just a line of plain text
            Another line of song lyrics
            No timestamps here at all
        """.trimIndent()

        val lyrics = LrcParser.parse(plain)

        assertFalse(lyrics.isSynced)
        assertEquals(0, lyrics.lines.size)
        assertTrue(lyrics.plainText.contains("Just a line of plain text"))
        assertTrue(lyrics.plainText.contains("Another line of song lyrics"))
    }

    @Test
    fun testOffsetTagHandling() {
        val lrc = """
            [offset:+500]
            [00:02.00]Offset test
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertEquals(500L, lyrics.offsetMs)
        assertEquals(2500L, lyrics.lines[0].timestampMs)
    }

    @Test
    fun testEstimatedWordsDistribution() {
        val lrc = """
            [00:00.00]One two three four
            [00:04.00]Next
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)
        val line = lyrics.lines[0]

        assertTrue(line.isEstimatedWords)
        assertEquals(4, line.words.size)
        assertEquals(0L, line.words[0].startMs)
        assertEquals(4000L, line.words.last().endMs)
        // Ensure monotonically increasing
        for (i in 0 until line.words.size - 1) {
            assertTrue(line.words[i].startMs <= line.words[i].endMs)
            assertEquals(line.words[i].endMs, line.words[i + 1].startMs)
        }
    }

    @Test
    fun testMalformedAndNoisyInput() {
        val lrc = """
            [ar:Artist]
            [ti:Title]
            [unknown:tag]
            [invalid:time] This should not crash
            [00:01.23]Valid line
            
            [00:03.456]Valid line with 3 digit ms
            Random un-bracketed line in between
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertTrue(lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1230L, lyrics.lines[0].timestampMs)
        assertEquals(3456L, lyrics.lines[1].timestampMs)
        assertTrue(lyrics.plainText.contains("Random un-bracketed line in between"))
    }
}
