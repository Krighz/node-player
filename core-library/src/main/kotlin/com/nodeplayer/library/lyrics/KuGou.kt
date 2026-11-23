package com.nodeplayer.library.lyrics

import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Base64

/**
 * KuGou lyrics client for :core-library.
 * Line-synced fallback offering vast global catalog coverage (including Bollywood, EDM, Asian, and indie).
 */
object KuGou {

    data class Candidate(val id: String, val accessKey: String)

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        trackId: Long = 0L
    ): Lyrics? = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).toInt()
        val query = "$title $artist".trim()
        val searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=" +
            URLEncoder.encode(query, "UTF-8") +
            (if (seconds > 0) "&duration=${seconds * 1000}" else "")

        val searchJson = LyricsHttp.get(searchUrl, timeoutMs = 4500) ?: return@withContext null
        val candidate = parseCandidate(searchJson) ?: return@withContext null

        val downloadUrl = "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${candidate.id}&accesskey=${candidate.accessKey}"
        val downloadJson = LyricsHttp.get(downloadUrl, timeoutMs = 4500) ?: return@withContext null
        val base64Content = extractField(downloadJson, "content") ?: return@withContext null

        val rawLrc = runCatching {
            String(Base64.getDecoder().decode(base64Content), Charsets.UTF_8)
        }.getOrNull() ?: return@withContext null

        val sanitized = sanitizeLrc(rawLrc)
        if (sanitized.isBlank()) return@withContext null

        val parsed = LrcParser.parse(sanitized, trackId = trackId, source = "kugou")
        if (parsed.lines.isNotEmpty()) parsed else null
    }

    fun sanitizeLrc(lrc: String): String {
        val lines = lrc.lineSequence().filter { it.trim().startsWith("[") }.toList()
        if (lines.isEmpty()) return ""

        // Check if file is only an instrumental notice
        if (lines.any { it.contains("纯音乐，请欣赏") || it.contains("没有填词") || it.contains("纯音乐") && lines.size <= 3 }) {
            return ""
        }

        // Filter out non-lyric metadata lines
        return lines.filterNot { line ->
            val content = line.substringAfter(']').trim()
            content.startsWith("作词") || content.startsWith("作曲") || content.startsWith("编曲") ||
                content.startsWith("制作") || content.startsWith("演唱") || content.startsWith("录音") ||
                content.startsWith("混音") || content.startsWith("母带") || content.startsWith("Lyrics by")
        }.joinToString("\n")
    }

    fun parseCandidate(json: String): Candidate? {
        val candidatesIdx = json.indexOf("\"candidates\"")
        if (candidatesIdx == -1) return null
        val arrayStart = json.indexOf('[', candidatesIdx)
        if (arrayStart == -1) return null
        val firstObj = json.indexOf('{', arrayStart)
        if (firstObj == -1) return null
        val objEnd = json.indexOf('}', firstObj)
        if (objEnd == -1) return null

        val sub = json.substring(firstObj, objEnd + 1)
        val id = extractField(sub, "id") ?: return null
        val accessKey = extractField(sub, "accesskey") ?: return null
        return Candidate(id, accessKey)
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
        return json.substring(quoteStart + 1, quoteEnd)
    }
}
