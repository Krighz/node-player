package com.nodeplayer.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import com.nodeplayer.common.AppModeState
import com.nodeplayer.model.Track
import com.nodeplayer.network.cache.HomeFeedDiskCache
import com.nodeplayer.network.innertube.SharedInnerTubeProvider
import com.nodeplayer.network.innertube.YouTubeSection
import com.nodeplayer.network.recommender.HomeFeedRecommender
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.HomeProfileSheet
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.LiquidColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HOME_CARD_SHAPE_14 = ContinuousRoundedRectangle(14.dp)
private val HOME_CAPSULE_SHAPE = ContinuousCapsule()
private val HOME_BANNER_SHAPE_16 = ContinuousRoundedRectangle(16.dp)
private val HOME_BANNER_ICON_SHAPE_10 = ContinuousRoundedRectangle(10.dp)
private val HOME_BTN_SHAPE_12 = ContinuousRoundedRectangle(12.dp)

fun <T> uniqueLazyKeys(items: List<T>, baseKeySelector: (T) -> String): List<String> {
    if (items.isEmpty()) return emptyList()
    val seen = HashMap<String, Int>(items.size)
    return items.map { item ->
        val base = baseKeySelector(item)
        val count = seen[base] ?: 0
        seen[base] = count + 1
        if (count == 0) base else "${base}_dup$count"
    }
}

fun uniqueLazyTrackKeys(tracks: List<Track>, prefix: String = "track"): List<String> =
    uniqueLazyKeys(tracks) { track -> "${prefix}_${track.id}_${track.path.hashCode()}" }

fun filterAndSortRecentTracks(tracks: List<Track>): List<Track> =
    tracks.filter { it.lastPlayedTimestamp > 0L }
        .sortedByDescending { it.lastPlayedTimestamp }

/**
 * Online Home View displaying 5 structured YouTube Music horizontal shelves:
 * 1. Quick Picks (1:1 squircle cards)
 * 2. New music videos (16:9 widescreen cards)
 * 3. Trending in region (1:1 squircle cards with rank)
 * 4. Playlists (1:1 squircle cards with curator)
 * 5. From artists you follow (1:1 squircle/circle cards)
 *
 * Backed by [HomeFeedDiskCache] for instant sub-50ms cold-start display,
 * and [HomeFeedRecommender] for local history fallback and "Not interested" dismissals.
 */
