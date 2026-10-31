package com.nodeplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val defaultCornerRadius: Dp = 26.dp
    val cardCornerRadius: Dp = 22.dp
    val pillCornerRadius: Dp = 999.dp
    val defaultBlurRadius: Dp = 28.dp
    val borderWidth: Dp = 1.2.dp

    // Liquid Glass illuminated specular edge (Top shine with chromatic prism highlights)
    fun liquidBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xCCFFFFFF), // Brilliant white specular rim
            Color(0x44FFFFFF), // Soft frosted edge
            Color(0x15FFFFFF), // Translucent side
            Color(0x05000000)  // Deep shadow at bottom
        )
    )

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x88FFFFFF),
            Color(0x22FFFFFF),
            Color.Transparent
        )
    )
}

@Composable
fun Modifier.glassSurface(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float = 0.55f,
    customTint: Color? = null
): Modifier {
    val baseTint = when {
        customTint != null -> customTint.copy(alpha = tintAlpha)
        isOled -> Color(0xFF000000).copy(alpha = 0.88f)
        isDark -> Color(0xFF131520).copy(alpha = tintAlpha)
        else -> Color(0xFFEBF0FA).copy(alpha = tintAlpha)
    }

    val borderBrush = if (isOled) GlassTokens.oledBorderBrush() else GlassTokens.liquidBorderBrush()
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
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            baseTint.copy(alpha = (tintAlpha * 1.1f).coerceAtMost(1f)),
                            baseTint.copy(alpha = (tintAlpha * 0.85f))
                        )
                    ),
                    shape
                )
            }
        )
        .border(
            width = GlassTokens.borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
