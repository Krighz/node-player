package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch

/**
 * Horizontal carousel shelf for Recently Played tracks:
 * - Continuous squircle album covers (`ContinuousRoundedRectangle(14.dp)`)
 * - High-contrast frosted glass play count pill badge at the top-right of cover (e.g. `▶ 12`)
 * - Track title and artist beneath cover with clean typography
 * - One-tap playback initiating playback with queue context
 * - Smooth desktop horizontal wheel scroll support
 */
@Composable
fun RecentlyPlayedShelf(
    recentTracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "RECENTLY PLAYED"
) {
    if (recentTracks.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val rowState = rememberLazyListState()
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    color = Color.White
                )
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onHorizontalScrollWheel { delta ->
                        coroutineScope.launch {
                            rowState.scrollBy(delta * 120f)
                        }
                    }
            ) {
                items(recentTracks) { track ->
                    RecentlyPlayedTrackCard(
                        track = track,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentlyPlayedTrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(180),
        label = "RecentCardScale"
    )

    val cardShape = remember { ContinuousRoundedRectangle(14.dp) }
    val badgeShape = remember { ContinuousRoundedRectangle(6.dp) }

    // Format play count string: e.g. "▶ 12" or "▶ 1"
    val playCountText = remember(track.playCount) {
        val count = if (track.playCount > 0) track.playCount else 1
        "▶ $count"
    }

    Column(
        modifier = modifier
            .width(132.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(cardShape)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isHovered) 0.40f else 0.16f),
                            Color.White.copy(alpha = if (isHovered) 0.20f else 0.05f)
                        )
                    ),
                    cardShape
                )
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )

            // High-contrast frosted glass play count badge at top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(badgeShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(
                        0.8.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        badgeShape
                    )
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playCountText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.60f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
