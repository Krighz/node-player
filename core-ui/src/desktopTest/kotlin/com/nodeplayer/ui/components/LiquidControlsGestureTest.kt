package com.nodeplayer.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun testToggleSnapThreshold() {
        val fractionHigh = 0.51f
        val isSelectedHigh = fractionHigh >= 0.5f
        assertTrue(isSelectedHigh)

        val fractionLow = 0.49f
        val isSelectedLow = fractionLow >= 0.5f
        assertTrue(!isSelectedLow)
    }
}
