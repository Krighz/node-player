package com.nodeplayer.network.recommender

import com.nodeplayer.model.Track
import com.nodeplayer.network.cache.HomeFeedDiskCache
import com.nodeplayer.network.innertube.YouTubeSection
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedRecommenderTest {

    private val recommender = HomeFeedRecommender()

    @Test
    fun playlistOnlySectionsSurviveFiltering() {
        val section = com.nodeplayer.network.innertube.YouTubeSection(
            title = "Made for you", items = emptyList(),
            playlists = listOf(com.nodeplayer.network.innertube.YouTubePlaylistSummary(id = "PL123", title = "Mix"))
        )
        assertEquals(listOf(section), HomeFeedRecommender().filterDismissedSections(listOf(section)))
    }

    @Test
    fun testScoringWeightsRecencyAndPlayCount() {
        val now = 1_700_000_000_000L

        val recentFrequentTrack = Track(
            id = 1L,
            title = "Recent Hit",
            artist = "Artist A",
            album = "Album A",
            durationMs = 200_000L,
            path = "https://youtube.com/watch?v=1",
            playCount = 10,
            lastPlayedTimestamp = now - 3600_000L // 1 hr ago
        )

        val oldFrequentTrack = Track(
            id = 2L,
            title = "Old Hit",
            artist = "Artist B",
            album = "Album B",
            durationMs = 180_000L,
            path = "https://youtube.com/watch?v=2",
            playCount = 10,
            lastPlayedTimestamp = now - (60 * 24 * 3600_000L) // 60 days ago
        )

        val recentScore = recommender.calculateScore(recentFrequentTrack, now)
        val oldScore = recommender.calculateScore(oldFrequentTrack, now)

        assertTrue(recentScore > oldScore, "Recent track should score higher than older track with identical play count")
    }

    @Test
    fun testDismissTrackAndArtistRemovesFromFeed() {
        val track1 = Track(
            id = 101L,
            title = "Unwanted Song",
            artist = "Annoying Artist",
            album = "Album X",
            durationMs = 180_000L,
            path = "video_101"
        )
        val track2 = Track(
            id = 102L,
            title = "Another Song",
            artist = "Annoying Artist",
            album = "Album Y",
            durationMs = 210_000L,
            path = "video_102"
        )
        val track3 = Track(
            id = 103L,
            title = "Good Song",
            artist = "Beloved Artist",
            album = "Album Z",
            durationMs = 240_000L,
            path = "video_103"
        )

        assertFalse(recommender.isDismissed(track1))

        // Dismiss track1 -> its artist "Annoying Artist" is also registered
        recommender.dismissTrack(track1)

        assertTrue(recommender.isDismissed(track1), "track1 should be dismissed")
        assertTrue(recommender.isDismissed(track2), "track2 by the same artist should also be filtered")
        assertFalse(recommender.isDismissed(track3), "track3 by different artist should NOT be dismissed")

        val filtered = recommender.filterDismissed(listOf(track1, track2, track3))
        assertEquals(1, filtered.size)
        assertEquals(track3.id, filtered.first().id)
    }

    @Test
    fun testFallbackQuickPicksNoRandomTracks() {
        val now = 1_700_000_000_000L

        val localTracks = listOf(
            Track(id = 1L, title = "Top Song", artist = "Artist 1", album = "", durationMs = 180_000L, path = "1", playCount = 20, lastPlayedTimestamp = now),
            Track(id = 2L, title = "Medium Song", artist = "Artist 2", album = "", durationMs = 180_000L, path = "2", playCount = 5, lastPlayedTimestamp = now),
            Track(id = 3L, title = "Low Song", artist = "Artist 3", album = "", durationMs = 180_000L, path = "3", playCount = 1, lastPlayedTimestamp = now)
        )

        val chartTracks = listOf(
            Track(id = 4L, title = "Chart Hit 1", artist = "Chart Artist 1", album = "", durationMs = 190_000L, path = "4"),
            Track(id = 5L, title = "Chart Hit 2", artist = "Chart Artist 2", album = "", durationMs = 190_000L, path = "5")
        )

        val picks = recommender.buildFallbackQuickPicks(
            recentTracks = localTracks,
            fallbackRegionCharts = chartTracks,
            limit = 4,
            now = now
        )

        assertEquals(4, picks.size)
        // Order should be sorted by score descending: 1 (top), 2 (medium), 3 (low), then 4 (chart)
        assertEquals(1L, picks[0].id)
        assertEquals(2L, picks[1].id)
        assertEquals(3L, picks[2].id)
        assertEquals(4L, picks[3].id)
    }

    @Test
    fun testRegionResolutionLocaleAndManualOverride() {
        assertEquals("JP", recommender.resolveRegion("JP"))
        assertEquals("GB", recommender.resolveRegion("gb"))
        // Invalid or empty override falls back to system locale or US
        val fallback = recommender.resolveRegion(null)
        assertTrue(fallback.isNotEmpty() && fallback.length == 2)
    }

    @Test
    fun cachedFeedsAreScopedAndRetainPlaylists() {
        val dir = File(System.getProperty("java.io.tmpdir"), "np_scoped_cache_${System.nanoTime()}")
        try {
            val section = YouTubeSection("Mixes", emptyList(), listOf(
                com.nodeplayer.network.innertube.YouTubePlaylistSummary("PL123", "My mix", thumbnailUrl = "https://example.com/image")
            ))
            HomeFeedDiskCache(cacheDir = dir, cacheKey = "home:account-a").save(listOf(section))
            assertEquals(listOf(section), HomeFeedDiskCache(cacheDir = dir, cacheKey = "home:account-a").loadFromDisk())
            assertTrue(HomeFeedDiskCache(cacheDir = dir, cacheKey = "home:account-b").loadFromDisk().isEmpty())
            assertTrue(HomeFeedDiskCache(cacheDir = dir, cacheKey = "explore:account-a").loadFromDisk().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testCacheSerializationAndDeserialization() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "np_cache_test_${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val cache = HomeFeedDiskCache(cacheDir = tempDir)
            val testSections = listOf(
                YouTubeSection(
                    title = "Quick Picks",
                    items = listOf(
                        Track(id = 10L, title = "Track 10", artist = "Artist 10", album = "Album 10", durationMs = 200_000L, path = "path_10", playCount = 5)
                    )
                ),
                YouTubeSection(
                    title = "Trending in region",
                    items = listOf(
                        Track(id = 20L, title = "Track 20", artist = "Artist 20", album = "Album 20", durationMs = 220_000L, path = "path_20", playCount = 0)
                    )
                )
            )

            cache.save(testSections, region = "US")
            assertTrue(cache.isValid())

            // Create fresh instance reading from same directory
            val cacheReader = HomeFeedDiskCache(cacheDir = tempDir)
            val loaded = cacheReader.loadFromDisk()

            assertEquals(2, loaded.size)
            assertEquals("Quick Picks", loaded[0].title)
            assertEquals("Track 10", loaded[0].items.first().title)
            assertEquals("Trending in region", loaded[1].title)
            assertEquals("Track 20", loaded[1].items.first().title)
            assertEquals("US", cacheReader.getRegion())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
