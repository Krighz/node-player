package com.nodeplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.onHorizontalScrollWheel
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class LocalGenreMix(
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val tracks: List<Track>
)

@Composable
fun HomeView(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onQuickShuffle: () -> Unit,
    onRescanClick: () -> Unit = {},
    onSelectMusicFolder: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()

    // 1. FOR YOU: Intelligently group local tracks into genre and acoustic style mixes
    val mixes = remember(tracks) {
        val list = mutableListOf<LocalGenreMix>()

        val popPool = tracks.filter { it.genre?.contains("Pop", ignoreCase = true) == true || it.title.contains("Pop", ignoreCase = true) }
        list.add(
            LocalGenreMix(
                title = "Pop Mixes",
                subtitle = "Upbeat & Vocal Hits",
                gradientColors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF1E1B4B)),
                tracks = if (popPool.isNotEmpty()) popPool else tracks.take(12)
            )
        )

        val cinematicPool = tracks.filter { 
            it.genre?.contains("Soundtrack", ignoreCase = true) == true || 
            it.genre?.contains("Classical", ignoreCase = true) == true || 
            it.genre?.contains("Ambient", ignoreCase = true) == true 
        }
        list.add(
            LocalGenreMix(
                title = "Cinematic Mixes",
                subtitle = "Orchestral & Deep Atmos",
                gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0F172A), Color(0xFF1E293B)),
                tracks = if (cinematicPool.isNotEmpty()) cinematicPool else tracks.drop(4).take(12)
            )
        )

        val electronicPool = tracks.filter { 
            it.genre?.contains("Electronic", ignoreCase = true) == true || 
            it.genre?.contains("Dance", ignoreCase = true) == true || 
            it.genre?.contains("Synth", ignoreCase = true) == true 
        }
        list.add(
            LocalGenreMix(
                title = "Electronic Mixes",
                subtitle = "Synthesizers & Heavy Bass",
                gradientColors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFF030712)),
                tracks = if (electronicPool.isNotEmpty()) electronicPool else tracks.drop(8).take(12)
            )
        )

        val studioPool = tracks.filter { it.isLossless || it.bitDepth >= 24 || it.sampleRate >= 88200 }
        if (studioPool.isNotEmpty()) {
            list.add(
                LocalGenreMix(
                    title = "Master Audio Mixes",
                    subtitle = "24-bit Hi-Res & Studio FLAC",
                    gradientColors = listOf(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF1C1917)),
                    tracks = studioPool
                )
            )
        }

        val acousticPool = tracks.filter { 
            it.genre?.contains("Rock", ignoreCase = true) == true || 
            it.genre?.contains("Acoustic", ignoreCase = true) == true || 
            it.genre?.contains("Jazz", ignoreCase = true) == true 
        }
        list.add(
            LocalGenreMix(
                title = "Acoustic & Rock",
                subtitle = "Organic Strings & Drums",
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857), Color(0xFF064E3B)),
                tracks = if (acousticPool.isNotEmpty()) acousticPool else tracks.shuffled().take(12)
            )
        )

        list
    }

    // 2. REDISCOVER: Real play history analysis (tracks not played in a while)
    val rediscoverTracks = remember(tracks) {
        val played = tracks.filter { it.lastPlayedTimestamp > 0L }
        val sortedByOldest = played.sortedBy { it.lastPlayedTimestamp }
        if (sortedByOldest.size >= 6) {
            sortedByOldest
        } else {
            (sortedByOldest + tracks.filter { it.playCount == 0 }.shuffled()).distinctBy { it.id }
        }
    }

    // 3. RECENTLY PLAYED: Ordered by latest timestamp
    val recentlyPlayedTracks = remember(tracks) {
        val list = tracks.filter { it.lastPlayedTimestamp > 0L }.sortedByDescending { it.lastPlayedTimestamp }
        if (list.isNotEmpty()) list else tracks
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ---------------------------------------------------------------------
        // Section 1: FOR YOU (Mix Cards)
        // ---------------------------------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "FOR YOU",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = Color.White
                    )
                )

                val rowState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onHorizontalScrollWheel { delta ->
                                coroutineScope.launch {
                                    rowState.scrollBy(delta * 120f)
                                }
                            }
                    ) {
                        items(mixes) { mix ->
                            MixCard(
                                mix = mix,
                                onClick = {
                                    val first = mix.tracks.firstOrNull()
                                    if (first != null) {
                                        onPlayTrack(first, mix.tracks)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // Section 2: REDISCOVER (Haven't heard in a while)
        // ---------------------------------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "REDISCOVER",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Haven't heard in a while)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.50f),
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                val rowState = rememberLazyListState()

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
                        items(rediscoverTracks) { track ->
                            RediscoverTrackCard(
                                track = track,
                                onClick = { onPlayTrack(track, rediscoverTracks) }
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // Section 3: RECENTLY PLAYED
        // ---------------------------------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "RECENTLY PLAYED",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = Color.White
                    )
                )

                val rowState = rememberLazyListState()

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
                        items(recentlyPlayedTracks) { track ->
                            StandardTrackCard(
                                track = track,
                                onClick = { onPlayTrack(track, recentlyPlayedTracks) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-Components: MixCard, RediscoverTrackCard, StandardTrackCard
// -----------------------------------------------------------------------------

@Composable
private fun MixCard(
    mix: LocalGenreMix,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1.0f,
        animationSpec = tween(180)
    )

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(140.dp)
            .scale(scale)
            .shadow(
                elevation = if (isHovered) 14.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = mix.gradientColors.first().copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(mix.gradientColors))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (isHovered) 0.35f else 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // Background Artwork Collage on right side
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-8).dp)
        ) {
            mix.tracks.take(3).forEachIndexed { i, track ->
                TrackArtwork(
                    track = track,
                    modifier = Modifier
                        .size(56.dp)
                        .offset(x = (-i * 18).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                )
            }
        }

        // Title and Subtitle on bottom left
        Column {
            Text(
                text = mix.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 17.sp
                )
            )
            Text(
                text = mix.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun RediscoverTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(180)
    )

    // Calculate real badge text based on lastPlayedTimestamp
    val badgeText = remember(track.lastPlayedTimestamp, track.playCount, track.isFavorite) {
        if (track.lastPlayedTimestamp <= 0L) {
            if (track.isFavorite) "Old favorite" else "Fresh listen"
        } else {
            val diffMs = System.currentTimeMillis() - track.lastPlayedTimestamp
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            when {
                days > 150 -> "Last played 6 months ago"
                days > 80 -> "Last played 3 months ago"
                days > 25 -> "Last played a month ago"
                track.playCount > 8 -> "Old favorite"
                else -> "Rediscover"
            }
        }
    }

    Column(
        modifier = Modifier
            .width(132.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = if (isHovered) 0.35f else 0.10f), RoundedCornerShape(14.dp))
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )

            // Real Badge overlay at bottom of artwork (matching reference image)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.55f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StandardTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(180)
    )

    Column(
        modifier = Modifier
            .width(132.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = if (isHovered) 0.35f else 0.10f), RoundedCornerShape(14.dp))
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.55f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
