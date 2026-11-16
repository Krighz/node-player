package com.nodeplayer.network.downloader

import com.nodeplayer.model.storage.AudioStreamOption
import com.nodeplayer.model.storage.DownloadContainerFormat
import com.nodeplayer.model.storage.DownloadQualityPreset
import com.nodeplayer.network.innertube.InnerTubeClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamFormatResolverTest {

    private fun sampleOptions(): List<AudioStreamOption> {
        return listOf(
            AudioStreamOption(
                itag = 251,
                codec = "opus",
                container = "opus",
                bitrateKbps = 160,
                contentLengthBytes = 3_500_000L,
                url = "https://example.com/audio/opus-160"
            ),
            AudioStreamOption(
                itag = 250,
                codec = "opus",
                container = "opus",
                bitrateKbps = 70,
                contentLengthBytes = 1_500_000L,
                url = "https://example.com/audio/opus-70"
            ),
            AudioStreamOption(
                itag = 140,
                codec = "mp4a.40.2",
                container = "m4a",
                bitrateKbps = 128,
                contentLengthBytes = 3_000_000L,
                url = "https://example.com/audio/m4a-128"
            ),
            AudioStreamOption(
                itag = 139,
                codec = "mp4a.40.2",
                container = "m4a",
                bitrateKbps = 48,
                contentLengthBytes = 1_000_000L,
                url = "https://example.com/audio/m4a-48"
            )
        )
    }

    @Test
    fun testSelectBestOptionHighestQuality() {
        val resolver = StreamFormatResolver()
        val options = sampleOptions()

        val highestAuto = resolver.selectBestOption(options, DownloadQualityPreset.HIGHEST, DownloadContainerFormat.AUTO)
        assertNotNull(highestAuto)
        assertEquals(160, highestAuto.bitrateKbps)
        assertEquals("opus", highestAuto.container)

        val highestM4a = resolver.selectBestOption(options, DownloadQualityPreset.HIGHEST, DownloadContainerFormat.M4A)
        assertNotNull(highestM4a)
        assertEquals(128, highestM4a.bitrateKbps)
        assertEquals("m4a", highestM4a.container)
    }

    @Test
    fun testSelectBestOptionDataSaver() {
        val resolver = StreamFormatResolver()
        val options = sampleOptions()

        val lowestAuto = resolver.selectBestOption(options, DownloadQualityPreset.DATA_SAVER, DownloadContainerFormat.AUTO)
        assertNotNull(lowestAuto)
        assertEquals(48, lowestAuto.bitrateKbps)

        val lowestOpus = resolver.selectBestOption(options, DownloadQualityPreset.DATA_SAVER, DownloadContainerFormat.OPUS)
        assertNotNull(lowestOpus)
        assertEquals(70, lowestOpus.bitrateKbps)
    }

    @Test
    fun testSelectBestOptionBalanced() {
        val resolver = StreamFormatResolver()
        val options = sampleOptions()

        // Closest to 140 kbps
        val balanced = resolver.selectBestOption(options, DownloadQualityPreset.BALANCED, DownloadContainerFormat.AUTO)
        assertNotNull(balanced)
        // 128 (diff 12) vs 160 (diff 20) -> 128 is closest
        assertEquals(128, balanced.bitrateKbps)
    }

    @Test
    fun testResolveAvailableAudioOptionsWithMockEngine() = runBlocking {
        val mockPlayerJson = """
        {
          "playabilityStatus": { "status": "OK" },
          "streamingData": {
            "adaptiveFormats": [
              {
                "itag": 251,
                "url": "https://googlevideo.com/videoplayback?itag=251",
                "mimeType": "audio/webm; codecs=\"opus\"",
                "bitrate": 160000,
                "contentLength": "3500000",
                "audioQuality": "AUDIO_QUALITY_MEDIUM"
              },
              {
                "itag": 140,
                "url": "https://googlevideo.com/videoplayback?itag=140",
                "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                "bitrate": 128000,
                "contentLength": "2800000",
                "audioQuality": "AUDIO_QUALITY_MEDIUM"
              },
              {
                "itag": 137,
                "url": "https://googlevideo.com/videoplayback?itag=137",
                "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                "bitrate": 3000000
              }
            ]
          }
        }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = mockPlayerJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val innerTubeClient = InnerTubeClient(engine = mockEngine)
        val resolver = StreamFormatResolver(innerTubeClient = innerTubeClient)

        val result = resolver.resolveAvailableAudioOptions("test_video_123")
        assertTrue(result.isSuccess, "Should resolve audio streams successfully")
        val audioList = result.getOrThrow()

        assertEquals(2, audioList.size, "Should only parse audio formats (excluding video itag 137)")
        assertEquals(251, audioList[0].itag)
        assertEquals("opus", audioList[0].container)
        assertEquals(160, audioList[0].bitrateKbps)

        assertEquals(140, audioList[1].itag)
        assertEquals("m4a", audioList[1].container)
        assertEquals(128, audioList[1].bitrateKbps)
    }
}
