package com.nodeplayer.dsp

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Biquad filter implementing Direct Form II transposed.
 * Pure Kotlin, zero Android dependencies.
 */
class BiquadFilter {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // State variables for Left and Right channels
    private var z1Left = 0.0f
    private var z2Left = 0.0f
    private var z1Right = 0.0f
    private var z2Right = 0.0f

    var enabled = true

    fun reset() {
        z1Left = 0.0f
        z2Left = 0.0f
        z1Right = 0.0f
        z2Right = 0.0f
    }

    /**
     * Configure as Peaking EQ filter.
     */
    fun configurePeaking(sampleRate: Float, centerFreq: Float, gainDb: Float, q: Float) {
        if (gainDb == 0.0f || !enabled) {
            b0 = 1.0f
            b1 = 0.0f
            b2 = 0.0f
            a1 = 0.0f
            a2 = 0.0f
            return
        }

        val clampedFreq = centerFreq.coerceIn(20f, (sampleRate / 2f) - 100f)
        val clampedQ = q.coerceIn(0.1f, 10.0f)
        val a = 10.0.pow((gainDb / 40.0).toDouble()).toFloat()
        val omega = (2.0 * Math.PI * clampedFreq / sampleRate).toFloat()
        val sn = sin(omega.toDouble()).toFloat()
        val cs = cos(omega.toDouble()).toFloat()
        val alpha = sn / (2.0f * clampedQ)

        val a0 = 1.0f + alpha / a
        b0 = (1.0f + alpha * a) / a0
        b1 = (-2.0f * cs) / a0
        b2 = (1.0f - alpha * a) / a0
        a1 = (-2.0f * cs) / a0
        a2 = (1.0f - alpha / a) / a0
    }

    /**
     * Process a single sample for Left channel.
     */
    fun processLeft(input: Float): Float {
        if (!enabled) return input
        val out = b0 * input + z1Left
        z1Left = b1 * input - a1 * out + z2Left
        z2Left = b2 * input - a2 * out
        return out
    }

    /**
     * Process a single sample for Right channel.
     */
    fun processRight(input: Float): Float {
        if (!enabled) return input
        val out = b0 * input + z1Right
        z1Right = b1 * input - a1 * out + z2Right
        z2Right = b2 * input - a2 * out
        return out
    }
}
