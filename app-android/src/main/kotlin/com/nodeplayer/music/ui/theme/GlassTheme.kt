package com.nodeplayer.music.ui.theme

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

data class GlassTokensData(
    val opacity: Float = 0.65f,
    val blurStrength: Float = 0.70f,
    val grainStrength: Float = 0.0f,
    val rimStrength: Float = 0.60f,
    val isFullyTransparent: Boolean = false,
    val keepTextReadable: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val defaultCornerRadius: Dp = 26.dp,
    val cardCornerRadius: Dp = 22.dp,
    val pillCornerRadius: Dp = 999.dp,
    val borderWidth: Dp = 1.dp
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
        val rimScale = if (isFullyTransparent) 1.0f else rimStrength.coerceIn(0.15f, 1.0f)
        val alphaTop = (0x55 * rimScale).toInt().coerceIn(0, 255)
        val alphaMid = (0x22 * rimScale).toInt().coerceIn(0, 255)
        val alphaBottom = (0x0C * rimScale).toInt().coerceIn(0, 255)

        return Brush.verticalGradient(
            colors = listOf(
                Color(alphaTop shl 24 or 0x00FFFFFF),
                Color(alphaMid shl 24 or 0x00FFFFFF),
                Color(alphaBottom shl 24 or 0x00FFFFFF)
            )
        )
    }

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x44FFFFFF),
            Color(0x18FFFFFF),
            Color(0x08FFFFFF)
        )
    )
}

val LocalGlassTokens = staticCompositionLocalOf { GlassTokensData() }

object GlassTokens {
    val defaultCornerRadius: Dp = 26.dp
    val cardCornerRadius: Dp = 22.dp
    val pillCornerRadius: Dp = 999.dp
    val defaultBlurRadius: Dp = 28.dp
    val borderWidth: Dp = 1.dp

    fun liquidBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x55FFFFFF),
            Color(0x22FFFFFF),
            Color(0x0CFFFFFF)
        )
    )

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x44FFFFFF),
            Color(0x18FFFFFF),
            Color(0x08FFFFFF)
        )
    )
}

@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    customTint: Color? = null
): Modifier {
    val tokens = LocalGlassTokens.current
    val accent = LocalAccentColor.current
    val masterOpacity = tokens.effectiveTintAlpha
    val effectiveAlpha = when {
        tokens.isFullyTransparent -> 0.0f
        tintAlpha != null -> (tintAlpha * (masterOpacity / 0.65f).coerceIn(0.25f, 1.8f)).coerceIn(0.0f, 0.96f)
        else -> masterOpacity
    }

    val baseTint = when {
        tokens.isFullyTransparent -> Color.Transparent
        customTint != null -> customTint.copy(alpha = effectiveAlpha)
        isOled -> Color.Black.copy(alpha = (0.86f * effectiveAlpha).coerceIn(0.35f, 0.92f))
        isDark -> Color(0xFF262626).copy(alpha = (0.78f * effectiveAlpha).coerceIn(0.30f, 0.88f))
        else -> Color(0xFFEBF0FA).copy(alpha = effectiveAlpha)
    }

    val topSheen = if (tokens.isFullyTransparent) {
        Color.Transparent
    } else if (customTint != null) {
        customTint.copy(alpha = (effectiveAlpha * 1.15f).coerceAtMost(0.95f))
    } else {
        Color.White.copy(alpha = if (isDark) 0.06f * tokens.rimStrength else 0.25f)
    }

    val borderBrush = if (isOled) tokens.oledBorderBrush() else tokens.liquidBorderBrush(isOled)

    val scrimModifier = if (tokens.keepTextReadable && effectiveAlpha < 0.25f && !tokens.isFullyTransparent) {
        Modifier.background(Color.Black.copy(alpha = 0.16f), shape)
    } else {
        Modifier
    }

    return this
        .clip(shape)
        .then(scrimModifier)
        .then(
            if (!tokens.isFullyTransparent && effectiveAlpha > 0.001f) {
                Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                baseTint.copy(alpha = (baseTint.alpha * 1.08f).coerceAtMost(0.96f)),
                                baseTint.copy(alpha = (baseTint.alpha * 0.88f))
                            )
                        ),
                        shape
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                topSheen,
                                accent.copy(alpha = 0.03f),
                                Color.Transparent
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
