package com.nodeplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

object GlassTokens {
    val defaultCornerRadius: Dp = 22.dp
    val cardCornerRadius: Dp = 18.dp
    val pillCornerRadius: Dp = 999.dp
    val defaultBlurRadius: Dp = 24.dp
    val borderWidth: Dp = 1.dp

    // Lit-edge gradient borders (stronger shine on top edge)
    fun lightBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f)
        )
    )

    fun darkBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.02f)
        )
    )

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.06f),
            Color.Transparent
        )
    )
}

@Composable
fun Modifier.glassSurface(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(GlassTokens.defaultCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float = 0.65f,
    customTint: Color? = null
): Modifier {
    val baseTint = when {
        customTint != null -> customTint.copy(alpha = tintAlpha)
        isOled -> Color(0xFF000000).copy(alpha = 0.85f)
        isDark -> Color(0xFF14151F).copy(alpha = tintAlpha)
        else -> Color(0xFFF0F2FA).copy(alpha = tintAlpha)
    }

    val borderBrush = when {
        isOled -> GlassTokens.oledBorderBrush()
        isDark -> GlassTokens.darkBorderBrush()
        else -> GlassTokens.lightBorderBrush()
    }

    // Support blur on Android 12+ (API 31+); fallback cleanly to translucent frosted surface below 12
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null

    return this
        .clip(shape)
        .then(
            if (canBlur && hazeState != null) {
                Modifier.hazeChild(
                    state = hazeState,
                    shape = shape,
                    tint = baseTint,
                    blurRadius = GlassTokens.defaultBlurRadius
                )
            } else {
                Modifier.background(baseTint, shape)
            }
        )
        .border(
            width = GlassTokens.borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
