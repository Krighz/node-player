package com.nodeplayer.library.lyrics

import com.nodeplayer.library.lyrics.romanization.ScriptDetector
import com.nodeplayer.library.lyrics.romanization.UrduMusicalLexicon
import com.nodeplayer.model.RomanizationScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectorTest {
    @Test
    fun testDetectHangul() {
        assertEquals(RomanizationScript.KOREAN_HANGUL, ScriptDetector.detect("사랑해 보고 싶어"))
    }

    @Test
    fun testDetectJapaneseKana() {
        assertEquals(RomanizationScript.JAPANESE_ROMAJI, ScriptDetector.detect("残酷な天使のテーゼ"))
    }

    @Test
    fun testDetectCyrillic() {
        assertEquals(RomanizationScript.CYRILLIC, ScriptDetector.detect("Привет как дела"))
    }

    @Test
    fun testDetectUrdu() {
        assertEquals(RomanizationScript.URDU_ARABIC, ScriptDetector.detect("دل میں تو ہے محبت"))
    }

    @Test
    fun testDetectDevanagari() {
        assertEquals(RomanizationScript.HINDI_DEVANAGARI, ScriptDetector.detect("तुझे देखा तो ये जाना सनम"))
    }

    @Test
    fun testDetectChinese() {
        assertEquals(RomanizationScript.CHINESE_PINYIN, ScriptDetector.detect("我只在乎你月亮代表我的心"))
    }

    @Test
    fun testDetectEnglishAndSpanishReturnsNone() {
        assertEquals(RomanizationScript.NONE, ScriptDetector.detect("Despacito quiero respirar"))
        assertEquals(RomanizationScript.NONE, ScriptDetector.detect("Hello world I love music"))
    }

    @Test
    fun testUrduMusicalLexiconPoeticDictionary() {
        val preprocessed = UrduMusicalLexicon.preprocessUrdu("دل محبت زندگی")
        assertTrue(preprocessed.contains("Dil"))
        assertTrue(preprocessed.contains("Mohabbat"))
        assertTrue(preprocessed.contains("Zindagi"))
    }
}
