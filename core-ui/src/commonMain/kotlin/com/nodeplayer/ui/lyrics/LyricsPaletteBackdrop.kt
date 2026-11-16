package com.nodeplayer.ui.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nodeplayer.model.AlbumArtPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Full-screen lyrics backdrop built from the whole album-art palette.
 * Preserves the actual album art colors (dominant, vibrant, accent, and darkMuted)
 * while gently drifting and breathing with a slight ambient animation.
 */
@Composable
fun LyricsPaletteBackdrop(palette: AlbumArtPalette, modifier: Modifier = Modifier) {
    val base = remember(palette) { Color(palette.darkMuted).preserveAlbumColor(minValue = 0.18f, maxValue = 0.36f) }
    val dominant = remember(palette) { Color(palette.dominant).preserveAlbumColor(minValue = 0.34f, maxValue = 0.72f) }
    val vibrant = remember(palette) { Color(palette.vibrant).preserveAlbumColor(minValue = 0.42f, maxValue = 0.82f) }
    val accent = remember(palette) { Color(palette.accent).preserveAlbumColor(minValue = 0.40f, maxValue = 0.80f) }

    val transition = rememberInfiniteTransition(label = "lyricsBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "backdropPhase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = max(w, h)

        // Base ambient wash blending dominant and darkMuted album colors
        val topBase = lerp(base, dominant, 0.48f)
        val bottomBase = lerp(base, vibrant, 0.24f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(topBase, lerp(topBase, bottomBase, 0.5f), bottomBase)
            )
        )

        fun drawColorBlob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
            if (radius <= 0f) return
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
            )
        }

        // Gentle orbital drift + subtle radius pulse so all album art colors stay alive
        val pulse1 = 1f + 0.06f * sin(phase * 2f)
        val pulse2 = 1f + 0.06f * cos(phase * 1.5f)

        // 1. Vibrant swatch blob (upper-left to center drift)
        drawColorBlob(
            color = vibrant,
            cx = w * (0.24f + 0.14f * cos(phase)),
            cy = h * (0.22f + 0.10f * sin(phase)),
            radius = r * 0.82f * pulse1,
            alpha = 0.78f
        )

        // 2. Accent swatch blob (right-mid drift)
        drawColorBlob(
            color = accent,
            cx = w * (0.78f + 0.12f * sin(phase * 0.85f)),
            cy = h * (0.52f + 0.12f * cos(phase * 0.9f)),
            radius = r * 0.78f * pulse2,
            alpha = 0.68f
        )

        // 3. Dominant swatch blob (lower-left / bottom anchor drift)
        drawColorBlob(
            color = dominant,
            cx = w * (0.36f + 0.14f * sin(phase * 1.15f)),
            cy = h * (0.86f + 0.08f * cos(phase * 1.1f)),
            radius = r * 0.88f * pulse1,
            alpha = 0.76f
        )

        // 4. Secondary dominant/vibrant blend in upper-right so the full canvas carries album art tones
        drawColorBlob(
            color = lerp(dominant, vibrant, 0.5f),
            cx = w * (0.76f + 0.10f * cos(phase * 1.2f)),
            cy = h * (0.18f + 0.08f * sin(phase * 1.3f)),
            radius = r * 0.65f * pulse2,
            alpha = 0.52f
        )
    }
}

/**
 * Keeps the album art color's true hue and saturation while clamping brightness into
 * a readable range for white lyric text (preserving grayscale covers as neutral).
 */
private fun Color.preserveAlbumColor(minValue: Float, maxValue: Float): Color {
    val mx = maxOf(red, green, blue)
    val mn = minOf(red, green, blue)
    val d = mx - mn
    val hue = when {
        d == 0f -> 0f
        mx == red -> ((green - blue) / d).mod(6f)
        mx == green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    }
    val rawSat = if (mx == 0f) 0f else d / mx
    // Keep grayscale / monochrome album art neutral instead of adding a fake tint
    val sat = if (rawSat < 0.08f) rawSat else rawSat.coerceIn(0.22f, 0.92f)
    val v = mx.coerceIn(minValue, maxValue)
    val c = v * sat
    val x = c * (1f - abs(hue.mod(2f) - 1f))
    val m = v - c
    val (r, g, b) = when (hue.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}
