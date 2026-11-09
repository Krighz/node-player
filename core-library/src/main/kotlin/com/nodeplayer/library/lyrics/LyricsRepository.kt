package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import java.io.File

/**
 * High-level repository orchestrating lyrics discovery and caching.
 * Resolves lyrics in priority order:
 * 1. Embedded tags / local sidecar `.lrc` file (LYR-03)
 * 2. Disk cache (with 7-day TTL for negative lookups) (LYR-06)
 * 3. Online providers (LRCLIB) (LYR-01, LYR-02)
 */
class LyricsRepository(
    private val providers: List<LyricsProvider> = emptyList(),
    private val cacheDir: File? = null
) {

    private val negativeTtlMs = 7L * 24 * 60 * 60 * 1000L // 7 days

    init {
        cacheDir?.mkdirs()
    }

    suspend fun getLyrics(
        track: Track,
        forceRefresh: Boolean = false,
        userOffsetMs: Long = 0L
    ): Lyrics? {
        val cacheKey = buildCacheKey(track)

        // 1. Local-first: Check local sidecar file or track embedded lyrics (LYR-03)
        val localLyrics = loadLocalLyrics(track)
        if (localLyrics != null) {
            return applyOffset(localLyrics, userOffsetMs)
        }

        // 2. Check disk cache if not forcing refresh
        if (!forceRefresh && cacheDir != null) {
            val cached = loadFromCache(cacheKey)
            if (cached != null) {
                if (cached.isNegative) {
                    // Check 7-day TTL
                    if (System.currentTimeMillis() - cached.timestamp < negativeTtlMs) {
                        NodeLog.d("LyricsRepo", "Hit negative cache for ${track.title} (within 7d TTL)")
                        return null
                    }
                } else if (cached.lyrics != null) {
                    return applyOffset(cached.lyrics, userOffsetMs)
                }
            }
        }

        // 3. Query online providers (LRCLIB etc.)
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
                if (lyrics != null) {
                    saveToCache(cacheKey, lyrics = lyrics, isNegative = false)
                    return applyOffset(lyrics, userOffsetMs)
                }
            } catch (e: Exception) {
                NodeLog.w("LyricsRepo", "Provider ${provider.name} failed: ${e.message}")
            }
        }

        // 4. Save negative cache entry so we don't spam API for 7 days (LYR-06)
        saveToCache(cacheKey, lyrics = null, isNegative = true)
        return null
    }

    fun loadLocalLyrics(track: Track): Lyrics? {
        // Try sidecar file .lrc next to audio
        if (track.path.isNotBlank()) {
            try {
                val lrcFile = File(track.path.substringBeforeLast(".") + ".lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val content = lrcFile.readText()
                    if (content.isNotBlank()) {
                        return LrcParser.parse(content, track.id, source = "local_lrc")
                    }
                }
            } catch (e: Exception) {
                NodeLog.w("LyricsRepo", "Failed to read local sidecar lrc: ${e.message}")
            }
        }

        return null
    }

    fun clearCache() {
        cacheDir?.listFiles()?.forEach { it.delete() }
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

    private fun loadFromCache(key: String): CachedLyricsEntry? {
        val file = File(cacheDir, "$key.lrc")
        val metaFile = File(cacheDir, "$key.meta")
        if (!file.exists() && !metaFile.exists()) return null

        try {
            if (metaFile.exists()) {
                val metaText = metaFile.readText().trim()
                val parts = metaText.split(":")
                val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val isNegative = parts.getOrNull(1) == "negative"
                if (isNegative) {
                    return CachedLyricsEntry(timestamp = timestamp, isNegative = true, lyrics = null)
                }
            }

            if (file.exists()) {
                val lyrics = LrcParser.parse(file.readText(), source = "cache")
                return CachedLyricsEntry(timestamp = file.lastModified(), isNegative = false, lyrics = lyrics)
            }
        } catch (e: Exception) {
            NodeLog.w("LyricsRepo", "Failed to load cached lyrics: ${e.message}")
        }
        return null
    }

    private fun saveToCache(key: String, lyrics: Lyrics?, isNegative: Boolean) {
        if (cacheDir == null) return
        try {
            val metaFile = File(cacheDir, "$key.meta")
            val timestamp = System.currentTimeMillis()
            metaFile.writeText("$timestamp:${if (isNegative) "negative" else "positive"}")

            val lrcFile = File(cacheDir, "$key.lrc")
            if (lyrics != null && !isNegative) {
                // Serialize lines to standard LRC
                val sb = StringBuilder()
                for (line in lyrics.lines) {
                    val m = (line.timestampMs / 60000)
                    val s = (line.timestampMs % 60000) / 1000
                    val ms = (line.timestampMs % 1000) / 10
                    sb.append(String.format("[%02d:%02d.%02d]%s\n", m, s, ms, line.text))
                }
                lrcFile.writeText(sb.toString())
            } else if (lrcFile.exists()) {
                lrcFile.delete()
            }
        } catch (e: Exception) {
            NodeLog.w("LyricsRepo", "Failed to write lyrics cache: ${e.message}")
        }
    }

    private data class CachedLyricsEntry(
        val timestamp: Long,
        val isNegative: Boolean,
        val lyrics: Lyrics?
    )
}
