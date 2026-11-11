package com.nodeplayer.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nodeplayer.music.ui.components.UpdateBanner
import com.nodeplayer.music.update.UpdateManager
import com.nodeplayer.music.update.AppUpdateInfo
import com.nodeplayer.model.AuthState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nodeplayer.common.AppModeState
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.HazeState
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.pressScale
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DeviceConnectPill
import com.nodeplayer.ui.components.DeviceConnectStatus
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.screens.OnlineHomeView
import java.io.File

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
    onOpenNodeConnect: () -> Unit = {},
    nodeConnectStatus: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED,
    connectedDeviceCount: Int = 0,
    onStartUpdate: (AppUpdateInfo) -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenOfflineMusic: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    currentRoute: String = "home",
    dockScrollController: com.nodeplayer.ui.components.DockScrollController? = null,
    onOpenPlaylistDetail: ((title: String, subtitle: String, artworkUrl: String?, items: List<Track>) -> Unit)? = null,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val isOnline by AppModeState.isOnlineMode.collectAsState()
    val ytAuthState by com.nodeplayer.music.auth.YouTubeAccountManager.authState.collectAsState()

    AnimatedContent(
        targetState = isOnline,
        transitionSpec = {
            (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.98f, animationSpec = tween(260))) togetherWith
            (fadeOut(animationSpec = tween(260)) + scaleOut(targetScale = 0.98f, animationSpec = tween(260)))
        },
        label = "AndroidHomeModeTransition",
        modifier = modifier.fillMaxSize()
    ) { online ->
        if (online) {
            val recentTracks = remember(allTracks) {
                allTracks.filter { it.lastPlayedTimestamp > 0L }.sortedByDescending { it.lastPlayedTimestamp }
            }
            OnlineHomeView(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onPlayTrack = onTrackClick,
                onPlayPause = onPlayPauseClick,
                recentTracks = recentTracks,
                currentRoute = currentRoute,
                dockScrollController = dockScrollController,
                isLoggedIn = ytAuthState is com.nodeplayer.model.AuthState.LoggedIn,
                onSearchClick = onSearchClick,
                onOpenEqualizer = onOpenEqualizer,
                onOpenSettings = onOpenSettings,
                onOpenOfflineMusic = onOpenOfflineMusic,
                onOpenDeviceConnect = onOpenNodeConnect,
                onSignInClick = onSignInClick,
                onOpenPlaylistDetail = onOpenPlaylistDetail,
                modifier = Modifier.fillMaxSize().statusBarsPadding()
            )
        } else {
            OfflineHomeScreenContent(
                allTracks = allTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = onTrackClick,
                onPlayPauseClick = onPlayPauseClick,
                onOpenSmartPlaylists = onOpenSmartPlaylists,
                onOpenStats = onOpenStats,
                onOpenDuplicateFinder = onOpenDuplicateFinder,
                onOpenNodeConnect = onOpenNodeConnect,
                nodeConnectStatus = nodeConnectStatus,
                connectedDeviceCount = connectedDeviceCount,
                onStartUpdate = onStartUpdate,
                onCancelUpdate = onCancelUpdate,
                onInstallUpdate = onInstallUpdate,
                hazeState = hazeState
            )
        }
    }
}

