package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Palette derivation and contrast calculation utilities for dynamic Now Playing and Lyrics backgrounds.
 */
object PaletteExtractor {

    private val paletteCache = ConcurrentHashMap<String, AlbumArtPalette>()
    private const val MAX_CACHE_SIZE = 120

    /**
     * Resolves a cached or computed AlbumArtPalette for the given track.
     */
    fun getCachedPalette(track: Track?): AlbumArtPalette? {
        if (track == null) return null
        val key = getCacheKey(track)
        return paletteCache[key]
    }

    /**
     * Stores an extracted palette in the LRU memory cache.
     */
    fun cachePalette(track: Track, palette: AlbumArtPalette) {
        if (paletteCache.size > MAX_CACHE_SIZE) {
            paletteCache.clear()
        }
        paletteCache[getCacheKey(track)] = palette
    }

    fun getCacheKey(track: Track): String {
        return track.path.ifBlank { track.id.toString() }
    }

    /**
     * Computes the relative luminance of a color according to WCAG 2.1 specs.
     */
    fun calculateLuminance(color: Color): Float {
        fun channelLuminance(c: Float): Float {
            return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
        }
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Computes the contrast ratio between foreground and background colors.
     * Returns a value between 1.0 and 21.0.
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val lum1 = calculateLuminance(foreground)
        val lum2 = calculateLuminance(background)
        val brightest = max(lum1, lum2)
        val darkest = min(lum1, lum2)
        return (brightest + 0.05f) / (darkest + 0.05f)
    }

    /**
     * Ensures an accent color has adequate contrast against the provided dark background.
     * If contrast is lower than minRatio (default 3.0), falls back to Color.White.
     */
    fun ensureContrastAgainstDark(accent: Color, background: Color, minRatio: Float = 3.0f): Color {
        val ratio = calculateContrastRatio(accent, background)
        return if (ratio >= minRatio) accent else Color.White
    }

    /**
     * Extracts a multi-swatch AlbumArtPalette directly from the decoded album cover ImageBitmap,
     * preserving the actual dominant, vibrant, accent, and darkMuted colors from the artwork.
     */
    fun extractFromBitmap(bitmap: ImageBitmap): AlbumArtPalette {
        val pixelMap = bitmap.toPixelMap()
        if (pixelMap.width <= 0 || pixelMap.height <= 0) {
            return AlbumArtPalette.DEFAULT_OBSIDIAN
        }

        // Quantize RGB into 5-bit buckets (32x32x32) and accumulate exact color averages per bucket
        class Bucket {
            var count = 0
            var rSum = 0f
            var gSum = 0f
            var bSum = 0f
        }

        val buckets = HashMap<Int, Bucket>()
        val stepX = max(1, pixelMap.width / 56)
        val stepY = max(1, pixelMap.height / 56)

        var y = 0
        while (y < pixelMap.height) {
            var x = 0
            while (x < pixelMap.width) {
                val c = pixelMap[x, y]
                if (c.alpha >= 0.5f) {
                    val r5 = (c.red * 31f).toInt().coerceIn(0, 31)
                    val g5 = (c.green * 31f).toInt().coerceIn(0, 31)
                    val b5 = (c.blue * 31f).toInt().coerceIn(0, 31)
                    val key = (r5 shl 10) or (g5 shl 5) or b5
                    val bucket = buckets.getOrPut(key) { Bucket() }
                    bucket.count++
                    bucket.rSum += c.red
                    bucket.gSum += c.green
                    bucket.bSum += c.blue
                }
                x += stepX
            }
            y += stepY
        }

        if (buckets.isEmpty()) return AlbumArtPalette.DEFAULT_OBSIDIAN

        data class Swatch(val color: Color, val hsv: FloatArray, val count: Int)

        val swatches = buckets.values.map { b ->
            val avgColor = Color(
                red = (b.rSum / b.count).coerceIn(0f, 1f),
                green = (b.gSum / b.count).coerceIn(0f, 1f),
                blue = (b.bSum / b.count).coerceIn(0f, 1f),
                alpha = 1f
            )
            Swatch(avgColor, ColorUtils.colorToHsv(avgColor), b.count)
        }

        // 1. Dominant: most frequent color cluster across the cover (avoiding pure clipping extremes when possible)
        val dominantSwatch = swatches
            .filter { it.hsv[2] in 0.08f..0.95f }
            .maxByOrNull { it.count }
            ?: swatches.maxBy { it.count }

        // 2. Vibrant: richest chromatic cluster present in the cover
        val vibrantSwatch = swatches
            .filter { it.hsv[1] >= 0.22f && it.hsv[2] >= 0.18f }
            .maxByOrNull { it.hsv[1] * it.hsv[1] * sqrt(it.count.toFloat()) }
            ?: dominantSwatch

        // 3. Accent: secondary distinct color cluster from the cover (at least 24 deg hue separation)
        val accentSwatch = swatches
            .filter {
                it.hsv[1] >= 0.18f && it.hsv[2] >= 0.22f &&
                    hueDistance(it.hsv[0], vibrantSwatch.hsv[0]) >= 24f
            }
            .maxByOrNull { it.hsv[1] * sqrt(it.count.toFloat()) }
            ?: swatches
                .filter { hueDistance(it.hsv[0], dominantSwatch.hsv[0]) >= 20f && it.hsv[2] >= 0.20f }
                .maxByOrNull { it.count }
            ?: vibrantSwatch

        // 4. DarkMuted: deepest low-luminance swatch from the cover, or darkened dominant tone
        val darkSwatch = swatches
            .filter { it.hsv[2] in 0.06f..0.32f }
            .maxByOrNull { it.count }
            ?.color
            ?: ColorUtils.hsvToColor(
                floatArrayOf(
                    dominantSwatch.hsv[0],
                    (dominantSwatch.hsv[1] * 0.70f).coerceIn(0f, 0.65f),
                    0.16f
                )
            )

        val safeAccent = ensureContrastAgainstDark(accentSwatch.color, darkSwatch)

        return AlbumArtPalette(
            dominant = dominantSwatch.color.toLongArgb(),
            vibrant = vibrantSwatch.color.toLongArgb(),
            darkMuted = darkSwatch.toLongArgb(),
            accent = safeAccent.toLongArgb(),
            isFallback = false
        )
    }

