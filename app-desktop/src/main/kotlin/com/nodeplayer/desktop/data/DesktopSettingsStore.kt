package com.nodeplayer.desktop.data

import com.nodeplayer.common.platform.KeyValueStore
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassControlsSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings and theme storage for Windows desktop.
 * Persists key-value properties to %APPDATA%\NodePlayer\settings.properties
 * and liquid glass controls configuration to %APPDATA%\NodePlayer\settings_glass_controls.json
 */
class DesktopSettingsStore : KeyValueStore {

    private val settingsFile: File
    private val glassControlsFile: File
    private val memoryMap = ConcurrentHashMap<String, String>()

    init {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "NodePlayer")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        settingsFile = File(dir, "settings.properties")
        glassControlsFile = File(dir, "settings_glass_controls.json")
        loadFromFile()
    }

    private fun loadFromFile() {
        if (!settingsFile.exists()) return
        try {
            settingsFile.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val splitIdx = trimmed.indexOf('=')
                    if (splitIdx > 0) {
                        val key = trimmed.substring(0, splitIdx).trim()
                        val value = trimmed.substring(splitIdx + 1).trim()
                        memoryMap[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToFile() {
        synchronized(this) {
            try {
                settingsFile.bufferedWriter().use { writer ->
                    writer.write("# Node Player Windows Settings\n")
                    for ((key, value) in memoryMap) {
                        writer.write("$key=$value\n")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getString(key: String, defaultValue: String): String {
        return memoryMap[key] ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        memoryMap[key] = value
        saveToFile()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return memoryMap[key]?.toIntOrNull() ?: defaultValue
    }

    override fun putInt(key: String, value: Int) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return memoryMap[key]?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return memoryMap[key]?.toFloatOrNull() ?: defaultValue
    }

    override fun putFloat(key: String, value: Float) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return memoryMap[key]?.toLongOrNull() ?: defaultValue
    }

    override fun putLong(key: String, value: Long) {
        memoryMap[key] = value.toString()
        saveToFile()
    }

    override fun contains(key: String): Boolean {
        return memoryMap.containsKey(key)
    }

    override fun remove(key: String) {
        memoryMap.remove(key)
        saveToFile()
    }

    override fun clear() {
        memoryMap.clear()
        saveToFile()
    }

    fun getLastMusicFolder(): String {
        return getString("last_music_folder", "")
    }

    fun setLastMusicFolder(path: String) {
        putString("last_music_folder", path)
    }

    fun loadGlassControlsConfig(): GlassControlsConfig {
        if (!glassControlsFile.exists()) {
            val storedJson = getString("glass_controls_config_json", "")
            if (storedJson.isNotBlank()) {
                return GlassControlsSerializer.fromJson(storedJson)
            }
            return GlassControlsConfig.PRESET_BALANCED
        }
        return try {
            val json = glassControlsFile.readText()
            GlassControlsSerializer.fromJson(json)
        } catch (e: Exception) {
            GlassControlsConfig.PRESET_BALANCED
        }
    }

    fun saveGlassControlsConfig(config: GlassControlsConfig) {
        val json = GlassControlsSerializer.toJson(config)
        synchronized(this) {
            try {
                val parent = glassControlsFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val tempFile = File(parent, "settings_glass_controls.json.tmp")
                tempFile.writeText(json)
                if (glassControlsFile.exists()) {
                    glassControlsFile.delete()
                }
                tempFile.renameTo(glassControlsFile)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    glassControlsFile.writeText(json)
                } catch (ignored: Exception) {}
            }
        }
        putString("glass_controls_config_json", json)
        putBoolean("controls_glass_enabled", config.masterEnabled)
    }

    fun loadThemeConfig(): com.nodeplayer.model.GlassThemeConfig {
        val controlsConfig = loadGlassControlsConfig()
        return com.nodeplayer.model.GlassThemeConfig(
            fixedAccentColorArgb = getLong("fixedAccentColorArgb", 0xFFE8D5B7L),
            backgroundAnimationStyle = getString("backgroundAnimationStyle", "AURORA"),
            backgroundAnimationSpeed = getFloat("backgroundAnimationSpeed", 1.0f),
            backgroundAnimationIntensity = getFloat("backgroundAnimationIntensity", 0.8f),
            backgroundColorSource = getString("backgroundColorSource", "CUSTOM"),
            glassOpacity = getFloat("glassOpacity", 0.55f),
            blurStrength = getFloat("blurStrength", 0.70f),
            edgeHighlightStrength = getFloat("edgeHighlightStrength", 0.65f),
            lensRefractionStrength = getFloat("lensRefractionStrength", 0.85f),
            glassTexture = getFloat("glassTexture", 0.35f),
            isOledBlack = getBoolean("isOledBlack", false),
            isPerformanceMode = getBoolean("isPerformanceMode", false),
            animateOnlyWhilePlaying = getBoolean("animateOnlyWhilePlaying", false),
            isFullyTransparent = getBoolean("isFullyTransparent", false),
            keepTextReadable = getBoolean("keepTextReadable", true),
            dockGlassLensEnabled = getBoolean("dockGlassLensEnabled", true),
            dockGlassRimEnabled = getBoolean("dockGlassRimEnabled", true),
            dockGlassPuckEnabled = getBoolean("dockGlassPuckEnabled", true),
            controlsGlassEnabled = getBoolean("controls_glass_enabled", controlsConfig.masterEnabled),
            controlsConfig = controlsConfig
        )
    }

    fun saveThemeConfig(cfg: com.nodeplayer.model.GlassThemeConfig) {
        putLong("fixedAccentColorArgb", cfg.fixedAccentColorArgb)
        putString("backgroundAnimationStyle", cfg.backgroundAnimationStyle)
        putFloat("backgroundAnimationSpeed", cfg.backgroundAnimationSpeed)
        putFloat("backgroundAnimationIntensity", cfg.backgroundAnimationIntensity)
        putString("backgroundColorSource", cfg.backgroundColorSource)
        putFloat("glassOpacity", cfg.glassOpacity)
        putFloat("blurStrength", cfg.blurStrength)
        putFloat("edgeHighlightStrength", cfg.edgeHighlightStrength)
        putFloat("lensRefractionStrength", cfg.lensRefractionStrength)
        putFloat("glassTexture", cfg.glassTexture)
        putBoolean("isOledBlack", cfg.isOledBlack)
        putBoolean("isPerformanceMode", cfg.isPerformanceMode)
        putBoolean("animateOnlyWhilePlaying", cfg.animateOnlyWhilePlaying)
        putBoolean("isFullyTransparent", cfg.isFullyTransparent)
        putBoolean("keepTextReadable", cfg.keepTextReadable)
        putBoolean("dockGlassLensEnabled", cfg.dockGlassLensEnabled)
        putBoolean("dockGlassRimEnabled", cfg.dockGlassRimEnabled)
        putBoolean("dockGlassPuckEnabled", cfg.dockGlassPuckEnabled)
        putBoolean("controls_glass_enabled", cfg.controlsGlassEnabled)
        saveGlassControlsConfig(cfg.controlsConfig)
    }

    fun loadEffectConfig(): com.nodeplayer.model.AudioEffectConfig {
        val defaultConfig = com.nodeplayer.model.AudioEffectConfig()
        val eqEnabled = getBoolean("eq_enabled", false)
        val presetName = getString("eq_preset", "Flat")
        val preampGainDb = getFloat("eq_preamp", 0.0f)
        val limiterEnabled = getBoolean("limiter_enabled", true)
        val stereoWidth = getFloat("stereo_width", 1.0f)

        val updatedBands = defaultConfig.bands.mapIndexed { idx, band ->
            val gain = getFloat("eq_band_$idx", band.gainDb)
            band.copy(gainDb = gain)
        }

        val tone = com.nodeplayer.model.ToneControlConfig(
            bassGainDb = getFloat("tone_bass", 0.0f),
            trebleGainDb = getFloat("tone_treble", 0.0f)
        )

        val loudness = com.nodeplayer.model.LoudnessConfig(
            enabled = getBoolean("loudness_enabled", false),
            strength = getFloat("loudness_strength", 0.5f)
        )

        val harmonic = com.nodeplayer.model.HarmonicBassConfig(
            enabled = getBoolean("harmonic_bass_enabled", false),
            frequencyHz = getFloat("harmonic_bass_freq", 60.0f),
            harmonicGain = getFloat("harmonic_bass_gain", 0.5f),
            highPassOriginal = getBoolean("harmonic_bass_hipass", false)
        )

        val reverb = com.nodeplayer.model.ReverbConfig(
            enabled = getBoolean("reverb_enabled", false),
            roomSize = getFloat("reverb_room_size", 0.5f),
            damping = getFloat("reverb_damping", 0.5f),
            wetLevel = getFloat("reverb_wet", 0.25f),
            preset = getString("reverb_preset", "Studio")
        )

        return defaultConfig.copy(
            eqEnabled = eqEnabled,
            presetName = presetName,
            preampGainDb = preampGainDb,
            bands = updatedBands,
            limiterEnabled = limiterEnabled,
            stereoWidth = stereoWidth,
            toneControls = tone,
            loudnessConfig = loudness,
            harmonicBassConfig = harmonic,
            reverbConfig = reverb
        )
    }

    fun saveEffectConfig(cfg: com.nodeplayer.model.AudioEffectConfig) {
        putBoolean("eq_enabled", cfg.eqEnabled)
        putString("eq_preset", cfg.presetName)
        putFloat("eq_preamp", cfg.preampGainDb)
        putBoolean("limiter_enabled", cfg.limiterEnabled)
        putFloat("stereo_width", cfg.stereoWidth)

        cfg.bands.forEachIndexed { idx, band ->
            putFloat("eq_band_$idx", band.gainDb)
        }

        putFloat("tone_bass", cfg.toneControls.bassGainDb)
        putFloat("tone_treble", cfg.toneControls.trebleGainDb)

        putBoolean("loudness_enabled", cfg.loudnessConfig.enabled)
        putFloat("loudness_strength", cfg.loudnessConfig.strength)

        putBoolean("harmonic_bass_enabled", cfg.harmonicBassConfig.enabled)
        putFloat("harmonic_bass_freq", cfg.harmonicBassConfig.frequencyHz)
        putFloat("harmonic_bass_gain", cfg.harmonicBassConfig.harmonicGain)
        putBoolean("harmonic_bass_hipass", cfg.harmonicBassConfig.highPassOriginal)

        putBoolean("reverb_enabled", cfg.reverbConfig.enabled)
        putFloat("reverb_room_size", cfg.reverbConfig.roomSize)
        putFloat("reverb_damping", cfg.reverbConfig.damping)
        putFloat("reverb_wet", cfg.reverbConfig.wetLevel)
        putString("reverb_preset", cfg.reverbConfig.preset)
    }
}
