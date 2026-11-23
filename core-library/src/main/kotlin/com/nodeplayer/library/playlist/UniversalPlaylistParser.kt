package com.nodeplayer.library.playlist

import com.nodeplayer.model.playlist.ArtistTitleOrder
import com.nodeplayer.model.playlist.ImportConfig
import com.nodeplayer.model.playlist.ParsedTrackCandidate
import java.util.regex.Pattern

/**
 * Universal parser for playlist files (CSV and TXT) and pasted text buffers.
 * Fully RFC 4180 compliant for CSV, handles quotes, delimiters, BOM, and multiple TXT formats.
 */
object UniversalPlaylistParser {

    private val LEADING_NUMBER_REGEX = Regex("""^\s*\d+(?:\s*[\).\-–—:]|\s+)\s*""")
    private val DASH_REGEX = Regex("""\s+[-–—]\s+""")
    private val YOUTUBE_URL_REGEX = Regex("""(?:https?://)?(?:www\.|music\.)?(?:youtube\.com/(?:watch\?.*v=|embed/|v/)|youtu\.be/)([a-zA-Z0-9_-]{11})""")

    /**
     * Detects delimiter from the first line of CSV text among comma, semicolon, and tab.
     */
    fun detectDelimiter(firstLine: String): Char {
        val s = firstLine.removePrefix("\uFEFF")
        var commaCount = 0
        var semiCount = 0
        var tabCount = 0
        var inQuotes = false

        for (c in s) {
            when {
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> commaCount++
                !inQuotes && c == ';' -> semiCount++
                !inQuotes && c == '\t' -> tabCount++
            }
        }

        return when {
            semiCount > commaCount && semiCount > tabCount -> ';'
            tabCount > commaCount && tabCount > semiCount -> '\t'
            else -> ','
        }
    }

