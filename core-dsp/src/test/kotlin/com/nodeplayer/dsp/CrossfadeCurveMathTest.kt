package com.nodeplayer.dsp

import com.nodeplayer.model.FadeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CrossfadeCurveMathTest {

    @Test
    fun testLinearFadeBoundaries() {
        val (outStart, inStart) = CrossfadeCurveMath.calculateGains(0.0f, FadeCurve.LINEAR)
        assertEquals(1.0f, outStart, 1e-5f)
        assertEquals(0.0f, inStart, 1e-5f)

        val (outMid, inMid) = CrossfadeCurveMath.calculateGains(0.5f, FadeCurve.LINEAR)
        assertEquals(0.5f, outMid, 1e-5f)
        assertEquals(0.5f, inMid, 1e-5f)

        val (outEnd, inEnd) = CrossfadeCurveMath.calculateGains(1.0f, FadeCurve.LINEAR)
        assertEquals(0.0f, outEnd, 1e-5f)
        assertEquals(1.0f, inEnd, 1e-5f)
    }

    @Test
    fun testEqualPowerEnergyConservation() {
        // At all steps, out^2 + in^2 must equal 1.0
        val steps = 100
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val (out, ins) = CrossfadeCurveMath.calculateGains(t, FadeCurve.EQUAL_POWER)
            val powerSum = out * out + ins * ins
            assertEquals("Equal power at t=$t must conserve power", 1.0f, powerSum, 1e-4f)
        }

        // At midpoint, each gain should be sqrt(0.5) ~ 0.7071
        val (outMid, inMid) = CrossfadeCurveMath.calculateGains(0.5f, FadeCurve.EQUAL_POWER)
        assertEquals(0.707106f, outMid, 1e-4f)
        assertEquals(0.707106f, inMid, 1e-4f)
    }

    @Test
    fun testLogarithmicSmoothstepBoundariesAndMonotonicity() {
        val (outStart, inStart) = CrossfadeCurveMath.calculateGains(0.0f, FadeCurve.LOGARITHMIC)
        assertEquals(1.0f, outStart, 1e-5f)
        assertEquals(0.0f, inStart, 1e-5f)

        val (outEnd, inEnd) = CrossfadeCurveMath.calculateGains(1.0f, FadeCurve.LOGARITHMIC)
        assertEquals(0.0f, outEnd, 1e-5f)
        assertEquals(1.0f, inEnd, 1e-5f)

        var lastIn = 0.0f
        var lastOut = 1.0f
        for (i in 1..50) {
            val t = i.toFloat() / 50f
            val (out, ins) = CrossfadeCurveMath.calculateGains(t, FadeCurve.LOGARITHMIC)
            assertTrue("Fade in must be monotonically increasing", ins >= lastIn)
            assertTrue("Fade out must be monotonically decreasing", out <= lastOut)
            lastIn = ins
            lastOut = out
        }
    }
}
