package com.nodeplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassThemeConfig

data class GlassTokensData(
    val opacity: Float = 0.55f,
    val blurStrength: Float = 0.70f,
    val grainStrength: Float = 0.35f,
    val rimStrength: Float = 0.65f,
    val refractionStrength: Float = 0.85f,
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
        val alphaWhite = (0xEE * rimScale).toInt().coerceIn(0, 255)
        val alphaSoft = (0x55 * rimScale).toInt().coerceIn(0, 255)
        val alphaSide = (0x18 * rimScale).toInt().coerceIn(0, 255)

        return Brush.verticalGradient(
            colors = listOf(
                Color(alphaWhite shl 24 or 0x00FFFFFF),
                Color(alphaSoft shl 24 or 0x00FFFFFF),
                Color(alphaSide shl 24 or 0x00FFFFFF),
                Color(0x04000000)
            )
        )
    }

    fun oledBorderBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xAAFFFFFF),
            Color(0x33FFFFFF),
            Color.Transparent
        )
    )
}

val LocalGlassTokens = staticCompositionLocalOf { GlassTokensData() }
val LocalAccentColor = staticCompositionLocalOf { Color(0xFF6366F1) }
val LocalGlassThemeConfig = staticCompositionLocalOf { GlassThemeConfig() }
val LocalGlassControlsConfig = staticCompositionLocalOf { GlassControlsConfig() }
val LocalControlBackdrop = compositionLocalOf<Backdrop> { emptyBackdrop() }

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
 * Renders glass background tint, specular Fresnel rim highlights, and frosted crystal borders.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = ContinuousRoundedRectangle(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    customTint: Color? = null
): Modifier {
    val tokens = LocalGlassTokens.current
    val masterOpacity = tokens.effectiveTintAlpha
    val effectiveAlpha = when {
        tokens.isFullyTransparent -> 0.0f
        tintAlpha != null -> (tintAlpha * (masterOpacity / 0.55f).coerceIn(0.15f, 2.0f)).coerceIn(0.0f, 1.0f)
        else -> masterOpacity
    }

    val rimStrength = tokens.rimStrength.coerceIn(0.1f, 1.0f)
    val borderBrush = if (isOled) tokens.oledBorderBrush() else tokens.liquidBorderBrush(isOled)

    return this
        .clip(shape)
        .drawBehind {
            val width = size.width
            val height = size.height

            // 1. Base glass fill with ambient depth gradient
            if (!tokens.isFullyTransparent) {
                if (isOled) {
                    drawRect(color = Color.Black.copy(alpha = 0.92f * effectiveAlpha))
                } else {
                    val topTint = (customTint ?: Color(0xFF161B2E)).copy(alpha = 0.40f * effectiveAlpha)
                    val botTint = (customTint ?: Color(0xFF0B0E18)).copy(alpha = 0.65f * effectiveAlpha)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(topTint, botTint),
                            startY = 0f,
                            endY = height
                        )
                    )
                }
            }

            // 2. Specular Fresnel gloss sheen on upper half
            if (!tokens.isPerformanceMode) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f * rimStrength),
                            Color.White.copy(alpha = 0.02f * rimStrength),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = height * 0.55f
                    )
                )

                // 3. Top crystal rim edge line
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.50f * rimStrength),
                            Color.White.copy(alpha = 0.70f * rimStrength),
                            Color.White.copy(alpha = 0.50f * rimStrength),
                            Color.Transparent
                        )
                    ),
                    start = Offset(16f, 0.5f),
                    end = Offset(width - 16f, 0.5f),
                    strokeWidth = 1.2f
                )
            }
        }
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
                Color.White.copy(alpha = 0.65f),
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
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.03f)
            )
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = borderPx)
    )
}
