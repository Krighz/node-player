package com.nodeplayer.music

import com.nodeplayer.model.GlassThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassShaderSettingTest {

    @Test
    fun testShaderModeAutoFollowsSelfTest() {
        val config = GlassThemeConfig(shaderMode = "AUTO")
        assertTrue(config.isShaderActive(selfTestPassed = true))
        assertFalse(config.isShaderActive(selfTestPassed = false))
    }

    @Test
    fun testShaderModeFull() {
        val config = GlassThemeConfig(shaderMode = "FULL", edgeHighlightStrength = 0.75f, glassTexture = 0.50f)
        assertTrue(config.isShaderActive(selfTestPassed = true))
        assertTrue(config.isShaderActive(selfTestPassed = false))
        assertEquals(0.75f, config.chromaticFringeValue(), 0.001f)
        assertEquals(0.50f, config.grainStrengthValue(), 0.001f)
    }

    @Test
    fun testShaderModeSimpleDisablesFringeAndGrain() {
        val config = GlassThemeConfig(shaderMode = "SIMPLE", edgeHighlightStrength = 0.8f, glassTexture = 0.6f)
        assertTrue(config.isShaderActive(selfTestPassed = true))
        assertEquals(0.0f, config.chromaticFringeValue(), 0.001f)
        assertEquals(0.0f, config.grainStrengthValue(), 0.001f)
    }

    @Test
    fun testShaderModeBlurOnlyAndOff() {
        val blurOnly = GlassThemeConfig(shaderMode = "BLUR_ONLY")
        assertFalse(blurOnly.isShaderActive(selfTestPassed = true))

        val off = GlassThemeConfig(shaderMode = "OFF")
        assertFalse(off.isShaderActive(selfTestPassed = true))
    }

    @Test
    fun testPerformanceModeAlwaysDisablesShader() {
        val config = GlassThemeConfig(shaderMode = "FULL", isPerformanceMode = true)
        assertFalse(config.isShaderActive(selfTestPassed = true))
    }
}