@Composable
fun OnlineHomeView(
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    recentTracks: List<Track> = emptyList(),
    currentRoute: String = "home",
    dockScrollController: DockScrollController? = null,
    isLoggedIn: Boolean = false,
    accountName: String? = null,
    accountEmail: String? = null,
    avatarUrl: String? = null,
    channelHandle: String? = null,
    onSearchClick: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenOfflineMusic: () -> Unit = {},
    onOpenDeviceConnect: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onRescanClick: () -> Unit = {},
    onOpenPlaylistDetail: ((title: String, subtitle: String, artworkUrl: String?, items: List<Track>) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current
    val scope = rememberCoroutineScope()
    var showOnboardingBanner by remember { mutableStateOf(true) }

    val diskCache = remember { HomeFeedDiskCache() }
    val recommender = remember { HomeFeedRecommender() }

    val dockConnection = rememberDockScrollConnection(
        controller = dockScrollController,
        enabled = themeConfig.dockAutoHideEnabled
    )

    var showProfileSheet by remember { mutableStateOf(false) }
    var sections by remember { mutableStateOf<List<YouTubeSection>>(diskCache.sectionsState.value) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isFallbackMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshFeed(forceNetwork: Boolean = false) {
        scope.launch {
            isRefreshing = true
            errorMessage = null

            withContext(Dispatchers.IO) {
                try {
                    val innerClient = SharedInnerTubeProvider.get()
                    val result = if (currentRoute == "new") {
                        val exploreRes = innerClient.getExploreSections().getOrDefault(emptyList())
                        val chartsRes = innerClient.getChartsSections().getOrDefault(emptyList())
                        val combined = (exploreRes + chartsRes).distinctBy { it.title }
                        if (combined.isNotEmpty()) Result.success(combined) else innerClient.getCombinedHomeFeed()
                    } else {
                        innerClient.getCombinedHomeFeed()
                    }
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val loaded = result.getOrNull().orEmpty()
                            val clean = recommender.filterDismissedSections(loaded)
                            sections = clean
                            diskCache.save(clean, region = recommender.resolveRegion())
                            isFallbackMode = false
                            errorMessage = null
                        } else {
                            // Fallback mode using local history & cached charts
                            val fallbackPicks = recommender.buildFallbackQuickPicks(recentTracks)
                            if (sections.isEmpty() && fallbackPicks.isNotEmpty()) {
                                sections = listOf(
                                    YouTubeSection(title = "Quick Picks", items = fallbackPicks)
                                )
                            }
                            isFallbackMode = true
                            errorMessage = result.exceptionOrNull()?.message ?: "Using offline fallback"
                        }
                        isRefreshing = false
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        val fallbackPicks = recommender.buildFallbackQuickPicks(recentTracks)
                        if (sections.isEmpty() && fallbackPicks.isNotEmpty()) {
                            sections = listOf(
                                YouTubeSection(title = "Quick Picks", items = fallbackPicks)
                            )
                        }
                        isFallbackMode = true
                        errorMessage = t.message ?: "Network error - using offline fallback"
                        isRefreshing = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Cold-start display from cache if present; refresh in background
        if (sections.isEmpty()) {
            val cached = diskCache.loadFromDisk()
            if (cached.isNotEmpty()) {
                sections = recommender.filterDismissedSections(cached)
            }
        }
        refreshFeed(forceNetwork = false)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) {
                    dockScrollController?.onListScrolledToTop(0, 0)
                }
            }
    }

    val orderedShelves = remember(sections) {
        if (sections.isNotEmpty()) organizeShelves(sections) else emptyList()
    }
    val shelfKeys = remember(orderedShelves) {
        uniqueLazyKeys(orderedShelves) { it.title }
    }
    val shelfListStates = remember { mutableMapOf<String, LazyListState>() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockConnection),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = LocalBottomBarPadding.current + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // Header: Flow title & Top-Right Profile Avatar button per D-02 / D-03
            item(key = "home_header", contentType = "home_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isFallbackMode) LiquidColors.AmberWarning else LiquidColors.ElectricCyan)
                            )
                            Text(
                                text = if (currentRoute == "new") "NEW RELEASES & CHARTS" else if (isFallbackMode) "OFFLINE / LOCAL FALLBACK" else "ONLINE FLOW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.3.sp,
                                    color = if (isFallbackMode) LiquidColors.AmberWarning else LiquidColors.ElectricCyan
                                )
                            )
                        }
                        Text(
                            text = if (currentRoute == "new") "Explore & Charts" else "YouTube Music",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                    }

                    // Top-Right Profile Avatar Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (isLoggedIn) accent else Color.White.copy(alpha = 0.20f), CircleShape)
                            .clickable { showProfileSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLoggedIn) Icons.Rounded.AccountCircle else Icons.Rounded.Person,
                            contentDescription = "Profile & Settings",
                            tint = if (isLoggedIn) accent else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // YouTube Music Onboarding / Sign-in Banner (as seen in Convx reference video)
            if (!isLoggedIn && showOnboardingBanner) {
                item(key = "yt_onboarding_banner", contentType = "onboarding_banner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HOME_BANNER_SHAPE_16)
                            .background(Color(0xFF1E212A))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), HOME_BANNER_SHAPE_16)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(HOME_BANNER_ICON_SHAPE_10)
                                            .background(Color(0xFFFF0033).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFFFF0033),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        text = "Bring your YouTube Music library, playlists and mixes here.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            lineHeight = 20.sp
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = { showOnboardingBanner = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onSignInClick,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = HOME_BTN_SHAPE_12,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF0033).copy(alpha = 0.22f),
                                    contentColor = Color(0xFFFF4D6D)
                                )
                            ) {
                                Text(
                                    text = "Get Started",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Subtle Status / Scan local library banner when fallback mode is active
            if (isFallbackMode && recentTracks.isEmpty()) {
                item(key = "offline_fallback_banner", contentType = "fallback_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HOME_BTN_SHAPE_12)
                            .background(LiquidColors.SurfaceDark.copy(alpha = 0.85f))
                            .border(1.dp, LiquidColors.AmberWarning.copy(alpha = 0.35f), HOME_BTN_SHAPE_12)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Offline fallback active — scan local files to boost recommendations",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LiquidColors.AmberLight,
                                fontSize = 11.5.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = LiquidColors.AmberWarning
                            ),
                            modifier = Modifier
                                .clip(HOME_CAPSULE_SHAPE)
                                .clickable { onRescanClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Ordered 5 Shelves: Quick Picks, New music videos, Trending in region, Playlists, Artists
            if (orderedShelves.isNotEmpty()) {
                itemsIndexed(
                    items = orderedShelves,
                    key = { index, _ -> shelfKeys[index] },
                    contentType = { _, shelf ->
                        if ("video" in shelf.title.lowercase()) "video_shelf" else "audio_shelf"
                    }
                ) { index, shelf ->
                    val shelfKey = shelfKeys[index]
                    val rowState = remember(shelfKey) {
                        shelfListStates.getOrPut(shelfKey) { LazyListState() }
                    }
                    HomeShelfSection(
                        shelf = shelf,
                        rowState = rowState,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onTrackClick = { clickedTrack ->
                            onPlayTrack(clickedTrack, shelf.items)
                        },
                        onShelfClick = { clickedShelf ->
                            onOpenPlaylistDetail?.invoke(
                                clickedShelf.title,
                                "YouTube Music",
                                clickedShelf.items.firstOrNull()?.path,
                                clickedShelf.items
                            )
                        },
                        onDismissTrack = { dismissedTrack ->
                            recommender.dismissTrack(dismissedTrack)
                            sections = recommender.filterDismissedSections(sections)
                        }
                    )
                }
            } else if (isRefreshing) {
                // Loading Shimmer Shelves
                item(key = "shimmer_quick_picks", contentType = "shimmer_shelf") {
                    ShimmerShelfSection(title = "QUICK PICKS", isVideo = false)
                }
                item(key = "shimmer_videos", contentType = "shimmer_shelf") {
                    ShimmerShelfSection(title = "NEW MUSIC VIDEOS", isVideo = true)
                }
                item(key = "shimmer_trending", contentType = "shimmer_shelf") {
                    ShimmerShelfSection(title = "TRENDING CHARTS", isVideo = false)
                }
            } else if (errorMessage != null) {
                item(key = "home_error_retry", contentType = "error_card") {
                    ErrorRetryCard(
                        message = errorMessage ?: "Failed to load feed",
                        onRetry = { refreshFeed(forceNetwork = true) }
                    )
                }
            }
        }

        // Sliding Liquid-Glass Profile Sheet
        HomeProfileSheet(
            visible = showProfileSheet,
            onDismiss = { showProfileSheet = false },
            accountName = accountName,
            accountEmail = accountEmail,
            avatarUrl = avatarUrl,
            channelHandle = channelHandle,
            onSignInClick = onSignInClick,
            onEqualizerClick = onOpenEqualizer,
            onSettingsClick = onOpenSettings,
            onOfflineMusicClick = onOpenOfflineMusic,
            onDeviceConnectClick = onOpenDeviceConnect
        )
    }
}

/**
 * Organizes raw sections into the 5 mandated shelves:
 * 1. Quick Picks
 * 2. New music videos
 * 3. Trending in region
 * 4. Playlists
 * 5. From artists you follow
 */
fun organizeShelves(sections: List<YouTubeSection>): List<YouTubeSection> {
    val consumed = HashSet<YouTubeSection>()

    val quickPicks = sections.find { "quick" in it.title.lowercase() || "pick" in it.title.lowercase() }
        ?.also { consumed.add(it) }
    val newVideos = sections.find { it !in consumed && ("video" in it.title.lowercase() || "new" in it.title.lowercase() || "release" in it.title.lowercase()) }
        ?.also { consumed.add(it) }
    val trending = sections.find { it !in consumed && ("trending" in it.title.lowercase() || "chart" in it.title.lowercase()) }
        ?.also { consumed.add(it) }
    val playlists = sections.find { it !in consumed && ("playlist" in it.title.lowercase() || "mix" in it.title.lowercase() || "mood" in it.title.lowercase()) }
        ?.also { consumed.add(it) }
    val artists = sections.find { it !in consumed && ("artist" in it.title.lowercase() || "follow" in it.title.lowercase()) }
        ?.also { consumed.add(it) }

    val result = ArrayList<YouTubeSection>(5)
    if (quickPicks != null) result.add(quickPicks.copy(title = "Quick Picks"))
    if (newVideos != null) result.add(newVideos.copy(title = "New Music Videos"))
    if (trending != null) result.add(trending.copy(title = "Trending in Region"))
    if (playlists != null) result.add(playlists.copy(title = "Playlists & Mixes"))
    if (artists != null) result.add(artists.copy(title = "From Artists You Follow"))

    // Append any remaining sections that weren't categorized
    for (sec in sections) {
        if (sec !in consumed) {
            result.add(sec)
        }
    }

    return if (result.isNotEmpty()) result else sections
}

@Composable
private fun HomeShelfSection(
    shelf: YouTubeSection,
    rowState: LazyListState = rememberLazyListState(),
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onShelfClick: ((YouTubeSection) -> Unit)? = null,
    onDismissTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val normalizedTitle = shelf.title.lowercase()
    val isVideoShelf = "video" in normalizedTitle

    val (shelfIcon, shelfTint) = when {
        "quick" in normalizedTitle || "pick" in normalizedTitle -> Icons.Rounded.Headphones to accent
        "video" in normalizedTitle -> Icons.Rounded.SmartDisplay to LiquidColors.CoralRed
        "trending" in normalizedTitle || "chart" in normalizedTitle -> Icons.Rounded.Whatshot to LiquidColors.OrangeBadge
        "playlist" in normalizedTitle || "mix" in normalizedTitle -> Icons.Rounded.LibraryMusic to LiquidColors.ElectricCyan
        else -> Icons.Rounded.PlayCircleOutline to accent
    }

    val itemKeys = remember(shelf.title, shelf.items) {
        uniqueLazyTrackKeys(shelf.items, shelf.title)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onShelfClick?.invoke(shelf) }
        ) {
            Icon(
                imageVector = shelfIcon,
                contentDescription = null,
                tint = shelfTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = shelf.title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    color = Color.White
                )
            )
        }

        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = shelf.items,
                key = { idx, _ -> itemKeys[idx] },
                contentType = { _, _ -> if (isVideoShelf) "video_card_16x9" else "audio_card_1x1" }
            ) { index, track ->
                if (isVideoShelf) {
                    VideoCard16x9(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        onClick = { onTrackClick(track) },
                        onDismiss = { onDismissTrack(track) }
                    )
                } else {
                    AudioSquircleCard1x1(
                        track = track,
                        rank = if ("trending" in normalizedTitle) index + 1 else null,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        onClick = { onTrackClick(track) },
                        onDismiss = { onDismissTrack(track) }
                    )
                }
            }
        }
    }
}

