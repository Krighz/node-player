package com.nodeplayer.library.lyrics.romanization

import com.nodeplayer.common.platform.PlatformTransliteratorRegistry
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.RomanizationScript

object LyricsRomanizer {
    fun enrich(lyrics: Lyrics): Lyrics {
        val transliterator = PlatformTransliteratorRegistry.instance ?: return lyrics
        val enrichedLines = lyrics.lines.map { line ->
            val script = if (line.detectedScript != RomanizationScript.NONE) {
                line.detectedScript
            } else {
                ScriptDetector.detect(line.text)
            }

            if (script == RomanizationScript.NONE) {
                line
            } else {
                val lineRom = transliterator.transliterate(line.text, script)
                val enrichedWords = line.words.map { word ->
                    val wordRom = transliterator.transliterate(word.text, script)
                    word.copy(romanizedText = wordRom)
                }
                val enrichedBg = line.background?.let { bg ->
                    val bgScript = if (bg.detectedScript != RomanizationScript.NONE) bg.detectedScript else ScriptDetector.detect(bg.text)
                    if (bgScript == RomanizationScript.NONE) {
                        bg
                    } else {
                        val bgRom = transliterator.transliterate(bg.text, bgScript)
                        val bgWords = bg.words.map { w ->
                            w.copy(romanizedText = transliterator.transliterate(w.text, bgScript))
                        }
                        bg.copy(romanizedText = bgRom, words = bgWords, detectedScript = bgScript)
                    }
                }
                line.copy(
                    romanizedText = lineRom,
                    words = enrichedWords,
                    background = enrichedBg,
                    detectedScript = script
                )
            }
        }
        return lyrics.copy(lines = enrichedLines)
    }
}
