package com.nodeplayer.ui.theme

import androidx.compose.ui.Modifier
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertNotNull

class MotionModifiersTest {

    @Test
    fun testPressScaleReturnsValidModifier() {
        val modifier = Modifier.pressScale(targetScale = 0.95f) { }
        assertNotNull(modifier)
    }

    @Test
    fun testInteractiveScaleReturnsValidModifier() {
        val interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
        val modifier = Modifier.interactiveScale(interactionSource, targetScale = 0.92f)
        assertNotNull(modifier)
    }

    @Test
    fun testFadeInOnLoadReturnsValidModifier() {
        val modifier = Modifier.fadeInOnLoad(key = "test_key", durationMillis = 250)
        assertNotNull(modifier)
    }

    @Test
    fun testStaggeredEntranceReturnsValidModifier() {
        val modifierNormal = Modifier.staggeredEntrance(index = 2, maxIndex = 12)
        assertNotNull(modifierNormal)

        val modifierClamped = Modifier.staggeredEntrance(index = 20, maxIndex = 12)
        assertNotNull(modifierClamped)
    }

    @Test
    fun testReducedMotionTokensDirectInspection() {
        val configReduced = GlassThemeConfig(motionLevel = MotionLevel.REDUCED)
        val duration = MotionTokens.resolveDuration(configReduced, MotionTokens.DurationLong)
        kotlin.test.assertEquals(MotionTokens.DurationShort, duration)

        val configOff = GlassThemeConfig(motionLevel = MotionLevel.OFF)
        val durationOff = MotionTokens.resolveDuration(configOff, MotionTokens.DurationLong)
        kotlin.test.assertEquals(0, durationOff)
    }
}
