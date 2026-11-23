package com.nodeplayer.library.lyrics

import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Native LRCLIB client for :core-library.
 * High-uptime community line-synced lyrics provider.
 * Endpoint: https://lrclib.net/api/get
 */
object LrcLib {

    private const val BASE_URL = "https://lrclib.net/api/get"

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).toInt()
        val url = "$BASE_URL?track_name=${URLEncoder.encode(title.trim(), "UTF-8")}&artist_name=${URLEncoder.encode(artist.trim(), "UTF-8")}" +
            (if (!album.isNullOrBlank()) "&album_name=${URLEncoder.encode(album.trim(), "UTF-8")}" else "") +
            (if (seconds > 0) "&duration=$seconds" else "")

        val json = LyricsHttp.get(url, timeoutMs = 4500) ?: return@withContext null

        val isInstrumental = extractBoolean(json, "instrumental") ?: false
        if (isInstrumental) return@withContext null

        val syncedLyrics = extractJsonString(json, "syncedLyrics")
        if (!syncedLyrics.isNullOrBlank()) {
            return@withContext LrcParser.parse(syncedLyrics, trackId = trackId, source = "lrclib")
        }

        val plainLyrics = extractJsonString(json, "plainLyrics")
        if (!plainLyrics.isNullOrBlank()) {
            return@withContext Lyrics(
                trackId = trackId,
                plainText = plainLyrics,
                source = "lrclib",
                isSynced = false
            )
        }

        null
    }

    private fun extractBoolean(json: String, field: String): Boolean? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon == -1) return null
        val sub = json.substring(colon + 1).trimStart()
        return when {
            sub.startsWith("true") -> true
            sub.startsWith("false") -> false
            else -> null
        }
    }

    private fun extractJsonString(json: String, field: String): String? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon == -1) return null
        val quoteStart = json.indexOf('"', colon + 1)
        if (quoteStart == -1) return null
        val sb = StringBuilder()
        var escaped = false
        for (i in (quoteStart + 1) until json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
            }
        }
        return null
    }
}
