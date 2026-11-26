package com.nodeplayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LyricsSettingsTest {

    @Test
    fun testDefaultLyricsSettings() {
        val settings = LyricsSettings()
        assertEquals(LyricsStyle.CINEMATIC_KINETIC, settings.style)
        assertEquals(1.00f, settings.commonZoom)
        assertEquals(0.35f, settings.sparkleDensity)
        assertEquals(0.80f, settings.lrSwing)
        assertEquals(1.15f, settings.verticalSpacing)
        assertEquals(1.00f, settings.cameraGlide)
        assertEquals(1.00f, settings.bounce)
        assertEquals(LyricsGlowPalette.CINEMA_WHITE, settings.palette)
        assertEquals(true, settings.showTopBar)
        assertEquals(LyricsProviderChoice.AUTO, settings.preferredProvider)
        assertEquals(LyricsFontChoice.INTER, settings.fontChoice)
        assertEquals(1.00f, settings.fontSizeScale)
        assertEquals(LyricsBaseColor.PURE_WHITE, settings.baseColor)
        assertEquals(1.00f, settings.glowIntensity)
        assertEquals(0.40f, settings.inactiveOpacity)
        assertEquals(true, settings.isBold)
    }

    @Test
    fun testGlowPalettes() {
        assertEquals(4, LyricsGlowPalette.entries.size)
        assertEquals(LyricsGlowPalette.CINEMA_WHITE, LyricsGlowPalette.fromId("white"))
        assertEquals(LyricsGlowPalette.GLACIAL_BLUE, LyricsGlowPalette.fromId("blue"))
        assertEquals(LyricsGlowPalette.WARM_GOLD, LyricsGlowPalette.fromId("gold"))
        assertEquals(LyricsGlowPalette.LUNAR_VIOLET, LyricsGlowPalette.fromId("violet"))
        assertEquals(LyricsGlowPalette.CINEMA_WHITE, LyricsGlowPalette.fromId("unknown"))
    }

    @Test
    fun testLyricsFontChoices() {
        assertEquals(14, LyricsFontChoice.entries.size)
        assertEquals(LyricsFontChoice.PAPYRUS, LyricsFontChoice.fromId("papyrus"))
        assertEquals(LyricsFontChoice.MINECRAFT, LyricsFontChoice.fromId("minecraft"))
        assertEquals(LyricsFontChoice.PATRICK_HAND, LyricsFontChoice.fromId("patrick_hand"))
        assertEquals(LyricsFontChoice.INTER, LyricsFontChoice.fromId("unknown"))
    }

    @Test
    fun testLyricsBaseColors() {
        assertEquals(8, LyricsBaseColor.entries.size)
        assertEquals(LyricsBaseColor.PURE_WHITE, LyricsBaseColor.fromId("white"))
        assertEquals(LyricsBaseColor.AMBER_GOLD, LyricsBaseColor.fromId("amber"))
        assertEquals(LyricsBaseColor.PURE_WHITE, LyricsBaseColor.fromId("unknown"))
    }

    @Test
    fun testLyricsProviderChoices() {
        assertEquals(10, LyricsProviderChoice.entries.size)
        assertEquals(LyricsProviderChoice.AUTO, LyricsProviderChoice.fromId("auto"))
        assertEquals(LyricsProviderChoice.BINI, LyricsProviderChoice.fromId("bini"))
        assertEquals(LyricsProviderChoice.LRCLIB, LyricsProviderChoice.fromId("lrclib"))
        assertEquals(LyricsProviderChoice.BETTER_LYRICS, LyricsProviderChoice.fromId("betterlyrics"))
        assertEquals(LyricsProviderChoice.LYRICS_PLUS, LyricsProviderChoice.fromId("lyricsplus"))
        assertEquals(LyricsProviderChoice.KUGOU, LyricsProviderChoice.fromId("kugou"))
        assertEquals(LyricsProviderChoice.LOCAL, LyricsProviderChoice.fromId("local"))
        assertEquals(LyricsProviderChoice.AUTO, LyricsProviderChoice.fromId("unknown"))
    }
}
