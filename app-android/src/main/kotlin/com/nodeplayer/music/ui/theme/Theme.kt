package com.nodeplayer.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

val DefaultAccent = Color(0xFF6366F1) // Indigo

private val DarkColorScheme = darkColorScheme(
    primary = DefaultAccent,
    background = Color(0xFF0C0D14),
    surface = Color(0xFF141622),
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEDF5),
    onSurface = Color(0xFFEDEDF5)
)

private val LightColorScheme = lightColorScheme(
    primary = DefaultAccent,
    background = Color(0xFFF4F5FB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF14151F),
    onSurface = Color(0xFF14151F)
)

val LocalAccentColor = staticCompositionLocalOf { DefaultAccent }

@Composable
fun NodePlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = DefaultAccent,
    isOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 600),
        label = "AccentAnimation"
    )

    val colorScheme = if (darkTheme || isOled) {
        DarkColorScheme.copy(
            primary = animatedAccent,
            background = if (isOled) Color.Black else Color(0xFF0C0D14),
            surface = if (isOled) Color(0xFF0A0A0A) else Color(0xFF141622)
        )
    } else {
        LightColorScheme.copy(
            primary = animatedAccent
        )
    }

    CompositionLocalProvider(LocalAccentColor provides animatedAccent) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
