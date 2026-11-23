package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistCreateTest {

    @Test
    fun testCreatePlaylistSuccess() = runBlocking {
        var requestedUrl = ""
        val mockEngine = MockEngine { request ->
            requestedUrl = request.url.encodedPath
            respond(
                content = """{"playlistId":"PLtest_playlist_123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine).apply {
            sessionCookie = "SAPISID=123456789; HSID=abc;"
        }

        val result = client.createPlaylist(
            title = "My Imported Playlist",
            description = "Imported tracks",
            videoIds = listOf("video1", "video2")
        )

        assertTrue(result.isSuccess)
        assertEquals("PLtest_playlist_123", result.getOrNull())
        assertTrue(requestedUrl.contains("playlist/create"))
    }

    @Test
    fun testCreatePlaylistRequiresAuth() = runBlocking {
        val client = InnerTubeClient()
        // No session cookie
        val result = client.createPlaylist("Unauthed Playlist")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Sign in required") == true)
    }

    @Test
    fun testAddTracksToPlaylist() = runBlocking {
        var requestedUrl = ""
        val mockEngine = MockEngine { request ->
            requestedUrl = request.url.encodedPath
            respond(
                content = """{"status":"STATUS_SUCCEEDED"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = InnerTubeClient(engine = mockEngine).apply {
            sessionCookie = "SAPISID=123456789; HSID=abc;"
        }

        val result = client.addTracksToPlaylist(
            playlistId = "PLtest_playlist_123",
            videoIds = listOf("vid_extra_1", "vid_extra_2")
        )

        assertTrue(result.isSuccess)
        assertTrue(requestedUrl.contains("browse/edit_playlist"))
    }
}
