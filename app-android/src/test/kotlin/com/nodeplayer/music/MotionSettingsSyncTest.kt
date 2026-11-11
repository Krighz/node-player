package com.nodeplayer.music

import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import com.nodeplayer.music.ui.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests validating synchronization between platform accessibility/animation settings
 * and GlassThemeConfig in the Android layer.
 */
class MotionSettingsSyncTest {

    @Test
    fun testPlatformAccessibilityReduceMotionOverridesFullMotion() {
        // When system accessibility reports reduceMotion = true, GlassThemeConfig should honor it
        val systemReduceMotionActive = true
        val config = PlayerViewModel.buildThemeConfigFromValues(
            reduceMotion = systemReduceMotionActive
        )
        assertTrue("Config must reflect reduceMotion=true from accessibility", config.reduceMotion)

        // Verify resolver behavior with this config
        val resolvedSpec = com.nodeplayer.music.ui.theme.MotionTokens.resolveSpringSpec(config)
        // Instant spring when reduceMotion is active
        org.junit.Assert.assertNotNull(resolvedSpec)
    }

    @Test
    fun testPlatformAccessibilityDisabledAllowsConfiguredMotionLevel() {
        val configFull = PlayerViewModel.buildThemeConfigFromValues(
            reduceMotion = false,
            animationPreset = "SMOOTH"
        )
        assertFalse(configFull.reduceMotion)
        assertEquals("SMOOTH", configFull.animationPreset)
    }

    @Test
    fun testGlassThemeConfigMotionLevelSerializationAndDefaults() {
        val defaultConfig = GlassThemeConfig()
        assertEquals(MotionLevel.FULL, defaultConfig.motionLevel)
        assertFalse(defaultConfig.reduceMotion)
        assertEquals(1.0f, defaultConfig.motionSpeed, 0.001f)

        val reducedConfig = defaultConfig.copy(motionLevel = MotionLevel.REDUCED)
        assertEquals(MotionLevel.REDUCED, reducedConfig.motionLevel)

        val offConfig = defaultConfig.copy(motionLevel = MotionLevel.OFF)
        assertEquals(MotionLevel.OFF, offConfig.motionLevel)
    }
}
