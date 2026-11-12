package com.nodeplayer.network.spotify

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyClientTest {

    @Test
    fun testPkceVerifierAndChallenge() {
        val verifier = SpotifyAuth.generateCodeVerifier()
        assertTrue(verifier.length >= 43)
        val challenge = SpotifyAuth.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        // URL should contain challenge
        val authUrl = SpotifyAuth.buildAuthorizeUrl("test_client_id", codeChallenge = challenge, state = "xyz123")
        assertTrue(authUrl.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(authUrl.contains("client_id=test_client_id"))
        assertTrue(authUrl.contains("code_challenge=$challenge"))
        assertTrue(authUrl.contains("state=xyz123"))
    }

    @Test
    fun testParsePlaylistsAndTracksWithIsrc() = runBlocking {
        val mockPlaylistsJson = """
        {
          "items": [
            {
              "id": "pl_123",
              "name": "Synthwave Favorites",
              "description": "Retro beats",
              "tracks": { "total": 2 },
              "owner": { "display_name": "TestUser" },
              "images": [{ "url": "https://image.spotify.com/cover.jpg" }]
            }
          ]
        }
        """.trimIndent()

        val mockTracksJson = """
        {
          "items": [
            {
              "track": {
                "id": "trk_456",
                "name": "Resonance",
                "artists": [{ "name": "HOME" }],
                "album": { "name": "Odyssey" },
                "duration_ms": 212000,
                "external_ids": { "isrc": "US1234567890" }
              }
            }
          ],
          "next": null
        }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("me/playlists") -> {
                    respond(
                        content = mockPlaylistsJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                path.contains("playlists/pl_123/tracks") || path.contains("pl_123") -> {
                    respond(
                        content = mockTracksJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("Not found for path: $path (full: ${request.url})", HttpStatusCode.NotFound)
            }
        }

        val client = SpotifyClient(engine = mockEngine)
        val playlists = client.getUserPlaylists("dummy_token").getOrNull()
        assertNotNull(playlists)
        assertEquals(1, playlists!!.size)
        assertEquals("Synthwave Favorites", playlists[0].name)
        assertEquals("TestUser", playlists[0].ownerName)

        val tracksResult = client.getPlaylistTracks("dummy_token", "pl_123")
        if (tracksResult.isFailure) {
            println("FAILED GET PLAYLIST TRACKS: ${tracksResult.exceptionOrNull()}")
            tracksResult.exceptionOrNull()?.printStackTrace()
        }
        val tracks = tracksResult.getOrNull()
        assertNotNull(tracksResult.exceptionOrNull()?.message, tracks)
        assertEquals(1, tracks!!.size)
        val trk = tracks[0]
        assertEquals("Resonance", trk.title)
        assertEquals("HOME", trk.primaryArtist)
        assertEquals(212000L, trk.durationMs)
        assertEquals("US1234567890", trk.isrc)
    }

    @Test
    fun testRateLimitBackoff() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = "Too Many Requests",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf("Retry-After" to listOf("1"))
                )
            } else {
                respond(
                    content = """{ "items": [] }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = SpotifyClient(engine = mockEngine)
        val result = client.getUserPlaylists("dummy_token")
        assertTrue(result.isSuccess)
        assertEquals(2, callCount)
    }
}
