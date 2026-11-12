package com.nodeplayer.network.innertube

import com.nodeplayer.common.media.CipherSolver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlayerSessionTest {
    @Test
    fun playerOnlyUsesSessionForSupportedClientsAndSuppliesSignatureTimestamp() = runTest {
        val previousSolver = CipherSolver.current
        CipherSolver.current = object : CipherSolver {
            override suspend fun getSignatureTimestamp() = 12345
            override suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? = null
            override suspend fun transformNParamInUrl(url: String) = url
        }
        var playerCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/player")) {
                playerCalls++
                val body = (request.body as TextContent).text
                if (request.headers["X-YouTube-Client-Name"] == "67") {
                    assertEquals("SAPISID=synthetic", request.headers[HttpHeaders.Cookie])
                    assertNotNull(request.headers[HttpHeaders.Authorization])
                    assertTrue(body.contains("\"signatureTimestamp\":12345"))
                } else {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertNull(request.headers["X-Origin"])
                }
                assertEquals(listOf(YouTubeClient.ORIGIN_YOUTUBE_MUSIC), request.headers.getAll("Origin"))
            }
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            InnerTubeClient(engine = engine).use { client ->
                client.sessionCookie = "SAPISID=synthetic"
                client.getPlayerResponse("test", YouTubeClient.ANDROID_VR).getOrThrow()
                client.getPlayerResponse("test", YouTubeClient.WEB_REMIX).getOrThrow()
                assertEquals(2, playerCalls)
            }
        } finally {
            CipherSolver.current = previousSolver
        }
    }
}
