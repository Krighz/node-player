package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig

/**
 * Unified design tokens for the Liquid Glass system.
 * Governs corner roundness, optical refraction, specular rim highlight,
 * vibrancy, blur, and chromatic dispersion across Android and Windows.
 */
data class GlassDesignTokens(
    // Radii
    val dockCapsuleRadius: Dp = 32.dp,
    val puckRadius: Dp = 22.dp,
    val cardRadius: Dp = 16.dp,
    val controlRadius: Dp = 12.dp,

    // Optics & Refraction
    val refractionHeightDock: Dp = 18.dp,
    val refractionAmountDock: Dp = 16.dp,
    val refractionHeightPuck: Dp = 10.dp,
    val refractionAmountPuck: Dp = 12.dp,
    val chromaticDispersionAmount: Float = 0.25f,
    val blurRadius: Dp = 14.dp,

    // Rim & Specular Shimmer
    val rimWidth: Dp = 1.dp,
    val rimSpecularAlpha: Float = 0.85f,
    val rimAmbientAlpha: Float = 0.30f,

    // Vibrancy & Tint
    val tintAlpha: Float = 0.65f,
    val darkTintFill: Color = Color(0xFF0F1424),
    val lightTintFill: Color = Color(0xFFF8FAFC)
) {
    companion object {
        fun resolve(config: GlassThemeConfig, isDark: Boolean): GlassDesignTokens {
            val quality = config.glassQuality
            return when (quality) {
                GlassQuality.FULL -> GlassDesignTokens(
                    refractionHeightDock = 20.dp,
                    refractionAmountDock = 18.dp,
                    refractionHeightPuck = 11.dp,
                    refractionAmountPuck = 13.dp,
                    chromaticDispersionAmount = 0.30f,
                    blurRadius = 16.dp,
                    rimSpecularAlpha = 0.90f,
                    tintAlpha = config.tintAlpha
                )
                GlassQuality.BALANCED -> GlassDesignTokens(
                    refractionHeightDock = 16.dp,
                    refractionAmountDock = 14.dp,
                    refractionHeightPuck = 9.dp,
                    refractionAmountPuck = 10.dp,
                    chromaticDispersionAmount = 0.15f,
                    blurRadius = 12.dp,
                    rimSpecularAlpha = 0.80f,
                    tintAlpha = config.tintAlpha
                )
                GlassQuality.LIGHT -> GlassDesignTokens(
                    refractionHeightDock = 0.dp,
                    refractionAmountDock = 0.dp,
                    refractionHeightPuck = 0.dp,
                    refractionAmountPuck = 0.dp,
                    chromaticDispersionAmount = 0.0f,
                    blurRadius = 0.dp,
                    rimSpecularAlpha = 0.40f,
                    tintAlpha = config.tintAlpha
                )
            }
        }
    }
}
