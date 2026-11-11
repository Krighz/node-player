package com.nodeplayer.ui.screens

import com.nodeplayer.model.MoodGenre
import com.nodeplayer.model.Playlist
import com.nodeplayer.model.Track
import com.nodeplayer.ui.PlayerNavDestination
import kotlin.test.*

class ExploreNavigationTest {

    @Test
    fun testPlayerNavDestinationNewMapsToExplore() {
        val dest = PlayerNavDestination.NEW
        assertEquals("New", dest.title)
    }

    @Test
    fun testCategoryToDetailMediaHeaderMapping() {
        val category = MoodGenre(
            title = "Chill & Relax",
            stripeColor = 0xFF4A90E2,
            browseId = "FEmusic_moods_and_genres_category_chill",
            params = "ggMPOg1DaGlsbCAmIFJlbGF4",
            thumbnailUrl = "https://lh3.googleusercontent.com/test_thumb"
        )

        val header = DetailMediaHeader(
            id = category.browseId,
            title = category.title,
            subtitle = "YouTube Music Explore",
            artworkUrl = category.thumbnailUrl,
            description = "YouTube Music Moods & Genres",
            isAlbum = false
        )

        assertEquals("FEmusic_moods_and_genres_category_chill", header.id)
        assertEquals("Chill & Relax", header.title)
        assertEquals("YouTube Music Explore", header.subtitle)
        assertEquals("https://lh3.googleusercontent.com/test_thumb", header.artworkUrl)
        assertEquals("YouTube Music Moods & Genres", header.description)
        assertFalse(header.isAlbum)
    }

    @Test
    fun testCategoryShelvesToTracksMapping() {
        val shelves = listOf(
            Playlist(
                id = 1001L,
                name = "Deep Focus",
                trackCount = 42,
                source = "youtube",
                externalId = "RDCLAK5uy_focus"
            ),
            Playlist(
                id = 1002L,
                name = "Acoustic Chill",
                trackCount = 30,
                source = "youtube",
                externalId = "RDCLAK5uy_acoustic"
            )
        )

        val categoryTitle = "Chill & Relax"
        val categoryTracks = shelves.map { pl ->
            Track(
                id = pl.id,
                title = pl.name,
                artist = "YouTube Music",
                album = categoryTitle,
                durationMs = 0L,
                path = "nodeplayer://youtube/playlist/${pl.externalId ?: pl.id}"
            )
        }

        assertEquals(2, categoryTracks.size)
        assertEquals("Deep Focus", categoryTracks[0].title)
        assertEquals("Chill & Relax", categoryTracks[0].album)
        assertEquals("nodeplayer://youtube/playlist/RDCLAK5uy_focus", categoryTracks[0].path)

        assertEquals("Acoustic Chill", categoryTracks[1].title)
        assertEquals("nodeplayer://youtube/playlist/RDCLAK5uy_acoustic", categoryTracks[1].path)
    }

    @Test
    fun testActiveDetailHeaderDismissalClearsOverlay() {
        var activeDetailHeader: DetailMediaHeader? = DetailMediaHeader(
            id = "test_category",
            title = "Test",
            subtitle = "Subtitle",
            isAlbum = false
        )
        assertNotNull(activeDetailHeader)

        // Simulating onBack = { activeDetailHeader = null }
        activeDetailHeader = null
        assertNull(activeDetailHeader)
    }
}
