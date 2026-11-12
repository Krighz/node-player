package com.nodeplayer.ui.components

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidVerticalSliderMathTest {

    @Test
    fun testVerticalDragDeltaInversion() {
        val valueRange = -12f..12f
        val rangeSpan = valueRange.endInclusive - valueRange.start // 24f
        val trackHeightPx = 500f

        // Upward drag is negative Y delta in screen coordinates
        val dragAmountY = -50f
        val rawDelta = -rangeSpan * (dragAmountY / trackHeightPx)
        assertEquals(2.4f, rawDelta)

        // Downward drag is positive Y delta in screen coordinates
        val downwardDragY = 50f
        val downDelta = -rangeSpan * (downwardDragY / trackHeightPx)
        assertEquals(-2.4f, downDelta)
    }

    @Test
    fun testClampingWithinValueRange() {
        val valueRange = -12f..12f
        val currentValue = 10f

        // Exceeding upper bound
        val largeBoost = 5f
        val clampedUpper = (currentValue + largeBoost).coerceIn(valueRange)
        assertEquals(12f, clampedUpper)

        // Exceeding lower bound
        val largeCut = -25f
        val clampedLower = (currentValue + largeCut).coerceIn(valueRange)
        assertEquals(-12f, clampedLower)
    }

    @Test
    fun testCenterZeroNotchFractionCalculation() {
        val valueRange = -12f..12f
        val rangeSpan = valueRange.endInclusive - valueRange.start
        val zeroFraction = (0f - valueRange.start) / rangeSpan
        assertEquals(0.5f, zeroFraction, "0 dB must be exactly in the center of -12dB..+12dB")

        // Asymmetric range test: 0dB to +24dB
        val asymRange = 0f..24f
        val asymSpan = asymRange.endInclusive - asymRange.start
        val asymZeroFraction = (0f - asymRange.start) / asymSpan
        assertEquals(0f, asymZeroFraction, "0 dB must be at bottom for 0..24dB")
    }

    @Test
    fun testBipolarFillHeightCalculation() {
        val totalH = 600f
        val zeroFraction = 0.5f
        val zeroY = totalH * (1f - zeroFraction) // 300f

        // Case 1: Boost to +6 dB (progress 0.75)
        val boostProgress = 0.75f
        val currentY = totalH * (1f - boostProgress) // 150f
        val fillTop = minOf(zeroY, currentY).roundToInt()
        val fillBottom = maxOf(zeroY, currentY).roundToInt()
        val fillHeight = fillBottom - fillTop
        assertEquals(150, fillTop)
        assertEquals(300, fillBottom)
        assertEquals(150, fillHeight)

        // Case 2: Cut to -6 dB (progress 0.25)
        val cutProgress = 0.25f
        val cutY = totalH * (1f - cutProgress) // 450f
        val cutFillTop = minOf(zeroY, cutY).roundToInt()
        val cutFillBottom = maxOf(zeroY, cutY).roundToInt()
        val cutFillHeight = cutFillBottom - cutFillTop
        assertEquals(300, cutFillTop)
        assertEquals(450, cutFillBottom)
        assertEquals(150, cutFillHeight)
    }

    @Test
    fun testZeroHeightDoesNotThrowDivisionByZero() {
        val trackHeightPx = 0f
        val effectiveHeight = trackHeightPx.coerceAtLeast(1f)
        assertEquals(1f, effectiveHeight)

        val dragAmountY = -10f
        val rangeSpan = 24f
        val delta = -rangeSpan * (dragAmountY / effectiveHeight)
        assertTrue(delta.isFinite())
    }
}
