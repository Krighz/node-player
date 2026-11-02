package com.nodeplayer.music.data

import android.content.Context
import android.util.Log
import com.nodeplayer.model.GlassThemeConfig
import org.json.JSONObject
import java.io.File

object NodeThemeSerializer {

    private const val TAG = "NodeThemeSerializer"
    const val THEME_FILE_EXTENSION = "nodetheme"

    fun toJson(config: GlassThemeConfig): String {
        val root = JSONObject()
        root.put("format", "NodePlayerTheme")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val theme = JSONObject()
        theme.put("glassStrength", config.glassStrength)
        theme.put("themeMode", config.themeMode)
        theme.put("accentSource", config.accentSource)
        theme.put("spacingDensity", config.spacingDensity)
        theme.put("cornerRoundness", config.cornerRoundness)
        theme.put("animatedBackdrop", config.animatedBackdrop)
        theme.put("blurRadiusDp", config.blurRadiusDp.toDouble())
        theme.put("tintAlpha", config.tintAlpha.toDouble())
        theme.put("borderAlpha", config.borderAlpha.toDouble())
        theme.put("noiseGrainAlpha", config.noiseGrainAlpha.toDouble())
        theme.put("isOledBlack", config.isOledBlack)
        theme.put("useDynamicAccent", config.useDynamicAccent)
        theme.put("fixedAccentColorArgb", config.fixedAccentColorArgb)
        theme.put("cornerRadiusDp", config.cornerRadiusDp.toDouble())
        theme.put("isPerformanceMode", config.isPerformanceMode)
        theme.put("refractionShaderEnabled", config.refractionShaderEnabled)
        theme.put("hapticsEnabled", config.hapticsEnabled)
        theme.put("shaderMode", config.shaderMode)
        theme.put("glassOpacity", config.glassOpacity.toDouble())
        theme.put("blurStrength", config.blurStrength.toDouble())
        theme.put("glassTexture", config.glassTexture.toDouble())
        theme.put("edgeHighlightStrength", config.edgeHighlightStrength.toDouble())
        theme.put("isFullyTransparent", config.isFullyTransparent)
        theme.put("keepTextReadable", config.keepTextReadable)
        theme.put("backgroundAnimationStyle", config.backgroundAnimationStyle)
        theme.put("backgroundAnimationSpeed", config.backgroundAnimationSpeed.toDouble())
        theme.put("backgroundAnimationIntensity", config.backgroundAnimationIntensity.toDouble())
        theme.put("backgroundColorSource", config.backgroundColorSource)
        theme.put("animateOnlyWhilePlaying", config.animateOnlyWhilePlaying)
        theme.put("frameRateCap", config.frameRateCap)
        theme.put("renderQuality", config.renderQuality)
        theme.put("antiBandingDithering", config.antiBandingDithering)
        theme.put("fontFamily", config.fontFamily)
        theme.put("showDockSearchButton", config.showDockSearchButton)
        theme.put("showMiniPlayerWave", config.showMiniPlayerWave)
        theme.put("formatBadgeVisible", config.formatBadgeVisible)
        theme.put("lensRefractionStrength", config.lensRefractionStrength.toDouble())
        theme.put("lensRimSpecular", config.lensRimSpecular.toDouble())

        root.put("themeConfig", theme)
        return root.toString(2)
    }

