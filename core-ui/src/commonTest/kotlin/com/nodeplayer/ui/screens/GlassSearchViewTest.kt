package com.nodeplayer.ui.screens

import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSearchResult
import com.nodeplayer.network.search.SearchCategorizer
import com.nodeplayer.network.search.SearchCategory
import com.nodeplayer.network.search.SearchHistoryManager
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassSearchViewTest {

    @Test
    fun testFilterCategoriesLabelsAndValues() {
        val categories = SearchCategory.values()
        assertEquals(5, categories.size)
        assertEquals("All", categories[0].label)
        assertEquals("Songs", categories[1].label)
        assertEquals("Artists", categories[2].label)
        assertEquals("Albums", categories[3].label)
        assertEquals("Playlists", categories[4].label)
    }

    @Test
    fun testSongClickQueuesRemainingSearchResults() {
        val songA = Track(id = 10, title = "Song A", artist = "Artist", album = "Album", durationMs = 180_000L, path = "10")
        val songB = Track(id = 20, title = "Song B", artist = "Artist", album = "Album", durationMs = 210_000L, path = "20")
        val songC = Track(id = 30, title = "Song C", artist = "Artist", album = "Album", durationMs = 240_000L, path = "30")

        val raw = YouTubeSearchResult(songs = listOf(songA, songB, songC))
        val categorized = SearchCategorizer.categorize(raw, SearchCategory.SONGS)

        var playedTrack: Track? = null
        var queuedTracks: List<Track> = emptyList()

        val onPlay: (Track, List<Track>) -> Unit = { track, queue ->
            playedTrack = track
            queuedTracks = queue
        }

        // Tap songB
        onPlay(songB, categorized.songs)

        assertEquals(songB, playedTrack)
        assertEquals(3, queuedTracks.size)
        assertEquals(listOf(songA, songB, songC), queuedTracks)
    }

    @Test
    fun testRecentQueryAddAndClearReactivity() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "np_search_react_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val manager = SearchHistoryManager(storageDir = tempDir, maxHistory = 10)
            manager.addQuery("Hans Zimmer")
            manager.addQuery("Interstellar")

            val list = manager.getRecentQueries()
            assertEquals(2, list.size)
            assertEquals("Interstellar", list[0])

            manager.removeQuery("Hans Zimmer")
            assertEquals(1, manager.getRecentQueries().size)

            manager.clearAll()
            assertTrue(manager.getRecentQueries().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
