package com.nodeplayer.network.lyrics

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrclibLyricsProviderTest {

    @Test
    fun testExactMatchSuccess() = runBlocking {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/get") {
                val json = """
                    {
                      "id": 1001,
                      "trackName": "Bohemian Rhapsody",
                      "artistName": "Queen",
                      "duration": 354.0,
                      "instrumental": false,
                      "plainLyrics": "Is this the real life?",
                      "syncedLyrics": "[00:01.00]Is this the real life?\n[00:04.00]Is this just fantasy?"
                    }
                """.trimIndent()
                respond(
                    content = json,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(content = "Not found", status = HttpStatusCode.NotFound)
            }
        }

        val provider = LrclibLyricsProvider(engine = mockEngine)
        val result = provider.fetchLyrics("Bohemian Rhapsody", "Queen", durationSec = 354)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()
        assertNotNull(lyrics)
        assertEquals(1001L, lyrics?.trackId)
        assertTrue(lyrics?.isSynced == true)
        assertEquals(2, lyrics?.lines?.size)
        assertEquals("Is this the real life?", lyrics?.lines?.get(0)?.text)
    }

    @Test
    fun testSearchFallbackWithTolerance() = runBlocking {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/get") {
                respond(content = "{}", status = HttpStatusCode.NotFound)
            } else if (request.url.encodedPath == "/api/search") {
                val json = """
                    [
                      {
                        "id": 2001,
                        "trackName": "Song A",
                        "artistName": "Artist",
                        "duration": 180.0,
                        "syncedLyrics": "[00:01.00]Wrong track by duration"
                      },
                      {
                        "id": 2002,
                        "trackName": "Song A",
                        "artistName": "Artist",
                        "duration": 202.0,
                        "syncedLyrics": "[00:01.00]Correct track within 5s"
                      }
                    ]
                """.trimIndent()
                respond(
                    content = json,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(content = "", status = HttpStatusCode.NotFound)
            }
        }

        val provider = LrclibLyricsProvider(engine = mockEngine)
        // Request with duration 200s (tolerates 195s..205s)
        val result = provider.fetchLyrics("Song A", "Artist", durationSec = 200)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()
        assertNotNull(lyrics)
        assertEquals(2002L, lyrics?.trackId)
        assertEquals("Correct track within 5s", lyrics?.lines?.get(0)?.text)
    }

    @Test
    fun testNotFoundReturnsNull() = runBlocking {
        val mockEngine = MockEngine {
            respond(content = "Not found", status = HttpStatusCode.NotFound)
        }

        val provider = LrclibLyricsProvider(engine = mockEngine)
        val result = provider.fetchLyrics("Unknown", "Unknown")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