/**
 * 16:9 Widescreen Card for Music Videos (D-01: 220dp width x 124dp height).
 */
@Composable
fun VideoCard16x9(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = LocalAccentColor.current
    var showMenu by remember { mutableStateOf(false) }

    val cardShape = HOME_CARD_SHAPE_14

    Column(
        modifier = modifier
            .width(220.dp)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .clip(cardShape)
                .border(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent) accent else Color.White.copy(alpha = if (isHovered) 0.35f else 0.12f),
                    shape = cardShape
                )
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp,
                targetSizePx = 320
            )

            // Duration badge at bottom-right
            if (track.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(HOME_CAPSULE_SHAPE)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = track.durationFormatted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Video Play overlay on hover or current
            if (isHovered || isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 3-dot dismiss menu button at top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(LiquidColors.SurfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Not interested / hide this", color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            showMenu = false
                            onDismiss()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = LiquidColors.CoralRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
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

/**
 * 1:1 Squircle Card for Quick Picks, Trending, and Playlists (D-01: 136dp x 136dp).
 */
@Composable
fun AudioSquircleCard1x1(
    track: Track,
    rank: Int? = null,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = LocalAccentColor.current
    var showMenu by remember { mutableStateOf(false) }

    val cardShape = HOME_CARD_SHAPE_14

    Column(
        modifier = modifier
            .width(136.dp)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(cardShape)
                .border(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent) accent else Color.White.copy(alpha = if (isHovered) 0.35f else 0.12f),
                    shape = cardShape
                )
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp,
                targetSizePx = 256
            )

            // Optional Rank badge for trending shelf
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(HOME_CAPSULE_SHAPE)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#$rank",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB300)
                    )
                }
            }

            // Hover / Playing indicator
            if (isHovered || isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 3-dot dismiss menu button at top-end
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(LiquidColors.SurfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Not interested / hide this", color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            showMenu = false
                            onDismiss()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = LiquidColors.CoralRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) accent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.60f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShimmerShelfSection(title: String, isVideo: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = 0.70f)
            )
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(5) {
                val cardShape = remember { ContinuousRoundedRectangle(14.dp) }
                Box(
                    modifier = Modifier
                        .size(width = if (isVideo) 220.dp else 136.dp, height = if (isVideo) 124.dp else 136.dp)
                        .clip(cardShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
                )
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
            .background(LiquidColors.SurfaceDark.copy(alpha = 0.70f))
            .border(1.dp, LiquidColors.CoralRed.copy(alpha = 0.35f), cardShape)
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
                tint = LiquidColors.CoralRed,
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
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = LiquidColors.CoralRed.copy(alpha = 0.75f))
            ) {
                Text("Retry Feed", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
