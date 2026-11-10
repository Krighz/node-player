package com.nodeplayer.network.spotify

import com.nodeplayer.common.logging.NodeLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable

/**
 * Pure Kotlin Spotify Web API client.
 * Features rate-limit resilience with backoff on HTTP 429 and ISRC extraction.
 */
class SpotifyClient(
    engine: HttpClientEngine? = null
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) {
            json(json)
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000L
            connectTimeoutMillis = 10000L
            socketTimeoutMillis = 15000L
        }
    }

    /**
     * Exchanges PKCE authorization code for access and refresh tokens.
     */
    suspend fun exchangeCodeForToken(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String = SpotifyAuth.DEFAULT_REDIRECT_URI
    ): Result<SpotifyTokenResponse> = runCatching {
        NodeLog.d(TAG, "Exchanging authorization code for Spotify token (clientId=$clientId)")
        val response = httpClient.submitForm(
            url = SpotifyAuth.SPOTIFY_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            }
        )

        if (response.status == HttpStatusCode.OK) {
            response.body<SpotifyTokenResponse>()
        } else {
            val errBody = response.bodyAsText()
            throw IllegalStateException("Token exchange failed (${response.status}): $errBody")
        }
    }

    /**
     * Refreshes an expired access token using the stored refresh_token.
     */
    suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String
    ): Result<SpotifyTokenResponse> = runCatching {
        NodeLog.d(TAG, "Refreshing Spotify access token")
        val response = httpClient.submitForm(
            url = SpotifyAuth.SPOTIFY_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", clientId)
                append("refresh_token", refreshToken)
            }
        )

        if (response.status == HttpStatusCode.OK) {
            response.body<SpotifyTokenResponse>()
        } else {
            val errBody = response.bodyAsText()
            throw IllegalStateException("Token refresh failed (${response.status}): $errBody")
        }
    }

    /**
     * Fetches current user's playlists.
     */
    suspend fun getUserPlaylists(accessToken: String): Result<List<SpotifyPlaylistSummary>> = runCatching {
        NodeLog.d(TAG, "Fetching current user playlists")
        val url = "https://api.spotify.com/v1/me/playlists?limit=50"
        val responseText = executeWithBackoff(accessToken, url)
        parseUserPlaylists(responseText)
    }

    /**
     * Fetches tracks from a Spotify playlist by playlist ID.
     */
    suspend fun getPlaylistTracks(
        accessToken: String,
        playlistId: String
    ): Result<List<SpotifyTrack>> = runCatching {
        NodeLog.d(TAG, "Fetching tracks for playlist $playlistId")
        val allTracks = mutableListOf<SpotifyTrack>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"

        while (!nextUrl.isNullOrBlank()) {
            val responseText = executeWithBackoff(accessToken, nextUrl)
            val parsedPage = parsePlaylistTracksPage(responseText)
            allTracks.addAll(parsedPage.first)
            nextUrl = parsedPage.second
        }

        NodeLog.i(TAG, "Retrieved ${allTracks.size} tracks for Spotify playlist $playlistId")
        allTracks
    }

    private suspend fun executeWithBackoff(
        accessToken: String,
        url: String,
        maxRetries: Int = 3
    ): String {
        var currentDelayMs = 1000L
        for (attempt in 1..maxRetries) {
            val response = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }

            if (response.status == HttpStatusCode.OK) {
                return response.bodyAsText()
            } else if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfterSec = response.headers["Retry-After"]?.toLongOrNull() ?: (currentDelayMs / 1000L)
                val waitTimeMs = (retryAfterSec.coerceAtLeast(1L) * 1000L)
                NodeLog.w(TAG, "Rate limited (429) by Spotify. Backing off for ${waitTimeMs}ms (attempt $attempt/$maxRetries)")
                delay(waitTimeMs)
                currentDelayMs *= 2
            } else {
                val err = response.bodyAsText()
                throw IllegalStateException("Spotify request failed (${response.status}): $err")
            }
        }
        throw IllegalStateException("Spotify request timed out after $maxRetries rate limit retries")
    }

    private fun parseUserPlaylists(jsonText: String): List<SpotifyPlaylistSummary> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList()

        return items.mapNotNull { itemElem ->
            try {
                val obj = itemElem.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: "Untitled Playlist"
                val desc = obj["description"]?.jsonPrimitive?.content
                val trackCount = obj["tracks"]?.jsonObject?.get("total")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val owner = obj["owner"]?.jsonObject?.get("display_name")?.jsonPrimitive?.content
                val images = obj["images"]?.jsonArray
                val coverUrl = images?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                SpotifyPlaylistSummary(
                    id = id,
                    name = name,
                    description = desc,
                    trackCount = trackCount,
                    ownerName = owner,
                    coverUrl = coverUrl
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parsePlaylistTracksPage(jsonText: String): Pair<List<SpotifyTrack>, String?> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList<SpotifyTrack>() to null
        val nextElement = root["next"]
        val next = if (nextElement != null && nextElement !is kotlinx.serialization.json.JsonNull) {
            nextElement.jsonPrimitive.content.takeIf { it.isNotBlank() && it != "null" }
        } else null

        val tracks = items.mapNotNull { itemElem ->
            try {
                val trackObj = itemElem.jsonObject["track"]?.jsonObject ?: return@mapNotNull null
                val id = trackObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = trackObj["name"]?.jsonPrimitive?.content ?: "Unknown Title"
                val artists = trackObj["artists"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                } ?: emptyList()
                val album = trackObj["album"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Spotify Album"
                val durationMs = trackObj["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val isrc = trackObj["external_ids"]?.jsonObject?.get("isrc")?.jsonPrimitive?.content

                SpotifyTrack(
                    id = id,
                    title = title,
                    artists = artists,
                    album = album,
                    durationMs = durationMs,
                    isrc = isrc
                )
            } catch (e: Exception) {
                null
            }
        }
        return tracks to next
    }

    override fun close() {
        httpClient.close()
    }

    companion object {
        private const val TAG = "spotify-client"
    }
}
