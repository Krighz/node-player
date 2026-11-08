package com.nodeplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.YouTubeSection
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Online Home View displaying curated YouTube Music flow, recommendations, and trending shelves.
 * Loads live carousels via [InnerTubeClient.getHomeRecommendations].
 */
@Composable
fun OnlineHomeView(
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    recentTracks: List<Track> = emptyList(),
    dockScrollController: DockScrollController? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current
    val scope = rememberCoroutineScope()

    val dockConnection = rememberDockScrollConnection(
        controller = dockScrollController,
        enabled = themeConfig.dockAutoHideEnabled
    )

    var isLoading by remember { mutableStateOf(true) }
    var sections by remember { mutableStateOf<List<YouTubeSection>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadHomeFeed() {
        scope.launch {
            isLoading = true
            errorMessage = null
            withContext(Dispatchers.IO) {
                try {
                    val client = InnerTubeClient()
                    client.use {
                        val result = it.getCombinedHomeFeed()
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                sections = result.getOrNull().orEmpty()
                                errorMessage = null
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to load recommendations"
                            }
                            isLoading = false
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        errorMessage = t.message ?: "Network error"
                        isLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHomeFeed()
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            dockScrollController?.onListScrolledToTop(0, 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dockConnection),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = LocalBottomBarPadding.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Online Curated Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Text(
                        text = "ONLINE FLOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                            color = Color(0xFF00E5FF)
                        )
                    )
                }
                Text(
                    text = "YouTube Music",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Curated recommendations, trending hits & endless radio",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
        }

        // Section: 4-Item Mobile Home Shortcuts Grid
        item {
            com.nodeplayer.ui.components.HomeShortcutsGrid(
                onOfflineMusicClick = {
                    com.nodeplayer.common.AppModeState.setOnlineMode(false)
                },
                onDeviceConnectClick = {
                    // Open device connect dialog or node connect
                },
                onHiResCollectionClick = {
                    com.nodeplayer.common.AppModeState.setOnlineMode(false)
                },
                onMyPlaylistsClick = {
                    com.nodeplayer.common.AppModeState.setOnlineMode(false)
                }
            )
        }

        // Section: RECENTLY PLAYED
        if (recentTracks.isNotEmpty()) {
            item {
                com.nodeplayer.ui.components.RecentlyPlayedShelf(
                    recentTracks = recentTracks,
                    onTrackClick = { track -> onPlayTrack(track, recentTracks) }
                )
            }
        }

        // Live Recommendations or Loading / Error State
        when {
            isLoading -> {
                // Section 1: QUICK PICKS (Shimmer Shelves)
                item {
                    ShimmerShelfSection(title = "QUICK PICKS", icon = Icons.Rounded.Headphones, tint = accent)
                }

                // Section 2: TRENDING NOW (Shimmer Shelves)
                item {
                    ShimmerShelfSection(title = "TRENDING CHARTS", icon = Icons.Rounded.Whatshot, tint = Color(0xFFFF5252))
                }

                // Section 3: MOODS & GENRES (Shimmer Mixes)
                item {
                    ShimmerShelfSection(title = "MOOD MIXES", icon = Icons.Rounded.GraphicEq, tint = Color(0xFFFFAB00))
                }
            }
            errorMessage != null && sections.isEmpty() -> {
                item {
                    ErrorRetryCard(
                        message = errorMessage ?: "Unable to connect to YouTube Music",
                        onRetry = { loadHomeFeed() }
                    )
                }
            }
            else -> {
                // Render live shelves returned from YouTube Music InnerTube
                items(sections) { section ->
                    OnlineSectionShelf(
                        section = section,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onTrackClick = { clickedTrack ->
                            onPlayTrack(clickedTrack, section.items)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineSectionShelf(
    section: YouTubeSection,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
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
                imageVector = Icons.Rounded.PlayCircleOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = section.title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    color = Color.White
                )
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(section.items) { track ->
                OnlineTrackCard(
                    track = track,
                    isCurrent = currentTrack?.id == track.id,
                    isPlaying = isPlaying && currentTrack?.id == track.id,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun OnlineTrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = LocalAccentColor.current

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(180),
        label = "OnlineTrackScale"
    )

    val cardShape = remember { ContinuousRoundedRectangle(14.dp) }

    Column(
        modifier = modifier
            .width(136.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(cardShape)
                .border(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    brush = if (isCurrent) Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.5f)))
                    else Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isHovered) 0.35f else 0.14f),
                            Color.White.copy(alpha = if (isHovered) 0.15f else 0.04f)
                        )
                    ),
                    shape = cardShape
                )
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )

            // Online Badge at top-end
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(ContinuousRoundedRectangle(6.dp))
                    .background(Color(0xFF0A1218).copy(alpha = 0.78f))
                    .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), ContinuousRoundedRectangle(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "OPUS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF)
                )
            }

            // Hover or active play indicator
            if (isHovered || isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) accent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.60f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShimmerShelfSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
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

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(5) {
                OnlineTrackShimmerCard()
            }
        }
    }
}

@Composable
private fun ErrorRetryCard(
    message: String,
    onRetry: () -> Unit
) {
    val cardShape = remember { ContinuousRoundedRectangle(16.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF1E1416).copy(alpha = 0.70f))
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f), cardShape)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Could Not Connect to YouTube Music",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
            com.nodeplayer.ui.components.LiquidButton(
                onClick = onRetry
            ) {
                Text(
                    text = "Retry Feed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun OnlineTrackShimmerCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )

    val cardShape = remember { ContinuousRoundedRectangle(14.dp) }
    val lineShape = remember { ContinuousCapsule() }

    Column(
        modifier = Modifier.width(132.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(cardShape)
                .background(Color.White.copy(alpha = alpha.value * 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(lineShape)
                .background(Color.White.copy(alpha = alpha.value * 0.15f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(10.dp)
                .clip(lineShape)
                .background(Color.White.copy(alpha = alpha.value * 0.10f))
        )
    }
}
