package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

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
        val darkMutedHsv = floatArrayOf(hue, (hsv[1] * 0.6f).coerceIn(0.2f, 0.5f), 0.12f)
        val darkMuted = ColorUtils.hsvToColor(darkMutedHsv)

        // Accent: high-contrast tint
        val accent = ensureContrastAgainstDark(vibrant, darkMuted)

        fun Color.toLongArgb(): Long {
            val a = (alpha * 255f).toInt() and 0xFF
            val r = (red * 255f).toInt() and 0xFF
            val g = (green * 255f).toInt() and 0xFF
            val b = (blue * 255f).toInt() and 0xFF
            return ((a.toLong() and 0xFFL) shl 24) or
                    ((r.toLong() and 0xFFL) shl 16) or
                    ((g.toLong() and 0xFFL) shl 8) or
                    (b.toLong() and 0xFFL)
        }

        return AlbumArtPalette(
            dominant = dominant.toLongArgb(),
            vibrant = vibrant.toLongArgb(),
            darkMuted = darkMuted.toLongArgb(),
            accent = accent.toLongArgb(),
            isFallback = false
        )
    }

    fun Long.toComposeColor(): Color {
        val a = ((this shr 24) and 0xFF).toInt()
        val r = ((this shr 16) and 0xFF).toInt()
        val g = ((this shr 8) and 0xFF).toInt()
        val b = (this and 0xFF).toInt()
        return Color(r, g, b, a)
    }
}
