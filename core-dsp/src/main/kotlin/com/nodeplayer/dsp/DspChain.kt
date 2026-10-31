package com.nodeplayer.dsp

import com.nodeplayer.model.AudioEffectConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Master 32-bit Float DSP Chain.
 * Thread-safe for real-time audio pipeline processing.
 */
class DspChain {
    val equalizer = ParametricEqualizer()
    val preamp = PreampGain()
    val replayGain = ReplayGainProcessor()
    val stereoWidth = StereoWidthProcessor()
    val crossfeed = CrossfeedFilter()
    val limiter = SoftLimiter()

    var sampleRate: Int = 44100
        set(value) {
            field = value
            equalizer.setSampleRate(value.toFloat())
        }

    fun applyConfig(config: AudioEffectConfig) {
        equalizer.enabled = config.eqEnabled
        equalizer.updateBands(config.bands)
        preamp.gainDb = config.preampGainDb
        limiter.enabled = config.limiterEnabled
        stereoWidth.width = config.stereoWidth
        crossfeed.enabled = config.crossfeedEnabled
        crossfeed.strength = config.crossfeedStrength
    }

    fun reset() {
        equalizer.reset()
        crossfeed.reset()
    }

    /**
     * Process an interleaved Float buffer (Left, Right, Left, Right...) in place.
     */
    fun processInterleavedFloat(buffer: FloatArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            var l = buffer[i]
            var r = buffer[i + 1]

            // 1. ReplayGain & Preamp
            l = replayGain.process(l)
            r = replayGain.process(r)
            l = preamp.process(l)
            r = preamp.process(r)

            // 2. Parametric EQ
            val eqOut = equalizer.processStereoSample(l, r)
            l = eqOut.first
            r = eqOut.second

            // 3. Stereo Width
            val widthOut = stereoWidth.process(l, r)
            l = widthOut.first
            r = widthOut.second

            // 4. Crossfeed
            val crossOut = crossfeed.process(l, r)
            l = crossOut.first
            r = crossOut.second

            // 5. Final 32-bit Soft Limiter (guarantees no harsh clipping)
            l = limiter.process(l)
            r = limiter.process(r)

            buffer[i] = l
            buffer[i + 1] = r
            i += 2
        }
    }
}
