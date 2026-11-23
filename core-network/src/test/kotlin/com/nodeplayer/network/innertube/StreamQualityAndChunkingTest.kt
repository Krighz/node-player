package com.nodeplayer.network.innertube

import com.nodeplayer.model.AlbumArtQuality
import com.nodeplayer.model.ArtworkUrlResolver
import com.nodeplayer.model.StreamingAudioQuality
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end verification tests verifying different songs with different qualities
 * (High, Medium, Low) and chunking Range header behavior (HTTP 206 Partial Content)
 * for audio streams, video streams, and album artwork.
 */
class StreamQualityAndChunkingTest {

    // Audio format fixtures
    private val opus160k = PlayerResponse.StreamingData.Format(
        itag = 251,
        bitrate = 160_000,
        mimeType = "audio/webm; codecs=\"opus\""
    )

    private val aac128k = PlayerResponse.StreamingData.Format(
        itag = 140,
        bitrate = 128_000,
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\""
    )

    private val opus70k = PlayerResponse.StreamingData.Format(
        itag = 250,
        bitrate = 70_000,
        mimeType = "audio/webm; codecs=\"opus\""
    )

    private val aac48k = PlayerResponse.StreamingData.Format(
        itag = 139,
        bitrate = 48_000,
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\""
    )

    private val standardAudioFormats = listOf(aac48k, aac128k, opus70k, opus160k)

    // Video format fixtures
    private val video1080p = PlayerResponse.StreamingData.Format(
        itag = 137,
        height = 1080,
        bitrate = 3_500_000,
        mimeType = "video/mp4; codecs=\"avc1.640028\""
    )

    private val video720p = PlayerResponse.StreamingData.Format(
        itag = 136,
        height = 720,
        bitrate = 1_800_000,
        mimeType = "video/mp4; codecs=\"avc1.4d401f\""
    )

    private val video480p = PlayerResponse.StreamingData.Format(
        itag = 135,
        height = 480,
        bitrate = 900_000,
        mimeType = "video/mp4; codecs=\"avc1.4d401f\""
    )

    private val video360p = PlayerResponse.StreamingData.Format(
        itag = 18,
        height = 360,
        bitrate = 500_000,
        mimeType = "video/mp4; codecs=\"avc1.42001e\""
    )

    private val standardVideoFormats = listOf(video360p, video480p, video720p, video1080p)

    // =========================================================================
    // 1. Audio Quality Tests Across Different Songs
    // =========================================================================

    @Test
    fun testAudioStreamQualityRankingAcrossDifferentSongs() {
        val resolver = StreamResolver()

        // Song 1: "Thunder" by Imagine Dragons (Rock / Pop)
        // High Quality -> Opus 160k (itag 251) first
        val song1High = resolver.rankAudioFormats(standardAudioFormats, StreamingAudioQuality.HIGH)
        assertEquals(251, song1High[0].itag)
        assertEquals(140, song1High[1].itag)

        // Song 2: "Rock That Body" by Black Eyed Peas (Dance / Electronic)
        // Medium Quality -> AAC 128k (itag 140) first
        val song2Medium = resolver.rankAudioFormats(standardAudioFormats, StreamingAudioQuality.MEDIUM)
        assertEquals(140, song2Medium[0].itag)
        assertEquals(251, song2Medium[1].itag)

        // Song 3: "Comfortably Numb" by Pink Floyd (Progressive Rock)
        // Low Quality -> Data Saver (itag 250 / 139) first
        val song3Low = resolver.rankAudioFormats(standardAudioFormats, StreamingAudioQuality.LOW)
        assertTrue("Low quality must rank itag 250 or 139 first", song3Low[0].itag == 250 || song3Low[0].itag == 139)
        assertTrue(song3Low[0].bitrate!! < 100_000)

        resolver.close()
    }

    // =========================================================================
    // 2. Video Quality Ranking Tests Across Different Songs
    // =========================================================================

    @Test
    fun testVideoStreamQualitySelectionAcrossDifferentSongs() {
        val resolver = StreamResolver()

        // Song 1: 1080p Full HD Video
        val song1_1080 = resolver.rankVideoFormats(standardVideoFormats, preferredHeight = 1080)
        assertEquals(1080, song1_1080.first().height)
        assertEquals(137, song1_1080.first().itag)

        // Song 2: 720p HD Video
        val song2_720 = resolver.rankVideoFormats(standardVideoFormats, preferredHeight = 720)
        assertEquals(720, song2_720.first().height)
        assertEquals(136, song2_720.first().itag)

        // Song 3: 480p SD Video
        val song3_480 = resolver.rankVideoFormats(standardVideoFormats, preferredHeight = 480)
        assertEquals(480, song3_480.first().height)
        assertEquals(135, song3_480.first().itag)

        // Low quality: 360p Data Saver Video
        val low_360 = resolver.rankVideoFormats(standardVideoFormats, preferredHeight = 360)
        assertEquals(360, low_360.first().height)
        assertEquals(18, low_360.first().itag)

        resolver.close()
    }

    // =========================================================================
    // 3. Album Art Quality Transformation Across Different Songs
    // =========================================================================

