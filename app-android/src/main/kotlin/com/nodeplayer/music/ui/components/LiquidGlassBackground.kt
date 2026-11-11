package com.nodeplayer.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.components.shouldAnimateLiquidGlassBackground
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val LIGHT_BASE_COLORS = listOf(Color(0xFFFFFFFF), Color(0xFFF4F5FB), Color(0xFFE8EAF6))
private val DARK_BASE_COLORS = listOf(Color(0xFF090A10), Color(0xFF0C0D14), Color(0xFF06070B))

@Composable
private fun rememberAnimatedBackgroundMotion(baseDurationMs: Int): Pair<State<Float>, State<Float>> {
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundMotion")
    val animProgress = infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(baseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AnimProgress"
    )
    val pulse = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(baseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )
    return animProgress to pulse
}

/**
 * High-end Liquid Glass Background supporting 6 animation styles:
 * 1. Liquid blobs (organic chrome & obsidian contours with chromatic caustics)
 * 2. Aurora (sweeping wave curtains)
 * 3. Mesh gradient (interpolated 4-quadrant ambient patches)
 * 4. Album-art blur (dynamic dominant color bleed)
 * 5. Slow caustics (liquid optical light refraction filaments)
 * 6. Static (frozen gradient glass, zero CPU/GPU overhead)
 *
 * Fully supports speed, intensity, color source, play-state gating,
 * frame-rate caps, adaptive quality, and anti-banding dithering.
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
    val canAnimate = shouldAnimateLiquidGlassBackground(
        style = style,
        animatedBackdrop = animatedBackdrop,
        glassStrength = glassStrength,
        animateOnlyWhilePlaying = cfg.animateOnlyWhilePlaying,
        isPlaying = isPlaying,
        isPerformanceMode = perfMode
    )

    val speed = cfg.backgroundAnimationSpeed.coerceIn(0.2f, 3.0f)
    val baseDurationMs = (4000f / speed).toInt().coerceIn(800, 20000)
    val intensity = cfg.backgroundAnimationIntensity.coerceIn(0.1f, 1.0f)

    val fixedColor = Color(cfg.fixedAccentColorArgb)
    val (primaryColor, secondaryColor, tertiaryColor) = remember(accentColor, cfg.backgroundColorSource, cfg.fixedAccentColorArgb) {
        derivePalette(accentColor, cfg.backgroundColorSource, fixedColor)
    }

    val (animProgressState, pulseState) = if (canAnimate) {
        rememberAnimatedBackgroundMotion(baseDurationMs)
    } else {
        remember(style, perfMode) {
            Pair(
                mutableFloatStateOf(if (style == "STATIC" || perfMode) 0f else 0.5f),
                mutableFloatStateOf(1.0f)
            )
        }
    }

    val ditherPaint = remember(cfg.antiBandingDithering) {
        if (!cfg.antiBandingDithering) null else createDitherPaint()
    }

    val effectiveOled = isOled || cfg.isOledBlack
    val crestPath = remember { Path() }
    val path1 = remember { Path() }
    val midContourPath = remember { Path() }
    val path2 = remember { Path() }
    val auroraPath1 = remember { Path() }
    val auroraPath2 = remember { Path() }
    val causticsPaths = remember { Array(4) { Path() } }

    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val width = size.width
                    val height = size.height

                    if (effectiveOled) {
                        val oledCenter = Offset(width * 0.5f, height * 0.75f)
                        val oledRadius = (width * 0.6f).coerceAtLeast(1f)
                        val oledBrush = if (isPlaying && glassStrength != "OFF") {
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.09f * intensity),
                                    Color.Transparent
                                ),
                                center = Offset.Zero,
                                radius = oledRadius
                            )
                        } else null
                        return@drawWithCache onDrawBehind {
                            drawRect(color = Color.Black)
                            if (oledBrush != null) {
                                val pulse = pulseState.value
                                translate(left = oledCenter.x, top = oledCenter.y) {
                                    scale(scaleX = pulse, scaleY = pulse, pivot = Offset.Zero) {
                                        drawCircle(
                                            brush = oledBrush,
                                            radius = oledRadius,
                                            center = Offset.Zero
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (glassStrength == "OFF") {
                        val flatColor = if (isLight) Color(0xFFF4F5FB) else Color(0xFF0C0D14)
                        return@drawWithCache onDrawBehind {
                            drawRect(color = flatColor)
                        }
                    }

                    val activeDither = if (cfg.antiBandingDithering && !isLight) ditherPaint else null

                    when (style) {
                        "AURORA" -> {
                            val baseBg = if (isLight) Color(0xFFF0F2F9) else Color(0xFF07080E)
                            val brush1 = Brush.linearGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.28f * intensity),
                                    tertiaryColor.copy(alpha = 0.22f * intensity),
                                    Color.Transparent
                                ),
                                start = Offset(0f, height * 0.2f),
                                end = Offset(width, height * 0.6f)
                            )
                            val brush2 = Brush.linearGradient(
                                listOf(
                                    secondaryColor.copy(alpha = 0.25f * intensity),
                                    primaryColor.copy(alpha = 0.18f * intensity),
                                    Color.Transparent
                                ),
                                start = Offset(0f, height * 0.5f),
                                end = Offset(width, height)
                            )
                            onDrawBehind {
                                drawAuroraStyle(
                                    width = width,
                                    height = height,
                                    baseBg = baseBg,
                                    brush1 = brush1,
                                    brush2 = brush2,
                                    path1 = auroraPath1,
                                    path2 = auroraPath2,
                                    progress = animProgressState.value,
                                    pulse = pulseState.value
                                )
                                drawDitherOverlay(width, height, activeDither)
                            }
                        }
                        "MESH_GRADIENT" -> {
                            val base = if (isLight) Color(0xFFF5F6FA) else Color(0xFF090A10)
                            val r1 = (width * 0.7f).coerceAtLeast(1f)
                            val r2 = (width * 0.65f).coerceAtLeast(1f)
                            val r3 = (width * 0.75f).coerceAtLeast(1f)
                            val r4 = (width * 0.7f).coerceAtLeast(1f)
                            val b1 = Brush.radialGradient(
                                listOf(primaryColor.copy(alpha = 0.25f * intensity), Color.Transparent),
                                center = Offset.Zero,
                                radius = r1
                            )
                            val b2 = Brush.radialGradient(
                                listOf(secondaryColor.copy(alpha = 0.22f * intensity), Color.Transparent),
                                center = Offset.Zero,
                                radius = r2
                            )
                            val b3 = Brush.radialGradient(
                                listOf(tertiaryColor.copy(alpha = 0.20f * intensity), Color.Transparent),
                                center = Offset.Zero,
                                radius = r3
                            )
                            val b4 = Brush.radialGradient(
                                listOf(primaryColor.copy(alpha = 0.18f * intensity), Color.Transparent),
                                center = Offset.Zero,
                                radius = r4
                            )
                            onDrawBehind {
                                drawMeshGradientStyle(
                                    width = width,
                                    height = height,
                                    base = base,
                                    b1 = b1, r1 = r1,
                                    b2 = b2, r2 = r2,
                                    b3 = b3, r3 = r3,
                                    b4 = b4, r4 = r4,
                                    progress = animProgressState.value
                                )
                                drawDitherOverlay(width, height, activeDither)
                            }
                        }
                        "ALBUM_ART_BLUR" -> {
                            val base = if (isLight) Color(0xFFF4F6FC) else Color(0xFF08090E)
                            val primaryRadius = (width * 0.85f).coerceAtLeast(1f)
                            val secondaryRadius = (width * 0.65f).coerceAtLeast(1f)
                            val primaryBrush = Brush.radialGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.35f * intensity),
                                    primaryColor.copy(alpha = 0.15f * intensity),
                                    Color.Transparent
                                ),
                                center = Offset.Zero,
                                radius = primaryRadius
                            )
                            val secondaryCenter = Offset(width * 0.5f, height * 0.80f)
                            val secondaryBrush = Brush.radialGradient(
                                listOf(secondaryColor.copy(alpha = 0.22f * intensity), Color.Transparent),
                                center = secondaryCenter,
                                radius = secondaryRadius
                            )
                            onDrawBehind {
                                drawAlbumArtBlurStyle(
                                    width = width,
                                    height = height,
                                    base = base,
                                    primaryBrush = primaryBrush,
                                    primaryRadius = primaryRadius,
                                    secondaryBrush = secondaryBrush,
                                    secondaryCenter = secondaryCenter,
                                    secondaryRadius = secondaryRadius,
                                    pulse = pulseState.value
                                )
                                drawDitherOverlay(width, height, activeDither)
                            }
                        }
                        "SLOW_CAUSTICS" -> {
                            val base = if (isLight) Color(0xFFEFEFF5) else Color(0xFF07070B)
                            val strokeBrush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.35f * intensity),
                                    secondaryColor.copy(alpha = 0.45f * intensity),
                                    Color.White.copy(alpha = 0.60f * intensity),
                                    Color.Transparent
                                )
                            )
                            val poolRadius = (width * 0.70f).coerceAtLeast(1f)
                            val poolBrush = Brush.radialGradient(
                                listOf(primaryColor.copy(alpha = 0.16f * intensity), Color.Transparent),
                                center = Offset.Zero,
                                radius = poolRadius
                            )
                            val strokes = Array(4) { i -> Stroke(width = (1.5f + i * 0.5f).dp.toPx()) }
                            onDrawBehind {
                                drawSlowCausticsStyle(
                                    width = width,
                                    height = height,
                                    base = base,
                                    strokeBrush = strokeBrush,
                                    strokes = strokes,
                                    poolBrush = poolBrush,
                                    poolRadius = poolRadius,
                                    paths = causticsPaths,
                                    progress = animProgressState.value
                                )
                                drawDitherOverlay(width, height, activeDither)
                            }
                        }
                        else -> {
                            val staticMode = style == "STATIC" || perfMode
                            val baseBrush = Brush.verticalGradient(if (isLight) LIGHT_BASE_COLORS else DARK_BASE_COLORS)
                            if (isLight) {
                                val lightCrestBrush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, primaryColor.copy(alpha = 0.20f * intensity), Color.Transparent)
                                )
                                val lightGlowCenter = Offset(width * 0.5f, height * 0.75f)
                                val lightGlowRadius = (width * 0.7f).coerceAtLeast(1f)
                                val lightGlowBrush = Brush.radialGradient(
                                    listOf(primaryColor.copy(alpha = 0.12f * intensity), Color.Transparent),
                                    center = lightGlowCenter,
                                    radius = lightGlowRadius
                                )
                                val crestStroke = Stroke(width = 2.dp.toPx())
                                onDrawBehind {
                                    drawRect(brush = baseBrush)
                                    if (!staticMode) {
                                        drawLiquidBlobsLightStyle(
                                            width = width,
                                            height = height,
                                            crestBrush = lightCrestBrush,
                                            crestStroke = crestStroke,
                                            glowBrush = lightGlowBrush,
                                            glowCenter = lightGlowCenter,
                                            glowRadius = lightGlowRadius,
                                            crestPath = crestPath,
                                            pulse = pulseState.value
                                        )
                                    }
                                }
                            } else {
                                val topGlowBrush = Brush.radialGradient(
                                    listOf(
                                        Color(0xFF282A38).copy(alpha = 0.7f * intensity),
                                        Color(0xFF151722).copy(alpha = 0.4f * intensity),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.5f, height * 0.15f),
                                    radius = (width * 0.8f).coerceAtLeast(1f)
                                )
                                val specularBrush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.80f * intensity),
                                        Color.White.copy(alpha = 0.95f * intensity),
                                        Color.Transparent
                                    )
                                )
                                val midFillBrush = Brush.radialGradient(
                                    listOf(
                                        Color(0xFF1B1D2A).copy(alpha = 0.8f * intensity),
                                        Color(0xFF10121A).copy(alpha = 0.5f * intensity),
                                        Color(0xFF06070D)
                                    ),
                                    center = Offset(width * 0.4f, height * 0.55f),
                                    radius = (width * 0.9f).coerceAtLeast(1f)
                                )
                                val chromaticBrush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFFFF9500).copy(alpha = 0.85f * intensity),
                                        secondaryColor.copy(alpha = 0.80f * intensity),
                                        Color(0xFFFF3B30).copy(alpha = 0.90f * intensity),
                                        Color(0xFFE040FB).copy(alpha = 0.50f * intensity),
                                        Color(0xFF00E5FF).copy(alpha = 0.75f * intensity),
                                        Color(0xFF007AFF).copy(alpha = 0.80f * intensity),
                                        Color.Transparent
                                    )
                                )
                                val ambientCenter = Offset(width * 0.5f, height * 0.78f)
                                val ambientRadius = (width * 0.75f).coerceAtLeast(1f)
                                val ambientBrush = Brush.radialGradient(
                                    listOf(primaryColor.copy(alpha = 0.18f * intensity), Color.Transparent),
                                    center = ambientCenter,
                                    radius = ambientRadius
                                )
                                val crestStroke = Stroke(width = 2.dp.toPx())
                                val chromaticStroke = Stroke(width = 2.5.dp.toPx())
                                onDrawBehind {
                                    drawRect(brush = baseBrush)
                                    if (!staticMode) {
                                        drawLiquidBlobsDarkStyle(
                                            width = width,
                                            height = height,
                                            topGlowBrush = topGlowBrush,
                                            specularBrush = specularBrush,
                                            crestStroke = crestStroke,
                                            midFillBrush = midFillBrush,
                                            chromaticBrush = chromaticBrush,
                                            chromaticStroke = chromaticStroke,
                                            ambientBrush = ambientBrush,
                                            ambientCenter = ambientCenter,
                                            ambientRadius = ambientRadius,
                                            crestPath = crestPath,
                                            path1 = path1,
                                            midContourPath = midContourPath,
                                            path2 = path2,
                                            pulse = pulseState.value
                                        )
                                    }
                                    drawDitherOverlay(width, height, activeDither)
                                }
                            }
                        }
                    }
                }
        )
    }
}

private fun DrawScope.drawDitherOverlay(width: Float, height: Float, ditherPaint: android.graphics.Paint?) {
    if (ditherPaint != null) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, width, height, ditherPaint)
        }
    }
}

private fun DrawScope.drawLiquidBlobsLightStyle(
    width: Float,
    height: Float,
    crestBrush: Brush,
    crestStroke: Stroke,
    glowBrush: Brush,
    glowCenter: Offset,
    glowRadius: Float,
    crestPath: Path,
    pulse: Float
) {
    crestPath.reset()
    crestPath.moveTo(-50f, height * 0.20f)
    crestPath.cubicTo(
        width * 0.35f, height * 0.08f * pulse,
        width * 0.65f, height * 0.35f * pulse,
        width + 50f, height * 0.18f
    )
    drawPath(path = crestPath, brush = crestBrush, style = crestStroke)
    drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
}

private fun DrawScope.drawLiquidBlobsDarkStyle(
    width: Float,
    height: Float,
    topGlowBrush: Brush,
    specularBrush: Brush,
    crestStroke: Stroke,
    midFillBrush: Brush,
    chromaticBrush: Brush,
    chromaticStroke: Stroke,
    ambientBrush: Brush,
    ambientCenter: Offset,
    ambientRadius: Float,
    crestPath: Path,
    path1: Path,
    midContourPath: Path,
    path2: Path,
    pulse: Float
) {
    crestPath.reset()
    crestPath.moveTo(-50f, height * 0.18f)
    crestPath.cubicTo(
        width * 0.35f, height * 0.04f * pulse,
        width * 0.65f, height * 0.32f * pulse,
        width + 50f, height * 0.16f
    )

    path1.reset()
    path1.addPath(crestPath)
    path1.lineTo(width + 50f, -50f)
    path1.lineTo(-50f, -50f)
    path1.close()
    drawPath(path = path1, brush = topGlowBrush)

    drawPath(path = crestPath, brush = specularBrush, style = crestStroke)

    midContourPath.reset()
    midContourPath.moveTo(-50f, height * 0.48f)
    midContourPath.cubicTo(
        width * 0.30f, height * 0.34f,
        width * 0.60f, height * 0.68f * pulse,
        width + 50f, height * 0.46f
    )

    path2.reset()
    path2.addPath(midContourPath)
    path2.lineTo(width + 50f, height + 50f)
    path2.lineTo(-50f, height + 50f)
    path2.close()
    drawPath(path = path2, brush = midFillBrush)

    drawPath(path = midContourPath, brush = chromaticBrush, style = chromaticStroke)

    drawCircle(brush = ambientBrush, radius = ambientRadius, center = ambientCenter)
}

private fun DrawScope.drawAuroraStyle(
    width: Float,
    height: Float,
    baseBg: Color,
    brush1: Brush,
    brush2: Brush,
    path1: Path,
    path2: Path,
    progress: Float,
    pulse: Float
) {
    drawRect(color = baseBg)

    val twoPi = (Math.PI * 2).toFloat()
    val waveOffset1 = sin(progress * twoPi) * 0.12f
    val waveOffset2 = cos(progress * twoPi) * 0.10f

    path1.reset()
    path1.moveTo(0f, height * (0.25f + waveOffset1))
    path1.cubicTo(
        width * 0.35f, height * (0.15f - waveOffset2 * pulse),
        width * 0.70f, height * (0.40f + waveOffset1 * pulse),
        width, height * (0.28f - waveOffset1)
    )
    path1.lineTo(width, height * 0.65f)
    path1.cubicTo(
        width * 0.65f, height * (0.60f - waveOffset1),
        width * 0.30f, height * (0.45f + waveOffset2),
        0f, height * 0.55f
    )
    path1.close()
    drawPath(path = path1, brush = brush1)

    path2.reset()
    path2.moveTo(0f, height * (0.50f - waveOffset2))
    path2.cubicTo(
        width * 0.40f, height * (0.65f + waveOffset1),
        width * 0.75f, height * (0.45f - waveOffset1),
        width, height * (0.58f + waveOffset2)
    )
    path2.lineTo(width, height)
    path2.lineTo(0f, height)
    path2.close()
    drawPath(path = path2, brush = brush2)
}

private fun DrawScope.drawMeshGradientStyle(
    width: Float,
    height: Float,
    base: Color,
    b1: Brush, r1: Float,
    b2: Brush, r2: Float,
    b3: Brush, r3: Float,
    b4: Brush, r4: Float,
    progress: Float
) {
    drawRect(color = base)

    val angle = progress * (Math.PI * 2).toFloat()
    val ox1 = cos(angle) * (width * 0.08f)
    val oy1 = sin(angle) * (height * 0.05f)
    val ox2 = sin(angle) * (width * 0.06f)
    val oy2 = cos(angle) * (height * 0.07f)

    translate(left = width * 0.2f + ox1, top = height * 0.2f + oy1) {
        drawCircle(brush = b1, radius = r1, center = Offset.Zero)
    }
    translate(left = width * 0.8f - ox2, top = height * 0.35f + oy2) {
        drawCircle(brush = b2, radius = r2, center = Offset.Zero)
    }
    translate(left = width * 0.25f - ox2, top = height * 0.75f - oy1) {
        drawCircle(brush = b3, radius = r3, center = Offset.Zero)
    }
    translate(left = width * 0.75f + ox1, top = height * 0.80f + oy2) {
        drawCircle(brush = b4, radius = r4, center = Offset.Zero)
    }
}

private fun DrawScope.drawAlbumArtBlurStyle(
    width: Float,
    height: Float,
    base: Color,
    primaryBrush: Brush,
    primaryRadius: Float,
    secondaryBrush: Brush,
    secondaryCenter: Offset,
    secondaryRadius: Float,
    pulse: Float
) {
    drawRect(color = base)

    translate(left = width * 0.5f, top = height * 0.45f) {
        scale(scaleX = pulse, scaleY = pulse, pivot = Offset.Zero) {
            drawCircle(brush = primaryBrush, radius = primaryRadius, center = Offset.Zero)
        }
    }
    drawCircle(brush = secondaryBrush, radius = secondaryRadius, center = secondaryCenter)
}

private fun DrawScope.drawSlowCausticsStyle(
    width: Float,
    height: Float,
    base: Color,
    strokeBrush: Brush,
    strokes: Array<Stroke>,
    poolBrush: Brush,
    poolRadius: Float,
    paths: Array<Path>,
    progress: Float
) {
    drawRect(color = base)

    val twoPi = (Math.PI * 2).toFloat()
    val shiftX = sin(progress * twoPi) * width * 0.05f
    val shiftY = cos(progress * twoPi) * height * 0.04f

    for (i in 0 until 4) {
        val yOffset = height * (0.22f + i * 0.18f)
        val path = paths[i]
        path.reset()
        path.moveTo(-30f, yOffset + shiftY * (if (i % 2 == 0) 1f else -1f))
        path.cubicTo(
            width * 0.33f + shiftX, yOffset - 35f,
            width * 0.66f - shiftX, yOffset + 35f,
            width + 30f, yOffset
        )
        drawPath(path = path, brush = strokeBrush, style = strokes[i])
    }

    translate(left = width * 0.5f + shiftX, top = height * 0.55f + shiftY) {
        drawCircle(brush = poolBrush, radius = poolRadius, center = Offset.Zero)
    }
}

/**
 * Creates a cached native Paint with a 64x64 blue-noise / dithering BitmapShader.
 * Completely eliminates gradient stepping and color banding on 8-bit displays.
 */
