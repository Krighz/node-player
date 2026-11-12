package com.nodeplayer.library.lyrics

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LrcRedTest {

    @Test
    fun testDurationWindowToleranceFiltering() {
        val hits = listOf(
            LrcRed.SearchCandidate(
                isrc = "USUM71703861",
                title = "Starboy",
                artist = "The Weeknd",
                duration = 230.0 // target is 231.0s (diff 1.0s <= 3.0s -> match)
            ),
            LrcRed.SearchCandidate(
                isrc = "USUM71703862",
                title = "Starboy",
                artist = "The Weeknd",
                duration = 235.0 // diff 4.0s > 3.0s -> reject
            )
        )

        val best = LrcRed.best(hits, title = "Starboy", artist = "The Weeknd", durationMs = 231_000L)
        assertNotNull(best)
        assertEquals("USUM71703861", best.isrc)
        assertEquals(230.0, best.duration)
    }

    @Test
    fun testDurationWindowRejectsOverTolerance() {
        val hits = listOf(
            LrcRed.SearchCandidate(
                isrc = "USUM71703862",
                title = "Starboy",
                artist = "The Weeknd",
                duration = 235.5 // 4.5s difference -> reject
            )
        )
        val best = LrcRed.best(hits, title = "Starboy", artist = "The Weeknd", durationMs = 231_000L)
        assertNull(best)
    }

    @Test
    fun testVersionGuardFiltering() {
        val studioTarget = "Bohemian Rhapsody"
        val hits = listOf(
            LrcRed.SearchCandidate(
                isrc = "GBUM71029605",
                title = "Bohemian Rhapsody (Live at Wembley)",
                artist = "Queen",
                duration = 354.0
            ),
            LrcRed.SearchCandidate(
                isrc = "GBUM71029606",
                title = "Bohemian Rhapsody (Acoustic Version)",
                artist = "Queen",
                duration = 354.0
            ),
            LrcRed.SearchCandidate(
                isrc = "GBUM71029607",
                title = "Bohemian Rhapsody - 2011 Remaster",
                artist = "Queen",
                duration = 354.2
            )
        )

        // Studio search should reject "Live" and "Acoustic" versions, matching the studio remaster
        val best = LrcRed.best(hits, title = studioTarget, artist = "Queen", durationMs = 355_000L)
        assertNotNull(best)
        assertEquals("GBUM71029607", best.isrc)
    }

    @Test
    fun testVersionGuardMatchesLiveTarget() {
        val liveTarget = "Bohemian Rhapsody (Live)"
        val hits = listOf(
            LrcRed.SearchCandidate(
                isrc = "GBUM71029605",
                title = "Bohemian Rhapsody (Live at Wembley)",
                artist = "Queen",
                duration = 354.0
            ),
            LrcRed.SearchCandidate(
                isrc = "GBUM71029607",
                title = "Bohemian Rhapsody - 2011 Remaster",
                artist = "Queen",
                duration = 354.2
            )
        )

        // Live target should match the Live candidate
        val best = LrcRed.best(hits, title = liveTarget, artist = "Queen", durationMs = 354_000L)
        assertNotNull(best)
        assertEquals("GBUM71029605", best.isrc)
    }

    @Test
    fun testParseHitsFromJson() {
        val json = """
            {
              "hits": [
                {
                  "isrc": "USUM71703861",
                  "title": "Starboy",
                  "artist": "The Weeknd feat. Daft Punk",
                  "album": "Starboy",
                  "year": 2016,
                  "duration": 230.45
                }
              ]
            }
        """.trimIndent()

        val parsed = LrcRed.parseHitsFromJson(json)
        assertEquals(1, parsed.size)
        val hit = parsed[0]
        assertEquals("USUM71703861", hit.isrc)
        assertEquals("Starboy", hit.title)
        assertEquals("The Weeknd feat. Daft Punk", hit.artist)
        assertEquals(2016, hit.year)
        assertEquals(230.45, hit.duration)
    }
}
