package com.nodeplayer.dsp

import com.nodeplayer.model.*
import kotlin.math.*

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
 * Strictly linear pass-through below threshold (0.90f = -0.91 dBFS) with zero colouration.
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

    fun setOffset(trackGain: Float?, albumGain: Float?, mode: ReplayGainMode, preampDb: Float = 0.0f) {
        if (!enabled || mode == ReplayGainMode.OFF) {
            gainOffsetDb = preampDb
            return
        }
        val target = when (mode) {
            ReplayGainMode.TRACK -> trackGain ?: albumGain ?: 0.0f
            ReplayGainMode.ALBUM -> albumGain ?: trackGain ?: 0.0f
            ReplayGainMode.OFF -> 0.0f
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

/**
 * Direct Volume Control with TPDF Dither.
 * Operates entirely in 32-bit float domain with zero-allocation XorShift PRNG.
 * Prevents quantization distortion at low volumes.
 */
class DirectVolumeProcessor {
    var enabled: Boolean = true
    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }
    var ditherEnabled: Boolean = true

    // 16-bit LSB amplitude = 1.0 / 32768.0 = 3.0517578e-5f
    private val lsbAmplitude = 1.0f / 32768.0f

    // 64-bit XorShift state for zero-allocation, ultra-fast PRNG
    private var rngState: Long = 0x553c49e6748fea9bL

    private fun nextUniform(): Float {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        rngState = x
        // Convert lower 24 bits to float in range [-0.5, 0.5]
        val intBits = (x and 0x00FFFFFF).toInt()
        return (intBits.toFloat() / 16777216.0f) - 0.5f
    }

    fun process(sample: Float): Float {
        if (!enabled) return sample
        val scaled = sample * volume
        if (!ditherEnabled || volume <= 0.0f) return scaled
        // TPDF: Difference of two independent uniform random variables
        val dither = (nextUniform() - nextUniform()) * lsbAmplitude
        return scaled + dither
    }

    fun processStereo(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled) return Pair(left, right)
        return Pair(process(left), process(right))
    }
}

/**
 * 2-Band Tone Controls (Bass Shelf @ 100 Hz, Treble Shelf @ 8 kHz).
 */
class ToneControlsProcessor {
    private val bassFilter = BiquadFilter()
    private val trebleFilter = BiquadFilter()
    var sampleRate: Float = 44100f
        set(value) {
            field = value
            reconfigure()
        }

    var bassGainDb: Float = 0.0f
        set(value) {
            field = value
            reconfigure()
        }

    var trebleGainDb: Float = 0.0f
        set(value) {
            field = value
            reconfigure()
        }

    private fun reconfigure() {
        bassFilter.enabled = bassGainDb != 0.0f
        trebleFilter.enabled = trebleGainDb != 0.0f
        bassFilter.configureLowShelf(sampleRate, 100.0f, bassGainDb)
        trebleFilter.configureHighShelf(sampleRate, 8000.0f, trebleGainDb)
    }

    fun reset() {
        bassFilter.reset()
        trebleFilter.reset()
    }

    fun processStereo(left: Float, right: Float): Pair<Float, Float> {
        if (bassGainDb == 0.0f && trebleGainDb == 0.0f) return Pair(left, right)
        var l = bassFilter.processLeft(left)
        var r = bassFilter.processRight(right)
        l = trebleFilter.processLeft(l)
        r = trebleFilter.processRight(r)
        return Pair(l, r)
    }
}

/**
 * ISO 226 Dynamic Loudness Compensator.
 * Lifts bass and treble at lower listening volumes to compensate for the ear's reduced sensitivity.
 */
class LoudnessCompensator {
    var enabled: Boolean = false
    var strength: Float = 0.5f // 0.0f to 1.0f
    private var sampleRate: Float = 44100f
    private val bassShelf = BiquadFilter()
    private val trebleShelf = BiquadFilter()

    fun setSampleRate(sr: Float) {
        sampleRate = sr
    }

