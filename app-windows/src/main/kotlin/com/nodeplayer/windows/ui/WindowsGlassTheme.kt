package com.nodeplayer.windows.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlassColors {
    val BackgroundDark = Color(0xFF090D16)
    val GlassSurface = Color(0x1AFFFFFF)
    val GlassSurfaceActive = Color(0x33FFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
    val GlassRim = Color(0x4DFFFFFF)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentCyan = Color(0xFF06B6D4)
    val AccentPink = Color(0xFFEC4899)
    val GlassCardBg = Color(0x140F172A)
    val HiResGold = Color(0xFFF59E0B)
}

val GlassDarkColorScheme = darkColorScheme(
    primary = GlassColors.AccentPurple,
    onPrimary = Color.White,
    secondary = GlassColors.AccentCyan,
    onSecondary = Color.Black,
    background = GlassColors.BackgroundDark,
    onBackground = GlassColors.TextPrimary,
    surface = GlassColors.GlassCardBg,
    onSurface = GlassColors.TextPrimary
)

val GlassTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = GlassColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = GlassColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = GlassColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = GlassColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        color = GlassColors.TextMuted
    )
)

@Composable
fun WindowsGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlassDarkColorScheme,
        typography = GlassTypography,
        content = content
    )
}