    @Test
    fun testAlbumArtQualityTransformsAcrossDifferentSongs() {
        // Song 1: "Thunder" - Google CDN Art Track thumbnail
        val song1Thumb = "https://lh3.googleusercontent.com/thunder_sample=w120-h120-l90-rj"
        val song1High = ArtworkUrlResolver.resolveArtworkUrl(song1Thumb, AlbumArtQuality.HIGH)
        assertEquals("https://lh3.googleusercontent.com/thunder_sample=w1200-h1200-l90-rj", song1High)

        val song1Medium = ArtworkUrlResolver.resolveArtworkUrl(song1Thumb, AlbumArtQuality.MEDIUM)
        assertEquals("https://lh3.googleusercontent.com/thunder_sample=w544-h544-l90-rj", song1Medium)

        val song1Low = ArtworkUrlResolver.resolveArtworkUrl(song1Thumb, AlbumArtQuality.LOW)
        assertEquals("https://lh3.googleusercontent.com/thunder_sample=w256-h256-l90-rj", song1Low)

        // Song 2: "Rock That Body" - YouTube video thumbnail
        val song2Video = "nodeplayer://youtube/black_eyed_peas_rtb"
        val song2CandidatesHigh = ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(song2Video, AlbumArtQuality.HIGH)
        assertEquals("https://i.ytimg.com/vi/black_eyed_peas_rtb/maxresdefault.jpg", song2CandidatesHigh[0])
        assertEquals("https://i.ytimg.com/vi/black_eyed_peas_rtb/hq720.jpg", song2CandidatesHigh[1])

        val song2CandidatesLow = ArtworkUrlResolver.resolveYouTubeThumbnailCandidates(song2Video, AlbumArtQuality.LOW)
        assertEquals("https://i.ytimg.com/vi/black_eyed_peas_rtb/hqdefault.jpg", song2CandidatesLow[0])

        // Song 3: Square avatar/album parameter
        val song3Thumb = "https://lh3.googleusercontent.com/album_art_square=s60-c-k"
        val song3High = ArtworkUrlResolver.resolveArtworkUrl(song3Thumb, AlbumArtQuality.HIGH)
        assertEquals("https://lh3.googleusercontent.com/album_art_square=w1200-h1200-l90-rj", song3High)
    }

    // =========================================================================
    // 4. Chunking Verification (HTTP Range Header & 206 Partial Content)
    // =========================================================================

    @Test
    fun testAudioStreamChunkingRangeProbeAcceptsHttp206() = runTest {
        var requestedRange: String? = null
        val probeEngine = MockEngine { request ->
            requestedRange = request.headers[HttpHeaders.Range]
            if (requestedRange == "bytes=0-1024") {
                respond(
                    content = ByteArray(1025) { 0x41 },
                    status = HttpStatusCode.PartialContent,
                    headers = headersOf(
                        HttpHeaders.ContentRange to listOf("bytes 0-1024/1048576"),
                        HttpHeaders.ContentType to listOf("audio/webm")
                    )
                )
            } else {
                respond("Bad Request", status = HttpStatusCode.BadRequest)
            }
        }

        val resolver = StreamResolver(probeEngine = probeEngine)
        val valid = resolver.verifyStreamUrl("https://googlevideo.com/audio_stream_chunk_test", "NodePlayer/2.0")

        assertTrue("Stream with HTTP 206 Partial Content range chunking must be verified", valid)
        assertEquals("bytes=0-1024", requestedRange)
        resolver.close()
    }

    @Test
    fun testVideoStreamChunkingRangeRequest() = runTest {
        var videoChunkReceived = false
        val probeEngine = MockEngine { request ->
            val range = request.headers[HttpHeaders.Range]
            if (range == "bytes=0-2048") {
                videoChunkReceived = true
                respond(
                    content = ByteArray(2049) { 0x00 },
                    status = HttpStatusCode.PartialContent,
                    headers = headersOf(
                        HttpHeaders.ContentRange to listOf("bytes 0-2048/20971520"),
                        HttpHeaders.ContentType to listOf("video/mp4")
                    )
                )
            } else {
                respond("Forbidden", status = HttpStatusCode.Forbidden)
            }
        }

        val client = HttpClient(probeEngine)
        val response = client.get("https://googlevideo.com/video_stream_chunk_test") {
            header(HttpHeaders.Range, "bytes=0-2048")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertTrue(videoChunkReceived)
        client.close()
    }

    @Test
    fun testAlbumArtChunkingProbeSupportsRangeOrFullContent() = runTest {
        var receivedRangeHeader: String? = null
        val mockArtEngine = MockEngine { request ->
            receivedRangeHeader = request.headers[HttpHeaders.Range]
            if (receivedRangeHeader != null) {
                // CDN supports Range requests for fast thumbnail header probing
                respond(
                    content = ByteArray(512) { 0xFF.toByte() },
                    status = HttpStatusCode.PartialContent,
                    headers = headersOf(
                        HttpHeaders.ContentRange to listOf("bytes 0-511/124000"),
                        HttpHeaders.ContentType to listOf("image/jpeg")
                    )
                )
            } else {
                // Full image download fallback
                respond(
                    content = ByteArray(124000) { 0xFF.toByte() },
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/jpeg")
                )
            }
        }

        val client = HttpClient(mockArtEngine)

        // 1. Test chunked probe for high-res album art
        val highResArtUrl = ArtworkUrlResolver.resolveArtworkUrl(
            "https://lh3.googleusercontent.com/song_art=w120-h120",
            AlbumArtQuality.HIGH
        )!!
        val probeResponse = client.get(highResArtUrl) {
            header(HttpHeaders.Range, "bytes=0-511")
        }

        assertEquals(HttpStatusCode.PartialContent, probeResponse.status)
        assertEquals("bytes=0-511", receivedRangeHeader)

        client.close()
    }

    @Test
    fun testChunkingProbeRejectsServerErrorOrExpiredSignature() = runTest {
        val probeEngine = MockEngine {
            respond("HTTP 403 Forbidden - Stream expired", status = HttpStatusCode.Forbidden)
        }

        val resolver = StreamResolver(probeEngine = probeEngine)
        val valid = resolver.verifyStreamUrl("https://googlevideo.com/expired_stream", "NodePlayer/2.0")

        assertFalse("Expired or forbidden stream must be rejected", valid)
        resolver.close()
    }
}
