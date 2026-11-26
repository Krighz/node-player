package com.nodeplayer.ui.lyrics

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.nodeplayer.model.LyricsFontChoice

/**
 * Fallback font family resolver for Compose Multiplatform.
 * When a platform (e.g. Android or Desktop) registers specific custom font files,
 * it provides this composition local to override default typography mappings.
 */
fun defaultLyricsFontFamily(choice: LyricsFontChoice): FontFamily {
    return when (choice) {
        LyricsFontChoice.INTER -> FontFamily.SansSerif
        LyricsFontChoice.OUTFIT -> FontFamily.SansSerif
        LyricsFontChoice.JETBRAINS_MONO -> FontFamily.Monospace
        LyricsFontChoice.PAPYRUS -> FontFamily.Cursive
        LyricsFontChoice.MINECRAFT -> FontFamily.Monospace
        LyricsFontChoice.PATRICK_HAND -> FontFamily.Cursive
        LyricsFontChoice.COMFORTAA -> FontFamily.SansSerif
        LyricsFontChoice.CAVEAT -> FontFamily.Cursive
        LyricsFontChoice.LOBSTER -> FontFamily.Cursive
        LyricsFontChoice.BEBAS_NEUE -> FontFamily.SansSerif
        LyricsFontChoice.ORBITRON -> FontFamily.SansSerif
        LyricsFontChoice.PLAYFAIR -> FontFamily.Serif
        LyricsFontChoice.DANCING_SCRIPT -> FontFamily.Cursive
        LyricsFontChoice.CINZEL -> FontFamily.Serif
    }
}

val LocalLyricsFontResolver = compositionLocalOf<(LyricsFontChoice) -> FontFamily> {
    { choice -> defaultLyricsFontFamily(choice) }
}