    /**
     * Derives a full AlbumArtPalette from an extracted vibrant color or base theme color.
     */
    fun derivePaletteFromBaseColor(baseColor: Color): AlbumArtPalette {
        val hsv = ColorUtils.colorToHsv(baseColor)
        val hue = hsv[0]

        // Vibrant: elevated saturation & brightness
        val vibrantHsv = floatArrayOf(hue, (hsv[1] * 1.15f).coerceIn(0.45f, 0.95f), (hsv[2] * 1.1f).coerceIn(0.6f, 0.95f))
        val vibrant = ColorUtils.hsvToColor(vibrantHsv)

        // Dominant: rich midtone
        val dominantHsv = floatArrayOf(hue, (hsv[1] * 0.85f).coerceIn(0.35f, 0.8f), (hsv[2] * 0.7f).coerceIn(0.3f, 0.65f))
        val dominant = ColorUtils.hsvToColor(dominantHsv)

        // Dark Muted: deep shaded background tone
        val darkMutedHsv = floatArrayOf(hue, (hsv[1] * 0.6f).coerceIn(0.2f, 0.5f), 0.14f)
        val darkMuted = ColorUtils.hsvToColor(darkMutedHsv)

        // Accent: high-contrast tint
        val accent = ensureContrastAgainstDark(vibrant, darkMuted)

        return AlbumArtPalette(
            dominant = dominant.toLongArgb(),
            vibrant = vibrant.toLongArgb(),
            darkMuted = darkMuted.toLongArgb(),
            accent = accent.toLongArgb(),
            isFallback = false
        )
    }

    private fun hueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2) % 360f
        return min(diff, 360f - diff)
    }

    private fun Color.toLongArgb(): Long {
        val a = (alpha * 255f).toInt() and 0xFF
        val r = (red * 255f).toInt() and 0xFF
        val g = (green * 255f).toInt() and 0xFF
        val b = (blue * 255f).toInt() and 0xFF
        return ((a.toLong() and 0xFFL) shl 24) or
            ((r.toLong() and 0xFFL) shl 16) or
            ((g.toLong() and 0xFFL) shl 8) or
            (b.toLong() and 0xFFL)
    }

    fun Long.toComposeColor(): Color {
        val a = ((this shr 24) and 0xFF).toInt()
        val r = ((this shr 16) and 0xFF).toInt()
        val g = ((this shr 8) and 0xFF).toInt()
        val b = (this and 0xFF).toInt()
        return Color(r, g, b, a)
    }
}
