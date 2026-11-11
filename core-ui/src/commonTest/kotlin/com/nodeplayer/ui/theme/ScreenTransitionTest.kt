package com.nodeplayer.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenTransitionTest {

    @Test
    fun testMotionDisabledDetection() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, reduceMotion = false)
        assertFalse(ScreenTransitions.isMotionDisabled(fullConfig))

        val reducedConfig = GlassThemeConfig(motionLevel = MotionLevel.REDUCED, reduceMotion = false)
        assertFalse(ScreenTransitions.isMotionDisabled(reducedConfig))

        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        assertTrue(ScreenTransitions.isMotionDisabled(offConfig))

        val reduceMotionConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, reduceMotion = true)
        assertTrue(ScreenTransitions.isMotionDisabled(reduceMotionConfig))

        val presetOffConfig = GlassThemeConfig(animationPreset = "OFF")
        assertTrue(ScreenTransitions.isMotionDisabled(presetOffConfig))

        val perfModeConfig = GlassThemeConfig(isPerformanceMode = true)
        assertTrue(ScreenTransitions.isMotionDisabled(perfModeConfig))
    }

    @Test
    fun testResolveTweenDuration() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 1.0f)
        val tweenFull = ScreenTransitions.resolveTween<Float>(fullConfig, duration = 300)
        assertEquals(300, tweenFull.durationMillis)

        val fastConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL, motionSpeed = 2.0f)
        val tweenFast = ScreenTransitions.resolveTween<Float>(fastConfig, duration = 300)
        assertEquals(150, tweenFast.durationMillis)

        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        val tweenOff = ScreenTransitions.resolveTween<Float>(offConfig, duration = 300)
        assertEquals(0, tweenOff.durationMillis)
        assertEquals(0, tweenOff.delay)
    }

    @Test
    fun testFadeThroughTransitionNonNull() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL)
        val transformFull = ScreenTransitions.fadeThrough(fullConfig)
        assertNotNull(transformFull)

        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        val transformOff = ScreenTransitions.fadeThrough(offConfig)
        assertNotNull(transformOff)

        val reducedConfig = GlassThemeConfig(motionLevel = MotionLevel.REDUCED)
        val transformReduced = ScreenTransitions.fadeThrough(reducedConfig)
        assertNotNull(transformReduced)
    }

    @Test
    fun testSharedAxisTransitionsNonNull() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL)

        val forwardX = ScreenTransitions.sharedAxisX(TransitionDirection.FORWARD, fullConfig)
        assertNotNull(forwardX)

        val backwardX = ScreenTransitions.sharedAxisX(TransitionDirection.BACKWARD, fullConfig)
        assertNotNull(backwardX)

        val noneX = ScreenTransitions.sharedAxisX(TransitionDirection.NONE, fullConfig)
        assertNotNull(noneX)

        val forwardY = ScreenTransitions.sharedAxisY(TransitionDirection.FORWARD, fullConfig)
        assertNotNull(forwardY)

        val backwardY = ScreenTransitions.sharedAxisY(TransitionDirection.BACKWARD, fullConfig)
        assertNotNull(backwardY)
    }

    @Test
    fun testSheetTransitions() {
        val fullConfig = GlassThemeConfig(motionLevel = MotionLevel.FULL)
        val enterFull = ScreenTransitions.sheetEnter(fullConfig)
        val exitFull = ScreenTransitions.sheetExit(fullConfig)
        assertNotNull(enterFull)
        assertNotNull(exitFull)

        val offConfig = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        val enterOff = ScreenTransitions.sheetEnter(offConfig)
        val exitOff = ScreenTransitions.sheetExit(offConfig)
        assertNotNull(enterOff)
        assertNotNull(exitOff)

        val springSpec = ScreenTransitions.sheetSpring(fullConfig)
        assertNotNull(springSpec)
    }
}
