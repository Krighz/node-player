package com.nodeplayer.ui.components

import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassCustomizationConfigTest {

    @Test
    fun testDefaultGlassCustomizationValues() {
        val config = GlassThemeConfig()

        assertTrue(config.dockGlassLensEnabled)
        assertTrue(config.dockGlassRimEnabled)
        assertTrue(config.dockGlassPuckEnabled)
        assertTrue(config.miniPlayerGlassEnabled)
        assertTrue(config.nowPlayingGlassEnabled)
        assertFalse(config.controlsGlassEnabled) // Plain-compose controls by default
        assertTrue(config.widgetsGlassEnabled)

        assertEquals(18.0f, config.glassRefractionHeightDp)
        assertEquals(16.0f, config.glassRefractionAmountDp)
        assertEquals(0.25f, config.glassChromaticFringe)
        assertEquals(0.50f, config.glassBackdropScale)
    }

    @Test
    fun testResetToRecommendedPreset() {
        val obsidian = GlassThemeConfig.PRESET_OBSIDIAN_LIQUID
        assertEquals(GlassQuality.BALANCED, obsidian.glassQuality)
        assertEquals(MotionLevel.FULL, obsidian.motionLevel)
        assertEquals(0.85f, obsidian.lensRefractionStrength)
    }
}
