package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import java.io.File

/**
 * High-level repository orchestrating lyrics discovery and caching.
 *
 * Implements syllable-first resolution hierarchy per D-05:
 * 1. In-memory 50-track LRU cache (LyricsMemoryCache) per D-07
 * 2. Embedded tags or local sidecar (.ttml / .lrc) file
 * 3. lrc.red ISRC resolution (if ISRC provided or discoverable)
 * 4. lrc.red free-text search matching (with duration tolerance & version guards per D-06)
 * 5. Online line-synced providers (LRCLIB etc.)
 * 6. Legacy local LRC fallback via LrcParser.parse()
 *
 * Also provides word estimation fallback per D-08:
 * For line-synced lyrics with no syllable data, interpolates word timestamps
 * linearly across each line with isEstimatedWords = true.
 */
class LyricsRepository(
    private val providers: List<LyricsProvider> = emptyList(),
    @Deprecated("Disk cache removed per D-07; retained for constructor backward compatibility")
    private val cacheDir: File? = null,
    private val memoryCache: LyricsMemoryCache = LyricsMemoryCache(50)
) {

    private val negativeTtlMs = 7L * 24 * 60 * 60 * 1000L // 7 days

    suspend fun getLyrics(
        track: Track,
        forceRefresh: Boolean = false,
        userOffsetMs: Long = 0L,
        isrc: String? = null
    ): Lyrics? {
        val cacheKey = buildCacheKey(track)

        // 1. Check in-memory LRU cache if not forcing refresh (D-07)
        if (!forceRefresh) {
            val cached = memoryCache.get(cacheKey)
            if (cached != null) {
                if (cached.isNegative) {
                    if (System.currentTimeMillis() - cached.timestamp < negativeTtlMs) {
                        NodeLog.d("LyricsRepo", "Hit negative cache for ${track.title} (within 7d TTL)")
                        return null
                    }
                } else if (cached.lyrics != null) {
                    return applyOffset(cached.lyrics, userOffsetMs)
                }
            }
        }

        // 2. Check local sidecar files (.ttml, .lrc)
        val localLyrics = loadLocalLyrics(track)
        if (localLyrics != null) {
            val processed = ensureWordTimings(localLyrics)
            memoryCache.put(cacheKey, processed, isNegative = false)
            return applyOffset(processed, userOffsetMs)
        }

        // 3. Try lrc.red by ISRC (D-05)
        if (!isrc.isNullOrBlank()) {
            try {
                val lrcRedLyrics = LrcRed.fetchByIsrc(isrc, trackId = track.id)
                if (lrcRedLyrics != null && lrcRedLyrics.lines.isNotEmpty()) {
                    val processed = ensureWordTimings(lrcRedLyrics)
                    memoryCache.put(cacheKey, processed, isNegative = false)
                    return applyOffset(processed, userOffsetMs)
                }
            } catch (e: Exception) {
                NodeLog.w("LyricsRepo", "lrc.red ISRC fetch failed: ${e.message}")
            }
        }

        // 4. Try lrc.red search (D-05, D-06)
        if (track.title.isNotBlank()) {
            try {
                val lrcRedSearch = LrcRed.search(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    trackId = track.id
                )
                if (lrcRedSearch != null && lrcRedSearch.lines.isNotEmpty()) {
                    val processed = ensureWordTimings(lrcRedSearch)
                    memoryCache.put(cacheKey, processed, isNegative = false)
                    return applyOffset(processed, userOffsetMs)
                }
            } catch (e: Exception) {
                NodeLog.w("LyricsRepo", "lrc.red search failed: ${e.message}")
            }
        }

        // 5. Query online line-synced providers (LRCLIB etc.)
        for (provider in providers) {
            try {
                val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else null
                val result = provider.fetchLyrics(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSec = durationSec
                )
                val lyrics = result.getOrNull()
                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                    val processed = ensureWordTimings(lyrics)
                    memoryCache.put(cacheKey, processed, isNegative = false)
                    return applyOffset(processed, userOffsetMs)
                }
            } catch (e: Exception) {
                NodeLog.w("LyricsRepo", "Provider ${provider.name} failed: ${e.message}")
            }
        }

        // 6. Save negative cache entry so we don't spam APIs for 7 days
        memoryCache.put(cacheKey, lyrics = null, isNegative = true)
        return null
    }

    fun loadLocalLyrics(track: Track): Lyrics? {
        if (track.path.isBlank()) return null
        val basePath = track.path.substringBeforeLast(".")

        // Try .ttml first
        try {
            val ttmlFile = File("$basePath.ttml")
            if (ttmlFile.exists() && ttmlFile.canRead()) {
                val content = ttmlFile.readText()
                if (content.isNotBlank()) {
                    val parsed = TtmlLyrics.parse(content, trackId = track.id, source = "local_ttml")
                    if (parsed.lines.isNotEmpty()) return parsed
                }
            }
        } catch (e: Exception) {
            NodeLog.w("LyricsRepo", "Failed to read local sidecar ttml: ${e.message}")
        }

        // Try .lrc sidecar file
        try {
            val lrcFile = File("$basePath.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                val content = lrcFile.readText()
                if (content.isNotBlank()) {
                    return LrcParser.parse(content, track.id, source = "local_lrc")
                }
            }
        } catch (e: Exception) {
            NodeLog.w("LyricsRepo", "Failed to read local sidecar lrc: ${e.message}")
        }

        return null
    }

    fun clearCache() {
        memoryCache.clear()
    }

    /**
     * Interpolates estimated word timestamps linearly across each line
     * if word timings are absent per D-08.
     */
    private fun ensureWordTimings(lyrics: Lyrics): Lyrics {
        if (lyrics.hasWordTimings) return lyrics
        var anyWordsAdded = false

        val newLines = lyrics.lines.mapIndexed { idx, line ->
            if (line.words.isNotEmpty()) {
                line
            } else {
                val nextTs = if (idx + 1 < lyrics.lines.size) lyrics.lines[idx + 1].timestampMs else null
                val lineDuration = if (nextTs != null && nextTs > line.timestampMs) {
                    minOf(nextTs - line.timestampMs, 10_000L)
                } else {
                    line.endTimeMs?.minus(line.timestampMs)?.takeIf { it > 0 } ?: 3_500L
                }
                val lineEnd = line.timestampMs + lineDuration
                val estimated = estimateWordsForLine(line.text, line.timestampMs, lineEnd)
                if (estimated.isNotEmpty()) anyWordsAdded = true
                line.copy(
                    endTimeMs = line.endTimeMs ?: lineEnd,
                    words = estimated,
                    isEstimatedWords = true
                )
            }
        }

        return lyrics.copy(
            lines = newLines,
            hasWordTimings = lyrics.hasWordTimings || anyWordsAdded
        )
    }

    private fun estimateWordsForLine(lineText: String, lineStartMs: Long, lineEndMs: Long): List<LyricWord> {
        val trimmed = lineText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return emptyList()

        val totalChars = tokens.sumOf { it.length }
        val totalDurationMs = maxOf(300L, lineEndMs - lineStartMs)

        var currentStart = lineStartMs
        val words = mutableListOf<LyricWord>()

        for (i in tokens.indices) {
            val token = tokens[i]
            val isLast = i == tokens.lastIndex
            val wordDuration = if (isLast) {
                lineEndMs - currentStart
            } else {
                val share = (token.length.toDouble() / totalChars.toDouble()) * totalDurationMs
                maxOf(150L, share.toLong())
            }

            val wordEnd = if (isLast) lineEndMs else currentStart + wordDuration
            val wordWithSpace = if (isLast) token else "$token "
            words.add(LyricWord(text = wordWithSpace, startMs = currentStart, endMs = wordEnd))
            currentStart = wordEnd
        }

        return words
    }

    private fun applyOffset(lyrics: Lyrics, userOffsetMs: Long): Lyrics {
        if (userOffsetMs == 0L) return lyrics
        val adjustedLines = lyrics.lines.map { line ->
            line.copy(
                timestampMs = maxOf(0L, line.timestampMs + userOffsetMs),
                endTimeMs = line.endTimeMs?.let { maxOf(0L, it + userOffsetMs) },
                words = line.words.map { word ->
                    word.copy(
                        startMs = maxOf(0L, word.startMs + userOffsetMs),
                        endMs = maxOf(0L, word.endMs + userOffsetMs)
                    )
                }
            )
        }
        return lyrics.copy(
            lines = adjustedLines,
            offsetMs = lyrics.offsetMs + userOffsetMs
        )
    }

    private fun buildCacheKey(track: Track): String {
        val raw = "${track.title.trim().lowercase()}_${track.artist.trim().lowercase()}"
        return raw.replace(Regex("[^a-z0-9_-]"), "_").take(80)
    }
}
