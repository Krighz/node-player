package com.nodeplayer.common.platform

import com.nodeplayer.model.RomanizationScript

interface LyricsTransliterator {
    fun transliterate(text: String, script: RomanizationScript): String
}

object PlatformTransliteratorRegistry {
    @Volatile
    var instance: LyricsTransliterator? = null
}
