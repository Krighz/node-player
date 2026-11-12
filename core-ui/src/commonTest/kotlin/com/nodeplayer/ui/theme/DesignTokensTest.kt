package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignTokensTest {

    @Test
    fun testLiquidColorsIntegrity() {
        assertNotEquals(Color.Unspecified, LiquidColors.OledBlack)
        assertNotEquals(Color.Unspecified, LiquidColors.SurfaceDark)
        assertNotEquals(Color.Unspecified, LiquidColors.ElectricCyan)
        assertNotEquals(Color.Unspecified, LiquidColors.AmberWarning)
        assertNotEquals(Color.Unspecified, LiquidColors.CoralRed)
        assertNotEquals(Color.Unspecified, LiquidColors.EmeraldGreen)
        assertNotEquals(Color.Unspecified, LiquidColors.GoldHiRes)
        assertNotEquals(Color.Unspecified, LiquidColors.CyanDeep)
        assertNotEquals(Color.Unspecified, LiquidColors.SlateDark)
        assertNotEquals(Color.Unspecified, LiquidColors.SkyIce)

        // Alpha checks
        assertTrue(LiquidColors.DividerSubtle.alpha in 0.01f..0.20f)
        assertTrue(LiquidColors.BorderSubtle.alpha in 0.05f..0.30f)
    }

    @Test
    fun testGlassDesignTokensQualityResolution() {
        val configFull = GlassThemeConfig(glassQuality = GlassQuality.FULL)
        val tokensFull = GlassDesignTokens.resolve(configFull, isDark = true)
        assertEquals(20f, tokensFull.refractionHeightDock.value)
        assertEquals(16f, tokensFull.blurRadius.value)
        assertEquals(0.90f, tokensFull.rimSpecularAlpha)

        val configBalanced = GlassThemeConfig(glassQuality = GlassQuality.BALANCED)
        val tokensBalanced = GlassDesignTokens.resolve(configBalanced, isDark = true)
        assertEquals(16f, tokensBalanced.refractionHeightDock.value)
        assertEquals(12f, tokensBalanced.blurRadius.value)

        val configLight = GlassThemeConfig(glassQuality = GlassQuality.LIGHT)
        val tokensLight = GlassDesignTokens.resolve(configLight, isDark = true)
        assertEquals(0f, tokensLight.refractionHeightDock.value)
        assertEquals(0f, tokensLight.blurRadius.value)
    }

    @Test
    fun testMotionTokensCurves() {
        assertNotNull(MotionTokens.ConvxPuck)
        assertNotNull(MotionTokens.Snappy)
        assertNotNull(MotionTokens.Smooth)
    }
}
