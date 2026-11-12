package com.nodeplayer.ui.components

import com.nodeplayer.model.EqBandConfig
import com.nodeplayer.model.GlassThemeConfig
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqCurveAndBackdropCharacterizationTest {

    @Test
    fun testGlassThemeConfigAnimateOnlyWhilePlayingDefaultIsTrue() {
        val cfg = GlassThemeConfig()
        assertTrue(cfg.animateOnlyWhilePlaying)
    }

    @Test
    fun testShouldAnimateLiquidGlassBackgroundPlayStateGating() {
        assertFalse(
            shouldAnimateLiquidGlassBackground(
                style = "LIQUID_BLOBS",
                animatedBackdrop = true,
                glassStrength = "BALANCED",
                animateOnlyWhilePlaying = true,
                isPlaying = false,
                isPerformanceMode = false
            )
        )
        assertTrue(
            shouldAnimateLiquidGlassBackground(
                style = "LIQUID_BLOBS",
                animatedBackdrop = true,
                glassStrength = "BALANCED",
                animateOnlyWhilePlaying = true,
                isPlaying = true,
                isPerformanceMode = false
            )
        )
        assertFalse(
            shouldAnimateLiquidGlassBackground(
                style = "STATIC",
                animatedBackdrop = true,
                glassStrength = "BALANCED",
                animateOnlyWhilePlaying = false,
                isPlaying = true,
                isPerformanceMode = false
            )
        )
        assertFalse(
            shouldAnimateLiquidGlassBackground(
                style = "AURORA",
                animatedBackdrop = true,
                glassStrength = "OFF",
                animateOnlyWhilePlaying = false,
                isPlaying = true,
                isPerformanceMode = false
            )
        )
        assertFalse(
            shouldAnimateLiquidGlassBackground(
                style = "AURORA",
                animatedBackdrop = false,
                glassStrength = "BALANCED",
                animateOnlyWhilePlaying = false,
                isPlaying = true,
                isPerformanceMode = false
            )
        )
        assertFalse(
            shouldAnimateLiquidGlassBackground(
                style = "AURORA",
                animatedBackdrop = true,
                glassStrength = "BALANCED",
                animateOnlyWhilePlaying = false,
                isPlaying = true,
                isPerformanceMode = true
            )
        )
    }

    @Test
    fun testEqCoordinateConversionsAndRoundTrip() {
        val width = 1000f
        val height = 400f

        assertEquals(0f, eqFreqToX(20f, width), 0.001f)
        assertEquals(width, eqFreqToX(20000f, width), 0.001f)
        assertEquals(width * 0.5f, eqFreqToX(632.4555f, width), 0.1f)

        for (freq in listOf(20f, 62.5f, 250f, 1000f, 4000f, 16000f, 20000f)) {
            val x = eqFreqToX(freq, width)
            val roundTrip = eqXToFreq(x, width)
            assertTrue(abs(roundTrip - freq) < 0.05f, "Round trip mismatch for $freq: $roundTrip")
        }

        assertEquals(0f, eqGainToY(12f, height), 0.001f)
        assertEquals(height * 0.5f, eqGainToY(0f, height), 0.001f)
        assertEquals(height, eqGainToY(-12f, height), 0.001f)

        for (gain in listOf(-12f, -6f, -1.5f, 0f, 3.5f, 12f)) {
            val y = eqGainToY(gain, height)
            val roundTrip = eqYToGain(y, height)
            assertEquals(gain, roundTrip, 0.001f)
        }
    }

    @Test
    fun testEvaluateEqResponseDbMatchesLegacyPeakingFormula() {
        val bands = listOf(
            EqBandConfig(index = 0, centerFreq = 60f, gainDb = 6.0f, q = 1.414f),
            EqBandConfig(index = 1, centerFreq = 1000f, gainDb = -4.5f, q = 2.0f),
            EqBandConfig(index = 2, centerFreq = 8000f, gainDb = 3.0f, q = 1.0f)
        )

        assertEquals(0.0f, evaluateEqResponseDb(1000f, bands, eqEnabled = false), 0.0001f)
        // Single-band peak matches band gainDb exactly at centerFreq
        assertEquals(6.0f, evaluateEqResponseDb(60f, listOf(bands[0]), eqEnabled = true), 0.0001f)
        assertEquals(-4.5f, evaluateEqResponseDb(1000f, listOf(bands[1]), eqEnabled = true), 0.0001f)
        assertEquals(3.0f, evaluateEqResponseDb(8000f, listOf(bands[2]), eqEnabled = true), 0.0001f)

        // Exact bit-for-bit verification against the original inline formula at 500 Hz and 1000 Hz
        for (f in listOf(500f, 1000f)) {
            var expected = 0f
            for (band in bands) {
                if (band.gainDb != 0f) {
                    val ratio = (f / band.centerFreq) - (band.centerFreq / f)
                    val qTerm = ratio / (1.0f / band.q)
                    expected += band.gainDb / (1.0f + qTerm * qTerm)
                }
            }
            assertEquals(expected, evaluateEqResponseDb(f, bands, eqEnabled = true))
        }
    }
}
