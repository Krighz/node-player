package com.nodeplayer.network.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverVideoTest {

    @Test
    fun testRankVideoFormatsWithPreferredHeight() {
        val resolver = StreamResolver()
        val formats = listOf(
            PlayerResponse.StreamingData.Format(itag = 18, mimeType = "video/mp4", height = 360, bitrate = 500_000),
            PlayerResponse.StreamingData.Format(itag = 135, mimeType = "video/mp4", height = 480, bitrate = 1_000_000),
            PlayerResponse.StreamingData.Format(itag = 136, mimeType = "video/mp4", height = 720, bitrate = 2_000_000),
            PlayerResponse.StreamingData.Format(itag = 137, mimeType = "video/mp4", height = 1080, bitrate = 4_000_000),
            PlayerResponse.StreamingData.Format(itag = 251, mimeType = "audio/webm", height = null, bitrate = 160_000)
        )

        // Preferred 720p: 720p should be first
        val ranked720 = resolver.rankVideoFormats(formats, preferredHeight = 720)
        assertEquals(720, ranked720.first().height)

        // Preferred 480p: 480p should be first
        val ranked480 = resolver.rankVideoFormats(formats, preferredHeight = 480)
        assertEquals(480, ranked480.first().height)

        // Default null (Auto): Should cap and rank 480p first
        val rankedDefault = resolver.rankVideoFormats(formats, preferredHeight = null)
        assertEquals(480, rankedDefault.first().height)

        // Audio formats filtered out
        assertTrue(ranked720.none { it.itag == 251 })
    }

    @Test
    fun testResolvedStreamCarriesAvailableHeights() {
        val stream = ResolvedStream(
            videoId = "test_vid",
            streamUrl = "https://example.com/stream.mp4",
            itag = 136,
            mimeType = "video/mp4",
            bitrate = 2_000_000,
            durationSeconds = 240,
            loudnessDb = -14.0,
            clientName = "WEB_REMIX",
            height = 720,
            availableHeights = listOf(1080, 720, 480, 360)
        )

        assertEquals(720, stream.height)
        assertEquals(4, stream.availableHeights.size)
        assertEquals(1080, stream.availableHeights[0])
    }
}
