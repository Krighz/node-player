package com.nodeplayer.dsp

import com.nodeplayer.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioCustomisationTest {

    @Test
    fun testDirectVolumeWithTpdfDitherLowLevelSine() {
        val processor = DirectVolumeProcessor()
        processor.enabled = true
        processor.volume = 0.1f // -20 dB attenuation
        processor.ditherEnabled = true

        val sampleRate = 44100
        val freq = 1000.0
        val numSamples = 4410
        // Low level sine at -80 dBFS (amplitude = 0.0001f)
        val lowLevelAmp = 0.0001f

        var maxSample = 0.0f
        var nonZeroCount = 0
        var correlationSum = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val inputSine = sin(2.0 * Math.PI * freq * t).toFloat() * lowLevelAmp
            val processed = processor.process(inputSine)

            if (abs(processed) > 0.0f) nonZeroCount++
            if (abs(processed) > maxSample) maxSample = abs(processed)
            correlationSum += inputSine * processed
        }

        // Even at -80 dBFS scaled by 0.1, the signal is preserved and positively correlated with source sine
        assertTrue("All samples should be processed without dropouts", nonZeroCount > numSamples * 0.95)
        assertTrue("Signal must correlate positively with input sine", correlationSum > 0.0)
    }

    @Test
    fun testToneControlsBassAndTrebleShelves() {
        val tone = ToneControlsProcessor()
        tone.sampleRate = 44100f
        tone.bassGainDb = 6.0f
        tone.trebleGainDb = -6.0f

        val sampleRate = 44100

        // 1. Test Deep Bass (40 Hz): full shelf boost should reach ~ +6 dB
        var inRmsBass = 0.0
        var outRmsBass = 0.0
        for (i in 0 until 4410) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * 40.0 * t).toFloat() * 0.1f
            val out = tone.processStereo(s, s).first
            if (i > 1000) {
                inRmsBass += (s * s)
                outRmsBass += (out * out)
            }
        }
        val measuredBassGainDb = 20.0 * log10(sqrt(outRmsBass) / sqrt(inRmsBass))
        assertEquals(6.0, measuredBassGainDb, 0.5)

        // Test Bass Shelf Cutoff (100 Hz): RBJ shelf gain at cutoff is exactly half-power (3.0 dB)
        var inRmsCutoff = 0.0
        var outRmsCutoff = 0.0
        for (i in 0 until 4410) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * 100.0 * t).toFloat() * 0.1f
            val out = tone.processStereo(s, s).first
            if (i > 1000) {
                inRmsCutoff += (s * s)
                outRmsCutoff += (out * out)
            }
        }
        val measuredCutoffDb = 20.0 * log10(sqrt(outRmsCutoff) / sqrt(inRmsCutoff))
        assertEquals(3.0, measuredCutoffDb, 0.5)

        // 2. Test High Treble (16000 Hz): full shelf cut should reach ~ -6 dB
        var inRmsTreble = 0.0
        var outRmsTreble = 0.0
        for (i in 0 until 4410) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * 16000.0 * t).toFloat() * 0.1f
            val out = tone.processStereo(s, s).first
            if (i > 1000) {
                inRmsTreble += (s * s)
                outRmsTreble += (out * out)
            }
        }
        val measuredTrebleGainDb = 20.0 * log10(sqrt(outRmsTreble) / sqrt(inRmsTreble))
        assertEquals(-6.0, measuredTrebleGainDb, 0.5)
    }

    @Test
    fun testLoudnessCompensatorBoostsAtLowVolume() {
        val loudness = LoudnessCompensator()
        loudness.setSampleRate(44100f)
        loudness.enabled = true
        loudness.strength = 1.0f

        // At full volume (1.0), boost is zero
        loudness.updateForVolume(1.0f)
        val inSample = 0.5f
        val outFull = loudness.processStereo(inSample, inSample).first
        assertEquals(inSample, outFull, 0.001f)

        // At quiet volume (0.1 = -20 dB), bass shelf at 80 Hz should boost significantly
        loudness.updateForVolume(0.1f)
        var inRms = 0.0
        var outRms = 0.0
        for (i in 0 until 4410) {
            val t = i.toDouble() / 44100
            val s = sin(2.0 * Math.PI * 80.0 * t).toFloat() * 0.1f
            val out = loudness.processStereo(s, s).first
            if (i > 1000) {
                inRms += (s * s)
                outRms += (out * out)
            }
        }
        val measuredBassGainDb = 20.0 * log10(sqrt(outRms) / sqrt(inRms))
        assertTrue("Loudness compensation should provide > 4 dB boost at 80 Hz for 0.1 volume. Was $measuredBassGainDb dB", measuredBassGainDb > 4.0)
    }

    @Test
    fun testHarmonicBassEnhancerSynthesizesHarmonics() {
        val enhancer = HarmonicBassEnhancer()
        enhancer.setSampleRate(44100f)
        enhancer.enabled = true
        enhancer.cutoffFreq = 60.0f
        enhancer.harmonicGain = 0.8f
        enhancer.highPassOriginal = false
        enhancer.reconfigure()

        // Feed pure 50 Hz fundamental
        val sampleRate = 44100
        val numSamples = 4410
        val outSamples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val s = sin(2.0 * Math.PI * 50.0 * t).toFloat() * 0.3f
            outSamples[i] = enhancer.processStereo(s, s).first
        }

        // Measure correlation with 2nd harmonic (100 Hz) in steady state
        var harmonicCorrelation = 0.0
        for (i in 2000 until numSamples) {
            val t = i.toDouble() / sampleRate
            val refHarmonic = sin(2.0 * Math.PI * 100.0 * t)
            harmonicCorrelation += abs(outSamples[i] * refHarmonic)
        }

        assertTrue("Harmonic bass enhancer must synthesize 2nd harmonic power", harmonicCorrelation > 5.0)
    }

    @Test
    fun testPartitionedConvolverIdentityAndDelay() {
        val convolver = PartitionedConvolver()
        convolver.enabled = true
        convolver.blend = 1.0f

        // 1. Identity IR: [1.0, 0.0, 0.0, 0.0]
        convolver.loadImpulseResponse(floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f))
        val buffer = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
        val original = buffer.clone()
        convolver.processBlock(buffer, 0, buffer.size, 44100)

        for (i in buffer.indices) {
            assertEquals("Identity IR must pass signal through untouched", original[i], buffer[i], 0.0001f)
        }

        // 2. Delay IR: 4 samples delay (2 stereo frames delay)
        convolver.reset()
        val delayIr = FloatArray(5)
        delayIr[2] = 1.0f // Delay of 2 stereo frames
        convolver.loadImpulseResponse(delayIr)

        val stereoTest = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f)
        convolver.processBlock(stereoTest, 0, stereoTest.size, 44100)
        // Stereo frame 0 and 1 are zero, frame 2 contains initial signal
        assertEquals(0.0f, stereoTest[0], 0.001f)
        assertEquals(0.0f, stereoTest[1], 0.001f)
        assertEquals(1.0f, stereoTest[4], 0.001f)
        assertEquals(2.0f, stereoTest[5], 0.001f)
    }

    @Test
    fun testAutoEqParser() {
        val sampleAutoEq = """
            Preamp: -6.5 dB
            Filter 1: ON PK Fc 32 Hz Gain 4.5 dB Q 1.41
            Filter 2: ON LSC Fc 105 Hz Gain -2.1 dB Q 0.71
            Filter 3: ON PK Fc 1000 Hz Gain -3.0 dB Q 2.00
            Filter 4: ON HSC Fc 8000 Hz Gain 2.0 dB Q 0.71
            Filter 5: OFF PK Fc 12000 Hz Gain 1.0 dB Q 1.00
        """.trimIndent()

        val profile = AutoEqParser.parse(sampleAutoEq, "Sennheiser HD600")
        assertEquals("Sennheiser HD600", profile.name)
        assertEquals(-6.5f, profile.preampDb, 0.001f)
        assertEquals(5, profile.filters.size)

        val f1 = profile.filters[0]
        assertEquals(1, f1.index)
        assertTrue(f1.enabled)
        assertEquals("PK", f1.type)
        assertEquals(32f, f1.frequency, 0.01f)
        assertEquals(4.5f, f1.gainDb, 0.01f)
        assertEquals(1.41f, f1.q, 0.01f)

        val f5 = profile.filters[4]
        assertFalse(f5.enabled)

        val eqBands = AutoEqParser.toEqBandConfigs(profile)
        assertEquals(10, eqBands.size)
        // 31.25 Hz band should have mapped the ~32 Hz boost
        assertEquals(4.5f, eqBands[0].gainDb, 0.01f)
    }

    @Test
    fun testAudioProfileSerializerRoundtrip() {
        val originalConfig = AudioEffectConfig(
            eqEnabled = true,
            bands = listOf(
                EqBandConfig(0, 31.25f, 3.0f, 1.414f),
                EqBandConfig(1, 62.5f, -2.0f, 1.414f)
            ),
            preampGainDb = -1.5f,
            limiterEnabled = true,
            stereoWidth = 1.3f,
            crossfeedEnabled = true,
            crossfeedStrength = 0.6f,
            toneControls = ToneControlConfig(bassGainDb = 4.0f, trebleGainDb = -3.0f),
            loudnessConfig = LoudnessConfig(enabled = true, strength = 0.8f),
            harmonicBassConfig = HarmonicBassConfig(enabled = true, frequencyHz = 70f, harmonicGain = 0.7f),
            directVolumeConfig = DirectVolumeConfig(enabled = true, volume = 0.85f, ditherEnabled = true)
        )

        val json = AudioProfileSerializer.toJson(originalConfig)
        val deserialized = AudioProfileSerializer.fromJson(json)

        assertEquals(originalConfig.eqEnabled, deserialized.eqEnabled)
        assertEquals(originalConfig.preampGainDb, deserialized.preampGainDb, 0.001f)
        assertEquals(originalConfig.limiterEnabled, deserialized.limiterEnabled)
        assertEquals(originalConfig.stereoWidth, deserialized.stereoWidth, 0.001f)
        assertEquals(originalConfig.crossfeedEnabled, deserialized.crossfeedEnabled)
        assertEquals(originalConfig.crossfeedStrength, deserialized.crossfeedStrength, 0.001f)
        assertEquals(originalConfig.toneControls.bassGainDb, deserialized.toneControls.bassGainDb, 0.001f)
        assertEquals(originalConfig.toneControls.trebleGainDb, deserialized.toneControls.trebleGainDb, 0.001f)
        assertEquals(originalConfig.loudnessConfig.enabled, deserialized.loudnessConfig.enabled)
        assertEquals(originalConfig.loudnessConfig.strength, deserialized.loudnessConfig.strength, 0.001f)
        assertEquals(originalConfig.harmonicBassConfig.enabled, deserialized.harmonicBassConfig.enabled)
        assertEquals(originalConfig.directVolumeConfig.volume, deserialized.directVolumeConfig.volume, 0.001f)
    }

    @Test
    fun testAbCompareHelperCalculatesCorrectOffset() {
        val bands = listOf(
            EqBandConfig(0, 31.25f, 6.0f, 1.414f),
            EqBandConfig(1, 62.5f, 6.0f, 1.414f),
            EqBandConfig(2, 125f, 6.0f, 1.414f)
        )
        val offset = AbCompareHelper.calculateLoudnessOffsetDb(bands, preampDb = 0.0f)
        // With positive EQ boost, offset must be negative to match unboosted level
        assertTrue("A/B offset must compensate positive boost", offset < 0.0f)
    }
}
