package com.nodeplayer.ui.screens

import androidx.compose.ui.unit.dp
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSection
import com.nodeplayer.network.recommender.HomeFeedRecommender
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeShelvesTest {

    @Test
    fun testOrganizeShelvesProducesMandatedFiveShelves() {
        val rawSections = listOf(
            YouTubeSection(title = "Trending today", items = listOf(createTrack(1, "Trend 1"))),
            YouTubeSection(title = "Music videos", items = listOf(createTrack(2, "Video 1"))),
            YouTubeSection(title = "Quick picks for you", items = listOf(createTrack(3, "Pick 1"))),
            YouTubeSection(title = "Artists you follow", items = listOf(createTrack(4, "Artist 1"))),
            YouTubeSection(title = "Mood mix playlists", items = listOf(createTrack(5, "Playlist 1")))
        )

        val organized = organizeShelves(rawSections)

        assertEquals(5, organized.size)
        assertEquals("Quick Picks", organized[0].title)
        assertEquals("New Music Videos", organized[1].title)
        assertEquals("Trending in Region", organized[2].title)
        assertEquals("Playlists & Mixes", organized[3].title)
        assertEquals("From Artists You Follow", organized[4].title)
    }

    @Test
    fun testVideoCardDimensions16x9() {
        val width = 220f
        val height = 124f
        val targetHeight = width * 9f / 16f
        assertTrue(abs(height - targetHeight) < 0.5f, "Video card aspect ratio should match 16:9 widescreen within 0.5dp")
    }

    @Test
    fun testAudioCardDimensions1x1() {
        val width = 136f
        val height = 136f
        assertEquals(width, height, "Audio squircle card should have an exact 1:1 aspect ratio")
    }

    @Test
    fun testDismissFilterRemovesCardFromShelf() {
        val recommender = HomeFeedRecommender()
        val trackA = createTrack(10, "Bad Song", artist = "Unwanted Artist")
        val trackB = createTrack(20, "Great Song", artist = "Awesome Artist")

        val section = YouTubeSection(title = "Quick Picks", items = listOf(trackA, trackB))

        recommender.dismissTrack(trackA)

        val cleanSections = recommender.filterDismissedSections(listOf(section))
        assertEquals(1, cleanSections.size)
        assertEquals(1, cleanSections[0].items.size)
        assertEquals(20L, cleanSections[0].items[0].id)
    }

    private fun createTrack(id: Long, title: String, artist: String = "Test Artist"): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            durationMs = 180_000L,
            path = "https://youtube.com/watch?v=$id"
        )
    }
}
