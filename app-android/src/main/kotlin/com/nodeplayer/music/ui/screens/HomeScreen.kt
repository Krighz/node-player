package com.nodeplayer.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeScreen(
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayPauseClick: () -> Unit,
    onOpenSmartPlaylists: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    // 1. Recently Played (tracks sorted by lastPlayedTimestamp descending)
    val recentlyPlayed = remember(allTracks) {
        allTracks.filter { it.lastPlayedTimestamp > 0L }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(15)
    }

    // 2. Rediscover: Tracks played in the past but unplayed for 30+ days (or favorite tracks unplayed recently)
    val thirtyDaysAgo = remember { System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L) }
    val rediscoverTracks = remember(allTracks) {
        allTracks.filter { (it.playCount > 0 && it.lastPlayedTimestamp < thirtyDaysAgo) || (it.isFavorite && it.lastPlayedTimestamp < thirtyDaysAgo) }
            .take(15)
            .ifEmpty { allTracks.shuffled().take(10) }
    }

    // 3. Quick-launch Smart Playlists
    val recentlyAdded = remember(allTracks) {
        allTracks.sortedByDescending { it.dateAdded }.take(25)
    }
    val losslessTracks = remember(allTracks) {
        allTracks.filter { it.isLossless }
    }
    val longTracks = remember(allTracks) {
        allTracks.filter { it.durationMs >= 300_000L } // >= 5 minutes
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
    ) {
        // App Title & Quick Hub Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "For You",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Curated listening flow & shortcuts",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenStats,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Stats", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onOpenDuplicateFinder,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate Finder", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Hero Card: Jump Back In
        item {
            val heroTrack = currentTrack ?: recentlyPlayed.firstOrNull() ?: allTracks.firstOrNull()
            if (heroTrack != null) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        hazeState = hazeState
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hero Artwork
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                AsyncImage(
                                    model = heroTrack.path,
                                    contentDescription = heroTrack.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlaying && currentTrack?.id == heroTrack.id) "Now Playing" else "Jump Back In",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = heroTrack.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${heroTrack.artist} • ${heroTrack.formatBadgeText}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.65f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play / Pause disc button
                            IconButton(
                                onClick = {
                                    if (currentTrack?.id == heroTrack.id) {
                                        onPlayPauseClick()
                                    } else {
                                        onTrackClick(heroTrack, allTracks)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(accent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && currentTrack?.id == heroTrack.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section: Recently Played Carousel
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Played", count = recentlyPlayed.size)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, recentlyPlayed) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section: Smart Playlists Shelf
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Mixes",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "All Smart Playlists",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.clickable { onOpenSmartPlaylists() }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SmartPlaylistChip(
                        title = "Recently Added",
                        subtitle = "${recentlyAdded.size} tracks",
                        icon = Icons.Rounded.Schedule,
                        accentColor = Color(0xFF38BDF8),
                        onClick = { if (recentlyAdded.isNotEmpty()) onTrackClick(recentlyAdded[0], recentlyAdded) }
                    )
                }
                item {
                    SmartPlaylistChip(
                        title = "Lossless Only",
                        subtitle = "${losslessTracks.size} tracks",
                        icon = Icons.Rounded.GraphicEq,
                        accentColor = Color(0xFF818CF8),
                        onClick = { if (losslessTracks.isNotEmpty()) onTrackClick(losslessTracks[0], losslessTracks) }
                    )
                }
                item {
                    SmartPlaylistChip(
                        title = "Long Tracks",
                        subtitle = "${longTracks.size} tracks (>5m)",
                        icon = Icons.Rounded.Timer,
                        accentColor = Color(0xFF10B981),
                        onClick = { if (longTracks.isNotEmpty()) onTrackClick(longTracks[0], longTracks) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Rediscover Shelf
        if (rediscoverTracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Rediscover", count = rediscoverTracks.size)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(rediscoverTracks, key = { "rediscover_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, rediscoverTracks) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "$count tracks",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun TrackCarouselCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAccentColor.current
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            AsyncImage(
                model = track.path,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Playing",
                        tint = accent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isPlaying) accent else Color.White,
                fontWeight = FontWeight.SemiBold
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

@Composable
private fun SmartPlaylistChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(accentColor.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.55f)
                )
            )
        }
    }
}
