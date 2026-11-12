package com.nodeplayer.model

/**
 * Presentation state of the full-screen player within SharedPlayerLayout.
 * Represents a strict 3-state hierarchy:
 * - [Closed]: Dock and mini-player visible; full-screen player hidden.
 * - [NowPlaying]: Full-screen Now Playing screen visible edge-to-edge above dock/mini-player.
 * - [Lyrics]: Full-screen synced lyrics view visible edge-to-edge; [controlsVisible] indicates
 *   whether the bottom timeline, transport, and action controls are expanded (State 2) or collapsed (State 3).
 */
sealed interface PlayerPresentationState {
    data object Closed : PlayerPresentationState
    data object NowPlaying : PlayerPresentationState
    data class Lyrics(val controlsVisible: Boolean = true) : PlayerPresentationState
}

/**
 * Cohesive color palette extracted from album artwork for dynamic liquid-glass backgrounds.
 * Uses ARGB Long primitives to preserve pure Kotlin multiplatform purity without framework dependencies.
 */
data class AlbumArtPalette(
    val dominant: Long,
    val vibrant: Long,
    val darkMuted: Long,
    val accent: Long,
    val isFallback: Boolean = false
) {
    companion object {
        // Fallback obsidian palette when no artwork exists or extraction produces zero luminance
        val DEFAULT_OBSIDIAN = AlbumArtPalette(
            dominant = 0xFF141416,
            vibrant = 0xFFD4883B,
            darkMuted = 0xFF0A0A0C,
            accent = 0xFFE5984A,
            isFallback = true
        )
    }
}
