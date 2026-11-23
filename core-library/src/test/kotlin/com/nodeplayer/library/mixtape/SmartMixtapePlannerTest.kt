package com.nodeplayer.library.mixtape

import com.nodeplayer.model.CanonicalTrackKey
import com.nodeplayer.model.SmartMixtapeConfig
import com.nodeplayer.model.SmartMixtapeEntry
import com.nodeplayer.model.Track
import org.junit.Assert.*
import org.junit.Test

class SmartMixtapePlannerTest {

    private fun dummyTrack(id: Long, videoId: String): Track {
        return Track(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            path = "nodeplayer://youtube/$videoId"
        )
    }

    private fun dummyEntry(videoId: String, lastPlayed: Long, sizeBytes: Long = 3_000_000L): SmartMixtapeEntry {
        return SmartMixtapeEntry(
            canonicalKey = CanonicalTrackKey(videoId),
            title = "Track $videoId",
            artist = "Artist",
            durationMs = 180000L,
            artworkUrl = null,
            fileSizeBytes = sizeBytes,
            lastPlayedEpochMs = lastPlayed,
            addedEpochMs = lastPlayed,
            filePath = "/smart_mixtape/$videoId.opus"
        )
    }

    @Test
    fun testCandidateBlendingAndManualDownloadExclusion() {
        val config = SmartMixtapeConfig(targetSongCount = 10)
        val manualKeys = setOf(CanonicalTrackKey("man_1"), CanonicalTrackKey("rot_1"))

        val discovery = (1..10).map { dummyTrack(it.toLong(), "disc_$it") }
        val rotation = (1..10).map { dummyTrack((100 + it).toLong(), "rot_$it") }

        val plan = SmartMixtapePlanner.createSyncPlan(
            config = config,
            discoveryTracks = discovery,
            heavyRotationTracks = rotation,
            existingMixtape = emptyList(),
            manualDownloadKeys = manualKeys
        )

        // rot_1 excluded because it's in manual downloads
        assertFalse(plan.tracksToDownload.any { it.path.contains("rot_1") })
        // Target count satisfied
        assertEquals(10, plan.tracksToDownload.size)
    }

    @Test
    fun testTwoTierLruEvictsStaleBeforeActive() {
        val config = SmartMixtapeConfig(targetSongCount = 2)

        val cachedStale = dummyEntry("stale_song", lastPlayed = 100L)
        val cachedActive = dummyEntry("active_song", lastPlayed = 50L) // Older played, but active candidate!

        val discovery = listOf(dummyTrack(1L, "active_song"), dummyTrack(2L, "fresh_song"))

        val plan = SmartMixtapePlanner.createSyncPlan(
            config = config,
            discoveryTracks = discovery,
            heavyRotationTracks = emptyList(),
            existingMixtape = listOf(cachedStale, cachedActive),
            manualDownloadKeys = emptySet()
        )

        // Stale song must be evicted first, even though active_song had older lastPlayed timestamp
        assertEquals(1, plan.entriesToEvict.size)
        assertEquals("stale_song", plan.entriesToEvict.first().canonicalKey.value)
        assertEquals(1, plan.tracksToDownload.size)
        assertEquals("fresh_song", CanonicalTrackKey.from(plan.tracksToDownload.first()).value)
    }
}