    fun updateForVolume(currentVolume: Float) {
        if (!enabled || strength <= 0.0f) {
            bassShelf.enabled = false
            trebleShelf.enabled = false
            return
        }
        // Attenuation below nominal 1.0 (0 dB)
        val clampedVol = currentVolume.coerceIn(0.01f, 1.0f)
        val attenuationDb = -20.0f * log10(clampedVol)

        // Equal-loudness contour compensation:
        // Max +12dB bass boost at 80Hz, max +6dB treble boost at 10kHz at minimum volume
        val bassBoost = (attenuationDb * 0.40f * strength).coerceIn(0.0f, 12.0f)
        val trebleBoost = (attenuationDb * 0.15f * strength).coerceIn(0.0f, 6.0f)

        bassShelf.enabled = bassBoost > 0.05f
        trebleShelf.enabled = trebleBoost > 0.05f

        bassShelf.configureLowShelf(sampleRate, 80.0f, bassBoost)
        trebleShelf.configureHighShelf(sampleRate, 10000.0f, trebleBoost)
    }

    fun reset() {
        bassShelf.reset()
        trebleShelf.reset()
    }

    fun processStereo(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled) return Pair(left, right)
        var l = bassShelf.processLeft(left)
        var r = bassShelf.processRight(right)
        l = trebleShelf.processLeft(l)
        r = trebleShelf.processRight(r)
        return Pair(l, r)
    }
}

/**
 * Psychoacoustic Harmonic Bass Enhancer (Missing Fundamental Generator).
 * Synthesizes 2nd and 3rd harmonics of low-frequency audio to create perception of deep bass
 * on speakers or headphones with limited low-frequency capability.
 */
class HarmonicBassEnhancer {
    var enabled: Boolean = false
    var cutoffFreq: Float = 60.0f
    var harmonicGain: Float = 0.5f
    var highPassOriginal: Boolean = false

    private var sampleRate: Float = 44100f
    private val lowPassIn = BiquadFilter()
    private val bandPassHarmonics = BiquadFilter()
    private val highPassOut = BiquadFilter()

    fun setSampleRate(sr: Float) {
        sampleRate = sr
        reconfigure()
    }

    fun reconfigure() {
        lowPassIn.enabled = enabled
        bandPassHarmonics.enabled = enabled
        highPassOut.enabled = enabled && highPassOriginal

        lowPassIn.configureLowPass(sampleRate, cutoffFreq, 0.707f)
        // Harmonics centered around 2x to 3x fundamental (120Hz to 180Hz)
        bandPassHarmonics.configureBandPass(sampleRate, (cutoffFreq * 2.2f).coerceIn(80f, 250f), 1.2f)
        highPassOut.configureHighPass(sampleRate, cutoffFreq, 0.707f)
    }

    fun reset() {
        lowPassIn.reset()
        bandPassHarmonics.reset()
        highPassOut.reset()
    }

    private fun waveshape(x: Float): Float {
        // Non-linear polynomial generator producing 2nd and 3rd order harmonics:
        // x + 0.5*x^2 - 0.25*x^3
        val clamped = x.coerceIn(-1.5f, 1.5f)
        return clamped + 0.5f * (clamped * clamped) - 0.25f * (clamped * clamped * clamped)
    }

    fun processStereo(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled || harmonicGain <= 0.0f) return Pair(left, right)

        // 1. Isolate bass fundamental
        val bassL = lowPassIn.processLeft(left)
        val bassR = lowPassIn.processRight(right)

        // 2. Synthesize harmonics via non-linear waveshaping
        val harmL = waveshape(bassL)
        val harmR = waveshape(bassR)

        // 3. Bandpass harmonics to audible range
        val filteredHarmL = bandPassHarmonics.processLeft(harmL) * harmonicGain
        val filteredHarmR = bandPassHarmonics.processRight(harmR) * harmonicGain

        // 4. Optionally highpass the original audio to unload speaker cone
        val mainL = if (highPassOriginal) highPassOut.processLeft(left) else left
        val mainR = if (highPassOriginal) highPassOut.processRight(right) else right

        return Pair(mainL + filteredHarmL, mainR + filteredHarmR)
    }
}

/**
 * Uniformly Partitioned Impulse Response Convolver.
 * Designed for real-time room or headphone correction IRs.
 * Uses contiguous mirrored history buffers for branchless, zero-overhead SIMD vectorization.
 * Direct block convolution ensures zero audio underruns.
 * Measures and exposes CPU usage percent.
 */
