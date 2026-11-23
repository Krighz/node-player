package com.nodeplayer.music.lyrics

import android.icu.text.Transliterator
import com.nodeplayer.common.platform.LyricsTransliterator
import com.nodeplayer.library.lyrics.romanization.UrduMusicalLexicon
import com.nodeplayer.model.RomanizationScript

class AndroidIcuTransliterator : LyricsTransliterator {

    private val genericTransliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII") }
    private val hangulTransliterator by lazy { Transliterator.getInstance("Hangul-Latin") }
    private val cyrillicTransliterator by lazy { Transliterator.getInstance("Cyrillic-Latin") }
    private val devanagariTransliterator by lazy { Transliterator.getInstance("Devanagari-Latin") }
    private val arabicTransliterator by lazy { Transliterator.getInstance("Arabic-Latin") }

    override fun transliterate(text: String, script: RomanizationScript): String {
        if (text.isBlank()) return text
        return try {
            when (script) {
                RomanizationScript.KOREAN_HANGUL -> hangulTransliterator.transliterate(text)
                RomanizationScript.CYRILLIC -> cyrillicTransliterator.transliterate(text)
                RomanizationScript.HINDI_DEVANAGARI -> devanagariTransliterator.transliterate(text)
                RomanizationScript.URDU_ARABIC -> {
                    val preprocessed = UrduMusicalLexicon.preprocessUrdu(text)
                    arabicTransliterator.transliterate(preprocessed)
                }
                RomanizationScript.JAPANESE_ROMAJI,
                RomanizationScript.CHINESE_PINYIN -> genericTransliterator.transliterate(text)
                else -> genericTransliterator.transliterate(text)
            }
        } catch (_: Exception) {
            text
        }
    }
}
