package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig

/**
 * Standard semantic palette for Node Player's Liquid Glass UI system.
 * Eliminates inline Color(0x...) literals across screens per POL-03.
 */
object LiquidColors {
    // Surfaces
    val OledBlack: Color = Color(0xFF000000)
    val SurfaceDark: Color = Color(0xFF0F1420)
    val SurfaceElevated: Color = Color(0xFF161B26)
    val SurfaceCard: Color = Color(0xFF1E232F)
    val SurfaceDialog: Color = Color(0xFF12161F)
    val SurfaceMuted: Color = Color(0xFF11141E)

    // Slate & Deep Surfaces
    val SlateDark: Color = Color(0xFF0F172A)
    val SlateDeep: Color = Color(0xFF0F232A)
    val SlateCharcoal: Color = Color(0xFF14171A)
    val SlateNavy: Color = Color(0xFF1E2333)
    val SlateGray: Color = Color(0xFF37474F)
    val SlateBlueDark: Color = Color(0xFF263238)

    // Accents & Semantics
    val ElectricCyan: Color = Color(0xFF00E5FF)
    val CyanGlow: Color = Color(0xFF00F0FF)
    val CyanDeep: Color = Color(0xFF003840)
    val CyanDark: Color = Color(0xFF004D40)
    val CyanMuted: Color = Color(0xFF0097A7)
    val SkyIce: Color = Color(0xFFBAE6FD)

    val AmberWarning: Color = Color(0xFFFFB300)
    val AmberWarm: Color = Color(0xFFF59E0B)
    val AmberLight: Color = Color(0xFFFFE082)
    val CoralRed: Color = Color(0xFFFF5252)
    val EmeraldGreen: Color = Color(0xFF10B981)
    val PurpleAccent: Color = Color(0xFF6366F1)
    val OrangeBadge: Color = Color(0xFFFF9100)

    // Hi-Res Gold Badges
    val GoldHiRes: Color = Color(0xFFFFD700)
    val GoldDark: Color = Color(0xFF261D00)
    val GoldLight: Color = Color(0xFFFFECB3)
    val GoldMuted: Color = Color(0xFFB8860B)

    // Subtle borders & dividers
    val DividerSubtle: Color = Color.White.copy(alpha = 0.08f)
    val BorderSubtle: Color = Color.White.copy(alpha = 0.12f)
}

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