private fun createDitherPaint(): android.graphics.Paint {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = Random(42)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val noise = (random.nextInt(7) - 3)
            val alpha = (8 + noise).coerceIn(2, 16)
            bitmap.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
        }
    }
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    return android.graphics.Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        this.shader = shader
    }
}

private fun derivePalette(baseColor: Color, source: String, fixedColor: Color): Triple<Color, Color, Color> {
    val primary = when (source) {
        "CUSTOM" -> fixedColor
        "PALETTE" -> baseColor
        else -> baseColor
    }

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(primary.toArgb(), hsv)

    val hsvSecondary = floatArrayOf(
        (hsv[0] + 45f) % 360f,
        (hsv[1] * 0.9f).coerceIn(0f, 1f),
        (hsv[2] * 0.95f).coerceIn(0f, 1f)
    )
    val secondary = Color(android.graphics.Color.HSVToColor(hsvSecondary))

    val hsvTertiary = floatArrayOf(
        (hsv[0] + 160f) % 360f,
        (hsv[1] * 0.85f).coerceIn(0f, 1f),
        (hsv[2] * 0.90f).coerceIn(0f, 1f)
    )
    val tertiary = Color(android.graphics.Color.HSVToColor(hsvTertiary))

    return Triple(primary, secondary, tertiary)
}
