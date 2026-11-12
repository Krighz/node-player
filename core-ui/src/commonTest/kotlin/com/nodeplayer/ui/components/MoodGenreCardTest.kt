package com.nodeplayer.ui.components

import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.MoodGenre
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodGenreCardTest {

    @Test
    fun testAspectAndStripeConstants() {
        assertEquals(1.72f, MOOD_CARD_ASPECT, 0.001f)
        assertEquals(0.08f, MOOD_STRIPE_FRACTION, 0.001f)
        assertEquals(12, MOOD_CORNER_RADIUS.value.toInt())
    }

    @Test
    fun testDuotoneColorFilterMatrixLuminanceWeights() {
        // Shadow is black (0,0,0), highlight is white (1,1,1)
        // For each channel: span = 1.0, from = 0.0
        // Expected row: [0.299, 0.587, 0.114, 0, 0]
        val matrix = duotoneMatrixArray(Color.Black, Color.White)
        assertEquals(20, matrix.size)

        // Red row (indices 0..4)
        assertEquals(0.299f, matrix[0], 0.001f)
        assertEquals(0.587f, matrix[1], 0.001f)
        assertEquals(0.114f, matrix[2], 0.001f)
        assertEquals(0.0f, matrix[3], 0.001f)
        assertEquals(0.0f, matrix[4], 0.001f)

        // Green row (indices 5..9)
        assertEquals(0.299f, matrix[5], 0.001f)
        assertEquals(0.587f, matrix[6], 0.001f)
        assertEquals(0.114f, matrix[7], 0.001f)
        assertEquals(0.0f, matrix[8], 0.001f)
        assertEquals(0.0f, matrix[9], 0.001f)

        // Blue row (indices 10..14)
        assertEquals(0.299f, matrix[10], 0.001f)
        assertEquals(0.587f, matrix[11], 0.001f)
        assertEquals(0.114f, matrix[12], 0.001f)
        assertEquals(0.0f, matrix[13], 0.001f)
        assertEquals(0.0f, matrix[14], 0.001f)

        // Alpha row (indices 15..19): [0, 0, 0, 1, 0]
        assertEquals(0.0f, matrix[15], 0.001f)
        assertEquals(0.0f, matrix[16], 0.001f)
        assertEquals(0.0f, matrix[17], 0.001f)
        assertEquals(1.0f, matrix[18], 0.001f)
        assertEquals(0.0f, matrix[19], 0.001f)
    }

    @Test
    fun testDuotoneColorFilterWithCustomColors() {
        val shadow = Color(0.1f, 0.2f, 0.3f, 1f)
        val highlight = Color(0.9f, 0.8f, 0.7f, 1f)

        val matrix = duotoneMatrixArray(shadow, highlight)

        val rSpan = 0.9f - 0.1f // 0.8
        val gSpan = 0.8f - 0.2f // 0.6
        val bSpan = 0.7f - 0.3f // 0.4

        // Red row
        assertEquals(rSpan * 0.299f, matrix[0], 0.001f)
        assertEquals(rSpan * 0.587f, matrix[1], 0.001f)
        assertEquals(rSpan * 0.114f, matrix[2], 0.001f)
        assertEquals(shadow.red * 255f, matrix[4], 0.01f)

        // Green row
        assertEquals(gSpan * 0.299f, matrix[5], 0.001f)
        assertEquals(gSpan * 0.587f, matrix[6], 0.001f)
        assertEquals(gSpan * 0.114f, matrix[7], 0.001f)
        assertEquals(shadow.green * 255f, matrix[9], 0.01f)

        // Blue row
        assertEquals(bSpan * 0.299f, matrix[10], 0.001f)
        assertEquals(bSpan * 0.587f, matrix[11], 0.001f)
        assertEquals(bSpan * 0.114f, matrix[12], 0.001f)
        assertEquals(shadow.blue * 255f, matrix[14], 0.01f)
    }

    @Test
    fun testMoodToneCalculationWithStripeColor() {
        val item = MoodGenre(
            title = "Chill & Relax",
            stripeColor = 0xFF4A90E2L,
            browseId = "FEchill",
            params = "xyz",
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val tone = moodTone(item)
        assertNotNull(tone.stripe)
        assertNotNull(tone.placeholder)
        assertNotNull(tone.duotone)

        // Stripe is lerped with black at 0.08f, so it should be slightly darker than base
        val base = Color(0xFF4A90E2.toInt())
        assertTrue(tone.stripe.red <= base.red)
        assertTrue(tone.stripe.green <= base.green)
        assertTrue(tone.stripe.blue <= base.blue)
    }

    @Test
    fun testFallbackMoodColorDeterminism() {
        val color1 = fallbackMoodColor("Hip-Hop")
        val color2 = fallbackMoodColor("Hip-Hop")
        val color3 = fallbackMoodColor("Classical")

        assertEquals(color1, color2)
        assertNotEquals(color1, color3)

        // Colors should have high alpha and vibrant components
        assertEquals(1f, color1.alpha, 0.001f)
        assertTrue(color1.red > 0f || color1.green > 0f || color1.blue > 0f)
    }

    @Test
    fun testThumbnailUrlFormatting() {
        val standard = "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w800-h800-l90-rj",
            formatMoodThumbnailUrl(standard)
        )

        val noParams = "https://lh3.googleusercontent.com/abc"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w800-h800-l90-rj",
            formatMoodThumbnailUrl(noParams)
        )

        // Upgrade http to https
        val httpUrl = "http://lh3.googleusercontent.com/xyz"
        assertEquals(
            "https://lh3.googleusercontent.com/xyz=w800-h800-l90-rj",
            formatMoodThumbnailUrl(httpUrl)
        )

        // Invalid or null URLs
        assertNull(formatMoodThumbnailUrl(null))
        assertNull(formatMoodThumbnailUrl(""))
        assertNull(formatMoodThumbnailUrl("ftp://invalid.com/pic"))
    }
}
