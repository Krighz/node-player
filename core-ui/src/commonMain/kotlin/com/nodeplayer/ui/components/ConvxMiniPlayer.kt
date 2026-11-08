package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassComponentType
import com.nodeplayer.model.Track
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.pressScale
import com.nodeplayer.ui.debug.GlassDebug

/**
 * Convx-style Floating Liquid Glass Mini Player.
 * Features:
 * - Continuous squircle container floating above the dock.
 * - Squircle artwork thumbnail on the left.
 * - Title and artist metadata with single-line ellipsis.
 * - Audio-reactive animated equalizer bars reflecting live RMS audio level.
 * - Play/Pause and Next transport controls.
 * - Strict layer separation: artwork, text and controls drawn un-refracted on top of glass.
 */
@Composable
fun ConvxMiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    shaderEnabled: Boolean = true,
    chromaticFringe: Float = 0.25f,
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current
    val controlsConfig = LocalGlassControlsConfig.current
    val resolvedMiniPlayer = controlsConfig.resolve(GlassComponentType.MINI_PLAYER)

    val lensActive = themeConfig.dockGlassLensEnabled && shaderEnabled && !isPerformanceMode && themeConfig.glassStrength != "OFF" && !resolvedMiniPlayer.isPlainSquircle && controlsConfig.masterEnabled
    val rimActive = themeConfig.dockGlassRimEnabled && themeConfig.glassStrength != "OFF"

    GlassDebug.logThrottled(
        "ConvxMiniPlayer",
        "lensActive=$lensActive, blurDp=${resolvedMiniPlayer.blurDp}, lensH=${resolvedMiniPlayer.lensHeightDp}, lensAmt=${resolvedMiniPlayer.lensAmountDp}, backdrop=${backdrop::class.simpleName}"
    )

    val containerShape = remember(resolvedMiniPlayer.cornerShapeDp) { ContinuousRoundedRectangle(resolvedMiniPlayer.cornerShapeDp.dp) }
    val artworkShape = remember { ContinuousRoundedRectangle(10.dp) }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val gestureModifier = if (gesturesEnabled) {
        Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                dragAccumulator += delta
            },
            onDragStopped = {
                if (dragAccumulator > 120f) {
                    onSkipPreviousClick()
                } else if (dragAccumulator < -120f) {
                    onSkipNextClick()
                }
                dragAccumulator = 0f
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .height(56.dp)
            .then(gestureModifier)
            .then(
                if (lensActive) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { containerShape },
                        effects = {
                            blur(resolvedMiniPlayer.blurDp.dp.toPx())
                            lens(
                                resolvedMiniPlayer.lensHeightDp.dp.toPx(),
                                resolvedMiniPlayer.lensAmountDp.dp.toPx(),
                                chromaticAberration = resolvedMiniPlayer.chromaticAberration > 0.05f
                            )
                            vibrancy()
                        },
                        highlight = {
                            if (rimActive) {
                                Highlight.Ambient.copy(
                                    width = 1.dp,
                                    alpha = (0.75f * resolvedMiniPlayer.highlightStrength).coerceIn(0.1f, 1f)
                                )
                            } else Highlight.Ambient.copy(alpha = 0f)
                        },
                        shadow = {
                            Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.22f * resolvedMiniPlayer.shadowStrength))
                        },
                        onDrawSurface = {
                            val isDark = themeConfig.themeMode != "LIGHT"
                            val surfaceAlpha = (resolvedMiniPlayer.tintOpacity * (if (isDark) 0.55f else 0.40f)).coerceIn(0.15f, 0.75f)
                            val surfaceColor = if (isDark) Color(0xFF141923) else Color.White
                            drawRect(surfaceColor.copy(alpha = surfaceAlpha))
                        }
                    )
                } else {
                    val isDark = themeConfig.themeMode != "LIGHT"
                    val fallbackAlpha = (resolvedMiniPlayer.tintOpacity * (if (isDark) 0.75f else 0.60f)).coerceIn(0.25f, 0.85f)
                    val fallbackColor = if (isDark) Color(0xFF141923) else Color.White
                    Modifier
                        .clip(containerShape)
                        .background(fallbackColor.copy(alpha = fallbackAlpha))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (rimActive) 0.35f else 0.12f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            ),
                            shape = containerShape
                        )
                }
            )
            .clickable(onClick = onClick)
    ) {
        // Subtle progress track along the bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .height(2.dp)
                .background(accent.copy(alpha = 0.85f))
        )

        // Foreground content (un-refracted, crisp)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(artworkShape)
            ) {
                TrackArtwork(
                    track = currentTrack,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 10.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata: Title and Artist
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTrack.title.ifEmpty { "Unknown Track" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = currentTrack.artist.ifEmpty { "Unknown Artist" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    AudioFormatBadge(
                        track = currentTrack,
                        isCompact = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Audio-reactive Equalizer Bars
            SoundWaveVisualizer(
                isPlaying = isPlaying,
                rmsLevel = rmsLevel,
                barColor = accent,
                maxHeight = 16.dp,
                isPerformanceMode = isPerformanceMode,
                modifier = Modifier.padding(end = 6.dp)
            )

            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .pressScale(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Next Track Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .pressScale(onClick = onSkipNextClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
