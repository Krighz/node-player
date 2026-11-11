package com.nodeplayer.music.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.music.R

val InterFontFamily = FontFamily(Font(R.font.inter))
val OutfitFontFamily = FontFamily(Font(R.font.outfit))
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono))

fun resolveAppFontFamily(fontFamilyName: String): FontFamily {
    return when (fontFamilyName.uppercase()) {
        "INTER" -> InterFontFamily
        "OUTFIT" -> OutfitFontFamily
        "JETBRAINS_MONO" -> JetBrainsMonoFontFamily
        else -> FontFamily.Default
    }
}

fun createThemedTypography(fontFamily: FontFamily): Typography {
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily)
    )
}

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

    val currentFontFamily = resolveAppFontFamily(themeConfig.fontFamily)
    val themedTypography = remember(currentFontFamily) {
        createThemedTypography(currentFontFamily)
    }

    CompositionLocalProvider(
        LocalAccentColor provides animatedAccent,
        LocalGlassThemeConfig provides themeConfig,
        com.nodeplayer.ui.theme.LocalGlassThemeConfig provides themeConfig,
        com.nodeplayer.ui.theme.LocalMotionConfig provides themeConfig,
        LocalGlassControlsConfig provides themeConfig.controlsConfig,
        LocalDensityPadding provides densityPadding,
        LocalCornerRadius provides cornerRadius,
        LocalGlassTokens provides glassTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = themedTypography,
            content = content
        )
    }
}
