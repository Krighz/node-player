package com.nodeplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

data class GlassTokensData(
    val opacity: Float = 0.65f,
    val blurStrength: Float = 0.70f,
    val grainStrength: Float = 0.40f,
    val rimStrength: Float = 0.60f,
    val isFullyTransparent: Boolean = false,
    val keepTextReadable: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val defaultCornerRadius: Dp = 26.dp,
    val cardCornerRadius: Dp = 22.dp,
    val pillCornerRadius: Dp = 999.dp,
    val borderWidth: Dp = 1.2.dp
) {
    val effectiveBlurRadius: Dp
        get() {
            if (isPerformanceMode || isFullyTransparent || blurStrength <= 0.001f) return 0.dp
            return (32f * blurStrength).dp
        }

    val effectiveTintAlpha: Float
        get() {
            if (isFullyTransparent) return 0.0f
            return opacity.coerceIn(0f, 1f)
        }

    fun liquidBorderBrush(isOled: Boolean = false): Brush {
        val rimScale = if (isFullyTransparent) 1.0f else rimStrength.coerceIn(0.1f, 1.0f)
        val alphaWhite = (0xCC * rimScale).toInt().coerceIn(0, 255)
        val alphaSoft = (0x44 * rimScale).toInt().coerceIn(0, 255)
        val alphaSide = (0x15 * rimScale).toInt().coerceIn(0, 255)

        return Brush.verticalGradient(
            colors = listOf(
                Color(alphaWhite shl 24 or 0x00FFFFFF),
                Color(alphaSoft shl 24 or 0x00FFFFFF),
                Color(alphaSide shl 24 or 0x00FFFFFF),
                Color(0x05000000)
            )
        )
    }

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x88FFFFFF),
            Color(0x22FFFFFF),
            Color.Transparent
        )
    )
}

val LocalGlassTokens = staticCompositionLocalOf { GlassTokensData() }

object GlassTokens {
    val defaultCornerRadius: Dp = 26.dp
    val cardCornerRadius: Dp = 22.dp
    val pillCornerRadius: Dp = 999.dp
    val defaultBlurRadius: Dp = 28.dp
    val borderWidth: Dp = 1.2.dp

    fun liquidBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xCCFFFFFF),
            Color(0x44FFFFFF),
            Color(0x15FFFFFF),
            Color(0x05000000)
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
    tintAlpha: Float? = null,
    customTint: Color? = null
): Modifier {
    val tokens = LocalGlassTokens.current
    val effectiveAlpha = if (tokens.isFullyTransparent) 0.0f else (tintAlpha ?: tokens.effectiveTintAlpha)

    val baseTint = when {
        tokens.isFullyTransparent -> Color.Transparent
        customTint != null -> customTint.copy(alpha = effectiveAlpha)
        isOled -> Color(0xFF000000).copy(alpha = 0.88f * effectiveAlpha)
        isDark -> Color(0xFF131520).copy(alpha = effectiveAlpha)
        else -> Color(0xFFEBF0FA).copy(alpha = effectiveAlpha)
    }

    val borderBrush = if (isOled) tokens.oledBorderBrush() else tokens.liquidBorderBrush(isOled)
    val blurRadius = tokens.effectiveBlurRadius
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null && blurRadius > 0.dp && !tokens.isFullyTransparent

    val scrimModifier = if (tokens.keepTextReadable && effectiveAlpha < 0.25f && !tokens.isFullyTransparent) {
        Modifier.background(Color.Black.copy(alpha = 0.12f), shape)
    } else {
        Modifier
    }

    return this
        .clip(shape)
        .then(scrimModifier)
        .then(
            if (canBlur && hazeState != null) {
                Modifier.hazeChild(
                    state = hazeState,
                    shape = shape,
                    tint = baseTint,
                    blurRadius = blurRadius
                )
            } else if (!tokens.isFullyTransparent && effectiveAlpha > 0.001f) {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            baseTint.copy(alpha = (effectiveAlpha * 1.1f).coerceAtMost(1f)),
                            baseTint.copy(alpha = (effectiveAlpha * 0.85f))
                        )
                    ),
                    shape
                )
            } else {
                Modifier
            }
        )
        .border(
            width = tokens.borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
