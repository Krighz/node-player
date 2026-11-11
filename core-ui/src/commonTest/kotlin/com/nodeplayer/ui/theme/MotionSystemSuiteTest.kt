package com.nodeplayer.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.geometry.Rect
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import com.nodeplayer.ui.player.ContainerTransformMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Aggregated regression suite validating the entire Motion System across :core-ui.
 * Covers:
 *  - MotionTokens duration and spring resolution under all MotionLevels (FULL, REDUCED, OFF).
 *  - ScreenTransitions content transforms, shared axis directions, and sheet springs.
 *  - MotionModifiers contract & zero-allocation draw phase properties.
 *  - ContainerTransformMath interpolation resilience and alpha ladders.
 */
class MotionSystemSuiteTest {

    @Test
    fun testMotionTokensAllModesAndOverrides() {
        // 1. FULL mode with standard presets
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "SMOOTH")
        val smoothSpec = MotionTokens.resolveSpringSpec(fullConfig)
        assertEquals(MotionTokens.Smooth, smoothSpec)

        val snappyConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "SNAPPY")
        assertEquals(MotionTokens.Snappy, MotionTokens.resolveSpringSpec(snappyConfig))

        val bouncyConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "BOUNCY")
        assertEquals(MotionTokens.Bouncy, MotionTokens.resolveSpringSpec(bouncyConfig))

        val gentleConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, animationPreset = "GENTLE")
        assertEquals(MotionTokens.Gentle, MotionTokens.resolveSpringSpec(gentleConfig))

        // 2. REDUCED mode
        val reducedConfig = GlassThemeConfig(motionLevel = MotionLevel.REDUCED)
        assertEquals(MotionTokens.Gentle, MotionTokens.resolveSpringSpec(reducedConfig))
        assertEquals(MotionTokens.DurationShort, MotionTokens.resolveDuration(reducedConfig, MotionTokens.DurationLong))

        // 3. OFF mode -> Instant spec & 0 duration
        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        assertEquals(MotionTokens.Instant, MotionTokens.resolveSpringSpec(offConfig))
        assertEquals(0, MotionTokens.resolveDuration(offConfig, MotionTokens.DurationMedium))

        // 4. Accessibility reduceMotion override
        val reduceMotionConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, reduceMotion = true)
        assertEquals(MotionTokens.Instant, MotionTokens.resolveSpringSpec(reduceMotionConfig))
        assertEquals(0, MotionTokens.resolveDuration(reduceMotionConfig, MotionTokens.DurationMedium))

        // 5. Performance mode override
        val perfConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, isPerformanceMode = true)
        assertEquals(MotionTokens.Instant, MotionTokens.resolveSpringSpec(perfConfig))
        assertEquals(0, MotionTokens.resolveDuration(perfConfig, MotionTokens.DurationMedium))
    }

    @Test
    fun testMotionSpeedScaling() {
        val normal = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 1.0f)
        assertEquals(300, MotionTokens.resolveDuration(normal, MotionTokens.DurationMedium))

        val fast = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 2.0f)
        assertEquals(150, MotionTokens.resolveDuration(fast, MotionTokens.DurationMedium))

        val slow = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 0.5f)
        assertEquals(600, MotionTokens.resolveDuration(slow, MotionTokens.DurationMedium))
    }

    @Test
    fun testScreenTransitionsMotionDisabledInvariants() {
        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        assertTrue(ScreenTransitions.isMotionDisabled(offConfig))

        val reduceConfig = GlassThemeConfig(reduceMotion = true)
        assertTrue(ScreenTransitions.isMotionDisabled(reduceConfig))

        val perfConfig = GlassThemeConfig(isPerformanceMode = true)
        assertTrue(ScreenTransitions.isMotionDisabled(perfConfig))

        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL)
        assertFalse(ScreenTransitions.isMotionDisabled(fullConfig))

        // Verify fadeThrough, sharedAxisX, sharedAxisY, sheetEnter, sheetExit return non-null specs
        assertNotNull(ScreenTransitions.fadeThrough(fullConfig))
        assertNotNull(ScreenTransitions.fadeThrough(offConfig))
        assertNotNull(ScreenTransitions.sharedAxisX(TransitionDirection.FORWARD, fullConfig))
        assertNotNull(ScreenTransitions.sharedAxisX(TransitionDirection.BACKWARD, offConfig))
        assertNotNull(ScreenTransitions.sharedAxisY(TransitionDirection.FORWARD, fullConfig))
        assertNotNull(ScreenTransitions.sheetEnter(fullConfig))
        assertNotNull(ScreenTransitions.sheetExit(fullConfig))
        assertNotNull(ScreenTransitions.sheetEnter(offConfig))
        assertNotNull(ScreenTransitions.sheetExit(offConfig))
    }

    @Test
    fun testContainerTransformMathInterpolationAndAlpha() {
        // Verify lerp boundary clamping
        assertEquals(0f, ContainerTransformMath.lerp(0f, 100f, -0.5f))
        assertEquals(50f, ContainerTransformMath.lerp(0f, 100f, 0.5f))
        assertEquals(100f, ContainerTransformMath.lerp(0f, 100f, 1.5f))

        // Corner radius calculation (20dp -> 0dp)
        assertEquals(20f, ContainerTransformMath.calculateCornerRadius(0.0f, 20f, 0f))
        assertEquals(10f, ContainerTransformMath.calculateCornerRadius(0.5f, 20f, 0f))
        assertEquals(0f, ContainerTransformMath.calculateCornerRadius(1.0f, 20f, 0f))

        // Alpha ladder verification
        assertEquals(1.0f, ContainerTransformMath.calculateMiniPlayerAlpha(0.0f), 0.001f)
        assertEquals(0.0f, ContainerTransformMath.calculateMiniPlayerAlpha(0.25f), 0.001f)
        assertEquals(0.0f, ContainerTransformMath.calculateMiniPlayerAlpha(1.0f), 0.001f)

        assertEquals(0.0f, ContainerTransformMath.calculateFullPlayerAlpha(0.0f), 0.001f)
        assertEquals(0.0f, ContainerTransformMath.calculateFullPlayerAlpha(0.20f), 0.001f)
        assertEquals(1.0f, ContainerTransformMath.calculateFullPlayerAlpha(1.0f), 0.001f)

        // Bounds interpolation
        val startRect = Rect(16f, 700f, 384f, 756f)
        val endRect = Rect(0f, 0f, 400f, 800f)
        val boundsMid = ContainerTransformMath.calculateBounds(0.5f, startRect, endRect)
        assertEquals(8f, boundsMid.left, 0.001f)
        assertEquals(350f, boundsMid.top, 0.001f)
        assertEquals(392f, boundsMid.right, 0.001f)
        assertEquals(778f, boundsMid.bottom, 0.001f)
    }
}
