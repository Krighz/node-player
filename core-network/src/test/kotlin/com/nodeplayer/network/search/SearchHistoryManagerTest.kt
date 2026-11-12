package com.nodeplayer.network.search

import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSearchResult
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHistoryManagerTest {

    @Test
    fun testAddQueryDeduplicatesAndCapsAt15() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "np_search_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val manager = SearchHistoryManager(storageDir = tempDir, maxHistory = 5)

            manager.addQuery("Daft Punk")
            manager.addQuery("The Weeknd")
            manager.addQuery("Radiohead")
            // Re-adding Daft Punk should move it to the front
            manager.addQuery("daft punk")

            val history = manager.getRecentQueries()
            assertEquals(3, history.size)
            assertEquals("daft punk", history[0])
            assertEquals("Radiohead", history[1])
            assertEquals("The Weeknd", history[2])

            // Add beyond limit
            manager.addQuery("Coldplay")
            manager.addQuery("Adele")
            manager.addQuery("Drake")

            assertEquals(5, manager.getRecentQueries().size, "History should be capped at maxHistory (5)")
            assertEquals("Drake", manager.getRecentQueries().first())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRemoveIndividualQuery() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "np_search_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val manager = SearchHistoryManager(storageDir = tempDir)
            manager.addQuery("Song A")
            manager.addQuery("Song B")
            manager.addQuery("Song C")

            assertEquals(3, manager.getRecentQueries().size)

            manager.removeQuery("song b")
            val updated = manager.getRecentQueries()
            assertEquals(2, updated.size)
            assertFalse(updated.contains("Song B"))
            assertTrue(updated.contains("Song C"))
            assertTrue(updated.contains("Song A"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testClearAll() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "np_search_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val manager = SearchHistoryManager(storageDir = tempDir)
            manager.addQuery("Query 1")
            manager.addQuery("Query 2")
            assertEquals(2, manager.getRecentQueries().size)

            manager.clearAll()
            assertTrue(manager.getRecentQueries().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSearchCategorizerPartitionsCorrectly() {
        val song1 = Track(id = 1, title = "Song 1", artist = "Artist 1", album = "Album 1", durationMs = 180_000L, path = "1")
        val song2 = Track(id = 2, title = "Song 2", artist = "Artist 2", album = "Album 2", durationMs = 210_000L, path = "2")
        val album1 = Track(id = 10, title = "Album 1", artist = "Artist 1", album = "Album 1", durationMs = 0L, path = "10")
        val artist1 = Track(id = 20, title = "Artist 1", artist = "Artist 1", album = "", durationMs = 0L, path = "20")

        val rawResult = YouTubeSearchResult(
            songs = listOf(song1, song2),
            albums = listOf(album1),
            artists = listOf(artist1)
        )

        val allCategory = SearchCategorizer.categorize(rawResult, SearchCategory.ALL)
        assertEquals(4, allCategory.totalCount)
        assertEquals(song1, allCategory.displayItems[0]) // Songs first

        val songsCategory = SearchCategorizer.categorize(rawResult, SearchCategory.SONGS)
        assertEquals(2, songsCategory.totalCount)
        assertEquals(listOf(song1, song2), songsCategory.displayItems)

        val albumsCategory = SearchCategorizer.categorize(rawResult, SearchCategory.ALBUMS)
        assertEquals(1, albumsCategory.totalCount)
        assertEquals(listOf(album1), albumsCategory.displayItems)

        val artistsCategory = SearchCategorizer.categorize(rawResult, SearchCategory.ARTISTS)
        assertEquals(1, artistsCategory.totalCount)
        assertEquals(listOf(artist1), artistsCategory.displayItems)
    }
}
