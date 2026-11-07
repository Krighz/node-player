package com.nodeplayer.model

data class AudioOutputInfo(
    val outputDeviceName: String = "Internal Speaker",
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val format: String = "—",
    val isBitPerfect: Boolean = false,
    val isResampled: Boolean = false,
    val originalSampleRate: Int = 0,
    val originalBitDepth: Int = 0,
    val isTrackInitialized: Boolean = false
) {
    val sampleRateDisplay: String
        get() = if (sampleRate > 0) "${sampleRate} Hz" else "—"

    val encodingDisplay: String
        get() = if (format.isNotBlank() && format != "—") format else "—"

    fun isTrulyBitPerfect(sourceSampleRate: Int, sourceBitDepth: Int, dspBypassed: Boolean): Boolean {
        if (!dspBypassed) return false
        if (sourceSampleRate <= 0 || sampleRate != sourceSampleRate) return false
        if (sourceBitDepth > 0 && bitDepth > 0 && bitDepth != sourceBitDepth) return false
        return isBitPerfect || (isTrackInitialized && !isResampled)
    }
}

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

data class ReverbConfig(
    val enabled: Boolean = false,
    val roomSize: Float = 0.5f, // 0.0 to 1.0
    val damping: Float = 0.5f,  // 0.0 to 1.0
    val wetLevel: Float = 0.25f, // 0.0 to 1.0
    val preset: String = "Studio"
)

