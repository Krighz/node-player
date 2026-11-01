package com.nodeplayer.library

import com.nodeplayer.model.Track
import org.junit.Assert.*
import org.junit.Test

class DuplicateFinderTest {

    private fun createTestTrack(
        id: Long,
        title: String,
        artist: String,
        durationMs: Long,
        size: Long,
        isLossless: Boolean = false,
        sampleRate: Int = 44100
    ): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            albumId = 1L,
            durationMs = durationMs,
            path = "/storage/music/$id.mp3",
            size = size,
            mimeType = if (isLossless) "audio/flac" else "audio/mpeg",
            sampleRate = sampleRate,
            bitDepth = if (isLossless) 24 else 16,
            isLossless = isLossless
        )
    }

    @Test
    fun testExactDuplicatesBySizeAndHash() {
        val t1 = createTestTrack(1, "Track A", "Artist 1", 180000, 5000000)
        val t2 = createTestTrack(2, "Track A (copy)", "Artist 1", 180000, 5000000)
        val t3 = createTestTrack(3, "Track B", "Artist 2", 200000, 7000000)

        val groups = DuplicateFinder.findDuplicates(listOf(t1, t2, t3)) { path ->
            // Mock hash returns same hash for 1 and 2
            if (path.contains("1") || path.contains("2")) "hash123" else "hash456"
        }

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].tracks.size)
        assertTrue(groups[0].reason.contains("Exact"))
    }

    @Test
    fun testSimilarDuplicatesByTitleArtistAndDuration() {
        val t1 = createTestTrack(1, "Starboy", "The Weeknd", 230000, 6000000, isLossless = false)
        val t2 = createTestTrack(2, "starboy", "the weeknd", 231500, 15000000, isLossless = true, sampleRate = 96000)
        val t3 = createTestTrack(3, "Blinding Lights", "The Weeknd", 200000, 5000000)

        // No size hash match
        val groups = DuplicateFinder.findDuplicates(listOf(t1, t2, t3)) { null }

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].tracks.size)
        assertEquals(2L, groups[0].recommendedTrackId) // t2 is lossless & 96k, so it should be recommended
        assertTrue(groups[0].reason.contains("Matching title"))
    }
}
