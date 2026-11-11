package com.nodeplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.MoodGenre
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

const val MOOD_CARD_ASPECT = 1.72f
const val MOOD_STRIPE_FRACTION = 0.08f
val MOOD_CORNER_RADIUS = 12.dp

data class MoodTone(
    val stripe: Color,
    val placeholder: Color,
    val duotone: ColorFilter
)

/**
 * Deterministically generates a fallback mood color from a category title
 * using a stable string hash mapped across vibrant HSV color space.
 */
fun fallbackMoodColor(title: String): Color {
    val hash = abs(title.hashCode())
    val hue = (hash % 360).toFloat()
    val sat = 0.65f + ((hash ushr 8) % 25) / 100f // 0.65 .. 0.89
    val value = 0.75f + ((hash ushr 16) % 20) / 100f // 0.75 .. 0.94
    return hsvToColor(hue, sat, value)
}

/**
 * Simple HSV to Color conversion utility avoiding platform dependencies.
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = (hue % 360f + 360f) % 360f / 60f
    val i = h.toInt()
    val f = h - i
    val p = value * (1f - saturation)
    val q = value * (1f - saturation * f)
    val t = value * (1f - saturation * (1f - f))

    val (r, g, b) = when (i) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return Color(r, g, b, 1f)
}

/**
 * Calculates stripe color, skeleton placeholder, and duotone ColorFilter from MoodGenre.
 * Per D-04 / BitChord specification:
 * - base: extracted from stripeColor or deterministic fallback
 * - stripe = lerp(base, Color.Black, 0.08f)
 * - shadow = lerp(base, Color.Black, 0.68f)
 * - highlight = lerp(base, Color.White, 0.32f)
 * - placeholder = lerp(shadow, highlight, 0.45f)
 * - duotone = duotoneColorFilter(shadow, highlight)
 */
fun moodTone(item: MoodGenre): MoodTone {
    val base = item.stripeColor?.let { raw ->
        // Handle unsigned 32-bit hex ARGB color from InnerTube
        Color(raw.toInt())
    } ?: fallbackMoodColor(item.title)

    val stripe = lerp(base, Color.Black, 0.08f)
    val shadow = lerp(base, Color.Black, 0.68f)
    val highlight = lerp(base, Color.White, 0.32f)
    val placeholder = lerp(shadow, highlight, 0.45f)
    val duotone = duotoneColorFilter(shadow, highlight)

    return MoodTone(
        stripe = stripe,
        placeholder = placeholder,
        duotone = duotone
    )
}

/**
 * Builds a 4x5 ColorMatrix that maps input image luminance:
 *   L = 0.299 * R + 0.587 * G + 0.114 * B
 * to a linear blend from shadow to highlight:
 *   outChannel = shadowChannel + L * (highlightChannel - shadowChannel)
 *
 * For each channel (Red, Green, Blue):
 *   span = highlightChannel - shadowChannel
 *   row = [ span * 0.299, span * 0.587, span * 0.114, 0, shadowChannel * 255 ]
 *
 * Alpha row: [ 0, 0, 0, 1, 0 ]
 */
fun duotoneColorFilter(shadow: Color, highlight: Color): ColorFilter {
    val matrix = duotoneMatrixArray(shadow, highlight)
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

/**
 * Returns raw 20-float array representing the duotone 4x5 ColorMatrix.
 */
fun duotoneMatrixArray(shadow: Color, highlight: Color): FloatArray {
    fun channelRow(from: Float, to: Float): FloatArray {
        val span = to - from
        return floatArrayOf(
            span * 0.299f,
            span * 0.587f,
            span * 0.114f,
            0f,
            from * 255f
        )
    }

    val rRow = channelRow(shadow.red, highlight.red)
    val gRow = channelRow(shadow.green, highlight.green)
    val bRow = channelRow(shadow.blue, highlight.blue)
    val aRow = floatArrayOf(0f, 0f, 0f, 1f, 0f)

    return rRow + gRow + bRow + aRow
}

/**
 * Formats YouTube Music thumbnail URL for high-res duotone sleeve rendering:
 * sanitizes URL schema to HTTPS and appends/replaces dimension parameters with '=w800-h800-l90-rj'.
 */
fun formatMoodThumbnailUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    var sanitized = url.trim()
    if (sanitized.startsWith("http://")) {
        sanitized = "https://" + sanitized.removePrefix("http://")
    }
    if (!sanitized.startsWith("https://")) return null

    // Replace existing dimension params if present, or append
    val eqIdx = sanitized.lastIndexOf('=')
    return if (eqIdx != -1 && eqIdx > sanitized.lastIndexOf('/')) {
        sanitized.substring(0, eqIdx) + "=w800-h800-l90-rj"
    } else {
        "$sanitized=w800-h800-l90-rj"
    }
}

/**
 * BitChord 1-to-1 duotone sleeve component for YouTube Music Moods & Genres.
 * Features:
 * - 1.72 aspect ratio card
 * - Rounded corners with ContinuousRoundedRectangle(12.dp)
 * - 0.08f left-hand accent stripe
 * - Luminance-based duotone ColorMatrix filtering on cover artwork
 * - High-legibility title with subtle text drop shadow
 */
@Composable
fun MoodGenreCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = remember(item.stripeColor, item.title) { moodTone(item) }
    val cardShape = remember { ContinuousRoundedRectangle(MOOD_CORNER_RADIUS) }

    // Cover art loading
    val formattedUrl = remember(item.thumbnailUrl) { formatMoodThumbnailUrl(item.thumbnailUrl) }
    var bitmap by remember(formattedUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(formattedUrl) {
        if (formattedUrl != null) {
            val loaded = withContext(Dispatchers.IO) {
                PlatformArtworkProvider.loadArtwork(formattedUrl, maxSizePx = 512)
            }
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(MOOD_CARD_ASPECT)
            .clip(cardShape)
            .background(tone.placeholder)
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Background duotone artwork (or fallback gradient placeholder)
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                colorFilter = tone.duotone,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant placeholder gradient between shadow and placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                tone.placeholder,
                                lerp(tone.placeholder, Color.Black, 0.35f)
                            )
                        )
                    )
            )
        }

        // Left-hand accent color stripe (0.08f of card width)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(MOOD_STRIPE_FRACTION)
                .align(Alignment.CenterStart)
                .background(tone.stripe)
        )

        // Subtle gradient overlay from bottom/left for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        ),
                        startY = 0f
                    )
                )
        )

        // Category title
        Text(
            text = item.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.70f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 18.dp, // Clear the stripe
                    end = 12.dp,
                    bottom = 12.dp,
                    top = 8.dp
                )
        )
    }
}
