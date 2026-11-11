package com.nodeplayer.library.taste

import com.nodeplayer.model.Track
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
        var historyTracks: List<Track> = emptyList(),
        var dailyMixTracks: List<Track> = emptyList(),
        var radioQueues: Map<String, List<Track>> = emptyMap(),
        var fallbackCharts: List<Track> = emptyList()
    ) : TasteHistorySource {

        override suspend fun getListeningHistory(): Result<List<Track>> = Result.success(historyTracks)
        override suspend fun getDailyMixTracks(): Result<List<Track>> = Result.success(dailyMixTracks)
        override suspend fun getRadioForTrack(videoId: String): Result<List<Track>> =
            Result.success(radioQueues[videoId] ?: emptyList())
        override suspend fun getFallbackTopCharts(): Result<List<Track>> = Result.success(fallbackCharts)
    }

    @Test
    fun testContextualMoodSlotsMapping() {
        val fakeSource = FakeTasteHistorySource()
        val engine = TasteRecommendationEngine(fakeSource)

        // Morning: 05:00 - 11:59
        assertTrue(engine.resolveContextualMood(5) is ContextualMood.Morning)
        assertTrue(engine.resolveContextualMood(11) is ContextualMood.Morning)

        // Focus: 12:00 - 16:59
        assertTrue(engine.resolveContextualMood(12) is ContextualMood.Focus)
        assertTrue(engine.resolveContextualMood(16) is ContextualMood.Focus)

        // Chill: 17:00 - 21:59
        assertTrue(engine.resolveContextualMood(17) is ContextualMood.Chill)
        assertTrue(engine.resolveContextualMood(21) is ContextualMood.Chill)

        // Late Night: 22:00 - 04:59
        assertTrue(engine.resolveContextualMood(22) is ContextualMood.LateNight)
        assertTrue(engine.resolveContextualMood(23) is ContextualMood.LateNight)
        assertTrue(engine.resolveContextualMood(0) is ContextualMood.LateNight)
        assertTrue(engine.resolveContextualMood(4) is ContextualMood.LateNight)
    }

    @Test
    fun testEmptyHistoryFallbackToCharts() = runBlocking {
        val fallbackTracks = (1..30).map { i ->
            createTrack(i.toLong(), "Chart Song $i", "Popular Artist", "chart_$i")
        }

        val fakeSource = FakeTasteHistorySource(
            historyTracks = emptyList(),
            fallbackCharts = fallbackTracks
        )
        val engine = TasteRecommendationEngine(fakeSource)

        val stationResult = engine.generateTasteStation()
        assertTrue(stationResult.isSuccess)
        val station = stationResult.getOrThrow()

        assertEquals(25, station.size)
        assertEquals("Chart Song 1", station.first().title)
    }

    @Test
    fun testExclusionOfLast15PlayedTracks() = runBlocking {
        // Create 20 history tracks. First 15 are recent, remaining 5 are older history
        val history = (1..20).map { i ->
            createTrack(i.toLong(), "History Song $i", if (i <= 15) "Recent Artist" else "Older Artist", "vid_$i")
        }

        // Daily mix contains some of the excluded recent tracks plus fresh tracks
        val dailyMix = (1..25).map { i ->
            createTrack((100 + i).toLong(), "Mix Song $i", "Mix Artist", if (i <= 5) "vid_$i" else "mix_vid_$i")
        }

        // Radio queue contains some of the excluded tracks
        val radioQueue = (1..20).map { i ->
            createTrack((200 + i).toLong(), "Radio Song $i", "Older Artist", if (i in 6..10) "vid_$i" else "radio_vid_$i")
        }

        val fakeSource = FakeTasteHistorySource(
            historyTracks = history,
            dailyMixTracks = dailyMix,
            radioQueues = mapOf("vid_16" to radioQueue, "vid_17" to radioQueue, "vid_18" to radioQueue)
        )

        val engine = TasteRecommendationEngine(fakeSource)
        val stationResult = engine.generateTasteStation()
        assertTrue(stationResult.isSuccess)
        val station = stationResult.getOrThrow()

        val excludedVids = (1..15).map { "vid_$it" }.toSet()
        val stationVids = station.map { it.path.removePrefix("nodeplayer://youtube/") }

        for (vid in stationVids) {
            assertFalse("Track $vid should have been excluded by last-15 filter!", vid in excludedVids)
        }
    }

    @Test
    fun testStationSizeAndUniqueness() = runBlocking {
        // History with 30 tracks across 5 artists
        val artists = listOf("Artist A", "Artist B", "Artist C", "Artist D", "Artist E")
        val history = (1..35).map { i ->
            val artist = artists[i % artists.size]
            createTrack(i.toLong(), "Song $i", artist, "hist_$i")
        }

        val dailyMix = (1..20).map { i ->
            createTrack((100 + i).toLong(), "Mix $i", "Daily Artist", "mix_$i")
        }

        val radioA = (1..15).map { i -> createTrack((200 + i).toLong(), "Radio A $i", "Artist A", "rad_a_$i") }
        val radioB = (1..15).map { i -> createTrack((300 + i).toLong(), "Radio B $i", "Artist B", "rad_b_$i") }
        val radioC = (1..15).map { i -> createTrack((400 + i).toLong(), "Radio C $i", "Artist C", "rad_c_$i") }

        val fakeSource = FakeTasteHistorySource(
            historyTracks = history,
            dailyMixTracks = dailyMix,
            radioQueues = mapOf(
                "hist_16" to radioA,
                "hist_17" to radioB,
                "hist_18" to radioC,
                "hist_19" to radioA,
                "hist_20" to radioB
            )
        )

        val engine = TasteRecommendationEngine(fakeSource, random = Random(42))
        val stationResult = engine.generateTasteStation()
        assertTrue(stationResult.isSuccess)
        val station = stationResult.getOrThrow()

        assertEquals(25, station.size)
        // Check uniqueness of IDs
        val uniqueIds = station.map { it.id }.toSet()
        assertEquals(25, uniqueIds.size)
    }

    @Test
    fun testWeightedArtistSampling() {
        val fakeSource = FakeTasteHistorySource()
        val engine = TasteRecommendationEngine(fakeSource, random = Random(12345))

        val rankedArtists = listOf(
            "Rank1", "Rank2", "Rank3", "Rank4", "Rank5",
            "Rank6", "Rank7", "Rank8", "Rank9", "Rank10"
        )

        val chosen = engine.drawWeightedArtists(rankedArtists, 3)
        assertEquals(3, chosen.size)
        assertEquals(3, chosen.toSet().size)
        // All chosen must be from ranked artists
        assertTrue(rankedArtists.containsAll(chosen))
    }
}
