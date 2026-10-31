package com.nodeplayer.dsp

import com.nodeplayer.model.FadeCurve
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure Kotlin mathematical implementation of audio crossfade curves.
 */
object CrossfadeCurveMath {

    /**
     * Calculates gain factors (fadeOutGain, fadeInGain) for a normalized progress [0.0, 1.0].
     * @param progress Normalized fade progress from 0.0 (start) to 1.0 (end)
     * @param curve Curve algorithm (LINEAR, EQUAL_POWER, LOGARITHMIC)
     * @return Pair of (fadeOutGain, fadeInGain)
     */
    fun calculateGains(progress: Float, curve: FadeCurve): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        return when (curve) {
            FadeCurve.LINEAR -> {
                val out = 1f - t
                val inside = t
                Pair(out, inside)
            }
            FadeCurve.EQUAL_POWER -> {
                val angle = (t * Math.PI / 2.0).toFloat()
                val out = cos(angle)
                val inside = sin(angle)
                Pair(out, inside)
            }
            FadeCurve.LOGARITHMIC -> {
                // Hermite / smoothstep curve: s(t) = 3*t^2 - 2*t^3
                val s = t * t * (3f - 2f * t)
                val out = 1f - s
                val inside = s
                Pair(out, inside)
            }
        }
    }
}
