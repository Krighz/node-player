package com.nodeplayer.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.components.ArtworkCache
import com.nodeplayer.ui.components.BoundedLruCache
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.components.imageBitmapOf
import com.nodeplayer.ui.theme.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Representation of a GPU-ready artwork mesh texture generated asynchronously.
 */
data class ArtworkMesh(
    val image: ImageBitmap,
    val seed: Int = 0
)

/**
 * Generator producing 32x32 blurred, seam-preserved, non-literal mesh textures
 * from an album cover or fallback color per D-05, D-06, D-07, and D-08.
 */
object ArtworkMeshGenerator {

    private const val COARSE_COLS = 6
    private const val COARSE_ROWS = 6
    private const val TEXTURE_SIZE = 32

    /**
     * Rotates lower cells (rows 1 until rows) via cyclic shift and horizontal mirror
     * while retaining row 0 (seam row) 100% literal per D-06.
     */
    fun rotateBelowSeam(cells: IntArray, cols: Int, rows: Int, seed: Int): IntArray {
        if (rows <= 1) return cells
        val random = Random(seed)
        val mirror = random.nextBoolean()
        val shift = if (cols > 0) (random.nextInt().let { if (it < 0) -it else it }) % cols else 0
        val out = cells.copyOf()
        for (row in 1 until rows) {
            val base = row * cols
            for (x in 0 until cols) {
                val src = if (mirror) cols - 1 - x else x
                out[base + x] = cells[base + (src + shift) % cols]
            }
        }
        return out
    }

