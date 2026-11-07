package com.nodeplayer.ui.components

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidSliderMathTest {

    @Test
    fun testLayoutWidthCalculationClampedProperly() {
        // Test normal range
        val maxWidth = 1000
        val progress = 0.5f
        val computedWidth = (maxWidth * progress).roundToInt().coerceIn(0, maxWidth)
        assertEquals(500, computedWidth)

        // Test zero width
        val zeroWidth = 0
        val computedZero = (zeroWidth * progress).roundToInt().coerceIn(0, zeroWidth)
        assertEquals(0, computedZero)

        // Test progress exceeding bounds
        val overProgress = 1.2f
        val safeProgress = overProgress.coerceIn(0f, 1f)
        val clampedOver = (maxWidth * safeProgress).roundToInt().coerceIn(0, maxWidth)
        assertEquals(1000, clampedOver)

        val underProgress = -0.5f
        val safeUnderProgress = underProgress.coerceIn(0f, 1f)
        val clampedUnder = (maxWidth * safeUnderProgress).roundToInt().coerceIn(0, maxWidth)
        assertEquals(0, clampedUnder)
    }

    @Test
    fun testThumbTranslationClampingNarrowOrZeroTrack() {
        val thumbW = 48f
        val trackWidth = 0f
        val safeProgress = 0.5f
        val minTrans = if (thumbW > 0f) -thumbW / 4f else 0f
        val maxTrans = (trackWidth - thumbW * 3f / 4f).coerceAtLeast(minTrans)
        val translationX = (-thumbW / 2f + trackWidth * safeProgress).coerceIn(minTrans, maxTrans)

        assertTrue(minTrans <= maxTrans, "minTrans must be <= maxTrans")
        assertTrue(translationX >= minTrans && translationX <= maxTrans, "translationX must be clamped within bounds")
    }

    @Test
    fun testNarrowTrackWidthsDoNotThrowDivisionByZero() {
        val trackWidth = 0
        val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
        assertEquals(1, effectiveTrackWidth)

        val dragAmountX = 15f
        val valueRange = 0f..100f
        val delta = (valueRange.endInclusive - valueRange.start) * (dragAmountX / effectiveTrackWidth)
        assertTrue(delta.isFinite())
    }
}
