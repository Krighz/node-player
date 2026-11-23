package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * BiniLyrics client â€” Apple Music TTML with ISRC discovery and word/syllable timing.
 * Endpoint: https://lyrics-api.binimum.org/
 */
object BiniLyrics {

    private const val BASE_URL = "https://lyrics-api.binimum.org/"

    data class IdentifyHit(
        val isrc: String? = null,
        val lyricsUrl: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val timingType: String? = null
    )

    suspend fun identify(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        isrc: String? = null
    ): IdentifyHit? = withContext(Dispatchers.IO) {
        val query = if (!isrc.isNullOrBlank()) {
            "isrc=${URLEncoder.encode(isrc.trim(), "UTF-8")}"
        } else {
            val seconds = durationMs / 1000
            val sb = StringBuilder()
            sb.append("track=").append(URLEncoder.encode(title.trim(), "UTF-8"))
            sb.append("&artist=").append(URLEncoder.encode(artist.trim(), "UTF-8"))
            if (!album.isNullOrBlank()) {
                sb.append("&album=").append(URLEncoder.encode(album.trim(), "UTF-8"))
            }
            if (seconds > 0) {
                sb.append("&duration=").append(seconds)
            }
            sb.toString()
        }

        val json = LyricsHttp.get("$BASE_URL?$query", timeoutMs = 4500) ?: return@withContext null
        parseIdentifyResponse(json)
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null,
        isrc: String? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        val hit = identify(title, artist, durationMs, album, isrc) ?: return@withContext null
        val docUrl = hit.lyricsUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val fullUrl = if (docUrl.startsWith("http")) docUrl else "https://lrc.red/$docUrl"
        val ttml = LyricsHttp.get(fullUrl, timeoutMs = 5000) ?: return@withContext null
        TtmlLyrics.parse(ttml, trackId = trackId, source = "binilyrics").takeIf { it.lines.isNotEmpty() }
    }

    fun parseIdentifyResponse(json: String): IdentifyHit? {
        val resultsIdx = json.indexOf("\"results\"")
        if (resultsIdx == -1) return null
        val arrayStart = json.indexOf('[', resultsIdx)
        if (arrayStart == -1) return null
        val firstObjStart = json.indexOf('{', arrayStart)
        if (firstObjStart == -1) return null

        var depth = 0
        var firstObjEnd = -1
        for (i in firstObjStart until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        firstObjEnd = i
                        break
                    }
                }
            }
        }
        if (firstObjEnd == -1) return null

        val objStr = json.substring(firstObjStart, firstObjEnd + 1)
        val isrc = extractField(objStr, "isrc")
        val lyricsUrl = extractField(objStr, "lyricsUrl")
        val trackName = extractField(objStr, "track_name")
        val artistName = extractField(objStr, "artist_name")
        val timingType = extractField(objStr, "timing_type")

        return if (isrc != null || lyricsUrl != null) {
            IdentifyHit(
                isrc = isrc,
                lyricsUrl = lyricsUrl,
                trackName = trackName,
                artistName = artistName,
                timingType = timingType
            )
        } else {
            null
        }
    }

    private fun extractField(json: String, field: String): String? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon == -1) return null
        val quoteStart = json.indexOf('"', colon + 1)
        if (quoteStart == -1) return null
        val quoteEnd = json.indexOf('"', quoteStart + 1)
        if (quoteEnd == -1) return null
        return json.substring(quoteStart + 1, quoteEnd).replace("\\/", "/")
    }
}
