package com.nodeplayer.ui.screens

import androidx.compose.ui.graphics.Color
import com.nodeplayer.ui.theme.ColorUtils
import kotlin.test.*

class PlaylistDetailViewTest {

    @Test
    fun testBuildMetadataStringWithHoursAndYear() {
        // 75 minutes = 4,500,000 ms
        val totalMs = 75L * 60L * 1000L
        val metadata = buildMetadataString(year = 2024, songCount = 15, totalDurationMs = totalMs)
        assertEquals("2024 • 15 songs • 1 hr 15 min", metadata)
    }

    @Test
    fun testBuildMetadataStringUnderOneHourWithoutYear() {
        // 38 minutes = 2,280,000 ms
        val totalMs = 38L * 60L * 1000L
        val metadata = buildMetadataString(year = 0, songCount = 9, totalDurationMs = totalMs)
        assertEquals("9 songs • 38 min", metadata)
    }

    @Test
    fun testDetailMediaHeaderModel() {
        val header = DetailMediaHeader(
            id = "album_random_access",
            title = "Random Access Memories",
            subtitle = "Daft Punk",
            year = 2013,
            isAlbum = true,
            isLossless = true,
            isHiRes = true
        )
        assertEquals("Random Access Memories", header.title)
        assertEquals("Daft Punk", header.subtitle)
        assertEquals(2013, header.year)
        assertTrue(header.isAlbum)
        assertTrue(header.isLossless)
        assertTrue(header.isHiRes)
    }

    @Test
    fun testAmbientColorExtractionFromHsv() {
        val baseColor = Color(0xFF6C5CE7)
        val hsv = ColorUtils.colorToHsv(baseColor)
        val derived = ColorUtils.hsvToColor(floatArrayOf((hsv[0] + 30f) % 360f, 0.65f, 0.80f))

        assertTrue(derived.red in 0f..1f)
        assertTrue(derived.green in 0f..1f)
        assertTrue(derived.blue in 0f..1f)
        assertEquals(1f, derived.alpha)
    }
}