    /**
     * Parses RFC 4180 compliant CSV text into a 2D list of cells.
     */
    fun parseCsv(text: String, delimiter: Char = ','): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < s.length && s[i + 1] == '"') {
                            sb.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        sb.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    row.add(sb.toString())
                    sb.setLength(0)
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row.add(sb.toString())
                    sb.setLength(0)
                    if (row.size > 1 || (row.isNotEmpty() && row[0].isNotEmpty())) {
                        rows.add(row)
                    }
                    row = ArrayList()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            if (row.size > 1 || (row.isNotEmpty() && row[0].isNotEmpty())) {
                rows.add(row)
            }
        }
        return rows
    }

    /**
     * Parses duration string representations into milliseconds:
     * - Only digits and > 10000: treated as milliseconds
     * - Contains colon (:): mm:ss or h:mm:ss
     * - Otherwise treated as seconds
     */
    fun parseDuration(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()

        if (trimmed.contains(':')) {
            val parts = trimmed.split(':')
            return try {
                when (parts.size) {
                    2 -> {
                        val min = parts[0].toLong()
                        val sec = parts[1].toDouble()
                        (min * 60_000L) + (sec * 1000.0).toLong()
                    }
                    3 -> {
                        val hours = parts[0].toLong()
                        val min = parts[1].toLong()
                        val sec = parts[2].toDouble()
                        (hours * 3_600_000L) + (min * 60_000L) + (sec * 1000.0).toLong()
                    }
                    else -> 0L
                }
            } catch (_: Exception) {
                0L
            }
        }

        val pureDigits = trimmed.toLongOrNull()
        if (pureDigits != null) {
            return if (pureDigits > 10000L) pureDigits else pureDigits * 1000L
        }

        val floatSeconds = trimmed.toDoubleOrNull()
        if (floatSeconds != null) {
            return if (floatSeconds > 10000.0) floatSeconds.toLong() else (floatSeconds * 1000.0).toLong()
        }

        return 0L
    }

    /**
     * Parses CSV text with column mapping into structured track candidates.
     * Enforces size and line count limits from [config].
     */
    fun parseCsvContent(
        csvText: String,
        config: ImportConfig = ImportConfig()
    ): List<ParsedTrackCandidate> {
        val firstLine = csvText.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = detectDelimiter(firstLine)
        val rows = parseCsv(csvText, delimiter)
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.trim().lowercase() }
        var titleIdx = -1
        var artistIdx = -1
        var albumIdx = -1
        var durationIdx = -1
        var isrcIdx = -1
        var urlIdx = -1

        for ((idx, header) in headers.withIndex()) {
            when {
                titleIdx == -1 && ImportColumnAliases.TITLE_ALIASES.contains(header) -> titleIdx = idx
                artistIdx == -1 && ImportColumnAliases.ARTIST_ALIASES.contains(header) -> artistIdx = idx
                albumIdx == -1 && ImportColumnAliases.ALBUM_ALIASES.contains(header) -> albumIdx = idx
                durationIdx == -1 && ImportColumnAliases.DURATION_ALIASES.contains(header) -> durationIdx = idx
                isrcIdx == -1 && ImportColumnAliases.ISRC_ALIASES.contains(header) -> isrcIdx = idx
                urlIdx == -1 && ImportColumnAliases.URL_ALIASES.contains(header) -> urlIdx = idx
            }
        }

        require(titleIdx != -1) { "CSV missing required Title column" }

        val candidates = ArrayList<ParsedTrackCandidate>()
        val dataRows = rows.drop(1).take(config.maxLines)

        for (row in dataRows) {
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            val rawTitle = row.getOrNull(titleIdx)?.trim().orEmpty()
            if (rawTitle.isEmpty()) continue

            val rawArtist = if (artistIdx != -1) row.getOrNull(artistIdx)?.trim().orEmpty() else ""
            val rawAlbum = if (albumIdx != -1) row.getOrNull(albumIdx)?.trim().orEmpty() else ""
            val rawDuration = if (durationIdx != -1) row.getOrNull(durationIdx) else null
            val rawIsrc = if (isrcIdx != -1) row.getOrNull(isrcIdx)?.trim()?.takeIf { it.isNotBlank() } else null
            val rawUrl = if (urlIdx != -1) row.getOrNull(urlIdx)?.trim()?.takeIf { it.isNotBlank() } else null

            candidates.add(
                ParsedTrackCandidate(
                    title = rawTitle,
                    artist = rawArtist,
                    album = rawAlbum,
                    durationMs = parseDuration(rawDuration),
                    isrc = rawIsrc,
                    url = rawUrl,
                    rawLine = row.joinToString(delimiter.toString())
                )
            )
        }

        return candidates
    }

    /**
     * Parses TXT formatted lines into structured track candidates.
     * Enforces line count limits from [config].
     */
    fun parseTxtContent(
        txtText: String,
        config: ImportConfig = ImportConfig()
    ): List<ParsedTrackCandidate> {
        val lines = txtText.lines()
            .take(config.maxLines)
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        val candidates = ArrayList<ParsedTrackCandidate>()

        for (rawLine in lines) {
            // Strip leading track numbering (e.g. "1. ", "01 - ", "12) ")
            val line = rawLine.replace(LEADING_NUMBER_REGEX, "").trim()
            if (line.isEmpty()) continue

            // Rule 1: Direct YouTube or YouTube Music link containing videoId
            val ytMatch = YOUTUBE_URL_REGEX.find(line)
            if (ytMatch != null) {
                val videoId = ytMatch.groupValues[1]
                candidates.add(
                    ParsedTrackCandidate(
                        title = line,
                        artist = "",
                        url = "https://www.youtube.com/watch?v=$videoId",
                        rawLine = rawLine
                    )
                )
                continue
            }

            // Rule 2: "Title by Artist" (split at the LAST " by ", case-insensitive)
            val byIndex = line.lastIndexOf(" by ", ignoreCase = true)
            if (byIndex > 0 && byIndex + 4 < line.length) {
                val titlePart = line.substring(0, byIndex).trim()
                val artistPart = line.substring(byIndex + 4).trim()
                if (titlePart.isNotEmpty() && artistPart.isNotEmpty()) {
                    candidates.add(
                        ParsedTrackCandidate(
                            title = titlePart,
                            artist = artistPart,
                            rawLine = rawLine
                        )
                    )
                    continue
                }
            }

            // Rule 3: "Artist - Title" using -, –, or — with surrounding spaces
            val dashMatch = DASH_REGEX.find(line)
            if (dashMatch != null) {
                val leftPart = line.substring(0, dashMatch.range.first).trim()
                val rightPart = line.substring(dashMatch.range.last + 1).trim()
                if (leftPart.isNotEmpty() && rightPart.isNotEmpty()) {
                    val (artist, title) = if (config.artistTitleOrder == ArtistTitleOrder.ARTIST_TITLE) {
                        leftPart to rightPart
                    } else {
                        rightPart to leftPart
                    }
                    candidates.add(
                        ParsedTrackCandidate(
                            title = title,
                            artist = artist,
                            rawLine = rawLine
                        )
                    )
                    continue
                }
            }

            // Rule 4: Plain text treated as search query (title only)
            candidates.add(
                ParsedTrackCandidate(
                    title = line,
                    artist = "",
                    rawLine = rawLine
                )
            )
        }

        return candidates
    }

    /**
     * Autodetects whether content is CSV (has recognizable headers and delimiter) or TXT.
     */
    fun parseAuto(
        content: String,
        config: ImportConfig = ImportConfig()
    ): List<ParsedTrackCandidate> {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = detectDelimiter(firstLine)
        val firstRow = parseCsv(firstLine, delimiter).firstOrNull() ?: emptyList()
        val headers = firstRow.map { it.trim().lowercase() }

        val hasTitle = headers.any { ImportColumnAliases.TITLE_ALIASES.contains(it) }
        return if (hasTitle) {
            parseCsvContent(content, config)
        } else {
            parseTxtContent(content, config)
        }
    }

    /**
     * Decodes raw bytes to string with automatic encoding detection.
     * Handles UTF-8 BOM, UTF-16 LE/BE BOM, raw UTF-16 without BOM, and falls back to Windows-1252 / ISO-8859-1.
     */
    fun decodeBytesWithFallback(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2) {
            // UTF-16 LE BOM: FF FE
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            // UTF-16 BE BOM: FE FF
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        if (bytes.size >= 3) {
            // UTF-8 BOM: EF BB BF
            if (bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
        }
        // Try strict UTF-8 decoding
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            // Check for UTF-16 without BOM (alternating zero bytes)
            if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) {
                try {
                    return String(bytes, Charsets.UTF_16LE)
                } catch (_: Exception) {}
            } else if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[2] == 0.toByte()) {
                try {
                    return String(bytes, Charsets.UTF_16BE)
                } catch (_: Exception) {}
            }
            // Fallback to Windows-1252 (or ISO-8859-1)
            try {
                String(bytes, java.nio.charset.Charset.forName("windows-1252"))
            } catch (_: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }
}
