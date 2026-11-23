package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * Musixmatch client for :core-library.
 * Connects to Musixmatch desktop/web API with automatic token management and LRC/RichSync parsing.
 */
object Musixmatch {

    private const val BASE_URL = "https://apic-desktop.musixmatch.com/ws/1.1/"
    private const val APP_ID = "web-desktop-app-v1.0"

    private val cachedUserToken = AtomicReference<String?>("21051986b988e60cc7e50529d007c6f2ee4decf8f95c0c92d52f63")

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        album: String? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        val token = ensureUserToken()
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0

        val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
        val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")

        val query = StringBuilder()
            .append("format=json")
            .append("&app_id=").append(APP_ID)
            .append("&usertoken=").append(token)
            .append("&q_track=").append(encodedTitle)
            .append("&q_artist=").append(encodedArtist)

        if (seconds > 0) {
            query.append("&f_subtitle_length=").append(seconds)
            query.append("&f_subtitle_length_max_deviation=4")
        }
        if (!album.isNullOrBlank()) {
            query.append("&q_album=").append(URLEncoder.encode(album.trim(), "UTF-8"))
        }

        val url = "${BASE_URL}macro.subtitles.get?$query"
        val json = LyricsHttp.get(url, timeoutMs = 5000) ?: return@withContext null

        parseMusixmatchResponse(json, trackId, durationMs)
    }

    private suspend fun ensureUserToken(): String {
        val current = cachedUserToken.get()
        if (!current.isNullOrBlank()) return current

        val tokenUrl = "${BASE_URL}token.get?format=json&app_id=$APP_ID"
        val response = LyricsHttp.get(tokenUrl, timeoutMs = 4000)
        if (!response.isNullOrBlank()) {
            val token = extractJsonField(response, "user_token")
            if (!token.isNullOrBlank()) {
                cachedUserToken.set(token)
                return token
            }
        }
        return "21051986b988e60cc7e50529d007c6f2ee4decf8f95c0c92d52f63"
    }

    private fun parseMusixmatchResponse(json: String, trackId: Long, durationMs: Long): Lyrics? {
        val statusCode = extractJsonInt(json, "status_code")
        if (statusCode != null && statusCode != 200) {
            NodeLog.w("Musixmatch", "API returned non-200 status code: $statusCode")
            return null
        }

        // Strict duration validation: if track duration is given, reject deviations > 4 seconds
        val trackDurationSec = extractJsonInt(json, "track_length")
            ?: extractJsonInt(json, "subtitle_length")
        if (durationMs > 0 && trackDurationSec != null && trackDurationSec > 0) {
            val expectedSec = (durationMs / 1000).toInt()
            if (kotlin.math.abs(trackDurationSec - expectedSec) > 4) {
                NodeLog.d("Musixmatch", "Duration mismatch: expected $expectedSec s, got $trackDurationSec s")
                return null
            }
        }

        // 1. Check for subtitle_body (LRC format)
        val subtitleBody = extractJsonField(json, "subtitle_body")
        if (!subtitleBody.isNullOrBlank()) {
            val unescaped = unescapeJsonString(subtitleBody)
            val parsed = LrcParser.parse(unescaped, trackId = trackId, source = "musixmatch")
            if (parsed.lines.isNotEmpty()) {
                return parsed
            }
        }

        // 2. Check for plain lyrics_body fallback
        val lyricsBody = extractJsonField(json, "lyrics_body")
        if (!lyricsBody.isNullOrBlank()) {
            val unescaped = unescapeJsonString(lyricsBody)
                .replace(Regex("""\n\n\.\.\.\n\n\*{7} This Lyrics is NOT for Commercial use \*{7}"""), "")
                .trim()
            if (unescaped.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    plainText = unescaped,
                    source = "musixmatch",
                    isSynced = false
                )
            }
        }

        return null
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

    private fun extractJsonInt(json: String, key: String): Int? {
        val search = "\"$key\":"
        val idx = json.indexOf(search)
        if (idx == -1) return null
        val sub = json.substring(idx + search.length).trimStart()
        val numStr = sub.takeWhile { it.isDigit() || it == '-' }
        return numStr.toIntOrNull()
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
