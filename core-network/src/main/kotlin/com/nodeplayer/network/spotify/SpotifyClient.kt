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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
        val root = (json.parseToJsonElement(jsonText) as? JsonObject) ?: return emptyList()
        val items = (root["items"] as? JsonArray) ?: return emptyList()

        return items.mapNotNull { itemElem ->
            try {
                val obj = itemElem as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: "Untitled Playlist"
                val desc = (obj["description"] as? JsonPrimitive)?.contentOrNull
                val trackCount = (obj["tracks"] as? JsonObject)?.get("total")?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() } ?: 0
                val owner = (obj["owner"] as? JsonObject)?.get("display_name")?.let { (it as? JsonPrimitive)?.contentOrNull }
                val images = obj["images"] as? JsonArray
                val coverUrl = (images?.firstOrNull() as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }

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
        val root = (json.parseToJsonElement(jsonText) as? JsonObject) ?: return emptyList<SpotifyTrack>() to null
        val items = (root["items"] as? JsonArray) ?: return emptyList<SpotifyTrack>() to null
        val nextElement = root["next"]
        val next = (nextElement as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

        val tracks = items.mapNotNull { itemElem ->
            try {
                val trackObj = ((itemElem as? JsonObject)?.get("track") as? JsonObject) ?: return@mapNotNull null
                val id = (trackObj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val title = (trackObj["name"] as? JsonPrimitive)?.contentOrNull ?: "Unknown Title"
                val artists = (trackObj["artists"] as? JsonArray)?.mapNotNull {
                    ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
                } ?: emptyList()
                val album = ((trackObj["album"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull ?: "Spotify Album"
                val durationMs = (trackObj["duration_ms"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                val isrc = ((trackObj["external_ids"] as? JsonObject)?.get("isrc") as? JsonPrimitive)?.contentOrNull

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