data class DirectVolumeConfig(
    val enabled: Boolean = true,
    val volume: Float = 1.0f, // 0.0 to 1.0
    val ditherEnabled: Boolean = false // Off by default to guarantee zero-noise floor / zero hiss
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
    val reverbConfig: ReverbConfig = ReverbConfig(),
    val directVolumeConfig: DirectVolumeConfig = DirectVolumeConfig(),
    val isAbComparing: Boolean = false,
    val abLoudnessOffsetDb: Float = 0.0f
) {
    val isDspBypassed: Boolean
        get() {
            val isEqFlat = !eqEnabled || (preampGainDb == 0.0f && bands.all { it.gainDb == 0.0f })
            val isToneFlat = toneControls.bassGainDb == 0.0f && toneControls.trebleGainDb == 0.0f
            val isLoudnessOff = !loudnessConfig.enabled
            val isBassOff = !harmonicBassConfig.enabled
            val isConvolverOff = !convolverConfig.enabled
            val isReverbOff = !reverbConfig.enabled
            val isCrossfeedOff = !crossfeedEnabled
            val isWidthNormal = stereoWidth == 1.0f
            return isEqFlat && isToneFlat && isLoudnessOff && isBassOff && isConvolverOff && isReverbOff && isCrossfeedOff && isWidthNormal
        }

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

enum class GlassQuality(val displayName: String) {
    FULL("Full Quality"),
    BALANCED("Balanced"),
    LIGHT("Light (Plain Compose)")
}

enum class MotionLevel(val displayName: String) {
    FULL("Full Springs & Dynamics"),
    REDUCED("Reduced Motion (Gentle)"),
    OFF("Off (Instant / No Animations)")
}

data class GlassThemeConfig(
    val glassQuality: GlassQuality = GlassQuality.BALANCED,
    val motionLevel: MotionLevel = MotionLevel.FULL,
    val glassStrength: String = "BALANCED", // SUBTLE, BALANCED, DRAMATIC, OFF
    val themeMode: String = "SYSTEM",       // SYSTEM, DARK, LIGHT, OLED
    val accentSource: String = "ARTWORK",   // ARTWORK, MATERIAL_YOU, PRESET
    val spacingDensity: String = "COMFORTABLE", // COMPACT, COMFORTABLE, ROOMY
    val cornerRoundness: String = "ROUNDED",   // SHARP (8dp), ROUNDED (16dp), PILL (24dp)
    val animatedBackdrop: Boolean = true,
    val blurRadiusDp: Float = 28.0f,
    val tintAlpha: Float = 0.65f,
    val borderAlpha: Float = 0.30f,
    val noiseGrainAlpha: Float = 0.04f,
    val isOledBlack: Boolean = false,
    val useDynamicAccent: Boolean = true,
    val fixedAccentColorArgb: Long = 0xFFE8D5B7L, // Cashmere Cream / Warm Beige default
    val cornerRadiusDp: Float = 16.0f,
    val isPerformanceMode: Boolean = false,
    val refractionShaderEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val shaderMode: String = "AUTO", // AUTO, FULL, SIMPLE, BLUR_ONLY, OFF
    val glassOpacity: Float = 0.65f, // 0.0 to 1.0 (fill opacity)
    val blurStrength: Float = 0.70f, // 0.0 to 1.0 (blur radius scale)
    val glassTexture: Float = 0.40f, // 0.0 to 1.0 (grain scale)
    val edgeHighlightStrength: Float = 0.60f, // 0.0 to 1.0 (rim light & chromatic fringe)
    val isFullyTransparent: Boolean = false,
    val keepTextReadable: Boolean = true,
    val backgroundAnimationStyle: String = "LIQUID_BLOBS", // LIQUID_BLOBS, AURORA, MESH_GRADIENT, ALBUM_ART_BLUR, SLOW_CAUSTICS, STATIC
    val backgroundAnimationSpeed: Float = 1.0f,            // 0.2f to 3.0f
    val backgroundAnimationIntensity: Float = 0.8f,        // 0.1f to 1.0f
    val backgroundColorSource: String = "ARTWORK",         // ARTWORK, PALETTE, CUSTOM
    val animateOnlyWhilePlaying: Boolean = true,
    val frameRateCap: String = "AUTO",                     // AUTO, 60, 90, 120
    val renderQuality: String = "MAX_NATIVE",              // MAX_NATIVE, HIGH, BALANCED, LOW, AUTO_ADAPTIVE
    val antiBandingDithering: Boolean = true,
    val fontFamily: String = "SYSTEM",                     // SYSTEM, INTER, OUTFIT, JETBRAINS_MONO
    val showDockSearchButton: Boolean = true,
    val showMiniPlayerWave: Boolean = true,
    val formatBadgeVisible: Boolean = true,
    val lensRefractionStrength: Float = 0.85f,             // 0.50f (Subtle), 0.85f (Balanced), 1.25f (Strong)
    val lensRimSpecular: Float = 0.85f,                    // 0.0f to 1.0f
    val animationPreset: String = "SMOOTH",                // OFF, MINIMAL, SMOOTH, BOUNCY, CUSTOM
    val motionSpeed: Float = 1.0f,                         // 0.5f to 2.0f
    val springBounciness: Float = 0.82f,                   // 0.5f to 1.0f (damping ratio)
    val springStiffness: Float = 380f,                     // 150f to 1000f
    val switchOvershootEnabled: Boolean = true,
    val pressScaleEnabled: Boolean = true,
    val pressScaleStrength: Float = 0.96f,                 // 0.90f to 0.98f
    val staggeredListAnimation: Boolean = true,
    val lyricsAnimationEnabled: Boolean = true,
    val screenTransitionStyle: String = "SLIDE",           // SLIDE, FADE, SHARED
    val reduceMotion: Boolean = false,
    val dockGlassLensEnabled: Boolean = true,
    val dockGlassRimEnabled: Boolean = true,
    val dockGlassPuckEnabled: Boolean = true,
    val dockAutoHideEnabled: Boolean = false,
    val dockAutoHideMiniPlayer: Boolean = false,
    val dockScrollSensitivity: Float = 1.0f,
    val dockReturnDelayMs: Long = 1200L
) {
    companion object {
        val PRESET_OBSIDIAN_LIQUID = GlassThemeConfig(
            glassStrength = "BALANCED",
            themeMode = "DARK",
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFF6366F1,
            glassOpacity = 0.55f,
            blurStrength = 0.85f,
            glassTexture = 0.35f,
            edgeHighlightStrength = 0.70f,
            backgroundAnimationStyle = "SLOW_CAUSTICS",
            cornerRoundness = "ROUNDED",
            spacingDensity = "COMFORTABLE",
            fontFamily = "INTER",
            lensRefractionStrength = 0.85f
        )

        val PRESET_FROST_PEARL = GlassThemeConfig(
            glassStrength = "BALANCED",
            themeMode = "LIGHT",
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFF06B6D4,
            glassOpacity = 0.75f,
            blurStrength = 0.90f,
            glassTexture = 0.45f,
            edgeHighlightStrength = 0.80f,
            backgroundAnimationStyle = "AURORA",
            cornerRoundness = "PILL",
            spacingDensity = "ROOMY",
            fontFamily = "OUTFIT",
            lensRefractionStrength = 1.0f
        )

        val PRESET_AMBER_FLARE = GlassThemeConfig(
            glassStrength = "BALANCED",
            themeMode = "DARK",
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFFF59E0B,
            glassOpacity = 0.60f,
            blurStrength = 0.70f,
            glassTexture = 0.30f,
            edgeHighlightStrength = 0.85f,
            backgroundAnimationStyle = "LIQUID_BLOBS",
            cornerRoundness = "ROUNDED",
            spacingDensity = "COMFORTABLE",
            fontFamily = "INTER",
            lensRefractionStrength = 0.85f
        )

        val PRESET_NEON_AURORA = GlassThemeConfig(
            glassStrength = "BALANCED",
            themeMode = "DARK",
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFF10B981,
            glassOpacity = 0.50f,
            blurStrength = 0.80f,
            glassTexture = 0.40f,
            edgeHighlightStrength = 0.90f,
            backgroundAnimationStyle = "AURORA",
            cornerRoundness = "PILL",
            spacingDensity = "COMFORTABLE",
            fontFamily = "OUTFIT",
            lensRefractionStrength = 1.15f
        )

        val PRESET_STEALTH_OLED = GlassThemeConfig(
            glassStrength = "OFF",
            themeMode = "OLED",
            isOledBlack = true,
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFFCBD5E1,
            glassOpacity = 0.20f,
            blurStrength = 0.0f,
            glassTexture = 0.0f,
            edgeHighlightStrength = 0.50f,
            backgroundAnimationStyle = "STATIC",
            cornerRoundness = "SHARP",
            spacingDensity = "COMPACT",
            fontFamily = "JETBRAINS_MONO",
            lensRefractionStrength = 0.50f
        )

        val PRESET_MINIMAL_GLASS = GlassThemeConfig(
            glassStrength = "SUBTLE",
            themeMode = "DARK",
            accentSource = "ARTWORK",
            glassOpacity = 0.30f,
            blurStrength = 0.60f,
            glassTexture = 0.15f,
            edgeHighlightStrength = 0.40f,
            backgroundAnimationStyle = "ALBUM_ART_BLUR",
            cornerRoundness = "ROUNDED",
            spacingDensity = "COMFORTABLE",
            fontFamily = "SYSTEM",
            lensRefractionStrength = 0.70f
        )
    }
    fun isShaderActive(selfTestPassed: Boolean): Boolean {
        if (isPerformanceMode) return false
        if (isFullyTransparent || blurStrength <= 0.001f) return false
        return when (shaderMode) {
            "AUTO" -> selfTestPassed && refractionShaderEnabled
            "FULL", "SIMPLE" -> true
            "BLUR_ONLY", "OFF" -> false
            else -> selfTestPassed && refractionShaderEnabled
        }
    }

    fun chromaticFringeValue(): Float {
        if (shaderMode == "SIMPLE" || isFullyTransparent) return 0.0f
        return edgeHighlightStrength
    }

    fun grainStrengthValue(): Float {
        if (shaderMode == "SIMPLE" || isFullyTransparent) return 0.0f
        return glassTexture
    }

    val effectiveCornerRadiusDp: Float
        get() = when (cornerRoundness) {
            "SHARP" -> 8f
            "PILL" -> 24f
            else -> 16f
        }

    val effectiveBlurDp: Float
        get() {
            if (isPerformanceMode || shaderMode == "OFF" || isFullyTransparent || blurStrength <= 0.001f) return 0f
            return 32f * blurStrength.coerceIn(0f, 1f)
        }

    val effectiveTintAlpha: Float
        get() {
            if (isFullyTransparent) return 0.0f
            return glassOpacity.coerceIn(0f, 1f)
        }
}
