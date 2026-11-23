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
    fun testLyricsProviderChoices() {
        assertEquals(7, LyricsProviderChoice.entries.size)
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
