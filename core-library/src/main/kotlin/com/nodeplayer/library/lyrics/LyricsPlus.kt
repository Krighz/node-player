package com.nodeplayer.library.lyrics

import com.nodeplayer.model.LyricSyllable
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * LyricsPlus client â€” aggregates syllable-timed lyrics from community mirrors (YouLy+ backend).
 * Automatically races active mirrors with fast timeouts and circuit breaking.
 */
object LyricsPlus {

    // Active, verified live mirrors (pruned dead ones: atomix.one, vercel, workers.dev)
    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.binimum.org"
    )

    private val lastGoodHost = AtomicReference<String?>(null)

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L,
        isrc: String? = null
    ): Lyrics? = coroutineScope {
        val preferred = lastGoodHost.get()
        val hosts = if (preferred != null) {
            listOf(preferred) + MIRRORS.filterNot { it == preferred }
        } else {
            MIRRORS
        }

        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) {
                withTimeoutOrNull(4000) {
                    fetchFromHost(host, title, artist, durationMs, trackId, isrc)
                }
            }
        }.toMutableList()

        try {
            while (pending.isNotEmpty()) {
                val (host, lyrics) = select {
                    pending.forEach { (h, job) -> job.onAwait { h to it } }
                }
                pending.removeAll { it.first == host }
                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                    lastGoodHost.set(host)
                    return@coroutineScope lyrics
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetchFromHost(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long,
        isrc: String?
    ): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = durationMs / 1000
        val url = "$host/v2/lyrics/get?title=${URLEncoder.encode(title.trim(), "UTF-8")}&artist=${URLEncoder.encode(artist.trim(), "UTF-8")}" +
            (if (seconds > 0) "&duration=$seconds" else "") +
            (if (!isrc.isNullOrBlank()) "&isrc=${URLEncoder.encode(isrc.trim(), "UTF-8")}" else "")

        val json = LyricsHttp.get(url, timeoutMs = 4000) ?: return@withContext null
        parseLyricsPlusJson(json, trackId)
    }

    fun parseLyricsPlusJson(json: String, trackId: Long): Lyrics? {
        val lyricsIdx = json.indexOf("\"lyrics\"")
        if (lyricsIdx == -1) return null
        val arrayStart = json.indexOf('[', lyricsIdx)
        if (arrayStart == -1) return null

        val parsedLines = mutableListOf<LyricsLine>()
        var depth = 0
        var lineObjStart = -1

        for (i in arrayStart until json.length) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) lineObjStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && lineObjStart != -1) {
                        val lineStr = json.substring(lineObjStart, i + 1)
                        val line = parseLineObject(lineStr)
                        if (line != null) {
                            parsedLines.add(line)
                        }
                        lineObjStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }

        if (parsedLines.isEmpty()) return null
        val hasWordTimings = parsedLines.any { it.words.isNotEmpty() }
        return Lyrics(
            trackId = trackId,
            lines = parsedLines,
            source = "lyricsplus",
            isSynced = true,
            hasWordTimings = hasWordTimings
        )
    }

    private fun parseLineObject(lineStr: String): LyricsLine? {
        val timeMs = extractNumber(lineStr, "time")?.toLong() ?: return null
        val durationMs = extractNumber(lineStr, "duration")?.toLong()
        val text = extractString(lineStr, "text") ?: ""

        // Check if syllabus array is present
        val syllabusIdx = lineStr.indexOf("\"syllabus\"")
        val words = if (syllabusIdx != -1) {
            parseSyllablesIntoWords(lineStr.substring(syllabusIdx))
        } else {
            emptyList()
        }

        val endTime = if (durationMs != null && durationMs > 0) timeMs + durationMs else null
        return LyricsLine(
            timestampMs = timeMs,
            text = text,
            endTimeMs = endTime,
            words = words
        )
    }

    private fun parseSyllablesIntoWords(subJson: String): List<LyricWord> {
        val arrayStart = subJson.indexOf('[')
        if (arrayStart == -1) return emptyList()

        val rawSyllables = mutableListOf<RawSyllable>()
        var depth = 0
        var sylStart = -1

        for (i in arrayStart until subJson.length) {
            when (subJson[i]) {
                '{' -> {
                    if (depth == 0) sylStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && sylStart != -1) {
                        val sylStr = subJson.substring(sylStart, i + 1)
                        val sTime = extractNumber(sylStr, "time")?.toLong()
                        val sDur = extractNumber(sylStr, "duration")?.toLong() ?: 0L
                        val sText = extractString(sylStr, "text")
                        if (sTime != null && sText != null) {
                            rawSyllables.add(RawSyllable(sTime, sTime + sDur, sText))
                        }
                        sylStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }

        if (rawSyllables.isEmpty()) return emptyList()

        // Merge syllables into words delimited by whitespace
        val words = mutableListOf<LyricWord>()
        var currentWordText = StringBuilder()
        var wordStartMs = -1L
        var wordEndMs = -1L
        val currentSyllables = mutableListOf<LyricSyllable>()

        for (syl in rawSyllables) {
            if (wordStartMs == -1L) wordStartMs = syl.startMs
            wordEndMs = syl.endMs

            val charStart = currentWordText.length
            currentWordText.append(syl.text)
            val charEnd = currentWordText.length

            currentSyllables.add(
                LyricSyllable(
                    startMs = syl.startMs,
                    endMs = syl.endMs,
                    startChar = charStart,
                    endChar = charEnd
                )
            )

            if (syl.text.endsWith(" ") || syl.text.endsWith("\t")) {
                words.add(
                    LyricWord(
                        text = currentWordText.toString(),
                        startMs = wordStartMs,
                        endMs = wordEndMs,
                        syllables = currentSyllables.toList()
                    )
                )
                currentWordText = StringBuilder()
                wordStartMs = -1L
                wordEndMs = -1L
                currentSyllables.clear()
            }
        }

        if (currentWordText.isNotEmpty()) {
            words.add(
                LyricWord(
                    text = currentWordText.toString(),
                    startMs = wordStartMs,
                    endMs = wordEndMs,
                    syllables = currentSyllables.toList()
                )
            )
        }

        return words
    }

    private data class RawSyllable(val startMs: Long, val endMs: Long, val text: String)

    private fun extractString(json: String, field: String): String? {
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

    private fun extractNumber(json: String, field: String): Double? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon == -1) return null
        val start = json.indexOfFirst(colon + 1) { it.isDigit() || it == '-' }
        if (start == -1) return null
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '.' || json[end] == '-')) {
            end++
        }
        return json.substring(start, end).toDoubleOrNull()
    }

    private inline fun String.indexOfFirst(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (i in startIndex until length) {
            if (predicate(this[i])) return i
        }
        return -1
    }
}
