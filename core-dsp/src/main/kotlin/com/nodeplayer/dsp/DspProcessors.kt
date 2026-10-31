package com.nodeplayer.dsp

import com.nodeplayer.model.EqBandConfig
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 10-Band Parametric Equalizer.
 */
class ParametricEqualizer {
    private val filters = Array(10) { BiquadFilter() }
    var enabled = false
    private var sampleRate = 44100f

    fun setSampleRate(sr: Float) {
        sampleRate = sr
    }

    fun updateBands(bands: List<EqBandConfig>) {
        bands.take(10).forEachIndexed { i, band ->
            filters[i].enabled = enabled && band.gainDb != 0.0f
            filters[i].configurePeaking(sampleRate, band.centerFreq, band.gainDb, band.q)
        }
    }

    fun reset() {
        filters.forEach { it.reset() }
    }

    fun processStereoSample(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled) return Pair(left, right)
        var l = left
        var r = right
        for (i in 0 until 10) {
            l = filters[i].processLeft(l)
            r = filters[i].processRight(r)
        }
        return Pair(l, r)
    }
}

/**
 * Limiter operating in 32-bit float audio domain.
 * Strictly linear pass-through below threshold (0.9f = -0.91 dBFS) with zero colouration.
 * Applies a smooth C1-continuous soft knee above threshold so excessive peaks never hard-clip
 * and never exceed 1.0f full scale.
 */
class SoftLimiter {
    var enabled = true
    var threshold = 0.90f // Linear region strictly below 0.90f

    fun process(sample: Float): Float {
        if (!enabled) return sample
        val absVal = abs(sample)
        // Strictly linear pass-through below threshold: output exactly equals input
        if (absVal <= threshold) {
            return sample
        }
        // Smooth soft knee with continuous first derivative (C1 continuity) above threshold:
        // y = T + H * (delta / (delta + H)), where T = threshold, H = 1 - threshold, delta = |x| - T
        val excess = absVal - threshold
        val headroom = 1.0f - threshold
        val saturatedExcess = headroom * (excess / (excess + headroom))
        val limited = (threshold + saturatedExcess).coerceAtMost(1.0f)
        return if (sample >= 0f) limited else -limited
    }
}

/**
 * Preamp gain stage in dB.
 */
class PreampGain {
    var enabled: Boolean = true
    var gainDb: Float = 0.0f
        set(value) {
            field = value
            linearGain = 10.0.pow((value / 20.0).toDouble()).toFloat()
        }
    var linearGain: Float = 1.0f
        private set

    fun process(sample: Float): Float = if (enabled) sample * linearGain else sample
}

/**
 * ReplayGain calculator and processor.
 */
class ReplayGainProcessor {
    var enabled: Boolean = true
    var gainOffsetDb: Float = 0.0f
        set(value) {
            field = value
            linearGain = 10.0.pow((value / 20.0).toDouble()).toFloat()
        }
    var linearGain: Float = 1.0f
        private set

    fun setOffset(trackGain: Float?, albumGain: Float?, mode: com.nodeplayer.model.ReplayGainMode, preampDb: Float = 0.0f) {
        if (!enabled || mode == com.nodeplayer.model.ReplayGainMode.OFF) {
            gainOffsetDb = preampDb
            return
        }
        val target = when (mode) {
            com.nodeplayer.model.ReplayGainMode.TRACK -> trackGain ?: albumGain ?: 0.0f
            com.nodeplayer.model.ReplayGainMode.ALBUM -> albumGain ?: trackGain ?: 0.0f
            com.nodeplayer.model.ReplayGainMode.OFF -> 0.0f
        }
        gainOffsetDb = target + preampDb
    }

    fun process(sample: Float): Float = sample * linearGain
}

/**
 * Stereo width adjuster using Mid-Side matrix.
 * width = 0.0f (mono), 1.0f (neutral stereo), 2.0f (wide)
 */
class StereoWidthProcessor {
    var width: Float = 1.0f
    private val sqrt2 = sqrt(2.0).toFloat()

    fun process(left: Float, right: Float): Pair<Float, Float> {
        if (width == 1.0f) return Pair(left, right)
        val mid = (left + right) / sqrt2
        val side = (left - right) / sqrt2 * width
        val newLeft = (mid + side) / sqrt2
        val newRight = (mid - side) / sqrt2
        return Pair(newLeft, newRight)
    }
}

/**
 * Chu Moy / Bauer style Crossfeed processor.
 * Blends high-attenuated opposite-channel signal to reduce headphone fatigue.
 */
class CrossfeedFilter {
    var enabled = false
    var strength = 0.5f // 0.0 to 1.0
    private var prevLeft = 0.0f
    private var prevRight = 0.0f

    fun reset() {
        prevLeft = 0.0f
        prevRight = 0.0f
    }

    fun process(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled || strength <= 0.0f) return Pair(left, right)
        // Simple 1-pole lowpass for cross-bleed
        val alpha = 0.4f
        val filteredLeft = alpha * left + (1f - alpha) * prevLeft
        val filteredRight = alpha * right + (1f - alpha) * prevRight
        prevLeft = filteredLeft
        prevRight = filteredRight

        val bleed = strength * 0.3f
        val outLeft = left * (1f - bleed * 0.5f) + filteredRight * bleed
        val outRight = right * (1f - bleed * 0.5f) + filteredLeft * bleed
        return Pair(outLeft, outRight)
    }
}
