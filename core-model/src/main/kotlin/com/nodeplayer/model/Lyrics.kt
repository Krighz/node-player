package com.nodeplayer.model

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class Lyrics(
    val trackId: Long,
    val lines: List<LyricsLine> = emptyList(),
    val source: String = "none", // "embedded", "lrc", "lrclib"
    val isSynced: Boolean = false,
    val plainText: String = ""
)
