package com.nodeplayer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.ArtworkCache
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.theme.PaletteExtractor
import com.nodeplayer.ui.theme.PaletteExtractor.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the huge album art at the top ~60% of the screen fading seamlessly
 * into a rich vertical gradient derived from the artwork's own palette.
 *
 * Implements strict single-backdrop layering:
 * Art + gradient form the one continuous background. No visible seams or hard edges.
 * Smooth ~600ms crossfade between track transitions.
 */
@Composable
fun AlbumArtGradientBackdrop(
    track: Track?,
    modifier: Modifier = Modifier
) {
    var loadedArtwork by remember(track?.id, track?.path) {
        mutableStateOf<ImageBitmap?>(
            if (track != null) {
                val key = track.path.ifBlank { track.id.toString() }
                ArtworkCache.get(key)
            } else {
                null
            }
        )
    }

    var currentPalette by remember(track?.id, track?.path) {
        mutableStateOf(PaletteExtractor.getCachedPalette(track) ?: AlbumArtPalette.DEFAULT_OBSIDIAN)
    }

    LaunchedEffect(track?.id, track?.path) {
        if (track == null) {
            currentPalette = AlbumArtPalette.DEFAULT_OBSIDIAN
            loadedArtwork = null
            return@LaunchedEffect
        }
        val key = track.path.ifBlank { track.id.toString() }
        val cached = ArtworkCache.get(key)
        if (cached != null) {
            loadedArtwork = cached
        } else {
            withContext(Dispatchers.IO) {
                val art = PlatformArtworkProvider.loadArtwork(track)
                if (art != null) {
                    ArtworkCache.put(key, art)
                    withContext(Dispatchers.Main) {
                        loadedArtwork = art
                    }
                }
            }
        }

        val cachedPal = PaletteExtractor.getCachedPalette(track)
        if (cachedPal != null) {
            currentPalette = cachedPal
        } else {
            withContext(Dispatchers.IO) {
                val vibrant = PlatformArtworkProvider.extractVibrantColor(track)
                val derived = if (vibrant != null) {
                    PaletteExtractor.derivePaletteFromBaseColor(vibrant)
                } else {
                    AlbumArtPalette.DEFAULT_OBSIDIAN
                }
                PaletteExtractor.cachePalette(track, derived)
                withContext(Dispatchers.Main) {
                    currentPalette = derived
                }
            }
        }
    }

    val animatedDominant by animateColorAsState(
        targetValue = currentPalette.dominant.toComposeColor(),
        animationSpec = tween(durationMillis = 600),
        label = "dominantColor"
    )

    val animatedDarkMuted by animateColorAsState(
        targetValue = currentPalette.darkMuted.toComposeColor(),
        animationSpec = tween(durationMillis = 600),
        label = "darkMutedColor"
    )

    val currentArt = loadedArtwork

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedDarkMuted)
    ) {
        // Enlarge album art to cover 50-60% of the screen height, bleeding seamlessly from top/sides
        if (currentArt != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
                    .align(Alignment.TopCenter)
            ) {
                Image(
                    bitmap = currentArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            // Bleed gradient mask: full opacity at top, smooth fade towards bottom & sides
                            val verticalFadeBrush = Brush.verticalGradient(
                                0.0f to Color.White,
                                0.40f to Color.White,
                                0.70f to Color.White.copy(alpha = 0.50f),
                                1.0f to Color.Transparent
                            )
                            val horizontalFadeBrush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.05f to Color.White,
                                0.95f to Color.White,
                                1.0f to Color.Transparent
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = verticalFadeBrush, blendMode = BlendMode.DstIn)
                                drawRect(brush = horizontalFadeBrush, blendMode = BlendMode.DstIn)
                            }
                        }
                )
            }
        }

        // Seamless overlay vertical & radial gradient:
        // Blends behind/over the artwork with layered opacities down to solid darkMuted
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val verticalBleedBrush = Brush.verticalGradient(
                        0.0f to animatedDominant.copy(alpha = 0.05f),
                        0.25f to animatedDominant.copy(alpha = 0.15f),
                        0.45f to animatedDominant.copy(alpha = 0.50f),
                        0.60f to animatedDominant.copy(alpha = 0.85f),
                        0.80f to animatedDarkMuted.copy(alpha = 0.95f),
                        1.0f to animatedDarkMuted
                    )
                    onDrawBehind {
                        drawRect(brush = verticalBleedBrush)
                    }
                }
        )
    }
}
