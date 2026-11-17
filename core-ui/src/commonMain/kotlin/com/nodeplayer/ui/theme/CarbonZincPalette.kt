package com.nodeplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Neutral Carbon / Zinc Palette
object CarbonZincPalette {
    val Background = Color(0xFF09090B)        // Main screen floor
    val SurfaceContainer = Color(0xFF121215)  // "Your Mix" cards & Switcher outer track
    val SurfaceElevated = Color(0xFF1C1C21)   // Active "YT Music" pill, Mood pills & Play button
    val SurfaceGlass = Color(0xCC141418)      // Mini-player & Bottom Nav base before blur
    
    val BorderSubtle = Color.White.copy(alpha = 0.08f) // 1.dp stroke for cards & inactive pills
    val BorderActive = Color.White.copy(alpha = 0.16f) // 1.dp stroke for active switcher & Play pill
    
    val TextPrimary = Color(0xFFF4F4F6)       // Crisp off-white for titles
    val TextSecondary = Color(0xFF9CA3AF)     // Zinc grey for artists & inactive tabs
    val AccentTeal = Color(0xFF5EEAD4)        // Muted cyan-teal dot & codec badge
}

// Typography Scale matching the mockup
fun appTypography(fontFamily: FontFamily = FontFamily.SansSerif) = Typography(
    // "Your Mix" section title
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        color = CarbonZincPalette.TextPrimary
    ),
    // "NEW MUSIC VIDEOS" tracked-out section header
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.2.sp,
        color = CarbonZincPalette.TextPrimary
    ),
    // Song titles inside cards ("Somebody That I Used to Know", "Daylight")
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
        color = CarbonZincPalette.TextPrimary
    ),
    // Artist subtitles ("Gotye", "David Kushner")
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = CarbonZincPalette.TextSecondary
    ),
    // Filter chips ("Pop", "Chill") & "Play" button
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
        color = CarbonZincPalette.TextPrimary
    )
)