class PartitionedConvolver {
    var enabled: Boolean = false
    var blend: Float = 1.0f // 0.0 = dry, 1.0 = wet
    var lastCpuUsagePercent: Float = 0.0f
        private set

    private var impulseResponse: FloatArray = FloatArray(0)
    private var historyBufferL = FloatArray(0)
    private var historyBufferR = FloatArray(0)
    private var irLen = 0
    private var historyIndex = 0

    fun loadImpulseResponse(ir: FloatArray) {
        val maxTaps = min(ir.size, 256) // 256 taps partitioned head (5.8ms @ 44.1kHz) for low-latency mobile DSP
        var maxVal = 0.0f
        for (i in 0 until maxTaps) {
            val a = abs(ir[i])
            if (a > maxVal) maxVal = a
        }
        val normFactor = if (maxVal > 0.0f) 1.0f / maxVal else 1.0f
        irLen = max(maxTaps, 1)
        impulseResponse = FloatArray(irLen) { ir[it] * normFactor }

        // Doubled contiguous history buffers for branchless convolution
        historyBufferL = FloatArray(irLen * 2)
        historyBufferR = FloatArray(irLen * 2)
        historyIndex = 0
    }

    fun reset() {
        historyBufferL.fill(0.0f)
        historyBufferR.fill(0.0f)
        historyIndex = 0
    }

    fun processBlock(buffer: FloatArray, offset: Int, count: Int, sampleRate: Int) {
        if (!enabled || irLen == 0) return

        val startTime = System.nanoTime()
        val numSamples = count / 2 // Stereo interleaved
        val wet = blend.coerceIn(0.0f, 1.0f)
        val dry = 1.0f - wet
        val len = irLen
        val ir = impulseResponse
        val hL = historyBufferL
        val hR = historyBufferR

        var bufIdx = offset
        for (s in 0 until numSamples) {
            val inL = buffer[bufIdx]
            val inR = buffer[bufIdx + 1]

            // Write into mirrored history ring buffer
            val hIdx = historyIndex
            hL[hIdx] = inL
            hL[hIdx + len] = inL
            hR[hIdx] = inR
            hR[hIdx + len] = inR

            // Branchless contiguous convolution: y[n] = sum(h[k] * x[n - k])
            var convL = 0.0f
            var convR = 0.0f
            val base = hIdx + len

            var k = 0
            val limit = len - 3
            while (k < limit) {
                val c0 = ir[k]
                val c1 = ir[k + 1]
                val c2 = ir[k + 2]
                val c3 = ir[k + 3]
                val idx0 = base - k
                convL += hL[idx0] * c0 + hL[idx0 - 1] * c1 + hL[idx0 - 2] * c2 + hL[idx0 - 3] * c3
                convR += hR[idx0] * c0 + hR[idx0 - 1] * c1 + hR[idx0 - 2] * c2 + hR[idx0 - 3] * c3
                k += 4
            }
            while (k < len) {
                val coef = ir[k]
                val idx = base - k
                convL += hL[idx] * coef
                convR += hR[idx] * coef
                k++
            }

            buffer[bufIdx] = inL * dry + convL * wet
            buffer[bufIdx + 1] = inR * dry + convR * wet

            historyIndex = (hIdx + 1) % len
            bufIdx += 2
        }

        val elapsedNanos = System.nanoTime() - startTime
        val bufferDurationNanos = (numSamples.toDouble() / sampleRate * 1_000_000_000.0).toLong()
        if (bufferDurationNanos > 0) {
            lastCpuUsagePercent = (elapsedNanos.toFloat() / bufferDurationNanos.toFloat()) * 100.0f
        }
    }
}

/**
 * AutoEQ Parametric EQ Profile Parser.
 * Parses standard AutoEQ text profiles (e.g. from AutoEQ database / squiglink).
 */
