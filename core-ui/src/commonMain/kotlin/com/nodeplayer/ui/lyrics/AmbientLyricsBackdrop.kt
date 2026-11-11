package com.nodeplayer.ui.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.ui.player.ArtworkMeshBackdrop

/**
 * Ambient artwork mesh backdrop for lyrics views conforming to D-09 & AESTHETICS-02.
 *
 * Functions as a non-destructive forwarding facade delegating directly to [ArtworkMeshBackdrop]
 * with zero per-frame CPU radial gradient loops. Preserves multi-overload signatures so all
 * callers compile cleanly without regressions.
 */
@Composable
fun AmbientLyricsBackdrop(
    palette: AlbumArtPalette,
    modifier: Modifier = Modifier
) {
    ArtworkMeshBackdrop(
        artworkUrl = null,
        fallback = Color(palette.darkMuted),
        modifier = modifier
    )
}

@Composable
fun AmbientLyricsBackdrop(
    artworkUrl: String?,
    accentColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp
) {
    ArtworkMeshBackdrop(
        artworkUrl = artworkUrl,
        fallback = if (accentColor != Color.Unspecified) accentColor else Color(0xFF121212),
        modifier = modifier
    )
}

@Composable
fun AmbientLyricsBackdrop(
    palette: AlbumArtPalette?,
    artworkUrl: String?,
    modifier: Modifier = Modifier
) {
    ArtworkMeshBackdrop(
        artworkUrl = artworkUrl,
        fallback = palette?.let { Color(it.darkMuted) } ?: Color(0xFF121212),
        modifier = modifier
    )
}
