package com.nodeplayer.network.spotify

import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.YouTubeSearchResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTrackMatcherTest {

    private val matcher = SpotifyTrackMatcher(InnerTubeClient())

    @Test
    fun testScoreCandidateExactMatch() {
        val spotify = SpotifyTrack(
            id = "s1",
            title = "Midnight City",
            artists = listOf("M83"),
            album = "Hurry Up, We're Dreaming",
            durationMs = 243000L,
            isrc = "US123"
        )
        val candidate = Track(
            id = 101L,
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243500L,
            path = "nodeplayer://youtube/vid123"
        )

        val score = matcher.scoreCandidate(spotify, candidate)
        assertTrue("Expected score >= 0.75 for exact match, got $score", score >= 0.75f)
    }

    @Test
    fun testScoreCandidateRemasterAndFeaturedVariants() {
        val spotify = SpotifyTrack(
            id = "s2",
            title = "Starboy (feat. Daft Punk) - Remastered",
            artists = listOf("The Weeknd", "Daft Punk"),
            album = "Starboy",
            durationMs = 230000L
        )
        val candidate = Track(
            id = 102L,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230500L,
            path = "nodeplayer://youtube/vid_starboy"
        )

        val score = matcher.scoreCandidate(spotify, candidate)
        assertTrue("Expected score >= 0.65 for variant match, got $score", score >= SpotifyTrackMatcher.HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun testScoreCandidateDurationDivergencePenalty() {
        val spotify = SpotifyTrack(
            id = "s3",
            title = "Stairway to Heaven",
            artists = listOf("Led Zeppelin"),
            album = "Led Zeppelin IV",
            durationMs = 482000L // ~8 mins
        )
        val shortCandidate = Track(
            id = 103L,
            title = "Stairway to Heaven (Radio Edit)",
            artist = "Led Zeppelin",
            album = "Radio Hits",
            durationMs = 210000L, // 3.5 mins (huge divergence)
            path = "nodeplayer://youtube/vid_short"
        )

        val score = matcher.scoreCandidate(spotify, shortCandidate)
        assertTrue("Expected score < 0.65 due to duration divergence penalty, got $score", score < SpotifyTrackMatcher.HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun testCleanTrackTitle() {
        assertEquals("Get Lucky", matcher.cleanTrackTitle("Get Lucky (feat. Pharrell Williams)"))
        assertEquals("Time", matcher.cleanTrackTitle("Time [2011 - Remaster]"))
        assertEquals("In the End", matcher.cleanTrackTitle("In the End - Live in Texas"))
        assertEquals("Something About Us", matcher.cleanTrackTitle("Something About Us (Official Music Video)"))
    }
}