@Composable
private fun OfflineHomeScreenContent(
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayPauseClick: () -> Unit,
    onOpenSmartPlaylists: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onOpenNodeConnect: () -> Unit = {},
    nodeConnectStatus: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED,
    connectedDeviceCount: Int = 0,
    onStartUpdate: (AppUpdateInfo) -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val availableUpdate by UpdateManager.availableUpdate.collectAsState()
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()

    // 1. Recently Played (tracks sorted by lastPlayedTimestamp descending)
    val recentlyPlayed = remember(allTracks) {
        allTracks.filter { it.lastPlayedTimestamp > 0L }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(15)
    }

    // 2. Heavy Rotation ("On Repeat" - top tracks by play count)
    val heavyRotation = remember(allTracks) {
        allTracks.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(15)
    }

    // 3. Artist Spotlight (Detect most played/prominent artist)
    val topArtist = remember(allTracks) {
        allTracks.groupBy { it.artist }
            .filterKeys { it.isNotBlank() && it != "Unknown Artist" && it != "<unknown>" }
            .maxByOrNull { entry -> entry.value.sumOf { it.playCount.coerceAtLeast(1) } }?.key
    }
    val artistSpotlightTracks = remember(allTracks, topArtist) {
        if (topArtist != null) allTracks.filter { it.artist == topArtist }.take(15) else emptyList()
    }

    // 4. Hi-Res Master Studio Vault (Audiophile FLAC / Lossless / 24-bit / Hi-Res)
    val hiResVault = remember(allTracks) {
        allTracks.filter { it.isLossless && (it.bitDepth >= 24 || it.sampleRate >= 88200 || it.formatDescription.contains("FLAC", ignoreCase = true)) }
            .take(15)
    }

    // 5. Fresh Discoveries (Untapped music in library with 0 play count)
    val freshDiscoveries = remember(allTracks) {
        allTracks.filter { it.playCount == 0 }.take(15)
    }

    // 6. Rediscover: Tracks played in the past but unplayed for 30+ days
    val thirtyDaysAgo = remember { System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L) }
    val rediscoverTracks = remember(allTracks) {
        allTracks.filter { (it.playCount > 0 && it.lastPlayedTimestamp < thirtyDaysAgo) || (it.isFavorite && it.lastPlayedTimestamp < thirtyDaysAgo) }
            .take(15)
    }

    // 7. Favorite Anthems
    val favorites = remember(allTracks) {
        allTracks.filter { it.isFavorite }.take(15)
    }

    // 8. Smart Playlist shortcuts
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeviceConnectPill(
                        status = nodeConnectStatus,
                        connectedDeviceCount = connectedDeviceCount,
                        compact = true,
                        onClick = onOpenNodeConnect
                    )
                    LiquidIconButton(
                        icon = Icons.Rounded.BarChart,
                        onClick = onOpenStats,
                        contentDescription = "Stats",
                        size = 38.dp,
                        lighterStyle = true
                    )
                    LiquidIconButton(
                        icon = Icons.Rounded.ContentCopy,
                        onClick = onOpenDuplicateFinder,
                        contentDescription = "Duplicate Finder",
                        size = 38.dp,
                        lighterStyle = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // In-App Update Banner (Follows glass appearance settings)
        if (availableUpdate != null) {
            item {
                UpdateBanner(
                    updateInfo = availableUpdate!!,
                    downloadStatus = downloadStatus,
                    
                    onUpdateNow = {
                        onStartUpdate(availableUpdate!!)
                        UpdateManager.startDownload(context, availableUpdate!!)
                    },
                    onLater = { UpdateManager.dismissForSession() },
                    onSkip = { UpdateManager.skipVersion(context, availableUpdate!!.versionName) },
                    onCancelDownload = {
                        onCancelUpdate()
                        UpdateManager.cancelDownload(context)
                    },
                    onInstall = onInstallUpdate,
                    onRetry = {
                        onStartUpdate(availableUpdate!!)
                        UpdateManager.startDownload(context, availableUpdate!!)
                    }
                )
            }
        }

        // Hero Card: Jump Back In
        item {
            val heroTrack = currentTrack ?: recentlyPlayed.firstOrNull() ?: allTracks.firstOrNull()
            if (heroTrack != null) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hero Artwork with fallback
                            ArtworkThumbnail(
                                track = heroTrack,
                                size = 72.dp,
                                cornerRadius = 16.dp
                            )

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

        // Section 1: Recently Played Carousel
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

        // Section 2: Heavy Rotation ("On Repeat")
        if (heavyRotation.isNotEmpty()) {
            item {
                SectionHeader(title = "Heavy Rotation", count = heavyRotation.size, subtitle = "Your most played anthems")
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(heavyRotation, key = { "heavy_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            badgeText = "${track.playCount}x plays",
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, heavyRotation) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section 3: Artist Spotlight (if available)
        if (artistSpotlightTracks.isNotEmpty() && topArtist != null) {
            item {
                SectionHeader(
                    title = "Spotlight: $topArtist",
                    count = artistSpotlightTracks.size,
                    actionText = "Play Mix",
                    onActionClick = { onTrackClick(artistSpotlightTracks[0], artistSpotlightTracks) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artistSpotlightTracks, key = { "artist_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            badgeText = track.formatBadgeText,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, artistSpotlightTracks) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section 4: Quick Mixes Shelf
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
                        title = "Lossless Only",
                        subtitle = "${losslessTracks.size} tracks",
                        icon = Icons.Rounded.GraphicEq,
                        accentColor = Color(0xFF818CF8),
                        onClick = { if (losslessTracks.isNotEmpty()) onTrackClick(losslessTracks[0], losslessTracks) }
                    )
                }
                if (favorites.isNotEmpty()) {
                    item {
                        SmartPlaylistChip(
                            title = "Loved Tracks",
                            subtitle = "${favorites.size} tracks",
                            icon = Icons.Rounded.Favorite,
                            accentColor = Color(0xFFF43F5E),
                            onClick = { onTrackClick(favorites[0], favorites) }
                        )
                    }
                }
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
                        title = "Deep Cuts",
                        subtitle = "${longTracks.size} tracks (>5m)",
                        icon = Icons.Rounded.Timer,
                        accentColor = Color(0xFF10B981),
                        onClick = { if (longTracks.isNotEmpty()) onTrackClick(longTracks[0], longTracks) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section 5: Hi-Res Studio Master Vault
        if (hiResVault.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Hi-Res Studio Masters",
                    count = hiResVault.size,
                    subtitle = "Pure lossless bit-perfect fidelity"
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(hiResVault, key = { "hires_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            badgeText = "${track.bitDepth}-bit / ${(track.sampleRate / 1000.0).toInt()}k",
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, hiResVault) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section 6: Fresh Discoveries (Untapped Library)
        if (freshDiscoveries.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Fresh Discoveries",
                    count = freshDiscoveries.size,
                    subtitle = "Never played in your library"
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(freshDiscoveries, key = { "fresh_${it.id}" }) { track ->
                        TrackCarouselCard(
                            track = track,
                            badgeText = "New",
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = { onTrackClick(track, freshDiscoveries) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section 7: Rediscover Shelf
        if (rediscoverTracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Rediscover", count = rediscoverTracks.size, subtitle = "Tracks you haven't heard in a while")
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
private fun SectionHeader(
    title: String,
    count: Int,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.50f)
                    )
                )
            }
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.clickable { onActionClick() }
            )
        } else {
            Text(
                text = "$count tracks",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.50f)
                )
            )
        }
    }
}

@Composable
private fun ArtworkThumbnail(
    track: Track,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val squircleShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    Box(
        modifier = modifier
            .size(size)
            .clip(squircleShape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))
                ),
                squircleShape
            )
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(size * 0.45f)
        )
        AsyncImage(
            model = track,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TrackCarouselCard(
    track: Track,
    badgeText: String? = null,
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
            modifier = Modifier.size(130.dp)
        ) {
            ArtworkThumbnail(
                track = track,
                size = 130.dp,
                cornerRadius = 16.dp
            )

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
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
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    val chipShape = remember { ContinuousRoundedRectangle(16.dp) }
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))
                ),
                chipShape
            )
            .pressScale(targetScale = 0.96f, onClick = onClick)
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
