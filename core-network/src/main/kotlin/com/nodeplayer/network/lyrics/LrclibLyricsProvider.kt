package com.nodeplayer.network.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.library.lyrics.LyricsProvider
import com.nodeplayer.model.Lyrics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * LRCLIB lyrics provider querying lrclib.net.
 * Implements LYR-01 & LYR-02 (exact match first, then search fallback with ±5s tolerance).
 */
class LrclibLyricsProvider(
    engine: HttpClientEngine? = null,
    private val userAgent: String = "NodePlayer/1.8.0 (https://github.com/satyanarayan1999/node-player)"
) : LyricsProvider {

    override val name: String = "lrclib"

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient: HttpClient = if (engine != null) {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }
    } else {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }
    }

    @Serializable
    data class LrclibRecord(
        val id: Long? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null
    )

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int?
    ): Result<Lyrics?> = runCatching {
        if (title.isBlank() || artist.isBlank()) {
            return@runCatching null
        }

        // 1. Try exact match: GET /api/get
        try {
            val exactResponse: HttpResponse = httpClient.get("https://lrclib.net/api/get") {
                header("User-Agent", userAgent)
                parameter("track_name", title.trim())
                parameter("artist_name", artist.trim())
                if (!album.isNullOrBlank()) {
                    parameter("album_name", album.trim())
                }
                if (durationSec != null && durationSec > 0) {
                    parameter("duration", durationSec)
                }
            }

            if (exactResponse.status == HttpStatusCode.OK) {
                val record = exactResponse.body<LrclibRecord>()
                val lyrics = parseRecord(record)
                if (lyrics != null) {
                    NodeLog.d("Lrclib", "Found exact match for '$title' by '$artist'")
                    return@runCatching lyrics
                }
            }
        } catch (e: Exception) {
            NodeLog.w("Lrclib", "Exact match lookup failed: ${e.message}")
        }

        // 2. Try search fallback: GET /api/search?q=...
        try {
            val query = "$title $artist".trim()
            val searchResponse: HttpResponse = httpClient.get("https://lrclib.net/api/search") {
                header("User-Agent", userAgent)
                parameter("q", query)
            }

            if (searchResponse.status == HttpStatusCode.OK) {
                val results = searchResponse.body<List<LrclibRecord>>()
                // Filter by ±5 seconds tolerance if durationSec is available
                val matched = results.firstOrNull { record ->
                    if (durationSec != null && durationSec > 0 && record.duration != null) {
                        abs(record.duration - durationSec) <= 5.0
                    } else {
                        true
                    }
                } ?: results.firstOrNull()

                if (matched != null) {
                    val lyrics = parseRecord(matched)
                    if (lyrics != null) {
                        NodeLog.d("Lrclib", "Found search fallback match for '$title' by '$artist'")
                        return@runCatching lyrics
                    }
                }
            }
        } catch (e: Exception) {
            NodeLog.w("Lrclib", "Search fallback failed: ${e.message}")
        }

        null
    }

    private fun parseRecord(record: LrclibRecord): Lyrics? {
        val synced = record.syncedLyrics?.trim()
        val plain = record.plainLyrics?.trim()

        if (!synced.isNullOrEmpty()) {
            return LrcParser.parse(synced, trackId = record.id ?: 0L, source = "lrclib")
        }

        if (!plain.isNullOrEmpty()) {
            return Lyrics(
                trackId = record.id ?: 0L,
                lines = emptyList(),
                source = "lrclib",
                isSynced = false,
                plainText = plain
            )
        }

        return null
    }
}
