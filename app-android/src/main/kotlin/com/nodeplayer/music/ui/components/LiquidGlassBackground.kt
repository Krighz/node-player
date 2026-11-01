package com.nodeplayer.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * High-end Liquid Glass Background inspired by Dribbble "Music Player - Liquid Glass UI".
 * Features fluid organic chrome/obsidian contours with chromatic refraction caustics.
 */
@Composable
fun LiquidGlassBackground(
    hazeState: HazeState? = null,
    accentColor: Color = Color(0xFF6366F1),
    isOled: Boolean = false,
    isPlaying: Boolean = false,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAnimation")
    val pulse by if (isPerformanceMode) {
        remember { mutableFloatStateOf(1.0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Pulse"
        )
    }

    val hazeModifier = if (hazeState != null) Modifier.haze(hazeState) else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(hazeModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (isOled) {
                drawRect(color = Color.Black)
                return@Canvas
            }

            // 1. Deep Obsidian Base
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090A10),
                        Color(0xFF0C0D14),
                        Color(0xFF06070B)
                    )
                )
            )

            // Performance Mode: Skip heavy liquid contours and chromatic specular highlights to drastically minimize GPU load
            if (isPerformanceMode) {
                return@Canvas
            }

            // 2. Liquid Fluid Contours (inspired by Viktor Sheen / Liquid Glass UI)
            // Upper fluid curve (velvety charcoal with white specular crest)
            val crestPath = Path().apply {
                moveTo(-50f, height * 0.18f)
                cubicTo(
                    width * 0.35f, height * 0.04f * pulse,
                    width * 0.65f, height * 0.32f * pulse,
                    width + 50f, height * 0.16f
                )
            }

            val path1 = Path().apply {
                addPath(crestPath)
                lineTo(width + 50f, -50f)
                lineTo(-50f, -50f)
                close()
            }

            drawPath(
                path = path1,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF282A38).copy(alpha = 0.7f),
                        Color(0xFF151722).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.15f),
                    radius = width * 0.8f
                )
            )

            // Specular brilliant white rim along the upper fluid crest (1.5 px)
            drawPath(
                path = crestPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xCCFFFFFF),
                        Color(0xFFFFFFFF),
                        Color(0x88FFFFFF),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )

            // Mid Organic Caustic Wave with Chromatic Dispersion Edge
            val midContourPath = Path().apply {
                moveTo(-50f, height * 0.48f)
                cubicTo(
                    width * 0.30f, height * 0.34f,
                    width * 0.60f, height * 0.68f * pulse,
                    width + 50f, height * 0.46f
                )
            }

            val path2 = Path().apply {
                addPath(midContourPath)
                lineTo(width + 50f, height + 50f)
                lineTo(-50f, height + 50f)
                close()
            }

            drawPath(
                path = path2,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B1D2A),
                        Color(0xFF10121A),
                        Color(0xFF06070D)
                    ),
                    center = Offset(width * 0.4f, height * 0.55f),
                    radius = width * 0.9f
                )
            )

            // Chromatic Dispersion Caustic Along the Mid Contour
            // Fiery amber/orange iridescent edge (left curve)
            drawPath(
                path = midContourPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x00FF9500),
                        Color(0xFFFF9500).copy(alpha = 0.85f),
                        Color(0xFFFF3B30).copy(alpha = 0.90f),
                        Color(0xFFE040FB).copy(alpha = 0.50f),
                        Color(0x0000E5FF),
                        Color(0xFF00E5FF).copy(alpha = 0.75f),
                        Color(0xFF007AFF).copy(alpha = 0.80f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Soft atmospheric chromatic caustic glows
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF9500).copy(alpha = 0.12f),
                        Color(0xFFFF3B30).copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.25f, height * 0.50f),
                    radius = width * 0.5f * pulse
                ),
                radius = width * 0.5f,
                center = Offset(width * 0.25f, height * 0.50f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.75f, height * 0.45f),
                    radius = width * 0.5f * pulse
                ),
                radius = width * 0.5f,
                center = Offset(width * 0.75f, height * 0.45f)
            )

            // Ambient Album Accent Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.16f),
                        accentColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.78f),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(width * 0.5f, height * 0.78f)
            )
        }
    }
}
