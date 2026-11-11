package com.nodeplayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceConfigTest {

    @Test
    fun testDefaultDockConfig() {
        val dockConfig = DockConfig()
        assertEquals(listOf("HOME", "EXPLORE", "LIBRARY"), dockConfig.tabOrder)
        assertTrue(dockConfig.showSearchButton)
        assertEquals(1.0f, dockConfig.collapseSensitivity)
        assertFalse(dockConfig.autoHideMiniPlayer)
    }

    @Test
    fun testDefaultHomeLayoutConfig() {
        val homeConfig = HomeLayoutConfig()
        assertEquals(5, homeConfig.shelfOrder.size)
        assertEquals("QUICK_PICKS", homeConfig.shelfOrder[0])
        assertTrue(homeConfig.hiddenShelves.isEmpty())
        assertTrue(homeConfig.cardAspectWide)
        assertEquals(null, homeConfig.regionOverride)
    }

    @Test
    fun testDefaultLyricsViewConfig() {
        val lyricsConfig = LyricsViewConfig()
        assertEquals(28.0f, lyricsConfig.fontSizeSp)
        assertEquals(4.0f, lyricsConfig.blurRadiusDp)
        assertEquals(5, lyricsConfig.autoHideDelaySec)
        assertTrue(lyricsConfig.keepScreenOn)
    }

    @Test
    fun testGlassThemeConfigNestingAndCopy() {
        val theme = GlassThemeConfig()
        assertEquals(28.0f, theme.lyricsViewConfig.fontSizeSp)
        assertTrue(theme.dockConfig.showSearchButton)

        val updated = theme.copy(
            lyricsViewConfig = theme.lyricsViewConfig.copy(fontSizeSp = 32.0f),
            dockConfig = theme.dockConfig.copy(collapseSensitivity = 1.5f)
        )
        assertEquals(32.0f, updated.lyricsViewConfig.fontSizeSp)
        assertEquals(1.5f, updated.dockConfig.collapseSensitivity)
    }
}
