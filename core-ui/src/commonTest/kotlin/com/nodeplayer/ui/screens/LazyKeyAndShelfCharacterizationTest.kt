package com.nodeplayer.ui.screens

import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.YouTubeSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyKeyAndShelfCharacterizationTest {

    @Test
    fun testOrganizeShelvesCanonicalOrderAndFallbackPreservation() {
        val raw = listOf(
            YouTubeSection(title = "Artists you follow", items = listOf(track(1, "A"))),
            YouTubeSection(title = "Custom Discovery", items = listOf(track(2, "B"))),
            YouTubeSection(title = "Trending charts", items = listOf(track(3, "C"))),
            YouTubeSection(title = "Mood mixes", items = listOf(track(4, "D"))),
            YouTubeSection(title = "Quick picks", items = listOf(track(5, "E"))),
            YouTubeSection(title = "New music videos", items = listOf(track(6, "F")))
        )

        val organized = organizeShelves(raw)
        assertEquals(
            listOf(
                "Quick Picks",
                "New Music Videos",
                "Trending in Region",
                "Playlists & Mixes",
                "From Artists You Follow",
                "Custom Discovery"
            ),
            organized.map { it.title }
        )
    }

    @Test
    fun testUniqueLazyTrackKeysPreventCollisionsOnDuplicateIdsAndPaths() {
        val items = listOf(
            track(id = 42L, title = "First", path = "https://yt/watch?v=abc"),
            track(id = 42L, title = "SameIdDiffPath", path = "https://yt/watch?v=def"),
            track(id = 42L, title = "ExactDup", path = "https://yt/watch?v=abc"),
            track(id = 0L, title = "ZeroA", path = ""),
            track(id = 0L, title = "ZeroB", path = "")
        )

        val keys = uniqueLazyTrackKeys(items, prefix = "search")
        assertEquals(items.size, keys.size)
        assertEquals(items.size, keys.toSet().size, "Every generated lazy key must be strictly unique: $keys")
        assertTrue(keys[0] != keys[1])
        assertTrue(keys[0] != keys[2])
        assertTrue(keys[3] != keys[4])
    }

    @Test
    fun testUniqueLazyKeysPreventCollisionsOnDuplicateStrings() {
        val queries = listOf("lofi", "synthwave", "lofi", "jazz", "lofi")
        val keys = uniqueLazyKeys(queries) { "rq_$it" }

        assertEquals(queries.size, keys.size)
        assertEquals(queries.size, keys.toSet().size, "Duplicate queries must still yield unique keys: $keys")
        assertEquals("rq_lofi", keys[0])
        assertEquals("rq_synthwave", keys[1])
    }

    @Test
    fun testFilterAndSortRecentTracksFiltersUnplayedAndSortsDescending() {
        val unplayed = track(1L, "Unplayed", lastPlayedTimestamp = 0L)
        val older = track(2L, "Older", lastPlayedTimestamp = 1_000L)
        val newest = track(3L, "Newest", lastPlayedTimestamp = 5_000L)
        val middle = track(4L, "Middle", lastPlayedTimestamp = 3_000L)

        val result = filterAndSortRecentTracks(listOf(unplayed, older, newest, middle))

        assertEquals(listOf(3L, 4L, 2L), result.map { it.id })
        assertTrue(result.none { it.lastPlayedTimestamp <= 0L })
    }

    private fun track(
        id: Long,
        title: String,
        path: String = "https://youtube.com/watch?v=$id",
        lastPlayedTimestamp: Long = 0L
    ): Track = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        path = path,
        lastPlayedTimestamp = lastPlayedTimestamp
    )
}
