package com.nodeplayer.model

enum class LyricAlignment { Start, End }

enum class RomanizationScript {
    NONE,
    KOREAN_HANGUL,
    JAPANESE_ROMAJI,
    CHINESE_PINYIN,
    URDU_ARABIC,
    HINDI_DEVANAGARI,
    CYRILLIC,
    SPANISH_LATIN
}

enum class LyricsDisplayMode {
    ORIGINAL_ONLY,
    DUAL_LINE,
    ROMANIZED_ONLY
}

data class LyricSyllable(
    val startMs: Long,
    val endMs: Long,
    val startChar: Int,
    val endChar: Int
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val syllables: List<LyricSyllable> = emptyList(),
    val romanizedText: String? = null // Preserves word-level timing
) {
    fun charsSungAt(positionMs: Long): Float {
        if (positionMs >= endMs) return text.length.toFloat()
        if (positionMs <= startMs) return 0f
        if (syllables.isEmpty()) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            val progress = ((positionMs - startMs).toFloat() / span * text.length)
            return progress.coerceIn(0f, text.length.toFloat())
        }
        for (syllable in syllables) {
            if (positionMs < syllable.startMs) {
                return syllable.startChar.toFloat().coerceIn(0f, text.length.toFloat())
            }
            if (positionMs < syllable.endMs) {
                val span = (syllable.endMs - syllable.startMs).coerceAtLeast(1L)
                val through = (positionMs - syllable.startMs).toFloat() / span
                val progress = syllable.startChar + through * (syllable.endChar - syllable.startChar)
                return progress.coerceIn(0f, text.length.toFloat())
            }
        }
        return text.length.toFloat()
    }
}

data class LyricsLine(
    val timestampMs: Long,
    val text: String,
    val endTimeMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val isEstimatedWords: Boolean = false,
    val background: LyricsLine? = null,
    val alignment: LyricAlignment = LyricAlignment.Start,
    val romanizedText: String? = null,
    val translationText: String? = null,
    val detectedScript: RomanizationScript = RomanizationScript.NONE
) {
    val timeMs: Long get() = timestampMs
    val isWordSynced: Boolean get() = words.isNotEmpty()
}

data class Lyrics(
    val trackId: Long,
    val lines: List<LyricsLine> = emptyList(),
    val source: String = "none", // "embedded", "lrc", "lrclib"
    val isSynced: Boolean = false,
    val plainText: String = "",
    val hasWordTimings: Boolean = false,
    val offsetMs: Long = 0L,
    val sections: List<SongSectionMarker> = emptyList()
)
