package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * High-level repository orchestrating lyrics discovery, multi-provider racing, and caching.
 *
 * Implements syllable-first concurrent resolution hierarchy:
 * 1. In-memory 50-track LRU cache (LyricsMemoryCache) per D-07
 * 2. Embedded tags or local sidecar (.ttml / .lrc) file
 * 3. Fast ISRC identification (via BiniLyrics or metadata, capped at 2.5s)
 * 4. Concurrent Tier 1 (Word/Syllable-Synced):
 *    - lrc.red (ISRC / title matching)
 *    - BiniLyrics (Apple Music TTML by recording)
 *    - BetterLyrics (Apple TTML & QQ Music Portato)
 *    - LyricsPlus (Community multi-mirror v2 syllable sync)
 * 5. Concurrent Tier 2 (Line-Synced Fallbacks):
 *    - LrcLib (Fast, 99.9% uptime)
 *    - KuGou (Massive global/Asian catalog)
 *    - Injected custom LyricsProviders
 * 6. Syllable priority & Word estimation:
 *    - If any Tier 1 provider returns word-synced lyrics, Tier 2 is canceled immediately.
 *    - If only line-synced lyrics are available, words are interpolated linearly via ensureWordTimings.
 * 7. Negative caching with transient 10-minute TTL to prevent cache poisoning.
 */
class LyricsRepository(
    private val providers: List<LyricsProvider> = emptyList(),
    @Deprecated("Disk cache removed per D-07; retained for constructor backward compatibility")
    private val cacheDir: File? = null,
    private val memoryCache: LyricsMemoryCache = LyricsMemoryCache(50)
) {

    // 10-minute transient negative TTL prevents locking out tracks during network glitches
    private val negativeTtlMs = 10L * 60 * 1000L

    suspend fun getLyrics(
        track: Track,
        forceRefresh: Boolean = false,
        userOffsetMs: Long = 0L,
        isrc: String? = null
    ): Lyrics? {
        val cacheKey = buildCacheKey(track)

        // 1. Check in-memory LRU cache if not forcing refresh
        if (!forceRefresh) {
            val cached = memoryCache.get(cacheKey)
            if (cached != null) {
                if (cached.isNegative) {
                    if (System.currentTimeMillis() - cached.timestamp < negativeTtlMs) {
                        NodeLog.d("LyricsRepo", "Hit negative cache for ${track.title} (within transient TTL)")
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

        // 3. Fast ISRC identification (capped at 2.5s)
        var effectiveIsrc = isrc
        if (effectiveIsrc.isNullOrBlank() && track.title.isNotBlank()) {
            effectiveIsrc = withTimeoutOrNull(2500) {
                runCatching {
                    BiniLyrics.identify(track.title, track.artist, track.durationMs)?.isrc
                }.getOrNull()
            }
            if (!effectiveIsrc.isNullOrBlank()) {
                NodeLog.d("LyricsRepo", "Resolved ISRC $effectiveIsrc for ${track.title}")
            }
        }

        // 4. Multi-provider concurrent race with Syllable priority
        val remoteLyrics = fetchRemoteLyricsConcurrently(track, effectiveIsrc)
        if (remoteLyrics != null && remoteLyrics.lines.isNotEmpty()) {
            val processed = ensureWordTimings(remoteLyrics)
            memoryCache.put(cacheKey, processed, isNegative = false)
            return applyOffset(processed, userOffsetMs)
        }

        // 5. Save transient negative cache entry
        memoryCache.put(cacheKey, lyrics = null, isNegative = true)
        return null
    }

    private suspend fun fetchRemoteLyricsConcurrently(
        track: Track,
        isrc: String?
    ): Lyrics? = coroutineScope {
        if (track.title.isBlank()) return@coroutineScope null

        // --- Tier 1: Syllable / Word-synced Providers ---
        val lrcRedJob = async(Dispatchers.IO) {
            runCatching {
                if (!isrc.isNullOrBlank()) {
                    LrcRed.fetchByIsrc(isrc, trackId = track.id)
                } else {
                    LrcRed.search(track.title, track.artist, track.durationMs, track.id)
                }
            }.getOrNull()
        }

        val biniJob = async(Dispatchers.IO) {
            runCatching {
                BiniLyrics.fetchLyrics(track.title, track.artist, track.durationMs, track.id, track.album, isrc)
            }.getOrNull()
        }

        val betterLyricsJob = async(Dispatchers.IO) {
            runCatching {
                BetterLyrics.fetchLyrics(track.title, track.artist, track.durationMs, track.id, track.album)
            }.getOrNull()
        }

        val betterPortatoJob = async(Dispatchers.IO) {
            runCatching {
                BetterLyrics.fetchPortato(track.title, track.artist, track.durationMs, track.id, track.album)
            }.getOrNull()
        }

        val lyricsPlusJob = async(Dispatchers.IO) {
            runCatching {
                LyricsPlus.fetchLyrics(track.title, track.artist, track.durationMs, track.id, isrc)
            }.getOrNull()
        }

        // --- Tier 2: Line-synced Fallbacks ---
        val lrcLibJob = async(Dispatchers.IO) {
            runCatching {
                LrcLib.fetchLyrics(track.title, track.artist, track.durationMs, track.id, track.album)
            }.getOrNull()
        }

        val kuGouJob = async(Dispatchers.IO) {
            runCatching {
                KuGou.fetchLyrics(track.title, track.artist, track.durationMs, track.id)
            }.getOrNull()
        }

        val injectedProvidersJobs = providers.map { provider ->
            async(Dispatchers.IO) {
                runCatching {
                    val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else null
                    provider.fetchLyrics(track.title, track.artist, track.album, durationSec).getOrNull()
                }.getOrNull()
            }
        }

        val syllableJobs = listOf(lrcRedJob, biniJob, betterLyricsJob, betterPortatoJob, lyricsPlusJob)
        val lineJobs = listOf(lrcLibJob, kuGouJob) + injectedProvidersJobs

        // 1. Check for genuine word/syllable-timed hit first
        for (job in syllableJobs) {
            val result = job.await()
            if (result != null && result.lines.isNotEmpty() && result.hasWordTimings) {
                NodeLog.i("LyricsRepo", "Word-synced match acquired from ${result.source}")
                // Cancel pending line fallbacks to save network bandwidth
                lineJobs.forEach { it.cancel() }
                return@coroutineScope result
            }
        }

        // 2. Check remaining syllable jobs if they produced line-level lyrics
        for (job in syllableJobs) {
            val result = job.await()
            if (result != null && result.lines.isNotEmpty()) {
                lineJobs.forEach { it.cancel() }
                return@coroutineScope result
            }
        }

        // 3. Fallback to Tier 2 line-synced providers in order of completion
        for (job in lineJobs) {
            val result = job.await()
            if (result != null && result.lines.isNotEmpty()) {
                NodeLog.i("LyricsRepo", "Line-synced fallback acquired from ${result.source}")
                return@coroutineScope result
            }
        }

        null
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
