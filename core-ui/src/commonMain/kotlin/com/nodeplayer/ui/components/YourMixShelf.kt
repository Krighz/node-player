package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.CarbonZincPalette

private val YOUR_MIX_CARD_SHAPE = ContinuousRoundedRectangle(20.dp)
private val YOUR_MIX_ART_SHAPE = ContinuousRoundedRectangle(14.dp)
private val SKELETON_LINE_SHAPE = ContinuousRoundedRectangle(4.dp)

/**
 * "Your Mix" horizontal shelf styled with CarbonZincPalette.
 *
 * - Header row contains the title ("Your Mix") and a pill "Play" button.
 * - Horizontal list with cards on SurfaceContainer background with subtle border.
 * - Album artwork inside each card has smooth rounded squircle corners.
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Shelf Header: "Your Mix" and [ ▶ Play ]
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    color = CarbonZincPalette.TextPrimary
                )

                if (isFallback) {
                    GlassBadge(
                        text = "Based on charts",
                        color = CarbonZincPalette.AccentTeal
                    )
                }
            }

            if (tracks.isNotEmpty() && !isLoading) {
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(ContinuousCapsule())
                        .background(CarbonZincPalette.SurfaceElevated)
                        .border(1.dp, CarbonZincPalette.BorderActive, ContinuousCapsule())
                        .clickable { onPlayAll(tracks) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play all",
                        modifier = Modifier.size(15.dp),
                        tint = CarbonZincPalette.TextPrimary
                    )
                    Text(
                        text = "Play",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                        color = CarbonZincPalette.TextPrimary
                    )
                }
            }
        }

        // Shelf Content: Skeleton or Track Cards
        if (isLoading) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(5, key = { "your_mix_skel_$it" }) {
                    Box(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(YOUR_MIX_CARD_SHAPE)
                            .background(CarbonZincPalette.SurfaceContainer)
                            .border(1.dp, CarbonZincPalette.BorderSubtle, YOUR_MIX_CARD_SHAPE)
                            .padding(10.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .clip(YOUR_MIX_ART_SHAPE)
                                    .background(CarbonZincPalette.SurfaceElevated)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(14.dp)
                                    .clip(SKELETON_LINE_SHAPE)
                                    .background(CarbonZincPalette.SurfaceElevated)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(65.dp)
                                    .height(12.dp)
                                    .clip(SKELETON_LINE_SHAPE)
                                    .background(CarbonZincPalette.SurfaceElevated.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        } else if (tracks.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> "${track.extractVideoId() ?: track.id}_$index" },
                    contentType = { _, _ -> "your_mix_card" }
                ) { index, track ->
                    Box(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(YOUR_MIX_CARD_SHAPE)
                            .background(CarbonZincPalette.SurfaceContainer)
                            .border(1.dp, CarbonZincPalette.BorderSubtle, YOUR_MIX_CARD_SHAPE)
                            .clickable {
                                val queueRemainder = tracks.drop(index)
                                onTrackClick(track, queueRemainder)
                            }
                            .padding(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TrackArtwork(
                                track = track,
                                modifier = Modifier
                                    .size(136.dp)
                                    .clip(YOUR_MIX_ART_SHAPE),
                                contentScale = ContentScale.Crop,
                                cornerRadius = 14.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = track.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.1).sp,
                                color = CarbonZincPalette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = CarbonZincPalette.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
