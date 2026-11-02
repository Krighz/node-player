package com.nodeplayer.music.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassThemeConfig

val DefaultAccent = Color(0xFF6366F1) // Indigo

private val DarkColorScheme = darkColorScheme(
    primary = DefaultAccent,
    background = Color(0xFF0C0D14),
    surface = Color(0xFF141622),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B3C7)
)

private val LightColorScheme = lightColorScheme(
    primary = DefaultAccent,
    background = Color(0xFFF4F5FB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF14151F),
    onSurface = Color(0xFF14151F),
    onSurfaceVariant = Color(0xFF5A5D72)
)

val LocalAccentColor = staticCompositionLocalOf { DefaultAccent }
val LocalGlassThemeConfig = staticCompositionLocalOf { GlassThemeConfig() }
val LocalDensityPadding = staticCompositionLocalOf { 16.dp }
val LocalCornerRadius = staticCompositionLocalOf { 16.dp }

@Composable
fun NodePlayerTheme(
    themeConfig: GlassThemeConfig = GlassThemeConfig(),
    artworkAccentColor: Color = DefaultAccent,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeConfig.themeMode) {
        "LIGHT" -> false
        "DARK", "OLED" -> true
        else -> systemInDark // SYSTEM
    }

    val isOled = themeConfig.isOledBlack || themeConfig.themeMode == "OLED"

    // Resolve target accent color
    val rawAccent = when (themeConfig.accentSource) {
        "MATERIAL_YOU" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
            } else {
                artworkAccentColor
            }
        }
        "PRESET" -> Color(themeConfig.fixedAccentColorArgb)
        else -> artworkAccentColor // "ARTWORK"
    }

    val animatedAccent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 500),
        label = "AccentAnimation"
    )

    val baseScheme = if (themeConfig.accentSource == "MATERIAL_YOU" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) DarkColorScheme else LightColorScheme
    }

    val colorScheme = baseScheme.copy(
        primary = animatedAccent,
        background = if (isOled) Color.Black else if (isDark) Color(0xFF0C0D14) else Color(0xFFF4F5FB),
        surface = if (isOled) Color(0xFF0A0A0A) else if (isDark) Color(0xFF141622) else Color(0xFFFFFFFF)
    )

    val densityPadding: Dp = when (themeConfig.spacingDensity) {
        "COMPACT" -> 10.dp
        "ROOMY" -> 22.dp
        else -> 16.dp // COMFORTABLE
    }

    val cornerRadius: Dp = themeConfig.effectiveCornerRadiusDp.dp

    val glassTokens = GlassTokensData(
        opacity = themeConfig.glassOpacity,
        blurStrength = themeConfig.blurStrength,
        grainStrength = themeConfig.glassTexture,
        rimStrength = themeConfig.edgeHighlightStrength,
        isFullyTransparent = themeConfig.isFullyTransparent,
        keepTextReadable = themeConfig.keepTextReadable,
        isPerformanceMode = themeConfig.isPerformanceMode
    )

    CompositionLocalProvider(
        LocalAccentColor provides animatedAccent,
        LocalGlassThemeConfig provides themeConfig,
        LocalDensityPadding provides densityPadding,
        LocalCornerRadius provides cornerRadius,
        LocalGlassTokens provides glassTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
