package com.nodeplayer.library.lyrics

import com.nodeplayer.model.LyricAlignment
import com.nodeplayer.model.LyricSyllable
import com.nodeplayer.model.LyricWord
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Hardened DOM XML TTML parser for Apple Music / lrc.red word-timed syllable lyrics.
 *
 * Implements:
 * - XXE mitigation via secure DocumentBuilderFactory configuration per D-04
 * - Element.qualified extension to resolve namespaced attributes across JVM Xerces and Android Expat
 * - Clock-time (HH:MM:SS.mmm / MM:SS.mmm) and offset-seconds (84.321s / 84s) timestamps per D-01
 * - Backing vocal extraction (ttm:role="x-bg") attached to LyricsLine.background per D-02
 * - Duet singer alignment via [lineAlignments] per D-03
 */
object TtmlLyrics {

    private val SKIPPED_ROLES = setOf("x-translation", "x-roman")
    private const val BACKGROUND_ROLE = "x-bg"

    fun parse(
        ttml: String,
        trackId: Long = 0L,
        source: String = "lrc.red"
    ): Lyrics = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            harden("http://apache.org/xml/features/disallow-doctype-decl")
            harden("http://xml.org/sax/features/external-general-entities", false)
            harden("http://xml.org/sax/features/external-parameter-entities", false)
            harden(XMLConstants.FEATURE_SECURE_PROCESSING)
            runCatching { isExpandEntityReferences = false }
        }

        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val paragraphs = document.getElementsByTagName("p")

        val sung = ArrayList<Pair<LyricsLine, String?>>(paragraphs.length)
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as? Element ?: continue
            val line = lineFrom(paragraph) ?: continue
            sung += line to paragraph.qualified("ttm:agent").takeIf { it.isNotEmpty() }
        }
        sung.sortBy { it.first.timestampMs }

        val sides = lineAlignments(sung.map { it.second }, agentTypes(document))
        val finalLines = sung.mapIndexed { index, (line, _) ->
            line.copy(alignment = sides[index])
        }

        val plainText = finalLines.joinToString("\n") { it.text }
        val hasWordTimings = finalLines.any { it.words.isNotEmpty() }

        Lyrics(
            trackId = trackId,
            lines = finalLines,
            source = source,
            isSynced = finalLines.isNotEmpty(),
            plainText = plainText,
            hasWordTimings = hasWordTimings
        )
    }.getOrDefault(
        Lyrics(
            trackId = trackId,
            lines = emptyList(),
            source = source,
            isSynced = false,
            plainText = "",
            hasWordTimings = false
        )
    )

    private fun DocumentBuilderFactory.harden(feature: String, value: Boolean = true) {
        runCatching { setFeature(feature, value) }
    }

    private fun agentTypes(document: org.w3c.dom.Document): Map<String, String> {
        val agents = document.getElementsByTagName("ttm:agent")
            .takeIf { it.length > 0 }
            ?: document.getElementsByTagName("agent")
        val types = HashMap<String, String>(agents.length)
        for (i in 0 until agents.length) {
            val agent = agents.item(i) as? Element ?: continue
            val id = agent.qualified("xml:id")
            val type = agent.getAttribute("type")
            if (id.isNotEmpty() && type.isNotEmpty()) types[id] = type
        }
        return types
    }

    /**
     * Resolves an attribute by qualified or local name, ensuring compatibility
     * across JVM Xerces and Android Expat DOM parsers when isNamespaceAware = false.
     */
    fun Element.qualified(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val found = attributes ?: return ""
        for (i in 0 until found.length) {
            val attribute = found.item(i) ?: continue
            if (attribute.nodeName == name ||
                attribute.nodeName == local ||
                attribute.localName == local
            ) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun lineFrom(paragraph: Element): LyricsLine? {
        val pieces = mutableListOf<Piece>()
        val backingPieces = mutableListOf<Piece>()
        collect(paragraph, pieces, backingPieces)
        val words = mergeIntoWords(pieces)
        val backing = mergeIntoWords(backingPieces).takeIf { it.isNotEmpty() }?.let {
            val startMs = it.first().startMs
            val endMs = it.last().endMs
            LyricsLine(
                timestampMs = startMs,
                text = it.joinToString(" ") { word -> word.text },
                endTimeMs = endMs,
                words = it
            )
        }

        if (words.isEmpty()) {
            val text = paragraph.textContent?.trim().orEmpty()
            val begin = time(paragraph.getAttribute("begin")) ?: return null
            if (text.isEmpty()) return null
            val end = time(paragraph.getAttribute("end"))?.takeIf { it > begin }
            return LyricsLine(
                timestampMs = begin,
                text = text,
                endTimeMs = end
            )
        }

        val begin = time(paragraph.getAttribute("begin")) ?: words.first().startMs
        val paragraphEnd = time(paragraph.getAttribute("end"))
        val end = paragraphEnd ?: words.last().endMs

        return LyricsLine(
            timestampMs = minOf(begin, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            endTimeMs = end,
            words = words,
            background = backing
        )
    }

    private fun collect(node: Node, out: MutableList<Piece>, backing: MutableList<Piece>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is Element -> {
                    val role = child.qualified("ttm:role")
                    if (role in SKIPPED_ROLES) continue
                    val sink = if (role == BACKGROUND_ROLE) backing else out
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        sink += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, sink, backing)
                    }
                }
                else -> if (child.nodeType == Node.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotBlank()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    private fun mergeIntoWords(pieces: List<Piece>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        val syllables = mutableListOf<LyricSyllable>()
        var start = 0L
        var end = 0L
        var timed = false

        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) {
                words += LyricWord(
                    text = text,
                    startMs = start,
                    endMs = maxOf(start + 50L, end),
                    syllables = normalizeSyllables(syllables, text.length)
                )
            }
            syllables.clear()
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    val from = current.length
                    current.append(piece.text.trim())
                    syllables += LyricSyllable(
                        startMs = piece.start,
                        endMs = piece.end,
                        startChar = from,
                        endChar = current.length
                    )
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    private fun normalizeSyllables(
        syllables: List<LyricSyllable>,
        wordLength: Int
    ): List<LyricSyllable> {
        if (syllables.isEmpty()) return emptyList()
        return syllables.map {
            it.copy(
                startChar = it.startChar.coerceIn(0, wordLength),
                endChar = it.endChar.coerceIn(0, wordLength)
            )
        }
    }

    /**
     * TTML clock values: `27.395`, `1:05.20`, `1:02:03.4`, or numbers with `s`/`ms` suffix.
     * Returns timestamp in milliseconds.
     */
    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms", ignoreCase = true)) {
            return raw.dropLast(2).trim().toDoubleOrNull()?.toLong()
        }
        val stripped = raw.removeSuffix("s").removeSuffix("S").trim()
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
