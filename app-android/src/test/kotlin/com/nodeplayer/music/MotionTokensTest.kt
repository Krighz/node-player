package com.nodeplayer.music

import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.ui.theme.MotionTokens
import org.junit.Assert.assertNotNull
import org.junit.Test

class MotionTokensTest {

    @Test
    fun testMotionTokensDefinitions() {
        assertNotNull(MotionTokens.Snappy)
        assertNotNull(MotionTokens.Smooth)
        assertNotNull(MotionTokens.Bouncy)
        assertNotNull(MotionTokens.Gentle)
    }

    @Test
    fun testResolveSpringSpec() {
        val configSmooth = GlassThemeConfig(animationPreset = "SMOOTH", motionSpeed = 1.0f)
        assertNotNull(MotionTokens.resolveSpringSpec(configSmooth))

        val configBouncy = GlassThemeConfig(animationPreset = "BOUNCY", motionSpeed = 1.5f)
        assertNotNull(MotionTokens.resolveSpringSpec(configBouncy))

        val configCustom = GlassThemeConfig(
            animationPreset = "CUSTOM",
            motionSpeed = 1.2f,
            springBounciness = 0.60f,
            springStiffness = 500f
        )
        assertNotNull(MotionTokens.resolveSpringSpec(configCustom))

        val configReduced = GlassThemeConfig(reduceMotion = true)
        assertNotNull(MotionTokens.resolveSpringSpec(configReduced))

        val configOff = GlassThemeConfig(animationPreset = "OFF")
        assertNotNull(MotionTokens.resolveSpringSpec(configOff))
    }
}
