package com.nodeplayer.library

import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import java.util.regex.Pattern

object LrcParser {
    private val TIME_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?]")

    fun parse(lrcContent: String, trackId: Long = 0, source: String = "lrc"): Lyrics {
        val lines = mutableListOf<LyricsLine>()
        val plainTextBuilder = StringBuilder()

        for (rawLine in lrcContent.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val matcher = TIME_TAG_REGEX.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1).toLongOrNull() ?: 0L
                val sec = matcher.group(2).toLongOrNull() ?: 0L
                val msPart = matcher.group(3)
                val ms = when (msPart?.length) {
                    2 -> msPart.toLong() * 10
                    3 -> msPart.toLong()
                    else -> 0L
                }
                val totalMs = min * 60_000 + sec * 1_000 + ms
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            val lyricText = trimmed.substring(lastMatchEnd).trim()
            plainTextBuilder.append(lyricText).append("\n")

            for (ts in timestamps) {
                lines.add(LyricsLine(timestampMs = ts, text = lyricText))
            }
        }

        lines.sortBy { it.timestampMs }

        return Lyrics(
            trackId = trackId,
            lines = lines,
            source = source,
            isSynced = lines.isNotEmpty(),
            plainText = plainTextBuilder.toString().trim()
        )
    }
}
