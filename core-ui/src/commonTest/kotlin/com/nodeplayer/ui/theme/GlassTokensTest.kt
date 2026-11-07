package com.nodeplayer.ui.theme

import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassTokensTest {

    @Test
    fun testGlassTokensResolutionFullQuality() {
        val config = GlassThemeConfig(glassQuality = GlassQuality.FULL)
        val tokens = GlassDesignTokens.resolve(config, isDark = true)

        assertTrue(tokens.refractionHeightDock.value > 0f)
        assertTrue(tokens.refractionAmountDock.value > 0f)
        assertEquals(0.30f, tokens.chromaticDispersionAmount)
        assertEquals(0.90f, tokens.rimSpecularAlpha)
    }

    @Test
    fun testGlassTokensResolutionBalancedQuality() {
        val config = GlassThemeConfig(glassQuality = GlassQuality.BALANCED)
        val tokens = GlassDesignTokens.resolve(config, isDark = true)

        assertTrue(tokens.refractionHeightDock.value > 0f)
        assertTrue(tokens.refractionAmountDock.value > 0f)
        assertEquals(0.15f, tokens.chromaticDispersionAmount)
        assertEquals(0.80f, tokens.rimSpecularAlpha)
    }

    @Test
    fun testGlassTokensResolutionLightQuality() {
        val config = GlassThemeConfig(glassQuality = GlassQuality.LIGHT)
        val tokens = GlassDesignTokens.resolve(config, isDark = true)

        // Light mode should have 0 refraction and 0 dispersion (plain Compose fallback)
        assertEquals(0f, tokens.refractionHeightDock.value)
        assertEquals(0f, tokens.refractionAmountDock.value)
        assertEquals(0f, tokens.chromaticDispersionAmount)
        assertEquals(0f, tokens.blurRadius.value)
        assertEquals(0.40f, tokens.rimSpecularAlpha)
    }
}
