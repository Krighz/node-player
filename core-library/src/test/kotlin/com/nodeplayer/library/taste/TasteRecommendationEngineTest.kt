package com.nodeplayer.library.taste

import com.nodeplayer.common.logging.LogLevel
import com.nodeplayer.common.logging.LogSink
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.MoodBucket
import com.nodeplayer.model.Track
import com.nodeplayer.model.bucketFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TasteRecommendationEngineTest {

    private fun createTrack(id: Long, title: String, artist: String, videoId: String): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            durationMs = 180_000L,
            path = "nodeplayer://youtube/$videoId",
            formatDescription = "YouTube Audio"
        )
    }

    private class FakeTasteHistorySource(
        var historyResult: HistoryResult? = null,
        var historyTracks: List<Track> = emptyList(),
        var dailyMixTracks: List<Track> = emptyList(),
        var radioQueues: Map<String, List<Track>> = emptyMap(),
        var fallbackCharts: List<Track> = emptyList()
    ) : TasteHistorySource {

        override suspend fun recentHistory(limit: Int): HistoryResult {
            return historyResult ?: HistoryResult.Success(
                historyTracks.mapNotNull { track ->
                    val vid = track.path.removePrefix("nodeplayer://youtube/").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    HistoryEntry(
                        videoId = vid,
                        title = track.title,
                        artistName = track.artist,
                        durationMs = track.durationMs
                    )
                }.take(limit)
            )
        }

        override suspend fun getListeningHistory(): Result<List<Track>> = Result.success(historyTracks)
        override suspend fun getDailyMixTracks(): Result<List<Track>> = Result.success(dailyMixTracks)
        override suspend fun getRadioForTrack(videoId: String): Result<List<Track>> =
            Result.success(radioQueues[videoId] ?: emptyList())
        override suspend fun getFallbackTopCharts(): Result<List<Track>> = Result.success(fallbackCharts)
    }

    @Test
    fun testWeightedSamplingNoDuplicatesAndPoolBoundary() {
        val items = listOf("A", "B", "C")
        val weights = listOf(10.0, 1.0, 0.1)

        // k larger than pool
        val sampledOverK = weightedSampleWithoutReplacement(items, weights, k = 10, Random(42))
        assertEquals(3, sampledOverK.size)
        assertEquals(setOf("A", "B", "C"), sampledOverK.toSet())

        // Empty pool
        val sampledEmpty = weightedSampleWithoutReplacement<String>(emptyList(), emptyList(), k = 5, Random(42))
        assertTrue(sampledEmpty.isEmpty())

        // Respects weights over many runs: "A" should be chosen first significantly more often than "C"
        var aFirstCount = 0
        var cFirstCount = 0
        for (i in 1..500) {
            val res = weightedSampleWithoutReplacement(items, weights, k = 2, Random(i))
            if (res.first() == "A") aFirstCount++
            if (res.first() == "C") cFirstCount++
        }
        assertTrue("A should be selected first much more often than C: a=$aFirstCount, c=$cFirstCount", aFirstCount > cFirstCount * 5)
    }

    @Test
    fun testRecent15Exclusion() {
        runBlocking {
            // Create 30 history tracks. First 15 are recent, remaining 15 are older history
            val history = (1..30).map { i ->
                createTrack(i.toLong(), "History Song $i", if (i <= 15) "Recent Artist" else "Older Artist", "vid_$i")
            }

            val radioQueue = (1..15).map { i ->
                createTrack((200 + i).toLong(), "Radio Song $i", "Older Artist", if (i in 1..5) "vid_$i" else "rad_vid_$i")
            }

            val fakeSource = FakeTasteHistorySource(
                historyTracks = history,
                radioQueues = mapOf("vid_16" to radioQueue)
            )

            val engine = TasteRecommendationEngine(fakeSource)
            val stationResult = engine.generateTasteStation(config = TasteConfig(recentExcludeCount = 15))
            assertTrue(stationResult.isSuccess)
            val station = stationResult.getOrThrow()

            val excludedVids = (1..15).map { "vid_$it" }.toSet()
            val stationVids = station.mapNotNull { it.extractVideoId() }

            for (vid in stationVids) {
                assertFalse("Track $vid should have been excluded by recent-15 filter!", vid in excludedVids)
            }
        }
    }

    @Test
    fun testNotInterestedExclusion() {
        runBlocking {
            val history = (1..25).map { i ->
                createTrack(i.toLong(), "Song $i", "Artist A", "vid_$i")
            }

            val fakeSource = FakeTasteHistorySource(historyTracks = history)
            val engine = TasteRecommendationEngine(fakeSource)

            val config = TasteConfig(
                recentExcludeCount = 10,
                notInterestedVideoIds = setOf("vid_18", "vid_20")
            )

            val station = engine.generateTasteStation(config).getOrThrow()
            val stationVids = station.mapNotNull { it.extractVideoId() }.toSet()

            assertFalse(stationVids.contains("vid_18"))
            assertFalse(stationVids.contains("vid_20"))
        }
    }

    @Test
    fun testTop10RankingAndTieBreak() {
        runBlocking {
            // 12 artists. Artists 1 to 10 have play counts descending, Artist 11 and 12 have 1 play each.
            // Two artists with equal play counts should tie-break by most recent play (earlier index in history).
            val entries = mutableListOf<HistoryEntry>()

            // Index 0: TieArtistA played
            entries.add(HistoryEntry("vid_tie_a", "Song Tie A", "TieArtistA"))
            // Index 1: TieArtistB played
            entries.add(HistoryEntry("vid_tie_b", "Song Tie B", "TieArtistB"))

            // Add remaining artists with distinct counts
            for (i in 1..10) {
                repeat(i + 2) { repeatIdx ->
                    entries.add(HistoryEntry("vid_${i}_$repeatIdx", "Song $i $repeatIdx", "Artist$i"))
                }
            }

            val fakeSource = FakeTasteHistorySource(
                historyResult = HistoryResult.Success(entries)
            )

            val engine = TasteRecommendationEngine(fakeSource)
            val config = TasteConfig(recentExcludeCount = 0, topArtistCount = 10, seedArtistCount = 10)
            val station = engine.generateTasteStation(config).getOrThrow()
            assertTrue(station.isNotEmpty())
        }
    }

    @Test
    fun testMixBuilder25SizeAndDedupe() {
        runBlocking {
            val artists = listOf("Artist A", "Artist B", "Artist C")
            val history = (1..40).map { i ->
                val artist = artists[i % artists.size]
                createTrack(i.toLong(), "Song $i", artist, "hist_$i")
            }

            val radioA = (1..15).map { i -> createTrack((200 + i).toLong(), "Radio A $i", "Artist A", "rad_a_$i") }
            val radioB = (1..15).map { i -> createTrack((300 + i).toLong(), "Radio B $i", "Artist B", "rad_b_$i") }
            val radioC = (1..15).map { i -> createTrack((400 + i).toLong(), "Radio C $i", "Artist C", "rad_c_$i") }

            val fakeSource = FakeTasteHistorySource(
                historyTracks = history,
                radioQueues = mapOf("hist_16" to radioB, "hist_17" to radioC, "hist_18" to radioA)
            )

            val engine = TasteRecommendationEngine(fakeSource, random = Random(42))
            val station = engine.generateTasteStation().getOrThrow()

            assertEquals(25, station.size)
            val vids = station.mapNotNull { it.extractVideoId() }
            assertEquals("Duplicate video IDs must be filtered out", vids.size, vids.toSet().size)
        }
    }

    @Test
    fun testFewerThan25TracksNeverPaddedWithFakeData() {
        runBlocking {
            // Only 18 history tracks total. 15 excluded leaves 3 tracks.
            val history = (1..18).map { i ->
                createTrack(i.toLong(), "Song $i", "Small Artist", "vid_$i")
            }

            val fakeSource = FakeTasteHistorySource(
                historyTracks = history,
                radioQueues = emptyMap() // No radio tracks returned
            )

            val engine = TasteRecommendationEngine(fakeSource)
            val station = engine.generateTasteStation().getOrThrow()

            // 3 remaining tracks, not padded to 25 with fake data
            assertEquals(3, station.size)
            val stationVids = station.mapNotNull { it.extractVideoId() }.toSet()
            assertEquals(setOf("vid_16", "vid_17", "vid_18"), stationVids)
        }
    }

    @Test
    fun testEmptyHistoryFallbackToCharts() {
        runBlocking {
            val fallbackTracks = (1..30).map { i ->
                createTrack(i.toLong(), "Chart Song $i", "Popular Artist", "chart_$i")
            }

            val fakeSource = FakeTasteHistorySource(
                historyResult = HistoryResult.Empty,
                fallbackCharts = fallbackTracks
            )
            val engine = TasteRecommendationEngine(fakeSource)

            val station = engine.generateTasteStation().getOrThrow()
            assertEquals(25, station.size)
            assertEquals("chart_1", station.first().extractVideoId())
        }
    }

    @Test
    fun testPausedHistoryFallbackToCharts() {
        runBlocking {
            val fallbackTracks = (1..30).map { i ->
                createTrack(i.toLong(), "Chart Song $i", "Popular Artist", "chart_$i")
            }

            val fakeSource = FakeTasteHistorySource(
                historyResult = HistoryResult.Paused,
                fallbackCharts = fallbackTracks
            )
            val engine = TasteRecommendationEngine(fakeSource)

            val station = engine.generateTasteStation().getOrThrow()
            assertEquals(25, station.size)
            assertEquals("chart_1", station.first().extractVideoId())
        }
    }

    @Test
    fun testBucketBoundaries() {
        val buckets = MoodBucket.DEFAULT_BUCKETS

        // 04:59 -> hour 4: Late Night Ambient
        assertEquals("late_night", bucketFor(4, buckets)?.id)

        // 05:00 -> hour 5: Morning Energy
        assertEquals("morning_energy", bucketFor(5, buckets)?.id)

        // 10:59 -> hour 10: Morning Energy
        assertEquals("morning_energy", bucketFor(10, buckets)?.id)

        // 11:00 -> hour 11: Deep Focus
        assertEquals("deep_focus", bucketFor(11, buckets)?.id)

        // 16:59 -> hour 16: Deep Focus
        assertEquals("deep_focus", bucketFor(16, buckets)?.id)

        // 17:00 -> hour 17: Evening Chill
        assertEquals("evening_chill", bucketFor(17, buckets)?.id)

        // 21:59 -> hour 21: Evening Chill
        assertEquals("evening_chill", bucketFor(21, buckets)?.id)

        // 22:00 -> hour 22: Late Night Ambient
        assertEquals("late_night", bucketFor(22, buckets)?.id)

        // 23:59 -> hour 23: Late Night Ambient
        assertEquals("late_night", bucketFor(23, buckets)?.id)

        // 00:00 -> hour 0: Late Night Ambient
        assertEquals("late_night", bucketFor(0, buckets)?.id)
    }

    @Test
    fun testSettingsChangeAltersResult() {
        runBlocking {
            val history = (1..30).map { i ->
                createTrack(i.toLong(), "Song $i", "Artist A", "vid_$i")
            }

            val fakeSource = FakeTasteHistorySource(historyTracks = history)
            val engine = TasteRecommendationEngine(fakeSource)

            val resultWithExclude5 = engine.generateTasteStation(
                config = TasteConfig(recentExcludeCount = 5, dailyMixTrackCount = 20)
            ).getOrThrow()

            val resultWithExclude20 = engine.generateTasteStation(
                config = TasteConfig(recentExcludeCount = 20, dailyMixTrackCount = 20)
            ).getOrThrow()

            // resultWithExclude5 contains vid_10, but resultWithExclude20 does not
            val vids5 = resultWithExclude5.mapNotNull { it.extractVideoId() }.toSet()
            val vids20 = resultWithExclude20.mapNotNull { it.extractVideoId() }.toSet()

            assertTrue(vids5.contains("vid_10"))
            assertFalse(vids20.contains("vid_10"))
        }
    }

    @Test
    fun testNoHistoryTitlesOrVideoIdsInLogs() {
        runBlocking {
            val secretTitle = "UltraSecretUniqueSongTitle"
            val secretVideoId = "secVid98765"
            val secretArtist = "TopSecretBandName"

            val history = listOf(
                createTrack(1L, secretTitle, secretArtist, secretVideoId),
                createTrack(2L, "Normal Song", "Normal Artist", "normal_vid_2")
            )

            val capturedMessages = mutableListOf<String>()
            val sink = LogSink { _, _, message, _ ->
                capturedMessages.add(message)
            }
            NodeLog.addSink(sink)

            try {
                val fakeSource = FakeTasteHistorySource(historyTracks = history)
                val engine = TasteRecommendationEngine(fakeSource)
                engine.generateTasteStation()

                for (msg in capturedMessages) {
                    assertFalse("Log message leaked secret track title: $msg", msg.contains(secretTitle))
                    assertFalse("Log message leaked secret video ID: $msg", msg.contains(secretVideoId))
                    assertFalse("Log message leaked secret artist name: $msg", msg.contains(secretArtist))
                }
            } finally {
                NodeLog.removeSink(sink)
            }
        }
    }
}
