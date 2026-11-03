package com.nodeplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassThemeConfig

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
val LocalAccentColor = staticCompositionLocalOf { Color(0xFF6366F1) }
val LocalGlassThemeConfig = staticCompositionLocalOf { GlassThemeConfig() }

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

/**
 * Multiplatform liquid glass surface modifier.
 * Renders glass background tint, specular rim highlights, and frosted borders.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    customTint: Color? = null
): Modifier {
    val tokens = LocalGlassTokens.current
    val masterOpacity = tokens.effectiveTintAlpha
    val effectiveAlpha = when {
        tokens.isFullyTransparent -> 0.0f
        tintAlpha != null -> (tintAlpha * (masterOpacity / 0.65f).coerceIn(0.15f, 2.0f)).coerceIn(0.0f, 1.0f)
        else -> masterOpacity
    }

    val baseTint = when {
        tokens.isFullyTransparent -> Color.Transparent
        customTint != null -> customTint.copy(alpha = effectiveAlpha)
        isOled -> Color(0xFF000000).copy(alpha = 0.88f * effectiveAlpha)
        isDark -> Color(0xFF131520).copy(alpha = effectiveAlpha)
        else -> Color(0xFFEBF0FA).copy(alpha = effectiveAlpha)
    }

    val borderBrush = if (isOled) tokens.oledBorderBrush() else tokens.liquidBorderBrush(isOled)

    return this
        .clip(shape)
        .background(baseTint)
        .border(
            width = tokens.borderWidth,
            brush = borderBrush,
            shape = shape
        )
}

/**
 * High-performance liquid glass effect with inner specular highlights and glowing rim borders.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.2.dp,
    surfaceAlpha: Float = 0.12f,
    customTint: Color? = null
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx()
    val borderPx = borderWidth.toPx()

    // 1. Refraction frosted glass backdrop
    val tint = customTint ?: Color(0xFF1E2640)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = surfaceAlpha * 1.5f),
                tint.copy(alpha = surfaceAlpha * 2.5f)
            )
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx)
    )

    // 2. Specular top rim light highlight
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.05f)
            )
        ),
        start = Offset(radiusPx, 0f),
        end = Offset(size.width - radiusPx, 0f),
        strokeWidth = borderPx
    )

    // 3. Ambient glass border
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f)
            )
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = borderPx)
    )
}