    fun fromJson(jsonStr: String): GlassThemeConfig? {
        return try {
            val root = JSONObject(jsonStr)
            val theme = if (root.has("themeConfig")) root.getJSONObject("themeConfig") else root
            val def = GlassThemeConfig()

            GlassThemeConfig(
                glassStrength = theme.optString("glassStrength", def.glassStrength),
                themeMode = theme.optString("themeMode", def.themeMode),
                accentSource = theme.optString("accentSource", def.accentSource),
                spacingDensity = theme.optString("spacingDensity", def.spacingDensity),
                cornerRoundness = theme.optString("cornerRoundness", def.cornerRoundness),
                animatedBackdrop = theme.optBoolean("animatedBackdrop", def.animatedBackdrop),
                blurRadiusDp = theme.optDouble("blurRadiusDp", def.blurRadiusDp.toDouble()).toFloat(),
                tintAlpha = theme.optDouble("tintAlpha", def.tintAlpha.toDouble()).toFloat(),
                borderAlpha = theme.optDouble("borderAlpha", def.borderAlpha.toDouble()).toFloat(),
                noiseGrainAlpha = theme.optDouble("noiseGrainAlpha", def.noiseGrainAlpha.toDouble()).toFloat(),
                isOledBlack = theme.optBoolean("isOledBlack", def.isOledBlack),
                useDynamicAccent = theme.optBoolean("useDynamicAccent", def.useDynamicAccent),
                fixedAccentColorArgb = theme.optLong("fixedAccentColorArgb", def.fixedAccentColorArgb),
                cornerRadiusDp = theme.optDouble("cornerRadiusDp", def.cornerRadiusDp.toDouble()).toFloat(),
                isPerformanceMode = theme.optBoolean("isPerformanceMode", def.isPerformanceMode),
                refractionShaderEnabled = theme.optBoolean("refractionShaderEnabled", def.refractionShaderEnabled),
                hapticsEnabled = theme.optBoolean("hapticsEnabled", def.hapticsEnabled),
                shaderMode = theme.optString("shaderMode", def.shaderMode),
                glassOpacity = theme.optDouble("glassOpacity", def.glassOpacity.toDouble()).toFloat(),
                blurStrength = theme.optDouble("blurStrength", def.blurStrength.toDouble()).toFloat(),
                glassTexture = theme.optDouble("glassTexture", def.glassTexture.toDouble()).toFloat(),
                edgeHighlightStrength = theme.optDouble("edgeHighlightStrength", def.edgeHighlightStrength.toDouble()).toFloat(),
                isFullyTransparent = theme.optBoolean("isFullyTransparent", def.isFullyTransparent),
                keepTextReadable = theme.optBoolean("keepTextReadable", def.keepTextReadable),
                backgroundAnimationStyle = theme.optString("backgroundAnimationStyle", def.backgroundAnimationStyle),
                backgroundAnimationSpeed = theme.optDouble("backgroundAnimationSpeed", def.backgroundAnimationSpeed.toDouble()).toFloat(),
                backgroundAnimationIntensity = theme.optDouble("backgroundAnimationIntensity", def.backgroundAnimationIntensity.toDouble()).toFloat(),
                backgroundColorSource = theme.optString("backgroundColorSource", def.backgroundColorSource),
                animateOnlyWhilePlaying = theme.optBoolean("animateOnlyWhilePlaying", def.animateOnlyWhilePlaying),
                frameRateCap = theme.optString("frameRateCap", def.frameRateCap),
                renderQuality = theme.optString("renderQuality", def.renderQuality),
                antiBandingDithering = theme.optBoolean("antiBandingDithering", def.antiBandingDithering),
                fontFamily = theme.optString("fontFamily", def.fontFamily),
                showDockSearchButton = theme.optBoolean("showDockSearchButton", def.showDockSearchButton),
                showMiniPlayerWave = theme.optBoolean("showMiniPlayerWave", def.showMiniPlayerWave),
                formatBadgeVisible = theme.optBoolean("formatBadgeVisible", def.formatBadgeVisible),
                lensRefractionStrength = theme.optDouble("lensRefractionStrength", def.lensRefractionStrength.toDouble()).toFloat(),
                lensRimSpecular = theme.optDouble("lensRimSpecular", def.lensRimSpecular.toDouble()).toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theme JSON", e)
            null
        }
    }

    fun exportToFile(context: Context, config: GlassThemeConfig, themeName: String = "custom"): File {
        val json = toJson(config)
        val sanitized = themeName.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val fileName = "node_theme_${sanitized}_${System.currentTimeMillis()}.$THEME_FILE_EXTENSION"
        val file = File(context.cacheDir, fileName)
        file.writeText(json)
        return file
    }
}
