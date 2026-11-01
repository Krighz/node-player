package com.nodeplayer.dsp

import com.nodeplayer.model.AudioEffectConfig
import kotlin.math.pow

/**
 * Master 32-bit Float DSP Chain.
 * Thread-safe for real-time audio pipeline processing.
 */
class DspChain {
    val equalizer = ParametricEqualizer()
    val preamp = PreampGain()
    val replayGain = ReplayGainProcessor()
    val toneControls = ToneControlsProcessor()
    val loudness = LoudnessCompensator()
    val harmonicBass = HarmonicBassEnhancer()
    val convolver = PartitionedConvolver()
    val reverb = ReverbProcessor()
    val stereoWidth = StereoWidthProcessor()
    val crossfeed = CrossfeedFilter()
    val directVolume = DirectVolumeProcessor()
    val limiter = SoftLimiter()

    var sampleRate: Int = 44100
        set(value) {
            field = value
            val srFloat = value.toFloat()
            equalizer.setSampleRate(srFloat)
            toneControls.sampleRate = srFloat
            loudness.setSampleRate(srFloat)
            harmonicBass.setSampleRate(srFloat)
        }

    private var abGainMultiplier: Float = 1.0f

    fun applyConfig(config: AudioEffectConfig) {
        equalizer.enabled = config.eqEnabled
        equalizer.updateBands(config.bands)
        preamp.gainDb = config.preampGainDb
        limiter.enabled = config.limiterEnabled
        stereoWidth.width = config.stereoWidth
        crossfeed.enabled = config.crossfeedEnabled
        crossfeed.strength = config.crossfeedStrength

        // Tone controls
        toneControls.bassGainDb = config.toneControls.bassGainDb
        toneControls.trebleGainDb = config.toneControls.trebleGainDb

        // Loudness compensation
        loudness.enabled = config.loudnessConfig.enabled
        loudness.strength = config.loudnessConfig.strength
        loudness.updateForVolume(config.directVolumeConfig.volume)

        // Harmonic bass
        harmonicBass.enabled = config.harmonicBassConfig.enabled
        harmonicBass.cutoffFreq = config.harmonicBassConfig.frequencyHz
        harmonicBass.harmonicGain = config.harmonicBassConfig.harmonicGain
        harmonicBass.highPassOriginal = config.harmonicBassConfig.highPassOriginal
        harmonicBass.reconfigure()

        // Convolver
        convolver.enabled = config.convolverConfig.enabled
        convolver.blend = config.convolverConfig.blend

        // Reverb & Ambience
        reverb.enabled = config.reverbConfig.enabled
        reverb.roomSize = config.reverbConfig.roomSize
        reverb.damping = config.reverbConfig.damping
        reverb.wet = config.reverbConfig.wetLevel

        // Direct volume
        directVolume.enabled = config.directVolumeConfig.enabled
        directVolume.volume = config.directVolumeConfig.volume
        directVolume.ditherEnabled = config.directVolumeConfig.ditherEnabled

        // A/B Loudness Matching
        if (config.isAbComparing) {
            // If EQ is bypassed during A/B test, scale by abLoudnessOffsetDb to match loudness
            val offsetDb = if (!config.eqEnabled) -config.abLoudnessOffsetDb else 0.0f
            abGainMultiplier = 10.0.pow((offsetDb / 20.0).toDouble()).toFloat()
        } else {
            abGainMultiplier = 1.0f
        }
    }

    fun reset() {
        equalizer.reset()
        toneControls.reset()
        loudness.reset()
        harmonicBass.reset()
        convolver.reset()
        reverb.reset()
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
            l = preamp.process(l) * abGainMultiplier
            r = preamp.process(r) * abGainMultiplier

            // 2. Parametric EQ
            val eqOut = equalizer.processStereoSample(l, r)
            l = eqOut.first
            r = eqOut.second

            // 3. Tone Controls (Bass/Treble Shelf)
            val toneOut = toneControls.processStereo(l, r)
            l = toneOut.first
            r = toneOut.second

            // 4. Loudness Compensation
            val loudOut = loudness.processStereo(l, r)
            l = loudOut.first
            r = loudOut.second

            // 5. Harmonic Bass Enhancer
            val harmOut = harmonicBass.processStereo(l, r)
            l = harmOut.first
            r = harmOut.second

            // 6. Stereo Width
            val widthOut = stereoWidth.process(l, r)
            l = widthOut.first
            r = widthOut.second

            // 7. Algorithmic Reverb & Ambience
            val revOut = reverb.processStereo(l, r)
            l = revOut.first
            r = revOut.second

            // 8. Crossfeed
            val crossOut = crossfeed.process(l, r)
            l = crossOut.first
            r = crossOut.second

            // 9. Direct Volume Control with TPDF Dither
            val volOut = directVolume.processStereo(l, r)
            l = volOut.first
            r = volOut.second

            // 10. Final 32-bit Soft Limiter (guarantees no harsh clipping)
            l = limiter.process(l)
            r = limiter.process(r)

            buffer[i] = l
            buffer[i + 1] = r
            i += 2
        }

        // 11. Convolver block processing (if enabled)
        if (convolver.enabled) {
            convolver.processBlock(buffer, offset, count, sampleRate)
        }
    }
}
