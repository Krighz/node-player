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

    private val stereoHolder = FloatArray(2)

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
            equalizer.processStereoSample(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 3. Tone Controls (Bass/Treble Shelf)
            toneControls.processStereo(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 4. Loudness Compensation
            loudness.processStereo(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 5. Harmonic Bass Enhancer
            harmonicBass.processStereo(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 6. Stereo Width
            stereoWidth.process(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 7. Algorithmic Reverb & Ambience
            reverb.processStereo(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 8. Crossfeed
            crossfeed.process(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            // 9. Direct Volume Control with TPDF Dither
            directVolume.processStereo(l, r, stereoHolder)
            l = stereoHolder[0]
            r = stereoHolder[1]

            buffer[i] = l
            buffer[i + 1] = r
            i += 2
        }

        // 10. Convolver block processing (if enabled) - executed BEFORE limiter to prevent digital clipping
        if (convolver.enabled) {
            convolver.processBlock(buffer, offset, count, sampleRate)
        }

        // 11. Final 32-bit Soft Limiter (guarantees 0 dBFS ceiling after convolver and all upstream DSP)
        var j = offset
        while (j < end) {
            buffer[j] = limiter.process(buffer[j])
            buffer[j + 1] = limiter.process(buffer[j + 1])
            j += 2
        }
    }
}