    /**
     * Smoothstep Hermite polynomial interpolation: t * t * (3 - 2 * t).
     */
    fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    /**
     * Resamples a coarse grid (cols x rows) up to (dstWidth x dstHeight) using
     * smoothstep Hermite interpolation in both dimensions.
     */
    fun resampleHermite(
        src: IntArray,
        srcCols: Int,
        srcRows: Int,
        dstWidth: Int,
        dstHeight: Int
    ): IntArray {
        val out = IntArray(dstWidth * dstHeight)
        val colScale = if (dstWidth > 1) (srcCols - 1).toFloat() / (dstWidth - 1) else 0f
        val rowScale = if (dstHeight > 1) (srcRows - 1).toFloat() / (dstHeight - 1) else 0f

        for (y in 0 until dstHeight) {
            val srcYExact = y * rowScale
            val y0 = srcYExact.toInt().coerceIn(0, srcRows - 1)
            val y1 = (y0 + 1).coerceIn(0, srcRows - 1)
            val ty = smoothstep(srcYExact - y0)

            for (x in 0 until dstWidth) {
                val srcXExact = x * colScale
                val x0 = srcXExact.toInt().coerceIn(0, srcCols - 1)
                val x1 = (x0 + 1).coerceIn(0, srcCols - 1)
                val tx = smoothstep(srcXExact - x0)

                val c00 = src[y0 * srcCols + x0]
                val c10 = src[y0 * srcCols + x1]
                val c01 = src[y1 * srcCols + x0]
                val c11 = src[y1 * srcCols + x1]

                // Bilinear Hermite interpolation of RGB channels
                val a = 0xFF
                val r0 = ((c00 shr 16) and 0xFF) * (1f - tx) + ((c10 shr 16) and 0xFF) * tx
                val r1 = ((c01 shr 16) and 0xFF) * (1f - tx) + ((c11 shr 16) and 0xFF) * tx
                val r = (r0 * (1f - ty) + r1 * ty).roundToInt().coerceIn(0, 255)

                val g0 = ((c00 shr 8) and 0xFF) * (1f - tx) + ((c10 shr 8) and 0xFF) * tx
                val g1 = ((c01 shr 8) and 0xFF) * (1f - tx) + ((c11 shr 8) and 0xFF) * tx
                val g = (g0 * (1f - ty) + g1 * ty).roundToInt().coerceIn(0, 255)

                val b0 = (c00 and 0xFF) * (1f - tx) + (c10 and 0xFF) * tx
                val b1 = (c01 and 0xFF) * (1f - tx) + (c11 and 0xFF) * tx
                val b = (b0 * (1f - ty) + b1 * ty).roundToInt().coerceIn(0, 255)

                out[y * dstWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /**
     * Applies horizontal and vertical box blur of radius 1 over the pixel buffer.
     */
    fun boxBlurPass(pixels: IntArray, width: Int, height: Int): IntArray {
        val temp = IntArray(pixels.size)
        val out = IntArray(pixels.size)

        // Horizontal pass
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var rAcc = 0
                var gAcc = 0
                var bAcc = 0
                var count = 0
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        val c = pixels[rowOffset + nx]
                        rAcc += (c shr 16) and 0xFF
                        gAcc += (c shr 8) and 0xFF
                        bAcc += c and 0xFF
                        count++
                    }
                }
                val a = 0xFF
                val r = (rAcc / count).coerceIn(0, 255)
                val g = (gAcc / count).coerceIn(0, 255)
                val b = (bAcc / count).coerceIn(0, 255)
                temp[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var rAcc = 0
                var gAcc = 0
                var bAcc = 0
                var count = 0
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        val c = temp[ny * width + x]
                        rAcc += (c shr 16) and 0xFF
                        gAcc += (c shr 8) and 0xFF
                        bAcc += c and 0xFF
                        count++
                    }
                }
                val a = 0xFF
                val r = (rAcc / count).coerceIn(0, 255)
                val g = (gAcc / count).coerceIn(0, 255)
                val b = (bAcc / count).coerceIn(0, 255)
                out[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return out
    }

    /**
     * Nudges saturation and floors brightness off pure black per D-05.
     */
    fun liftColor(argb: Int): Int {
        val color = Color(argb)
        val hsv = ColorUtils.colorToHsv(color)
        hsv[1] = (hsv[1] * 1.12f).coerceIn(0f, 1f) // Nudge saturation
        hsv[2] = hsv[2].coerceIn(0.045f, 1f)      // Floor off pure black
        return ColorUtils.hsvToColor(hsv).toArgb()
    }

    /**
     * Downsamples an ImageBitmap to a 6x6 grid, transforms lower cells,
     * interpolates to 32x32, and runs 3-pass box blur.
     */
    fun meshOf(source: ImageBitmap, seed: Int = 0): ArtworkMesh {
        val srcW = source.width
        val srcH = source.height
        val fullPixels = IntArray(srcW * srcH)
        source.readPixels(fullPixels)

        // Downsample to 6x6 coarse cells.
        // Invert vertically so row 0 corresponds to the bottom edge of the album cover (seam).
        val coarse = IntArray(COARSE_COLS * COARSE_ROWS)
        val cellW = srcW.toFloat() / COARSE_COLS
        val cellH = srcH.toFloat() / COARSE_ROWS

        for (gridY in 0 until COARSE_ROWS) {
            // Seam row 0 = bottom of cover (srcH - 1 down to srcH - cellH)
            val flippedGridY = (COARSE_ROWS - 1) - gridY
            val startY = (flippedGridY * cellH).toInt().coerceIn(0, srcH - 1)
            val endY = ((flippedGridY + 1) * cellH).toInt().coerceIn(startY + 1, srcH)

            for (gridX in 0 until COARSE_COLS) {
                val startX = (gridX * cellW).toInt().coerceIn(0, srcW - 1)
                val endX = ((gridX + 1) * cellW).toInt().coerceIn(startX + 1, srcW)

                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0

                for (py in startY until endY) {
                    val rowOff = py * srcW
                    for (px in startX until endX) {
                        val c = fullPixels[rowOff + px]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                        count++
                    }
                }

                val avgR = if (count > 0) (rSum / count).toInt() else 0
                val avgG = if (count > 0) (gSum / count).toInt() else 0
                val avgB = if (count > 0) (bSum / count).toInt() else 0
                val rawArgb = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                coarse[gridY * COARSE_COLS + gridX] = liftColor(rawArgb)
            }
        }

        // Apply seam row retention and cell transformation
        val rotated = rotateBelowSeam(coarse, COARSE_COLS, COARSE_ROWS, seed)

        // Hermite smoothstep resample up to 32x32
        val resampled = resampleHermite(rotated, COARSE_COLS, COARSE_ROWS, TEXTURE_SIZE, TEXTURE_SIZE)

        // Bake 3 passes of box blur (radius 1)
        var blurred = resampled
        repeat(3) {
            blurred = boxBlurPass(blurred, TEXTURE_SIZE, TEXTURE_SIZE)
        }

        val bitmap = imageBitmapOf(blurred, TEXTURE_SIZE, TEXTURE_SIZE)
        return ArtworkMesh(bitmap, seed)
    }

    /**
     * Fallback mesh for missing artwork or loading state per D-08.
     */
    fun fallbackMesh(baseColor: Color = Color(0xFF121212)): ArtworkMesh {
        val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE)
        val hsv = ColorUtils.colorToHsv(baseColor)
        val darkHsv = floatArrayOf(hsv[0], (hsv[1] * 0.7f).coerceIn(0f, 1f), 0.045f)
        val midHsv = floatArrayOf(hsv[0], (hsv[1] * 0.85f).coerceIn(0f, 1f), 0.08f)
        val c0 = ColorUtils.hsvToColor(darkHsv).toArgb()
        val c1 = ColorUtils.hsvToColor(midHsv).toArgb()

        for (y in 0 until TEXTURE_SIZE) {
            val t = smoothstep(y.toFloat() / (TEXTURE_SIZE - 1))
            val r = (((c0 shr 16) and 0xFF) * (1f - t) + ((c1 shr 16) and 0xFF) * t).roundToInt()
            val g = (((c0 shr 8) and 0xFF) * (1f - t) + ((c1 shr 8) and 0xFF) * t).roundToInt()
            val b = ((c0 and 0xFF) * (1f - t) + (c1 and 0xFF) * t).roundToInt()
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            for (x in 0 until TEXTURE_SIZE) {
                pixels[y * TEXTURE_SIZE + x] = argb
            }
        }

        val bitmap = imageBitmapOf(pixels, TEXTURE_SIZE, TEXTURE_SIZE)
        return ArtworkMesh(bitmap, 0)
    }
}

/**
 * Cache for tiny 32x32 precomputed mesh textures.
 */
object ArtworkMeshCache {
    private val cache = BoundedLruCache<String, ArtworkMesh>(128)

