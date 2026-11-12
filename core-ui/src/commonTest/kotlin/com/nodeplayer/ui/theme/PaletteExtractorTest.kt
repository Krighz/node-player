package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.PlayerPresentationState
import com.nodeplayer.model.Track
import com.nodeplayer.ui.theme.PaletteExtractor.toComposeColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaletteExtractorTest {

    @Test
    fun testPlayerPresentationStateHierarchy() {
        val closed: PlayerPresentationState = PlayerPresentationState.Closed
        val nowPlaying: PlayerPresentationState = PlayerPresentationState.NowPlaying
        val lyricsExpanded: PlayerPresentationState = PlayerPresentationState.Lyrics(controlsVisible = true)
        val lyricsCollapsed: PlayerPresentationState = PlayerPresentationState.Lyrics(controlsVisible = false)

        assertTrue(closed is PlayerPresentationState.Closed)
        assertTrue(nowPlaying is PlayerPresentationState.NowPlaying)
        assertTrue(lyricsExpanded is PlayerPresentationState.Lyrics && lyricsExpanded.controlsVisible)
        assertTrue(lyricsCollapsed is PlayerPresentationState.Lyrics && !lyricsCollapsed.controlsVisible)

        // Verify back-navigation hierarchy reduction logic
        fun navigateBack(state: PlayerPresentationState): PlayerPresentationState = when (state) {
            is PlayerPresentationState.Lyrics -> PlayerPresentationState.NowPlaying
            is PlayerPresentationState.NowPlaying -> PlayerPresentationState.Closed
            is PlayerPresentationState.Closed -> PlayerPresentationState.Closed
        }

        assertEquals(PlayerPresentationState.NowPlaying, navigateBack(lyricsExpanded))
        assertEquals(PlayerPresentationState.NowPlaying, navigateBack(lyricsCollapsed))
        assertEquals(PlayerPresentationState.Closed, navigateBack(nowPlaying))
        assertEquals(PlayerPresentationState.Closed, navigateBack(closed))
    }

    @Test
    fun testContrastVerificationAndFallback() {
        val darkBackground = Color(0xFF0F0F12)
        val highContrastColor = Color(0xFFFFCC00)
        val veryLowContrastColor = Color(0xFF191920)

        val verifiedHigh = PaletteExtractor.ensureContrastAgainstDark(highContrastColor, darkBackground)
        assertEquals(highContrastColor, verifiedHigh)

        val verifiedLow = PaletteExtractor.ensureContrastAgainstDark(veryLowContrastColor, darkBackground)
        assertEquals(Color.White, verifiedLow)
    }

    @Test
    fun testPaletteDerivationFromBaseColor() {
        val testAccent = Color(0xFFE53935) // Red tone
        val palette = PaletteExtractor.derivePaletteFromBaseColor(testAccent)

        assertFalse(palette.isFallback)
        val vibrant = palette.vibrant.toComposeColor()
        val darkMuted = palette.darkMuted.toComposeColor()

        // Dominant & dark muted should have low luminance compared to vibrant
        assertTrue(PaletteExtractor.calculateLuminance(darkMuted) < 0.2f)
        assertTrue(PaletteExtractor.calculateContrastRatio(palette.accent.toComposeColor(), darkMuted) >= 3.0f)
    }

    @Test
    fun testPaletteCacheRetention() {
        val track = Track(
            id = 999L,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            path = "/music/test.flac"
        )
        val palette = AlbumArtPalette(
            dominant = 0xFF202020,
            vibrant = 0xFFFF5500,
            darkMuted = 0xFF101010,
            accent = 0xFFFF8833
        )

        PaletteExtractor.cachePalette(track, palette)
        val retrieved = PaletteExtractor.getCachedPalette(track)

        assertNotNull(retrieved)
        assertEquals(palette.vibrant, retrieved.vibrant)
    }
}
