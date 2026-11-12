package com.nodeplayer.library

import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import java.util.regex.Pattern

object LrcParser {
    // Standard line tags: [mm:ss.xx] or [mm:ss:xx] or [mm:ss]
    private val LINE_TIME_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?]")
    // Word timestamp tag: <mm:ss.xx> or <mm:ss:xx> or <mm:ss>
    private val WORD_TIME_TAG_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?>")
    // ID tags: [ar:...], [ti:...], [al:...], [offset:...], etc.
    private val ID_TAG_REGEX = Pattern.compile("\\[([a-zA-Z]+):([^\\]]*)]")

    fun parse(lrcContent: String, trackId: Long = 0, source: String = "lrc"): Lyrics {
        val parsedLines = mutableListOf<ParsedLineEntry>()
        val plainTextBuilder = StringBuilder()
        var hasWordTimingsGlobal = false
        var lrcOffsetMs = 0L

        for (rawLine in lrcContent.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Check for metadata tag [offset:+/-ms]
            val idMatcher = ID_TAG_REGEX.matcher(trimmed)
            if (idMatcher.matches()) {
                val tag = idMatcher.group(1).lowercase()
                val value = idMatcher.group(2).trim()
                if (tag == "offset") {
                    lrcOffsetMs = value.toLongOrNull() ?: 0L
                }
                continue
            }

            // Find all line timestamp tags at the beginning
            val lineMatcher = LINE_TIME_TAG_REGEX.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (lineMatcher.find()) {
                // If there was text before this tag that isn't another tag, stop treating as line header
                if (lineMatcher.start() != lastMatchEnd) {
                    break
                }
                val min = lineMatcher.group(1).toLongOrNull() ?: 0L
                val sec = lineMatcher.group(2).toLongOrNull() ?: 0L
                val msPart = lineMatcher.group(3)
                val ms = parseMillis(msPart)
                val totalMs = min * 60_000 + sec * 1_000 + ms
                timestamps.add(totalMs)
                lastMatchEnd = lineMatcher.end()
            }

            val lineContent = trimmed.substring(lastMatchEnd).trim()

            if (timestamps.isEmpty()) {
                // Check if this line has no timestamp tags at all -> plain text line
                if (lineContent.isNotEmpty()) {
                    plainTextBuilder.append(lineContent).append("\n")
                }
                continue
            }

            // Now parse enhanced LRC per-word tags inside lineContent if present:
            // e.g. "<00:10.00>Hello <00:10.50>world <00:11.20>"
            val (words, cleanText) = parseWordTimings(lineContent, timestamps.first())
            if (words.isNotEmpty()) {
                hasWordTimingsGlobal = true
            }

            if (cleanText.isNotEmpty()) {
                plainTextBuilder.append(cleanText).append("\n")
            }

            for (ts in timestamps) {
                // Offset adjustment if offset tag was present
                val adjustedTs = maxOf(0L, ts + lrcOffsetMs)
                val adjustedWords = if (lrcOffsetMs != 0L && words.isNotEmpty()) {
                    words.map { it.copy(startMs = maxOf(0L, it.startMs + lrcOffsetMs), endMs = maxOf(0L, it.endMs + lrcOffsetMs)) }
                } else {
                    words
                }
                parsedLines.add(ParsedLineEntry(timestampMs = adjustedTs, text = cleanText, words = adjustedWords))
            }
        }

        parsedLines.sortBy { it.timestampMs }

        // Construct final LyricsLines with endTimeMs and word estimation if words are missing
        val finalLines = mutableListOf<LyricsLine>()
        for (i in parsedLines.indices) {
            val curr = parsedLines[i]
            val nextTs = if (i + 1 < parsedLines.size) parsedLines[i + 1].timestampMs else null
            val lineDuration = if (nextTs != null && nextTs > curr.timestampMs) {
                minOf(nextTs - curr.timestampMs, 10_000L) // cap single line duration to 10s
            } else {
                3_500L // default 3.5s for last line or identical timestamps
            }
            val endTimeMs = curr.timestampMs + lineDuration

            val (finalWords, isEstimated) = if (curr.words.isNotEmpty()) {
                curr.words to false
            } else {
                estimateWords(curr.text, curr.timestampMs, endTimeMs) to true
            }

            finalLines.add(
                LyricsLine(
                    timestampMs = curr.timestampMs,
                    text = curr.text,
                    endTimeMs = endTimeMs,
                    words = finalWords,
                    isEstimatedWords = isEstimated
                )
            )
        }

        return Lyrics(
            trackId = trackId,
            lines = finalLines,
            source = source,
            isSynced = finalLines.isNotEmpty(),
            plainText = plainTextBuilder.toString().trim(),
            hasWordTimings = hasWordTimingsGlobal,
            offsetMs = lrcOffsetMs
        )
    }

    private fun parseMillis(msPart: String?): Long {
        return when (msPart?.length) {
            1 -> msPart.toLong() * 100
            2 -> msPart.toLong() * 10
            3 -> msPart.toLong()
            else -> 0L
        }
    }

    private fun parseWordTimings(content: String, baseLineTs: Long): Pair<List<LyricWord>, String> {
        val wordMatcher = WORD_TIME_TAG_REGEX.matcher(content)
        if (!wordMatcher.find()) {
            return emptyList<LyricWord>() to content
        }

        wordMatcher.reset()
        val tags = mutableListOf<Pair<Long, Int>>() // Pair of (timestampMs, matchEndPos)
        while (wordMatcher.find()) {
            val min = wordMatcher.group(1).toLongOrNull() ?: 0L
            val sec = wordMatcher.group(2).toLongOrNull() ?: 0L
            val ms = parseMillis(wordMatcher.group(3))
            val ts = min * 60_000 + sec * 1_000 + ms
            tags.add(ts to wordMatcher.end())
        }

        val words = mutableListOf<LyricWord>()
        val cleanTextBuilder = StringBuilder()

        for (i in tags.indices) {
            val startTs = tags[i].first
            val startPos = tags[i].second
            val endPos = if (i + 1 < tags.size) {
                // Start of next tag: need to find its match start
                val nextTagMatcher = WORD_TIME_TAG_REGEX.matcher(content)
                if (nextTagMatcher.find(startPos)) nextTagMatcher.start() else content.length
            } else {
                content.length
            }

            val wordText = content.substring(startPos, endPos)
            if (wordText.isNotEmpty()) {
                val endTs = if (i + 1 < tags.size) tags[i + 1].first else startTs + 600L
                cleanTextBuilder.append(wordText)
                val trimmedWord = wordText.trim()
                if (trimmedWord.isNotEmpty()) {
                    words.add(
                        LyricWord(
                            text = wordText, // keep spacing
                            startMs = startTs,
                            endMs = maxOf(startTs + 100L, endTs)
                        )
                    )
                }
            }
        }

        return words to cleanTextBuilder.toString().trim()
    }

    private fun estimateWords(lineText: String, lineStartMs: Long, lineEndMs: Long): List<LyricWord> {
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
            // Attach a trailing space to all except last token
            val wordWithSpace = if (isLast) token else "$token "
            words.add(LyricWord(text = wordWithSpace, startMs = currentStart, endMs = wordEnd))
            currentStart = wordEnd
        }

        return words
    }

    private data class ParsedLineEntry(
        val timestampMs: Long,
        val text: String,
        val words: List<LyricWord>
    )
}
