package com.nodeplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.theme.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-end Liquid Glass Background supporting 6 animation styles:
 * 1. Liquid blobs (organic chrome & obsidian contours with chromatic caustics)
 * 2. Aurora (sweeping wave curtains)
 * 3. Mesh gradient (interpolated 4-quadrant ambient patches)
 * 4. Album-art blur (dynamic dominant color bleed)
 * 5. Slow caustics (liquid optical light refraction filaments)
 * 6. Static (frozen gradient glass, zero CPU/GPU overhead)
 */
@Composable
fun LiquidGlassBackground(
    accentColor: Color = Color(0xFF6366F1),
    isOled: Boolean = false,
    isLight: Boolean = false,
    isPlaying: Boolean = false,
    isPerformanceMode: Boolean = false,
    animatedBackdrop: Boolean = true,
    glassStrength: String = "BALANCED",
    themeConfig: GlassThemeConfig? = null,
    modifier: Modifier = Modifier
) {
    val cfg = themeConfig ?: GlassThemeConfig()
    val perfMode = isPerformanceMode || cfg.isPerformanceMode
    val style = if (perfMode) "STATIC" else cfg.backgroundAnimationStyle
    val isStatic = style == "STATIC" || !animatedBackdrop || glassStrength == "OFF"
    val animateOnlyWhilePlaying = cfg.animateOnlyWhilePlaying
    val canAnimate = !isStatic && (!animateOnlyWhilePlaying || isPlaying)

    val speed = cfg.backgroundAnimationSpeed.coerceIn(0.2f, 3.0f)
    val baseDurationMs = (4000f / speed).toInt().coerceIn(800, 20000)
    val intensity = cfg.backgroundAnimationIntensity.coerceIn(0.1f, 1.0f)

    val fixedColor = Color(cfg.fixedAccentColorArgb)
    val (primaryColor, secondaryColor, tertiaryColor) = remember(accentColor, cfg.backgroundColorSource, cfg.fixedAccentColorArgb) {
        derivePalette(accentColor, cfg.backgroundColorSource, fixedColor)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundMotion")
    val animProgress by if (!canAnimate) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(baseDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "AnimProgress"
        )
    }

    val pulse by if (!canAnimate) {
        remember { mutableFloatStateOf(1.0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(baseDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Pulse"
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (isOled || cfg.isOledBlack) {
                drawRect(color = Color.Black)
                if (isPlaying && glassStrength != "OFF") {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.09f * intensity),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.75f),
                            radius = width * 0.6f * pulse
                        ),
                        radius = width * 0.6f,
                        center = Offset(width * 0.5f, height * 0.75f)
                    )
                }
                return@Canvas
            }

            if (glassStrength == "OFF") {
                val flatColor = if (isLight) Color(0xFFF4F5FB) else Color(0xFF0C0D14)
                drawRect(color = flatColor)
                return@Canvas
            }

            when (style) {
                "AURORA" -> {
                    drawAuroraStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        tertiary = tertiaryColor,
                        progress = animProgress,
                        pulse = pulse,
                        intensity = intensity
                    )
                }
                "MESH_GRADIENT" -> {
                    drawMeshGradientStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        tertiary = tertiaryColor,
                        progress = animProgress,
                        intensity = intensity
                    )
                }
                "ALBUM_ART_BLUR" -> {
                    drawAlbumArtBlurStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        pulse = pulse,
                        intensity = intensity
                    )
                }
                "SLOW_CAUSTICS" -> {
                    drawSlowCausticsStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        progress = animProgress,
                        intensity = intensity
                    )
                }
                "STATIC" -> {
                    drawLiquidBlobsStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        pulse = 1.0f,
                        intensity = intensity,
                        isPerformanceMode = true
                    )
                }
                else -> {
                    drawLiquidBlobsStyle(
                        width = width,
                        height = height,
                        isLight = isLight,
                        primary = primaryColor,
                        secondary = secondaryColor,
                        pulse = pulse,
                        intensity = intensity,
                        isPerformanceMode = perfMode
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidBlobsStyle(
    width: Float,
    height: Float,
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    pulse: Float,
    intensity: Float,
    isPerformanceMode: Boolean
) {
    if (isLight) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFF4F5FB), Color(0xFFE8EAF6))
            )
        )
        if (isPerformanceMode) return

        val crestPath = Path().apply {
            moveTo(-50f, height * 0.20f)
            cubicTo(
                width * 0.35f, height * 0.08f * pulse,
                width * 0.65f, height * 0.35f * pulse,
                width + 50f, height * 0.18f
            )
        }
        drawPath(
            path = crestPath,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, primary.copy(alpha = 0.20f * intensity), Color.Transparent)
            ),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = 0.12f * intensity), Color.Transparent),
                center = Offset(width * 0.5f, height * 0.75f),
                radius = width * 0.7f
            ),
            radius = width * 0.7f,
            center = Offset(width * 0.5f, height * 0.75f)
        )
        return
    }

    // Obsidian base
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF090A10), Color(0xFF0C0D14), Color(0xFF06070B))
        )
    )
    if (isPerformanceMode) return

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
            listOf(
                Color(0xFF282A38).copy(alpha = 0.7f * intensity),
                Color(0xFF151722).copy(alpha = 0.4f * intensity),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, height * 0.15f),
            radius = width * 0.8f
        )
    )

    drawPath(
        path = crestPath,
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.80f * intensity),
                Color.White.copy(alpha = 0.95f * intensity),
                Color.Transparent
            )
        ),
        style = Stroke(width = 2.dp.toPx())
    )

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
            listOf(
                Color(0xFF1B1D2A).copy(alpha = 0.8f * intensity),
                Color(0xFF10121A).copy(alpha = 0.5f * intensity),
                Color(0xFF06070D)
            ),
            center = Offset(width * 0.4f, height * 0.55f),
            radius = width * 0.9f
        )
    )

    drawPath(
        path = midContourPath,
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFFFF9500).copy(alpha = 0.85f * intensity),
                secondary.copy(alpha = 0.80f * intensity),
                Color(0xFFFF3B30).copy(alpha = 0.90f * intensity),
                Color(0xFFE040FB).copy(alpha = 0.50f * intensity),
                Color(0xFF00E5FF).copy(alpha = 0.75f * intensity),
                Color(0xFF007AFF).copy(alpha = 0.80f * intensity),
                Color.Transparent
            )
        ),
        style = Stroke(width = 2.5.dp.toPx())
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.18f * intensity), Color.Transparent),
            center = Offset(width * 0.5f, height * 0.78f),
            radius = width * 0.75f
        ),
        radius = width * 0.75f,
        center = Offset(width * 0.5f, height * 0.78f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAuroraStyle(
    width: Float,
    height: Float,
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    progress: Float,
    pulse: Float,
    intensity: Float
) {
    val baseBg = if (isLight) Color(0xFFF0F2F9) else Color(0xFF07080E)
    drawRect(color = baseBg)

    val twoPi = (Math.PI * 2).toFloat()
    val waveOffset1 = sin(progress * twoPi) * 0.12f
    val waveOffset2 = cos(progress * twoPi) * 0.10f

    val auroraPath1 = Path().apply {
        moveTo(0f, height * (0.25f + waveOffset1))
        cubicTo(
            width * 0.35f, height * (0.15f - waveOffset2 * pulse),
            width * 0.70f, height * (0.40f + waveOffset1 * pulse),
            width, height * (0.28f - waveOffset1)
        )
        lineTo(width, height * 0.65f)
        cubicTo(
            width * 0.65f, height * (0.60f - waveOffset1),
            width * 0.30f, height * (0.45f + waveOffset2),
            0f, height * 0.55f
        )
        close()
    }

    drawPath(
        path = auroraPath1,
        brush = Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.28f * intensity),
                tertiary.copy(alpha = 0.22f * intensity),
                Color.Transparent
            ),
            start = Offset(0f, height * 0.2f),
            end = Offset(width, height * 0.6f)
        )
    )

    val auroraPath2 = Path().apply {
        moveTo(0f, height * (0.50f - waveOffset2))
        cubicTo(
            width * 0.40f, height * (0.65f + waveOffset1),
            width * 0.75f, height * (0.45f - waveOffset1),
            width, height * (0.58f + waveOffset2)
        )
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }

    drawPath(
        path = auroraPath2,
        brush = Brush.linearGradient(
            listOf(
                secondary.copy(alpha = 0.25f * intensity),
                primary.copy(alpha = 0.18f * intensity),
                Color.Transparent
            ),
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height)
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeshGradientStyle(
    width: Float,
    height: Float,
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    progress: Float,
    intensity: Float
) {
    val base = if (isLight) Color(0xFFF5F6FA) else Color(0xFF090A10)
    drawRect(color = base)

    val angle = progress * (Math.PI * 2).toFloat()
    val ox1 = cos(angle) * (width * 0.08f)
    val oy1 = sin(angle) * (height * 0.05f)
    val ox2 = sin(angle) * (width * 0.06f)
    val oy2 = cos(angle) * (height * 0.07f)

    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.25f * intensity), Color.Transparent),
            center = Offset(width * 0.2f + ox1, height * 0.2f + oy1),
            radius = width * 0.7f
        ),
        radius = width * 0.7f,
        center = Offset(width * 0.2f + ox1, height * 0.2f + oy1)
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(secondary.copy(alpha = 0.22f * intensity), Color.Transparent),
            center = Offset(width * 0.8f - ox2, height * 0.35f + oy2),
            radius = width * 0.65f
        ),
        radius = width * 0.65f,
        center = Offset(width * 0.8f - ox2, height * 0.35f + oy2)
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(tertiary.copy(alpha = 0.20f * intensity), Color.Transparent),
            center = Offset(width * 0.25f - ox2, height * 0.75f - oy1),
            radius = width * 0.75f
        ),
        radius = width * 0.75f,
        center = Offset(width * 0.25f - ox2, height * 0.75f - oy1)
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.18f * intensity), Color.Transparent),
            center = Offset(width * 0.75f + ox1, height * 0.80f + oy2),
            radius = width * 0.7f
        ),
        radius = width * 0.7f,
        center = Offset(width * 0.75f + ox1, height * 0.80f + oy2)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlbumArtBlurStyle(
    width: Float,
    height: Float,
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    pulse: Float,
    intensity: Float
) {
    val base = if (isLight) Color(0xFFF4F6FC) else Color(0xFF08090E)
    drawRect(color = base)

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                primary.copy(alpha = 0.35f * intensity),
                primary.copy(alpha = 0.15f * intensity),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, height * 0.45f),
            radius = width * 0.85f * pulse
        ),
        radius = width * 0.85f * pulse,
        center = Offset(width * 0.5f, height * 0.45f)
    )

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                secondary.copy(alpha = 0.22f * intensity),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, height * 0.80f),
            radius = width * 0.65f
        ),
        radius = width * 0.65f,
        center = Offset(width * 0.5f, height * 0.80f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlowCausticsStyle(
    width: Float,
    height: Float,
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    progress: Float,
    intensity: Float
) {
    val base = if (isLight) Color(0xFFEFEFF5) else Color(0xFF07070B)
    drawRect(color = base)

    val twoPi = (Math.PI * 2).toFloat()
    val shiftX = sin(progress * twoPi) * width * 0.05f
    val shiftY = cos(progress * twoPi) * height * 0.04f

    for (i in 0 until 4) {
        val yOffset = height * (0.22f + i * 0.18f)
        val path = Path().apply {
            moveTo(-30f, yOffset + shiftY * (if (i % 2 == 0) 1f else -1f))
            cubicTo(
                width * 0.33f + shiftX, yOffset - 35f,
                width * 0.66f - shiftX, yOffset + 35f,
                width + 30f, yOffset
            )
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    primary.copy(alpha = 0.35f * intensity),
                    secondary.copy(alpha = 0.45f * intensity),
                    Color.White.copy(alpha = 0.60f * intensity),
                    Color.Transparent
                )
            ),
            style = Stroke(width = (1.5f + i * 0.5f).dp.toPx())
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.16f * intensity), Color.Transparent),
            center = Offset(width * 0.5f + shiftX, height * 0.55f + shiftY),
            radius = width * 0.70f
        ),
        radius = width * 0.70f,
        center = Offset(width * 0.5f + shiftX, height * 0.55f + shiftY)
    )
}

private fun derivePalette(baseColor: Color, source: String, fixedColor: Color): Triple<Color, Color, Color> {
    val primary = when (source) {
        "CUSTOM" -> fixedColor
        "PALETTE" -> baseColor
        else -> baseColor
    }

    val hsv = ColorUtils.colorToHsv(primary)

    val hsvSecondary = floatArrayOf(
        (hsv[0] + 45f) % 360f,
        (hsv[1] * 0.9f).coerceIn(0f, 1f),
        (hsv[2] * 0.95f).coerceIn(0f, 1f)
    )
    val secondary = ColorUtils.hsvToColor(hsvSecondary)

    val hsvTertiary = floatArrayOf(
        (hsv[0] + 160f) % 360f,
        (hsv[1] * 0.85f).coerceIn(0f, 1f),
        (hsv[2] * 0.90f).coerceIn(0f, 1f)
    )
    val tertiary = ColorUtils.hsvToColor(hsvTertiary)

    return Triple(primary, secondary, tertiary)
}
