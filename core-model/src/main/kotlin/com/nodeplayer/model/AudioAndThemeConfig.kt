package com.nodeplayer.model

data class AudioOutputInfo(
    val outputDeviceName: String = "Internal Speaker",
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val format: String = "PCM_16BIT",
    val isBitPerfect: Boolean = false,
    val isResampled: Boolean = false,
    val originalSampleRate: Int = 44100,
    val originalBitDepth: Int = 16
)

enum class ReplayGainMode {
    OFF,
    TRACK,
    ALBUM
}

enum class OutputDeviceType(val displayName: String) {
    SPEAKER("Internal Speaker"),
    WIRED("Wired Headphones"),
    BLUETOOTH("Bluetooth A2DP"),
    USB("USB DAC")
}

data class ToneControlConfig(
    val bassGainDb: Float = 0.0f, // -12dB to +12dB (Low-shelf 100Hz)
    val trebleGainDb: Float = 0.0f // -12dB to +12dB (High-shelf 8kHz)
)

data class LoudnessConfig(
    val enabled: Boolean = false,
    val strength: Float = 0.5f // 0.0f (mild) to 1.0f (full Fletcher-Munson ISO 226)
)

data class HarmonicBassConfig(
    val enabled: Boolean = false,
    val frequencyHz: Float = 60.0f, // Fundamental cutoff frequency (40Hz to 100Hz)
    val harmonicGain: Float = 0.5f, // Blend 0.0 to 1.0
    val highPassOriginal: Boolean = false // Cut sub-bass below 80Hz to protect phone speaker
)

data class ConvolverConfig(
    val enabled: Boolean = false,
    val irName: String = "",
    val irPath: String = "",
    val blend: Float = 1.0f // 0.0 = dry, 1.0 = wet
)

data class DirectVolumeConfig(
    val enabled: Boolean = true,
    val volume: Float = 1.0f, // 0.0 to 1.0
    val ditherEnabled: Boolean = true // Triangular Probability Density Function (TPDF)
)

data class AutoEqFilter(
    val index: Int,
    val enabled: Boolean = true,
    val type: String = "PK", // PK = Peaking, LSC = Low-Shelf, HSC = High-Shelf
    val frequency: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1.414f
)

data class AutoEqProfile(
    val name: String,
    val preampDb: Float,
    val filters: List<AutoEqFilter>
)

data class EqBandConfig(
    val index: Int,
    val centerFreq: Float,
    val gainDb: Float = 0.0f,
    val q: Float = 1.414f
)

data class AudioEffectConfig(
    val eqEnabled: Boolean = false,
    val presetName: String = "Flat",
    val bands: List<EqBandConfig> = default10Bands(),
    val preampGainDb: Float = 0.0f,
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.1f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.TRACK,
    val replayGainPreampDb: Float = 0.0f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedStrength: Float = 0.5f,
    val stereoWidth: Float = 1.0f, // 0.0 = mono, 1.0 = normal, 2.0 = wide
    val toneControls: ToneControlConfig = ToneControlConfig(),
    val loudnessConfig: LoudnessConfig = LoudnessConfig(),
    val harmonicBassConfig: HarmonicBassConfig = HarmonicBassConfig(),
    val convolverConfig: ConvolverConfig = ConvolverConfig(),
    val directVolumeConfig: DirectVolumeConfig = DirectVolumeConfig(),
    val isAbComparing: Boolean = false,
    val abLoudnessOffsetDb: Float = 0.0f
) {
    companion object {
        fun default10Bands(): List<EqBandConfig> = listOf(
            EqBandConfig(0, 31f, 0f, 1.414f),
            EqBandConfig(1, 62f, 0f, 1.414f),
            EqBandConfig(2, 125f, 0f, 1.414f),
            EqBandConfig(3, 250f, 0f, 1.414f),
            EqBandConfig(4, 500f, 0f, 1.414f),
            EqBandConfig(5, 1000f, 0f, 1.414f),
            EqBandConfig(6, 2000f, 0f, 1.414f),
            EqBandConfig(7, 4000f, 0f, 1.414f),
            EqBandConfig(8, 8000f, 0f, 1.414f),
            EqBandConfig(9, 16000f, 0f, 1.414f)
        )

        val PRESETS: Map<String, List<Float>> = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to listOf(5.5f, 4.5f, 3.2f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2.0f, 3.8f, 5.0f, 6.0f),
            "Vocal" to listOf(-2.0f, -1.0f, 0f, 1.5f, 3.0f, 3.5f, 2.8f, 1.0f, 0f, -1.0f),
            "Electronic" to listOf(4.5f, 4.0f, 2.0f, 0f, -1.0f, 0.5f, 2.0f, 3.5f, 4.5f, 4.0f),
            "Rock" to listOf(4.0f, 2.5f, -0.5f, -1.5f, -0.5f, 1.0f, 2.5f, 3.5f, 4.0f, 4.0f),
            "Acoustic" to listOf(2.5f, 2.0f, 1.0f, 0.5f, 1.5f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f)
        )

        fun createPreset(name: String): AudioEffectConfig {
            val gains = PRESETS[name] ?: PRESETS["Flat"]!!
            val bands = default10Bands().mapIndexed { idx, band ->
                band.copy(gainDb = gains.getOrElse(idx) { 0f })
            }
            return AudioEffectConfig(
                eqEnabled = true,
                presetName = name,
                bands = bands
            )
        }
    }
}

enum class FadeCurve(val displayName: String) {
    LINEAR("Linear"),
    EQUAL_POWER("Equal Power"),
    LOGARITHMIC("Logarithmic")
}

data class CrossfadeConfig(
    val enabled: Boolean = true,
    val durationSeconds: Float = 4.0f,
    val curve: FadeCurve = FadeCurve.EQUAL_POWER,
    val autoGapless: Boolean = true
)

data class GlassThemeConfig(
    val blurRadiusDp: Float = 24.0f,
    val tintAlpha: Float = 0.65f,
    val borderAlpha: Float = 0.25f,
    val noiseGrainAlpha: Float = 0.04f,
    val isOledBlack: Boolean = false,
    val useDynamicAccent: Boolean = true,
    val fixedAccentColorArgb: Long = 0xFF6366F1, // Indigo default
    val cornerRadiusDp: Float = 20.0f,
    val isPerformanceMode: Boolean = false,
    val refractionShaderEnabled: Boolean = true
)
