package com.nodeplayer.ui.theme

import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MotionTokensTest {

    @Test
    fun testMotionTokensFullMode() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.FULL)
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.ConvxPuck, spec)
    }

    @Test
    fun testMotionTokensReducedMode() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.REDUCED)
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Gentle, spec)
    }

    @Test
    fun testMotionTokensOffMode() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Instant, spec)
    }

    @Test
    fun testMotionTokensReduceMotionSystemAccessibility() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.FULL, reduceMotion = true)
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Instant, spec)
    }
}
