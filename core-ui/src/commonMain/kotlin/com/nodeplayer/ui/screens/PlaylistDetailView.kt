package com.nodeplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.model.Track
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.components.SoundWaveVisualizer
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.theme.ColorUtils
import com.nodeplayer.ui.theme.LocalAccentColor

private val PLAYLIST_HERO_SHAPE = ContinuousRoundedRectangle(20.dp)
private val PLAYLIST_HERO_BORDER_BRUSH = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f))
)
private val PLAYLIST_ROW_THUMB_SHAPE = ContinuousRoundedRectangle(10.dp)

/**
 * Universal media header model for Playlist and Album detail screens (PLAY-01).
 */
data class DetailMediaHeader(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val year: Int = 0,
    val isAlbum: Boolean = false,
    val isHiRes: Boolean = false,
    val isLossless: Boolean = false
)

/**
 * Apple Music-Grade Playlist & Album Detail Page (PLAY-01, PLAY-02, PLAY-03).
 *
 * Features:
 * - Centered large squircle hero cover art with title, artist/curator, and metadata stats (PLAY-01).
 * - Standardized action row: circular Shuffle, wide pill Play, circular + Add to Library with checkmark (D-02).
 * - Dynamic subtle ambient glow gradient backdrop derived from dominant cover color (PLAY-02, D-03).
 * - Dynamic collapsing top header: morphs from transparent chevron into sticky glass bar on scroll (D-01).
 * - Track list rows with small thumbnails, duration, active playing visualizer, and 3-dot menus (PLAY-03).
 */
@Composable
fun PlaylistDetailView(
    header: DetailMediaHeader,
    tracks: List<Track>,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    isSavedInLibrary: Boolean = false,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onToggleSaveLibrary: (DetailMediaHeader) -> Unit = {},
    onTrackOptionsClick: (Track) -> Unit = {},
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val bottomPadding = LocalBottomBarPadding.current + 80.dp
    val listState = rememberLazyListState()

    var savedState by remember(isSavedInLibrary) { mutableStateOf(isSavedInLibrary) }

    // Derive ambient hue from header id/artwork
    val ambientColor = remember(header.id, header.artworkUrl, accent) {
        val hsv = ColorUtils.colorToHsv(accent)
        val hueOffset = (kotlin.math.abs(header.id.hashCode()) % 60).toFloat()
        val adjustedHue = (hsv[0] + hueOffset + 360f) % 360f
        ColorUtils.hsvToColor(floatArrayOf(adjustedHue, 0.65f, 0.80f))
    }

    // Scroll collapse state (D-01)
    val isScrolledPastHero by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380
        }
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (isScrolledPastHero) 1.0f else 0.0f,
        animationSpec = spring(stiffness = 400f),
        label = "HeaderCollapseAlpha"
    )

    val placeholderTrack = remember(header.id, header.artworkUrl) {
        Track(
            id = header.id.hashCode().toLong(),
            title = header.title,
            artist = header.subtitle,
            album = header.title,
            durationMs = 0L,
            path = header.artworkUrl ?: ""
        )
    }

    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dynamic Ambient Glow Gradient Backdrop (PLAY-02, D-03)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = 0.40f),
                            ambientColor.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black
                        )
                    )
                )
        )

        // Scrollable Body Content
        val trackKeys = remember(tracks, header.id) {
            uniqueLazyTrackKeys(tracks, "pl_${header.id}")
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = bottomPadding)
        ) {
            // Hero Section (PLAY-01)
            item(key = "hero_section", contentType = "playlist_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Centered 210dp Cover Art Squircle
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .clip(PLAYLIST_HERO_SHAPE)
                            .border(
                                width = 0.8.dp,
                                brush = PLAYLIST_HERO_BORDER_BRUSH,
                                shape = PLAYLIST_HERO_SHAPE
                            )
                    ) {
                        TrackArtwork(
                            track = placeholderTrack,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 20.dp,
                            targetSizePx = 512
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = header.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle (Artist or Curator)
                    Text(
                        text = header.subtitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata Row (D-04)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = buildMetadataString(header.year, tracks.size, totalDurationMs),
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.60f),
                            fontWeight = FontWeight.Normal
                        )

                        if (header.isLossless || header.isHiRes) {
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassBadge(
                                text = if (header.isHiRes) "Hi-Res" else "Lossless",
                                color = accent,
                                textColor = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Row (Apple Music Reference: Circular Shuffle, Wide White Pill Play, Circular +)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Shuffle Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { onShuffleAll(tracks) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Wide Pill Play Button (Apple Music signature: White pill with black Play icon and text)
                        Button(
                            onClick = { onPlayAll(tracks) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .widthIn(min = 160.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Circular + / Check Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    savedState = !savedState
                                    onToggleSaveLibrary(header)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (savedState) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = if (savedState) "Added" else "Add",
                                tint = if (savedState) accent else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Optional Description Paragraph (Apple Music style)
                    if (!header.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = header.description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // Divider between Hero and Tracks
            item(key = "hero_divider", contentType = "playlist_divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
            }

            // Track List Items (PLAY-03)
            itemsIndexed(
                items = tracks,
                key = { index, _ -> trackKeys[index] },
                contentType = { _, _ -> "playlist_track_row" }
            ) { index, track ->
                val isCurrent = currentTrack?.id == track.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, tracks) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Artwork Thumbnail or Number
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(PLAYLIST_ROW_THUMB_SHAPE)
                    ) {
                        TrackArtwork(
                            track = track,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 10.dp,
                            targetSizePx = 144
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrent) accent else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrent && isPlaying) {
                        SoundWaveVisualizer(
                            isPlaying = true,
                            barColor = accent,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(width = 16.dp, height = 14.dp)
                        )
                    }

                    Text(
                        text = track.durationFormatted,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    LiquidIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        onClick = { onTrackOptionsClick(track) },
                        size = 36.dp,
                        lighterStyle = true
                    )
                }

                if (index < tracks.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        // Collapsing Top Glass Header Bar (D-01)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    if (headerAlpha > 0.01f) {
                        Color(0xFF12161F).copy(alpha = 0.88f * headerAlpha)
                    } else {
                        Color.Transparent
                    }
                )
                .border(
                    width = (0.5 * headerAlpha).dp,
                    color = Color.White.copy(alpha = 0.08f * headerAlpha)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Chevron (Apple Music style)
                LiquidIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 40.dp,
                    tint = Color.White,
                    lighterStyle = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Morphed Collapsed Title & Subtitle
                if (headerAlpha > 0.05f) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = header.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = headerAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = header.subtitle,
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.60f * headerAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Compact Mini Play Button
                    LiquidIconButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = "Play all",
                        onClick = { onPlayAll(tracks) },
                        size = 36.dp,
                        tint = accent,
                        lighterStyle = true
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Apple Music top-right Share & More buttons
                IconButton(
                    onClick = { /* Share */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { /* Menu */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formats metadata string: 'Year • N songs • Total duration'.
 */
fun buildMetadataString(year: Int, songCount: Int, totalDurationMs: Long): String {
    val totalMinutes = totalDurationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText = if (hours > 0) "${hours} hr ${minutes} min" else "${totalMinutes} min"

    val yearPart = if (year > 0) "$year • " else ""
    return "$yearPart$songCount songs • $durationText"
}