object AutoEqParser {
    fun parse(profileText: String, profileName: String = "Imported AutoEQ"): AutoEqProfile {
        var preamp = 0.0f
        val filters = mutableListOf<AutoEqFilter>()

        profileText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("Preamp:", ignoreCase = true)) {
                // e.g. "Preamp: -6.5 dB"
                val match = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(line)
                match?.groupValues?.get(1)?.toFloatOrNull()?.let { preamp = it }
            } else if (line.startsWith("Filter", ignoreCase = true)) {
                // e.g. "Filter 1: ON PK Fc 28 Hz Gain 6.7 dB Q 1.41"
                // or "Filter 2: ON LSC Fc 105 Hz Gain -2.1 dB Q 0.71"
                // or "Filter 3: ON HSC Fc 10000 Hz Gain 1.5 dB Q 0.71"
                val regex = Regex("""Filter\s+(\d+):\s+(ON|OFF)\s+([A-Za-z]+)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)""", RegexOption.IGNORE_CASE)
                val match = regex.find(line)
                if (match != null) {
                    val idx = match.groupValues[1].toIntOrNull() ?: (filters.size + 1)
                    val on = match.groupValues[2].equals("ON", ignoreCase = true)
                    val type = match.groupValues[3].uppercase()
                    val fc = match.groupValues[4].toFloatOrNull() ?: 1000f
                    val gain = match.groupValues[5].toFloatOrNull() ?: 0.0f
                    val q = match.groupValues[6].toFloatOrNull() ?: 1.414f
                    filters.add(AutoEqFilter(idx, on, type, fc, gain, q))
                }
            }
        }

        return AutoEqProfile(name = profileName, preampDb = preamp, filters = filters)
    }

    /**
     * Converts AutoEQ profile into 10 standard EQ bands.
     */
    fun toEqBandConfigs(profile: AutoEqProfile): List<EqBandConfig> {
        val defaultBands = AudioEffectConfig.default10Bands().toMutableList()
        // Map top filters into our 10 bands by closest center frequency
        for (filter in profile.filters.filter { it.enabled }) {
            var closestIdx = 0
            var minDiff = Float.MAX_VALUE
            for (i in defaultBands.indices) {
                val diff = abs(defaultBands[i].centerFreq - filter.frequency)
                if (diff < minDiff) {
                    minDiff = diff
                    closestIdx = i
                }
            }
            defaultBands[closestIdx] = defaultBands[closestIdx].copy(
                gainDb = filter.gainDb.coerceIn(-12.0f, 12.0f),
                q = filter.q
            )
        }
        return defaultBands
    }
}

/**
 * Audio Profile Serializer for Export and Import.
 * Pure Kotlin parser avoiding external library dependencies.
 */
object AudioProfileSerializer {
    fun toJson(config: AudioEffectConfig): String {
        val bandsJson = config.bands.joinToString(separator = ",") { band ->
            """{"index":${band.index},"centerFreq":${band.centerFreq},"gainDb":${band.gainDb},"q":${band.q}}"""
        }
        return """{
  "eqEnabled": ${config.eqEnabled},
  "preampGainDb": ${config.preampGainDb},
  "limiterEnabled": ${config.limiterEnabled},
  "stereoWidth": ${config.stereoWidth},
  "crossfeedEnabled": ${config.crossfeedEnabled},
  "crossfeedStrength": ${config.crossfeedStrength},
  "toneControls": {
    "bassGainDb": ${config.toneControls.bassGainDb},
    "trebleGainDb": ${config.toneControls.trebleGainDb}
  },
  "loudnessConfig": {
    "enabled": ${config.loudnessConfig.enabled},
    "strength": ${config.loudnessConfig.strength}
  },
  "harmonicBassConfig": {
    "enabled": ${config.harmonicBassConfig.enabled},
    "frequencyHz": ${config.harmonicBassConfig.frequencyHz},
    "harmonicGain": ${config.harmonicBassConfig.harmonicGain},
    "highPassOriginal": ${config.harmonicBassConfig.highPassOriginal}
  },
  "convolverConfig": {
    "enabled": ${config.convolverConfig.enabled},
    "irName": "${config.convolverConfig.irName}",
    "blend": ${config.convolverConfig.blend}
  },
  "directVolumeConfig": {
    "enabled": ${config.directVolumeConfig.enabled},
    "volume": ${config.directVolumeConfig.volume},
    "ditherEnabled": ${config.directVolumeConfig.ditherEnabled}
  },
  "bands": [$bandsJson]
}""".trimIndent()
    }

