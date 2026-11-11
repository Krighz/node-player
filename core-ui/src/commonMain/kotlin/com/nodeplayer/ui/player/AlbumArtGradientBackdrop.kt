package com.nodeplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.Track

/**
 * Album art gradient backdrop conforming to D-09 & AESTHETICS-02.
 *
 * Functions as a non-destructive forwarding facade delegating directly to [ArtworkMeshBackdrop]
 * with zero per-frame CPU radial gradient loops. Preserves parameter overloads so all callers
 * compile cleanly without regressions.
 */
@Composable
fun AlbumArtGradientBackdrop(
    track: Track?,
    modifier: Modifier = Modifier,
    seam: () -> Dp = { 0.dp }
) {
    val artworkUrl = remember(track?.id, track?.path) {
        track?.path?.takeIf { it.isNotBlank() } ?: track?.id?.toString()
    }
    ArtworkMeshBackdrop(
        artworkUrl = artworkUrl,
        seam = seam,
        modifier = modifier
    )
}
