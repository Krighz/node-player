package com.nodeplayer.ui.player

import com.nodeplayer.library.LrcParser
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingActiveLyricCharacterizationTest {

    @Test
    fun testFindActiveLyricTextMatchesLegacyIndexOfLastAcrossLrcTrack() {
        val lrc = """
            [00:05.00]First verse line
            [00:10.50]Second verse line
            [00:10.50]Duplicate timestamp chorus
            [00:18.25]Bridge line
            [00:25.00]Final outro line
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc, trackId = 42L)

        val probeTimestamps = listOf(
            0L, 4999L, 5000L, 7500L, 10499L, 10500L, 14000L, 18249L, 18250L, 24999L, 25000L, 40000L
        )
        for (pos in probeTimestamps) {
            val expectedIdx = lyrics.lines.indexOfLast { it.timestampMs <= pos }
            val expectedText = if (expectedIdx in lyrics.lines.indices) lyrics.lines[expectedIdx].text else null
            assertEquals(expectedText, findActiveLyricText(lyrics.lines, pos), "Mismatch at pos=$pos")
        }

        assertEquals(null, findActiveLyricText(emptyList(), 5000L))
    }
}
