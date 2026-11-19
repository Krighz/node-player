package com.nodeplayer.network.innertube

import com.nodeplayer.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTrackMatcherTest {

    private fun createTrack(id: Long, title: String, artist: String, durationMs: Long, path: String): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Single",
            durationMs = durationMs,
            path = path
        )
    }

    @Test
    fun matchesOfficialMusicVideoWithIntroDrift() {
        val candidates = listOf(
            createTrack(1, "Maroon 5 - Sugar (Official Music Video)", "Maroon 5", 300_000L, "nodeplayer://youtube/09R8_2nJtjg"),
            createTrack(2, "Sugar (1 Hour Loop)", "Maroon 5", 3_600_000L, "nodeplayer://youtube/fake_loop"),
            createTrack(3, "Sugar - Acoustic Cover", "Jane Doe", 220_000L, "nodeplayer://youtube/fake_cover")
        )

        val match = VideoTrackMatcher.bestMatch(
            wantedTitle = "Sugar",
            wantedArtist = "Maroon 5",
            wantedDurationMs = 235_000L, // Audio is 3m55s
            candidates = candidates
        )

        assertNotNull(match)
        assertEquals("09R8_2nJtjg", match?.path?.removePrefix("nodeplayer://youtube/"))
    }

    @Test
    fun rejectsUnrelatedArtistAndMismatchedVersion() {
        val candidates = listOf(
            createTrack(1, "Passenger - Let Her Go (Remix)", "Passenger", 250_000L, "nodeplayer://youtube/remix_vid"),
            createTrack(2, "Let Her Go (Karaoke Version)", "Various Artists", 250_000L, "nodeplayer://youtube/karaoke_vid")
        )

        // Target wants original studio track, not remix or karaoke
        val match = VideoTrackMatcher.bestMatch(
            wantedTitle = "Let Her Go",
            wantedArtist = "Passenger",
            wantedDurationMs = 252_000L,
            candidates = candidates
        )

        assertNull(match)
    }

    @Test
    fun stripsComplexPackagingAndMatchesCoreTitle() {
        val candidate = createTrack(1, "The Weeknd - Blinding Lights (Official Video) [4K]", "The Weeknd", 260_000L, "nodeplayer://youtube/4NRXx6U8ABQ")

        val match = VideoTrackMatcher.bestMatch(
            wantedTitle = "Blinding Lights",
            wantedArtist = "The Weeknd",
            wantedDurationMs = 200_000L,
            candidates = listOf(candidate)
        )

        assertNotNull(match)
        assertEquals(1L, match?.id)
    }
}
