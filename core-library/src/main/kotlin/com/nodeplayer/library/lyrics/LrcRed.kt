package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * lrc.red client — Apple Music TTML syllable lyrics client.
 *
 * Implements:
 * - Direct ISRC query via https://lrc.red/s/{ISRC}.ttml
 * - Search query via https://lrc.red/search.json?q={title}+{artist}
 * - Strict candidate matching per D-06:
 *   - Duration tolerance: abs(candidate.duration - (durationMs / 1000.0)) <= 3.0 seconds
 *   - Version word guard: requires exact match on version words (live, acoustic, remix, etc.)
 * - Exponential backoff retry on transient 5xx HTTP errors
 */
object LrcRed {

    private const val BASE_URL = "https://lrc.red/"
    const val DURATION_TOLERANCE_SECONDS = 3.0

    private val ISRC_REGEX = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")

    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE
    )

    val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended"
    )

    data class SearchCandidate(
        val isrc: String? = null,
        val id: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val duration: Double? = null
    )

    fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC_REGEX.matches(it) }?.let { "${BASE_URL}s/$it.ttml" }

    suspend fun fetchByIsrc(
        isrc: String,
        trackId: Long = 0L,
        httpGet: (suspend (String) -> String?) = { defaultHttpGet(it) }
    ): Lyrics? = withContext(Dispatchers.IO) {
        val url = documentUrl(isrc) ?: return@withContext null
        val body = httpGet(url) ?: return@withContext null
        val parsed = TtmlLyrics.parse(body, trackId = trackId, source = "lrc.red")
        if (parsed.lines.isNotEmpty()) parsed else null
    }

    suspend fun search(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        httpGet: (suspend (String) -> String?) = { defaultHttpGet(it) }
    ): Lyrics? = withContext(Dispatchers.IO) {
        val query = "$title $artist".trim()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "${BASE_URL}search.json?q=$encodedQuery"
        val searchBody = httpGet(searchUrl) ?: return@withContext null

        val hits = parseHitsFromJson(searchBody)
        if (hits.isEmpty()) return@withContext null

        val bestHit = best(hits, title, artist, durationMs) ?: return@withContext null
        val targetId = bestHit.isrc ?: bestHit.id ?: return@withContext null
        val docUrl = documentUrl(targetId) ?: "${BASE_URL}s/$targetId.ttml"
        val ttml = httpGet(docUrl) ?: return@withContext null

        val parsed = TtmlLyrics.parse(ttml, trackId = trackId, source = "lrc.red")
        if (parsed.lines.isNotEmpty()) parsed else null
    }

    fun best(
        hits: List<SearchCandidate>,
        title: String,
        artist: String,
        durationMs: Long
    ): SearchCandidate? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)
        val seconds = durationMs / 1000.0

        fun distance(hit: SearchCandidate): Double =
            if (durationMs > 0 && hit.duration != null) abs(hit.duration - seconds) else 0.0

        return hits
            .filter { hit ->
                val name = hit.title ?: return@filter false
                val passesIsrc = (hit.isrc?.let(::documentUrl) != null) || (!hit.id.isNullOrBlank())
                passesIsrc &&
                    coreOf(name) == wantedTitle &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                    distance(hit) <= DURATION_TOLERANCE_SECONDS
            }
            .minByOrNull(::distance)
    }

    fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - "))
            .ifEmpty { normalized(title) }

    fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Parses search response JSON into List<SearchCandidate> without external JSON library dependencies.
     */
    fun parseHitsFromJson(json: String): List<SearchCandidate> {
        val hits = mutableListOf<SearchCandidate>()
        val hitsIdx = json.indexOf("\"hits\"")
        if (hitsIdx == -1) return emptyList()

        val arrayStart = json.indexOf('[', hitsIdx)
        if (arrayStart == -1) return emptyList()

        var depth = 0
        var objStart = -1
        for (i in arrayStart until json.length) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart != -1) {
                        val objStr = json.substring(objStart, i + 1)
                        val isrc = extractStringField(objStr, "isrc")
                        val id = extractStringField(objStr, "id")
                        val title = extractStringField(objStr, "title")
                        val artist = extractStringField(objStr, "artist")
                        val album = extractStringField(objStr, "album")
                        val year = extractNumberField(objStr, "year")?.toInt()
                        val duration = extractNumberField(objStr, "duration")
                        hits.add(
                            SearchCandidate(
                                isrc = isrc,
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                year = year,
                                duration = duration
                            )
                        )
                        objStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }
        return hits
    }

    private fun extractStringField(json: String, field: String): String? {
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
                sb.append(c)
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

    private fun extractNumberField(json: String, field: String): Double? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon == -1) return null
        val part = json.substring(colon + 1).trim()
        val numStr = part.takeWhile { it.isDigit() || it == '.' || it == '-' }
        return numStr.toDoubleOrNull()
    }

    suspend fun defaultHttpGet(urlStr: String, maxRetries: Int = 2): String? = withContext(Dispatchers.IO) {
        var attempt = 0
        var backoffMs = 500L
        while (attempt <= maxRetries) {
            try {
                val url = URL(urlStr)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "NodePlayer/1.8.0 (Audiophile Music Player)")
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    return@withContext connection.inputStream.bufferedReader().use { it.readText() }
                } else if (code in 500..599 && attempt < maxRetries) {
                    attempt++
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    attempt++
                    delay(backoffMs)
                    backoffMs *= 2
                } else {
                    NodeLog.w("LrcRed", "HTTP GET failed for $urlStr: ${e.message}")
                    return@withContext null
                }
            }
        }
        null
    }
}
