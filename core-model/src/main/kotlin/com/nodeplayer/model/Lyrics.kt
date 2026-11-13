package com.nodeplayer.model

enum class LyricAlignment { Start, End }

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
    val syllables: List<LyricSyllable> = emptyList()
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
    val alignment: LyricAlignment = LyricAlignment.Start
) {
    val timeMs: Long get() = timestampMs
    val isWordSynced: Boolean get() = words.isNotEmpty()

    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    val sweepSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        words.flatMapIndexed { index, word ->
            val span = wordSpans.getOrNull(index) ?: (0 until 0)
            if (word.syllables.isEmpty()) {
                listOf(span)
            } else {
                word.syllables.map { (span.first + it.startChar) until (span.first + it.endChar) }
            }
        }
    }

    /**
     * How many characters of [text] have been sung at [positionMs], matching BitChord's 1-to-1 algorithm.
     */
    fun revealedChars(positionMs: Long): Float {
        if (text.isEmpty()) return 0f
        if (words.isEmpty()) {
            val end = endTimeMs ?: (timestampMs + 3500L)
            if (positionMs <= timestampMs) return 0f
            if (positionMs >= end) return text.length.toFloat()
            val duration = (end - timestampMs).coerceAtLeast(1L)
            return ((positionMs - timestampMs).toFloat() / duration.toFloat()) * text.length
        }
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) return start + word.charsSungAt(positionMs)
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }
}

data class Lyrics(
    val trackId: Long,
    val lines: List<LyricsLine> = emptyList(),
    val source: String = "none", // "embedded", "lrc", "lrclib"
    val isSynced: Boolean = false,
    val plainText: String = "",
    val hasWordTimings: Boolean = false,
    val offsetMs: Long = 0L
)
