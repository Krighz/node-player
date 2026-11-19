package com.nodeplayer.library.lyrics

import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * BetterLyrics client â€” Apple Music TTML (per-syllable) and QQ Music Portato karaoke timings.
 * Endpoints:
 * - https://lyrics-api.boidu.dev/getLyrics
 * - https://lyrics-api.boidu.dev/qq/getLyrics
 */
object BetterLyrics {

    private const val BASE_ENDPOINT = "https://lyrics-api.boidu.dev/getLyrics"
    private const val PORTATO_ENDPOINT = "https://lyrics-api.boidu.dev/qq/getLyrics"

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null
    ): Lyrics? = query(BASE_ENDPOINT, title, artist, durationMs, trackId, album, "betterlyrics")

    suspend fun fetchPortato(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null
    ): Lyrics? = query(PORTATO_ENDPOINT, title, artist, durationMs, trackId, album, "betterlyrics_portato")

    private suspend fun query(
        endpoint: String,
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long,
        album: String?,
        sourceTag: String
    ): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = durationMs / 1000
        val url = "$endpoint?s=${URLEncoder.encode(title.trim(), "UTF-8")}&a=${URLEncoder.encode(artist.trim(), "UTF-8")}" +
            (if (seconds > 0) "&d=$seconds" else "") +
            (if (!album.isNullOrBlank()) "&al=${URLEncoder.encode(album.trim(), "UTF-8")}" else "")

        val body = LyricsHttp.get(url, timeoutMs = 5000) ?: return@withContext null
        val trimmed = body.trim()

        // Guard against HTML error pages
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return@withContext null
        }

        if (trimmed.startsWith("<tt", ignoreCase = true) || trimmed.contains("<ttml", ignoreCase = true)) {
            val parsed = TtmlLyrics.parse(trimmed, trackId = trackId, source = sourceTag)
            if (parsed.lines.isNotEmpty()) return@withContext parsed
        }

        // Fallback for LRC / Enhanced LRC payloads
        if (trimmed.contains("[") && trimmed.contains("]")) {
            val parsed = EnhancedLrc.parse(trimmed, trackId = trackId, source = sourceTag)
            if (parsed.lines.isNotEmpty()) return@withContext parsed
        }

        null
    }
}
