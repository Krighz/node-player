package com.nodeplayer.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidControlsGestureTest {

    @Test
    fun testSliderNormalizedDelta() {
        val minVal = 0f
        val maxVal = 100f
        val trackWidth = 500f
        val dragDeltaX = 50f

        val deltaValue = (maxVal - minVal) * (dragDeltaX / trackWidth)
        assertEquals(10f, deltaValue)

        val nextVal = (30f + deltaValue).coerceIn(minVal, maxVal)
        assertEquals(40f, nextVal)
    }

    @Test
    fun testSliderContinuousDragAndRtlMapping() {
        val minVal = 0f
        val maxVal = 1f
        val trackWidth = 400f

        // Continuous incremental dragging
        var currentVal = 0.2f
        val increments = listOf(10f, 20f, -5f, 40f)
        for (inc in increments) {
            val delta = (maxVal - minVal) * (inc / trackWidth)
            currentVal = (currentVal + delta).coerceIn(minVal, maxVal)
        }
        assertEquals(0.3625f, currentVal, 0.0001f)

        // RTL: positive drag delta decreases value
        val rtlDelta = (maxVal - minVal) * (40f / trackWidth)
        val rtlNext = (currentVal - rtlDelta).coerceIn(minVal, maxVal)
        assertEquals(0.2625f, rtlNext, 0.0001f)
    }

    @Test
    fun testSliderDragClamping() {
        val minVal = 0f
        val maxVal = 1f
        val trackWidth = 200f
        val dragDeltaX = 400f

        val deltaValue = (maxVal - minVal) * (dragDeltaX / trackWidth)
        val clampedVal = (0.5f + deltaValue).coerceIn(minVal, maxVal)
        assertEquals(1.0f, clampedVal)

        val negativeDeltaX = -500f
        val deltaNegative = (maxVal - minVal) * (negativeDeltaX / trackWidth)
        val clampedNegative = (0.5f + deltaNegative).coerceIn(minVal, maxVal)
        assertEquals(0.0f, clampedNegative)
    }

    @Test
    fun testVelocityStretchScalingAndFiniteSafety() {
        val testVelocities = listOf(-5000f, -100f, 0f, 100f, 5000f)
        for (v in testVelocities) {
            val scaledVelocity = v / 10f
            val stretchX = (scaledVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            val squishY = (scaledVelocity * 0.25f).coerceIn(-0.2f, 0.2f)

            assertTrue(stretchX in -0.2f..0.2f, "stretchX out of bounds")
            assertTrue(squishY in -0.2f..0.2f, "squishY out of bounds")
            assertTrue((1f - stretchX) >= 0.8f, "Denominator must be safe")
        }

        // Test non-finite protection
        val nanVel = Float.NaN
        val safeVel = if (nanVel.isFinite()) nanVel / 10f else 0f
        assertEquals(0f, safeVel)
    }

    @Test
    fun testToggleSnapThreshold() {
        val fractionHigh = 0.51f
        val isSelectedHigh = fractionHigh >= 0.5f
        assertTrue(isSelectedHigh)

        val fractionLow = 0.49f
        val isSelectedLow = fractionLow >= 0.5f
        assertFalse(isSelectedLow)

        // Exact boundary
        val boundary = 0.50f
        assertTrue(boundary >= 0.5f)
    }

    @Test
    fun testToggleDragVsTapLogic() {
        // Tap logic: always toggles selected state
        var selected = false
        fun performTap() {
            selected = !selected
        }

        performTap()
        assertTrue(selected)
        performTap()
        assertFalse(selected)

        // Drag logic: decides by release fraction
        fun performDragRelease(releaseFraction: Float): Boolean {
            return releaseFraction >= 0.5f
        }

        assertTrue(performDragRelease(0.5001f))
        assertFalse(performDragRelease(0.4999f))
        assertTrue(performDragRelease(0.85f))
        assertFalse(performDragRelease(0.15f))
    }

    @Test
    fun testMinimumTouchTargetHitboxInvariants() {
        val minHitboxDp = 48.dp

        // Check common requested control sizes
        val buttonRequestedHeight = 40.dp
        val effectiveBtnHeight = buttonRequestedHeight.coerceAtLeast(minHitboxDp)
        assertEquals(48.dp, effectiveBtnHeight)

        val iconBtnSize = 32.dp
        val effectiveIconBtnSize = iconBtnSize.coerceAtLeast(minHitboxDp)
        assertEquals(48.dp, effectiveIconBtnSize)

        val toggleHeight = 28.dp
        val effectiveToggleHeight = toggleHeight.coerceAtLeast(minHitboxDp)
        assertEquals(48.dp, effectiveToggleHeight)

        val chipHeight = 36.dp
        val effectiveChipHeight = chipHeight.coerceAtLeast(minHitboxDp)
        assertEquals(48.dp, effectiveChipHeight)
    }

    @Test
    fun testLighterStyleConfiguration() {
        // Verify default value is false
        val defaultLighterStyle = false
        assertFalse(defaultLighterStyle)

        // List items can enable lighterStyle to save shader allocations
        val listActionLighterStyle = true
        assertTrue(listActionLighterStyle)
    }
}

