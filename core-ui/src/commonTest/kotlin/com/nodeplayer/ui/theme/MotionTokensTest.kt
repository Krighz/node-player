package com.nodeplayer.ui.theme

import androidx.compose.animation.core.SpringSpec
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTokensTest {

    @Test
    fun testMotionTokensFullModeDefault() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "SMOOTH")
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Smooth, spec)
    }

    @Test
    fun testMotionTokensFullModePresets() {
        val snappyConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "SNAPPY")
        assertEquals(MotionTokens.Snappy, MotionTokens.resolveSpringSpec(snappyConfig))

        val bouncyConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "BOUNCY")
        assertEquals(MotionTokens.Bouncy, MotionTokens.resolveSpringSpec(bouncyConfig))

        val gentleConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "GENTLE")
        assertEquals(MotionTokens.Gentle, MotionTokens.resolveSpringSpec(gentleConfig))
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

    @Test
    fun testMotionTokensPerformanceMode() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.FULL, isPerformanceMode = true)
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Instant, spec)
    }

    @Test
    fun testMotionTokensAnimationPresetOff() {
        val config = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "OFF")
        val spec = MotionTokens.resolveSpringSpec(config, MotionTokens.ConvxPuck)
        assertEquals(MotionTokens.Instant, spec)
    }

    @Test
    fun testMotionTokensCustomPreset() {
        val config = GlassThemeConfig(
            motionLevel = MotionLevel.FULL,
            animationPreset = "CUSTOM",
            springBounciness = 0.55f,
            springStiffness = 520f
        )
        val spec = MotionTokens.resolveSpringSpec(config)
        assertTrue(spec is SpringSpec<Float>)
        assertEquals(0.55f, spec.dampingRatio)
        assertEquals(520f, spec.stiffness)
    }

    @Test
    fun testResolveDuration() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 1.0f)
        assertEquals(300, MotionTokens.resolveDuration(fullConfig, MotionTokens.DurationMedium))
        assertEquals(150, MotionTokens.resolveDuration(fullConfig, MotionTokens.DurationShort))
        assertEquals(500, MotionTokens.resolveDuration(fullConfig, MotionTokens.DurationLong))

        val fastConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 2.0f)
        assertEquals(150, MotionTokens.resolveDuration(fastConfig, MotionTokens.DurationMedium))

        val reducedConfig = GlassThemeConfig(motionLevel = MotionLevel.REDUCED)
        assertEquals(MotionTokens.DurationShort, MotionTokens.resolveDuration(reducedConfig, MotionTokens.DurationLong))

        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        assertEquals(0, MotionTokens.resolveDuration(offConfig, MotionTokens.DurationMedium))

        val reduceMotionConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, reduceMotion = true)
        assertEquals(0, MotionTokens.resolveDuration(reduceMotionConfig, MotionTokens.DurationMedium))
    }
}