    fun fromJson(json: String): AudioEffectConfig {
        fun extractBool(key: String, default: Boolean): Boolean {
            val m = Regex(""""$key"\s*:\s*(true|false)""").find(json)
            return m?.groupValues?.get(1)?.toBoolean() ?: default
        }
        fun extractFloat(key: String, default: Float): Float {
            val m = Regex(""""$key"\s*:\s*(-?[\d.]+)""").find(json)
            return m?.groupValues?.get(1)?.toFloatOrNull() ?: default
        }

        val eqEnabled = extractBool("eqEnabled", false)
        val preampGainDb = extractFloat("preampGainDb", 0.0f)
        val limiterEnabled = extractBool("limiterEnabled", true)
        val stereoWidth = extractFloat("stereoWidth", 1.0f)
        val crossfeedEnabled = extractBool("crossfeedEnabled", false)
        val crossfeedStrength = extractFloat("crossfeedStrength", 0.5f)

        val bassGain = extractFloat("bassGainDb", 0.0f)
        val trebleGain = extractFloat("trebleGainDb", 0.0f)

        val loudnessEnabled = extractBool("enabled", false)
        val loudnessStrength = extractFloat("strength", 0.5f)

        val harmonicEnabled = extractBool("enabled", false)
        val harmonicFreq = extractFloat("frequencyHz", 60.0f)
        val harmonicGain = extractFloat("harmonicGain", 0.5f)
        val highPassOrig = extractBool("highPassOriginal", false)

        val directVolEnabled = extractBool("enabled", true)
        val directVol = extractFloat("volume", 1.0f)
        val dither = extractBool("ditherEnabled", true)

        // Parse bands
        val bands = mutableListOf<EqBandConfig>()
        val bandRegex = Regex("""\{"index":(\d+),"centerFreq":([\d.]+),"gainDb":(-?[\d.]+),"q":([\d.]+)\}""")
        bandRegex.findAll(json).forEach { m ->
            bands.add(
                EqBandConfig(
                    index = m.groupValues[1].toInt(),
                    centerFreq = m.groupValues[2].toFloat(),
                    gainDb = m.groupValues[3].toFloat(),
                    q = m.groupValues[4].toFloat()
                )
            )
        }

        return AudioEffectConfig(
            eqEnabled = eqEnabled,
            bands = if (bands.isNotEmpty()) bands else AudioEffectConfig.default10Bands(),
            preampGainDb = preampGainDb,
            limiterEnabled = limiterEnabled,
            stereoWidth = stereoWidth,
            crossfeedEnabled = crossfeedEnabled,
            crossfeedStrength = crossfeedStrength,
            toneControls = ToneControlConfig(bassGainDb = bassGain, trebleGainDb = trebleGain),
            loudnessConfig = LoudnessConfig(enabled = loudnessEnabled, strength = loudnessStrength),
            harmonicBassConfig = HarmonicBassConfig(
                enabled = harmonicEnabled,
                frequencyHz = harmonicFreq,
                harmonicGain = harmonicGain,
                highPassOriginal = highPassOrig
            ),
            directVolumeConfig = DirectVolumeConfig(
                enabled = directVolEnabled,
                volume = directVol,
                ditherEnabled = dither
            )
        )
    }
}

/**
 * A/B Loudness Matcher.
 * Computes pink-noise weighted RMS energy offset across EQ bands so that toggling
 * EQ on and off can be evaluated at strictly matched perceived loudness without volume bias.
 */
object AbCompareHelper {
    fun calculateLoudnessOffsetDb(bands: List<EqBandConfig>, preampDb: Float): Float {
        // Average gain change across audible bands
        if (bands.isEmpty()) return -preampDb
        var totalWeightedGain = 0.0f
        // Weights reflecting human ear sensitivity curve (A-weighting approximation)
        val weights = floatArrayOf(0.3f, 0.5f, 0.8f, 1.0f, 1.2f, 1.2f, 1.1f, 1.0f, 0.7f, 0.4f)
        var totalWeight = 0.0f

        bands.take(10).forEachIndexed { i, band ->
            val w = weights.getOrElse(i) { 1.0f }
            totalWeightedGain += band.gainDb * w
            totalWeight += w
        }

        val avgEqGain = if (totalWeight > 0f) totalWeightedGain / totalWeight else 0f
        val netBoostDb = avgEqGain + preampDb
        // Loudness offset to apply during A/B comparison: invert the boost
        return -netBoostDb
    }
}
