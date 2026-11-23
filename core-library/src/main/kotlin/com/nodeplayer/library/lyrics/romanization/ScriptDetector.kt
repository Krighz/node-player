package com.nodeplayer.library.lyrics.romanization

import com.nodeplayer.model.RomanizationScript

object ScriptDetector {
    fun detect(text: String): RomanizationScript {
        var hangul = 0
        var kana = 0
        var cjk = 0
        var cyrillic = 0
        var devanagari = 0
        var arabic = 0

        for (ch in text) {
            val code = ch.code
            when {
                code in 0xAC00..0xD7AF || code in 0x1100..0x11FF -> hangul++
                code in 0x3040..0x309F || code in 0x30A0..0x30FF -> kana++
                code in 0x4E00..0x9FFF -> cjk++
                code in 0x0400..0x04FF -> cyrillic++
                code in 0x0900..0x097F -> devanagari++
                code in 0x0600..0x06FF || code in 0x0750..0x077F -> arabic++
            }
        }

        return when {
            hangul >= 2 -> RomanizationScript.KOREAN_HANGUL
            kana >= 2 || (kana > 0 && cjk > 0) -> RomanizationScript.JAPANESE_ROMAJI
            arabic >= 2 -> RomanizationScript.URDU_ARABIC
            devanagari >= 2 -> RomanizationScript.HINDI_DEVANAGARI
            cyrillic >= 3 -> RomanizationScript.CYRILLIC
            cjk >= 3 -> RomanizationScript.CHINESE_PINYIN
            else -> RomanizationScript.NONE
        }
    }
}
