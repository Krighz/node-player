package com.nodeplayer.network.innertube

import com.nodeplayer.common.media.CipherSolver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverTest {

    private fun samplePlayerResponse(
        status: String = "OK",
        includeAdaptive: Boolean = true,
        formats: List<String> = emptyList(),
    ): String {
        val formatsJson = formats.joinToString(",")
        val streamingDataJson = if (includeAdaptive) {
            """
            "streamingData": {
              "adaptiveFormats": [
                $formatsJson
              ]
            }
            """.trimIndent()
        } else {
            ""
        }

        return """
        {
          "playabilityStatus": {
            "status": "$status"
          },
          "videoDetails": {
            "videoId": "test_video_123",
            "title": "Comfortably Numb",
            "author": "Pink Floyd",
            "lengthSeconds": "382"
          },
          "playerConfig": {
            "audioConfig": {
              "loudnessDb": -5.5
            }
          }
          ${if (streamingDataJson.isNotEmpty()) ", $streamingDataJson" else ""}
        }
        """.trimIndent()
    }

    private val format251 = """
    {
      "itag": 251,
      "url": "https://googlevideo.com/playback?itag=251",
      "mimeType": "audio/webm; codecs=\"opus\"",
      "bitrate": 160000,
      "approxDurationMs": "382000"
    }
    """.trimIndent()

    private val format140 = """
    {
      "itag": 140,
      "url": "https://googlevideo.com/playback?itag=140",
      "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
      "bitrate": 128000,
      "approxDurationMs": "382000"
    }
    """.trimIndent()

    private val format250 = """
    {
      "itag": 250,
      "url": "https://googlevideo.com/playback?itag=250",
      "mimeType": "audio/webm; codecs=\"opus\"",
      "bitrate": 70000,
      "approxDurationMs": "382000"
    }
    """.trimIndent()

    private val formatCipher = """
    {
      "itag": 251,
      "signatureCipher": "s=mock_sig&sp=sig&url=https%3A%2F%2Fgooglevideo.com%2Fplayback%3Fitag%3D251%26n%3Dtest_n_param",
      "mimeType": "audio/webm; codecs=\"opus\"",
      "bitrate": 160000,
      "approxDurationMs": "382000"
    }
    """.trimIndent()

    @Test
    fun testFormatPrioritization() {
        val resolver = StreamResolver()
        val formats = listOf(
            PlayerResponse.StreamingData.Format(itag = 250, bitrate = 70000, mimeType = "audio/webm"),
            PlayerResponse.StreamingData.Format(itag = 140, bitrate = 128000, mimeType = "audio/mp4"),
            PlayerResponse.StreamingData.Format(itag = 251, bitrate = 160000, mimeType = "audio/webm"),
            PlayerResponse.StreamingData.Format(itag = 139, bitrate = 48000, mimeType = "audio/mp4")
        )

        val ranked = resolver.rankAudioFormats(formats)
        assertEquals(4, ranked.size)
        assertEquals(251, ranked[0].itag)
        assertEquals(140, ranked[1].itag)
        assertEquals(250, ranked[2].itag)
        assertEquals(139, ranked[3].itag)
    }

    @Test
    fun testRangeProbeValidationAccepts206And200() = runTest {
        val probeEngine = MockEngine { request ->
            val url = request.url.toString()
            if (url.contains("status206")) {
                respond("partial-bytes", status = HttpStatusCode.PartialContent)
            } else if (url.contains("status200")) {
                respond("all-bytes", status = HttpStatusCode.OK)
            } else {
                respond("forbidden", status = HttpStatusCode.Forbidden)
            }
        }

        val resolver = StreamResolver(probeEngine = probeEngine)
        assertTrue(resolver.verifyStreamUrl("https://example.com/status206", "TestAgent"))
        assertTrue(resolver.verifyStreamUrl("https://example.com/status200", "TestAgent"))
        assertFalse(resolver.verifyStreamUrl("https://example.com/status403", "TestAgent"))
        resolver.close()
    }

    @Test
    fun testClientRotationWhenPrimaryFails() = runTest {
        var requestedClients = mutableListOf<String>()

        val innerTubeEngine = MockEngine { request ->
            val clientName = request.headers["X-YouTube-Client-Name"] ?: "unknown"
            requestedClients.add(clientName)

            // Primary client is ANDROID_VR (clientId = "28") -> Return 403 / error playability
            if (clientName == "28") {
                respond(
                    content = samplePlayerResponse(status = "ERROR"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (clientName == "5") {
                // IOS (clientId = "5") -> Return valid player response with format 251
                respond(
                    content = samplePlayerResponse(status = "OK", formats = listOf(format251)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = samplePlayerResponse(status = "ERROR"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val probeEngine = MockEngine {
            respond("partial audio", status = HttpStatusCode.PartialContent)
        }

        val innerTubeClient = InnerTubeClient(engine = innerTubeEngine)
        val resolver = StreamResolver(
            innerTubeClient = innerTubeClient,
            probeEngine = probeEngine
        )

        val result = resolver.resolveStream("test_video_123")
        assertTrue(result.isSuccess)
        val stream = result.getOrThrow()
        assertEquals("IOS", stream.clientName)
        assertEquals(251, stream.itag)
        assertEquals("https://googlevideo.com/playback?itag=251", stream.streamUrl)
        assertEquals(-5.5, stream.loudnessDb ?: 0.0, 0.01)

        // Verify rotation sequence tried ANDROID_VR (28) first, then IOS (5)
        assertEquals(listOf("28", "5"), requestedClients)

        innerTubeClient.close()
        resolver.close()
    }

    @Test
    fun testDecipheringAndNTransformInvocation() = runTest {
        val innerTubeEngine = MockEngine {
            respond(
                content = samplePlayerResponse(status = "OK", formats = listOf(formatCipher)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val probeEngine = MockEngine {
            respond("partial audio", status = HttpStatusCode.PartialContent)
        }

        var deobfuscateCalled = false
        var transformNCalled = false

        val mockCipherSolver = object : CipherSolver {
            override suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
                deobfuscateCalled = true
                assertTrue(signatureCipher.contains("mock_sig"))
                return "https://googlevideo.com/playback?itag=251&sig=decrypted_sig&n=orig_n"
            }

            override suspend fun transformNParamInUrl(url: String): String {
                transformNCalled = true
                return url.replace("n=orig_n", "n=transformed_n")
            }
        }

        val innerTubeClient = InnerTubeClient(engine = innerTubeEngine)
        val resolver = StreamResolver(
            innerTubeClient = innerTubeClient,
            cipherSolver = mockCipherSolver,
            probeEngine = probeEngine
        )

        val result = resolver.resolveStream("test_video_123")
        assertTrue(result.isSuccess)
        val stream = result.getOrThrow()

        assertTrue(deobfuscateCalled)
        assertTrue(transformNCalled)
        assertTrue(stream.streamUrl.contains("sig=decrypted_sig"))
        assertTrue(stream.streamUrl.contains("n=transformed_n"))

        innerTubeClient.close()
        resolver.close()
    }

    @Test
    fun testRangeProbeFailureRotatesToNextClient() = runTest {
        val innerTubeEngine = MockEngine { request ->
            val clientName = request.headers["X-YouTube-Client-Name"] ?: "unknown"
            if (clientName == "28") {
                // ANDROID_VR returns url that fails probe
                respond(
                    content = samplePlayerResponse(status = "OK", formats = listOf(format251.replace("googlevideo.com", "forbidden.com"))),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (clientName == "5") {
                // IOS returns valid url that succeeds
                respond(
                    content = samplePlayerResponse(status = "OK", formats = listOf(format140)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("{}", status = HttpStatusCode.BadRequest)
            }
        }

        val probeEngine = MockEngine { request ->
            if (request.url.toString().contains("forbidden.com")) {
                respond("Forbidden", status = HttpStatusCode.Forbidden)
            } else {
                respond("audio-bytes", status = HttpStatusCode.PartialContent)
            }
        }

        val innerTubeClient = InnerTubeClient(engine = innerTubeEngine)
        val resolver = StreamResolver(
            innerTubeClient = innerTubeClient,
            probeEngine = probeEngine
        )

        val result = resolver.resolveStream("test_video_123")
        assertTrue(result.isSuccess)
        val stream = result.getOrThrow()
        assertEquals("IOS", stream.clientName)
        assertEquals(140, stream.itag)

        innerTubeClient.close()
        resolver.close()
    }
}
