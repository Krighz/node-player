package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Unison client for :core-library.
 * High-performance Asian & community synchronized lyrics provider with syllable & karaoke timing support.
 */
object Unison {

    // Active community Unison / Syrics endpoints
    private val ENDPOINTS = listOf(
        "https://lyricsplus.prjktla.my.id/v2/lyrics",
        "https://syncedlyrics.vercel.app/api/lyrics"
    )

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0
        val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
        val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")

        for (endpoint in ENDPOINTS) {
            val url = "$endpoint?title=$encodedTitle&artist=$encodedArtist" +
                (if (seconds > 0) "&duration=$seconds" else "") +
                (if (!album.isNullOrBlank()) "&album=${URLEncoder.encode(album.trim(), "UTF-8")}" else "")

            val response = LyricsHttp.get(url, timeoutMs = 4500) ?: continue
            val trimmed = response.trim()

            // 1. Check for TTML payload
            if (trimmed.startsWith("<tt", ignoreCase = true) || trimmed.contains("<ttml", ignoreCase = true)) {
                val parsed = TtmlLyrics.parse(trimmed, trackId = trackId, source = "unison")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }

            // 2. Check for LRC / Enhanced LRC payload
            if (trimmed.contains("[") && trimmed.contains("]") && trimmed.contains(":")) {
                val parsed = LrcParser.parse(trimmed, trackId = trackId, source = "unison")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }

            // 3. Check for JSON format { "lyrics": "...", "synced": "..." }
            val synced = extractJsonField(trimmed, "synced") ?: extractJsonField(trimmed, "lyrics")
            if (!synced.isNullOrBlank()) {
                val parsed = LrcParser.parse(unescapeJsonString(synced), trackId = trackId, source = "unison")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }
        }

        null
    }

    private fun extractJsonField(json: String, key: String): String? {
        val search = "\"$key\":"
        val idx = json.indexOf(search)
        if (idx == -1) return null
        val startQuote = json.indexOf('"', idx + search.length)
        if (startQuote == -1) return null

        var i = startQuote + 1
        var escaped = false
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (escaped) {
                sb.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
                sb.append(c)
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
            }
            i++
        }
        return null
    }

    private fun unescapeJsonString(str: String): String {
        return str
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