    fun get(key: String): ArtworkMesh? = cache.get(key)
    fun put(key: String, mesh: ArtworkMesh) = cache.put(key, mesh)
    fun clear() = cache.clear()
}

/**
 * High-performance 120fps hardware artwork mesh backdrop per D-05, D-06, D-07, and D-08.
 *
 * Uses DrawScope.drawImage with FilterQuality.Low to harness GPU hardware bilinear texture
 * filtering with zero per-frame CPU allocations.
 */
@Composable
fun ArtworkMeshBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    seam: () -> Dp = { 0.dp },
    fallback: Color = Color(0xFF121212)
) {
    val density = LocalDensity.current
    var currentMesh by remember { mutableStateOf(ArtworkMeshCache.get(artworkUrl ?: "") ?: ArtworkMeshGenerator.fallbackMesh(fallback)) }
    var previousMesh by remember { mutableStateOf<ArtworkMesh?>(null) }
    val crossfadeAlpha = remember { Animatable(1f) }

    LaunchedEffect(artworkUrl, fallback) {
        val targetMesh = withContext(Dispatchers.Default) {
            if (artworkUrl.isNullOrBlank()) {
                ArtworkMeshGenerator.fallbackMesh(fallback)
            } else {
                val cached = ArtworkMeshCache.get(artworkUrl)
                if (cached != null) {
                    cached
                } else {
                    val rawBitmap = PlatformArtworkProvider.loadArtwork(artworkUrl, 128)
                    if (rawBitmap != null) {
                        val generated = ArtworkMeshGenerator.meshOf(rawBitmap, artworkUrl.hashCode())
                        ArtworkMeshCache.put(artworkUrl, generated)
                        generated
                    } else {
                        ArtworkMeshGenerator.fallbackMesh(fallback)
                    }
                }
            }
        }

        if (targetMesh != currentMesh) {
            previousMesh = currentMesh
            currentMesh = targetMesh
            crossfadeAlpha.snapTo(0f)
            crossfadeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            previousMesh = null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val seamPx = with(density) { seam().toPx() }
        val alpha = crossfadeAlpha.value
        val prev = previousMesh

        if (prev != null && alpha < 1f) {
            drawMesh(mesh = prev, seamY = seamPx, alpha = 1f - alpha)
        }
        drawMesh(mesh = currentMesh, seamY = seamPx, alpha = alpha)

        // Subtle vertical legibility scrim
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.30f)
                )
            ),
            topLeft = Offset.Zero,
            size = size
        )
    }
}

/**
 * Zero-allocation draw of the mesh texture.
 * Above seam: hold row 0 of the texture stretched across the width.
 * Below seam: stretch full 32x32 texture with bilinear GPU texture filtering.
 */
private fun DrawScope.drawMesh(mesh: ArtworkMesh, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val image = mesh.image
    val width = size.width.roundToInt()
    val clampedSeamY = seamY.coerceIn(0f, size.height)

    // Above seam: Hold row 0 (seam row)
    if (clampedSeamY > 0.5f) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, clampedSeamY.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low
        )
    }

    // From seam down: Stretch full mesh texture
    val remainingHeight = (size.height - clampedSeamY).roundToInt()
    if (remainingHeight > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(0, clampedSeamY.roundToInt()),
            dstSize = IntSize(width, remainingHeight),
            alpha = alpha,
            filterQuality = FilterQuality.Low
        )
    }
}
