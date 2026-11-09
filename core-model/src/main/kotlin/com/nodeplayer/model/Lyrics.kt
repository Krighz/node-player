package com.nodeplayer.model

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricsLine(
    val timestampMs: Long,
    val text: String,
    val endTimeMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val isEstimatedWords: Boolean = false
)

data class Lyrics(
    val trackId: Long,
    val lines: List<LyricsLine> = emptyList(),
    val source: String = "none", // "embedded", "lrc", "lrclib"
    val isSynced: Boolean = false,
    val plainText: String = "",
    val hasWordTimings: Boolean = false,
    val offsetMs: Long = 0L
)
