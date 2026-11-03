package com.nodeplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.NodePlayerLogo
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.pressScale
import java.util.Calendar

/**
 * Shared "For You" Home screen matching Android reference.
 * Provides curated flow, intelligent carousels (Jump Back In, Recently Played,
 * Heavy Rotation, Audiophile Studio Vault, Fresh Discoveries, and Smart Shortcuts).
 */
@Composable
fun HomeView(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onQuickShuffle: () -> Unit,
    onRescanClick: () -> Unit,
    onSelectMusicFolder: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    // Greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Late night listening"
        }
    }

    // Curated Carousels matching Android HomeScreen logic
    val recentlyPlayed = remember(tracks) {
        val filtered = tracks.filter { it.lastPlayedTimestamp > 0L }.sortedByDescending { it.lastPlayedTimestamp }
        if (filtered.isNotEmpty()) filtered.take(16) else tracks.take(16)
    }

    val heavyRotation = remember(tracks) {
        val filtered = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
        if (filtered.isNotEmpty()) filtered.take(16) else tracks.shuffled().take(16)
    }

    val audiophileVault = remember(tracks) {
        tracks.filter {
            it.isLossless || it.bitDepth >= 24 || it.sampleRate >= 88200 || it.formatDescription.contains("FLAC", ignoreCase = true)
        }.take(16)
    }

    val freshDiscoveries = remember(tracks) {
        val unplayed = tracks.filter { it.playCount == 0 }
        if (unplayed.isNotEmpty()) unplayed.take(16) else emptyList()
    }

    val favorites = remember(tracks) {
        tracks.filter { it.isFavorite }.take(16)
    }

    val heroTrack = currentTrack ?: recentlyPlayed.firstOrNull() ?: tracks.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. Top Header with Greeting & Quick Action Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "For You",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$greeting • Curated listening flow & shortcuts",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        )
                    }

                    // Audiophile Node Player Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NodePlayerLogo(size = 32.dp, accentColor = accent)
                        GlassBadge(
                            text = "HI-FI PURE",
                            color = accent
                        )
                    }
                }

                // Quick Action Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeActionChip(
                        icon = Icons.Rounded.Shuffle,
                        label = "Quick Shuffle",
                        accent = accent,
                        onClick = onQuickShuffle
                    )
                    HomeActionChip(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Equalizer",
                        accent = accent,
                        onClick = onNavigateToEqualizer
                    )
                    HomeActionChip(
                        icon = Icons.Rounded.FolderOpen,
                        label = "Change Music Folder",
                        accent = accent,
                        onClick = onSelectMusicFolder
                    )
                    HomeActionChip(
                        icon = Icons.Rounded.Refresh,
                        label = "Rescan Library",
                        accent = accent,
                        onClick = onRescanClick
                    )
                }
            }
        }

        // Empty Library Fallback Card
        if (tracks.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSelectMusicFolder
                ) {
                    Row(
                        modifier = Modifier.padding(28.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your Library is Waiting",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select your music folder to import FLAC, WAV, MP3 and lossless audio tracks.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            )
                        }
                        Button(
                            onClick = onSelectMusicFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Select Folder", color = Color(0xFF131728), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Hero Card: Jump Back In / Now Playing
        if (heroTrack != null) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hero Disc Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(accent.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = accent.copy(alpha = 0.85f),
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isPlaying && currentTrack?.id == heroTrack.id) "NOW PLAYING" else "JUMP BACK IN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accent,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp
                                    )
                                )
                                if (heroTrack.isHiRes || heroTrack.bitDepth > 16 || heroTrack.sampleRate >= 88200) {
                                    GlassBadge(text = if (heroTrack.bitDepth > 16) "${heroTrack.bitDepth}b/${heroTrack.sampleRate / 1000}k" else "HI-RES", color = Color(0xFFF59E0B))
                                } else if (heroTrack.isLossless) {
                                    GlassBadge(text = "LOSSLESS", color = accent)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = heroTrack.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${heroTrack.artist} • ${heroTrack.album} • ${heroTrack.formatDescription}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Large Play/Pause Disc Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable {
                                    if (currentTrack?.id == heroTrack.id) {
                                        onPlayPause()
                                    } else {
                                        onPlayTrack(heroTrack, tracks)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying && currentTrack?.id == heroTrack.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF131728),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Carousel 1: Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                HomeCarouselSection(
                    title = "Recently Played",
                    subtitle = "Pick up right where your session left off",
                    tracks = recentlyPlayed,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    accent = accent,
                    onTrackClick = { track -> onPlayTrack(track, recentlyPlayed) }
                )
            }
        }

        // 4. Carousel 2: Heavy Rotation ("On Repeat")
        if (heavyRotation.isNotEmpty()) {
            item {
                HomeCarouselSection(
                    title = "Heavy Rotation",
                    subtitle = "Your top-tier anthems and most-played tracks",
                    tracks = heavyRotation,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    accent = accent,
                    onTrackClick = { track -> onPlayTrack(track, heavyRotation) }
                )
            }
        }

        // 5. Carousel 3: Audiophile Master Studio Vault (FLAC & Lossless)
        if (audiophileVault.isNotEmpty()) {
            item {
                HomeCarouselSection(
                    title = "Audiophile Studio Vault",
                    subtitle = "Bit-perfect FLAC, Lossless & Hi-Res studio masters",
                    tracks = audiophileVault,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    accent = accent,
                    badgeLabel = "STUDIO",
                    onTrackClick = { track -> onPlayTrack(track, audiophileVault) }
                )
            }
        }

        // 6. Carousel 4: Fresh Discoveries
        if (freshDiscoveries.isNotEmpty()) {
            item {
                HomeCarouselSection(
                    title = "Fresh Discoveries",
                    subtitle = "Untapped tracks in your music collection waiting to be played",
                    tracks = freshDiscoveries,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    accent = accent,
                    onTrackClick = { track -> onPlayTrack(track, freshDiscoveries) }
                )
            }
        }

        // 7. Carousel 5: Favorite Anthems
        if (favorites.isNotEmpty()) {
            item {
                HomeCarouselSection(
                    title = "Favorite Anthems",
                    subtitle = "Your handpicked favorites",
                    tracks = favorites,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    accent = accent,
                    badgeLabel = "FAVORITE",
                    onTrackClick = { track -> onPlayTrack(track, favorites) }
                )
            }
        }
    }
}

@Composable
private fun HomeActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isHovered) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isHovered) accent else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isHovered) Color.White else Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun HomeCarouselSection(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    accent: Color,
    badgeLabel: String? = null,
    onTrackClick: (Track) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
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

            Text(
                text = "${tracks.size} tracks",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.40f)
                )
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                val isCurrent = currentTrack?.id == track.id
                HomeTrackCard(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    accent = accent,
                    badgeLabel = badgeLabel,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun HomeTrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accent: Color,
    badgeLabel: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(targetValue = if (isHovered) 1.03f else 1.0f, animationSpec = tween(150))

    Column(
        modifier = Modifier
            .width(148.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.15f) else if (isHovered) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (isCurrent) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.4f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) accent else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(44.dp)
            )

            // Badge overlay (Lossless or Studio)
            if (badgeLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    GlassBadge(text = badgeLabel, color = accent)
                }
            } else if (track.isLossless) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    GlassBadge(text = "LOSSLESS", color = accent)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) accent else Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.60f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
