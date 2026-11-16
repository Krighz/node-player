package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * "Your Mix" horizontal shelf for the Home tab built from liquid glass controls per F01.
 *
 * - Header row contains the title ("Your Mix"), subtle "Based on charts" fallback badge,
 *   and a "Play" button playing all 25 tracks in order.
 * - Horizontal list with 140dp squircle cards displaying artwork, title, and artist.
 * - Tapping a track plays it and queues the remaining tracks.
 * - Skeleton loading state while computing the authentic history blend.
 */
@Composable
fun YourMixShelf(
    tracks: List<Track>,
    isLoading: Boolean,
    isFallback: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Your Mix"
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Shelf Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (isFallback) {
                    GlassBadge(
                        text = "Based on charts",
                        color = accent
                    )
                }
            }

            if (tracks.isNotEmpty() && !isLoading) {
                LiquidButton(
                    onClick = { onPlayAll(tracks) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play all",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Play",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Shelf Content: Skeleton or Track Cards
        if (isLoading) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) {
                    GlassCard(
                        modifier = Modifier
                            .width(140.dp)
                            .height(190.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(124.dp)
                                    .clip(ContinuousRoundedRectangle(12.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(12.dp)
                                    .clip(ContinuousRoundedRectangle(4.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(10.dp)
                                    .clip(ContinuousRoundedRectangle(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            )
                        }
                    }
                }
            }
        } else if (tracks.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    GlassCard(
                        modifier = Modifier
                            .width(140.dp)
                            .height(195.dp),
                        onClick = {
                            val queueRemainder = tracks.drop(index)
                            onTrackClick(track, queueRemainder)
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            TrackArtwork(
                                track = track,
                                modifier = Modifier
                                    .size(124.dp)
                                    .clip(ContinuousRoundedRectangle(12.dp)),
                                cornerRadius = 12.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
