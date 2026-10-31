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

data class EqBandConfig(
    val index: Int,
    val centerFreq: Float,
    val gainDb: Float = 0.0f,
    val q: Float = 1.414f
)

data class AudioEffectConfig(
    val eqEnabled: Boolean = false,
    val bands: List<EqBandConfig> = default10Bands(),
    val preampGainDb: Float = 0.0f,
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.1f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.TRACK,
    val replayGainPreampDb: Float = 0.0f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedStrength: Float = 0.5f,
    val stereoWidth: Float = 1.0f // 0.0 = mono, 1.0 = normal, 2.0 = wide
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
    }
}

data class GlassThemeConfig(
    val blurRadiusDp: Float = 24.0f,
    val tintAlpha: Float = 0.65f,
    val borderAlpha: Float = 0.25f,
    val noiseGrainAlpha: Float = 0.04f,
    val isOledBlack: Boolean = false,
    val useDynamicAccent: Boolean = true,
    val fixedAccentColorArgb: Long = 0xFF6366F1, // Indigo default
    val cornerRadiusDp: Float = 20.0f
)
