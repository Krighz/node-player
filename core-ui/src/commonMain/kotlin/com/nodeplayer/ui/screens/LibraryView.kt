package com.nodeplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import com.nodeplayer.network.library.*
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.components.SoundWaveVisualizer
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.LiquidColors

/**
 * Sub-destinations within the Apple Music-style Library view.
 */
enum class LibrarySubDestination {
    NONE,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    SONGS
}

/**
 * Revamped Apple Music-Style Online Library View (LBRY-01, LBRY-02, LBRY-03).
 *
 * Features:
 * - Dedicated navigation rows: Playlists, Artists, Albums, Songs with badges and in-place drilldown (D-01).
 * - "Recently Added" 2-row horizontal scroll carousel with 1:1 squircle cover cards (D-02).
 * - Strict online-first content aggregation: exclusively shows online saved/liked songs, playlists,
 *   and derived online albums/artists (D-03, LBRY-02).
 * - Centered liquid-glass empty-state card with "Explore Home" and "Search Music" quick actions (D-04).
 * - Offline local storage remains accessed via the Home profile menu 'Offline Music' switch (LBRY-03).
 */
@Composable
fun LibraryView(
    tracks: List<Track> = emptyList(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    folders: List<MusicFolder> = emptyList(),
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRescanClick: () -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    remoteTracks: List<Track> = emptyList(),
    onlineLibraryManager: OnlineLibraryManager? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onOpenPlaylistDetail: ((OnlinePlaylist) -> Unit)? = null,
    onOpenAlbumDetail: ((OnlineAlbum) -> Unit)? = null,
    dockScrollController: DockScrollController? = null,
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalGlassThemeConfig.current
    val bottomBarPadding = LocalBottomBarPadding.current + 80.dp

    val manager = remember(onlineLibraryManager) {
        onlineLibraryManager ?: OnlineLibraryManager()
    }

    // Seed any remote online tracks into online library manager
    LaunchedEffect(remoteTracks) {
        for (track in remoteTracks) {
            if (manager.isOnlineTrack(track) && !manager.isSaved(track.id)) {
                manager.saveTrack(track)
            }
        }
    }

    val libraryState by manager.state.collectAsState()
    var subDestination by remember { mutableStateOf(LibrarySubDestination.NONE) }

    val dockConnection = rememberDockScrollConnection(
        controller = dockScrollController,
        enabled = themeConfig.dockAutoHideEnabled
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dockConnection)
    ) {
        AnimatedContent(
            targetState = subDestination,
            transitionSpec = {
                if (targetState != LibrarySubDestination.NONE) {
                    (slideInHorizontally(spring(stiffness = 500f)) { it } + fadeIn()).togetherWith(
                        slideOutHorizontally(spring(stiffness = 500f)) { -it / 3 } + fadeOut()
                    )
                } else {
                    (slideInHorizontally(spring(stiffness = 500f)) { -it / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally(spring(stiffness = 500f)) { it } + fadeOut()
                    )
                }
            },
            label = "LibrarySubDestinationTransition"
        ) { targetSub ->
            when (targetSub) {
                LibrarySubDestination.NONE -> {
                    LibraryRootView(
                        libraryState = libraryState,
                        onNavigateSub = { subDestination = it },
                        onTrackClick = onTrackClick,
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        onOpenPlaylistDetail = onOpenPlaylistDetail,
                        onOpenAlbumDetail = onOpenAlbumDetail,
                        bottomPadding = bottomBarPadding
                    )
                }
                LibrarySubDestination.SONGS -> {
                    OnlineSongsSubView(
                        songs = libraryState.songs,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onBack = { subDestination = LibrarySubDestination.NONE },
                        onTrackClick = onTrackClick,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        bottomPadding = bottomBarPadding
                    )
                }
                LibrarySubDestination.PLAYLISTS -> {
                    OnlinePlaylistsSubView(
                        playlists = libraryState.playlists,
                        onBack = { subDestination = LibrarySubDestination.NONE },
                        onPlaylistClick = { pl -> onOpenPlaylistDetail?.invoke(pl) },
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        bottomPadding = bottomBarPadding
                    )
                }
                LibrarySubDestination.ALBUMS -> {
                    OnlineAlbumsSubView(
                        albums = libraryState.albums,
                        onBack = { subDestination = LibrarySubDestination.NONE },
                        onAlbumClick = { al -> onOpenAlbumDetail?.invoke(al) },
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        bottomPadding = bottomBarPadding
                    )
                }
                LibrarySubDestination.ARTISTS -> {
                    OnlineArtistsSubView(
                        artists = libraryState.artists,
                        onBack = { subDestination = LibrarySubDestination.NONE },
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        bottomPadding = bottomBarPadding
                    )
                }
            }
        }
    }
}

/**
 * Root Apple Music-style Library view:
 * Top navigation rows (Playlists, Artists, Albums, Songs) + "Recently Added" 2-row carousel.
 */
@Composable
private fun LibraryRootView(
    libraryState: OnlineLibraryState,
    onNavigateSub: (LibrarySubDestination) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onOpenPlaylistDetail: ((OnlinePlaylist) -> Unit)? = null,
    onOpenAlbumDetail: ((OnlineAlbum) -> Unit)? = null,
    bottomPadding: Dp
) {
    val accent = LocalAccentColor.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding)
    ) {
        // Top Title
        item(key = "library_title") {
            Text(
                text = "Library",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // Apple Music Navigation Rows (D-01, LBRY-01)
        item(key = "nav_rows") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .background(LiquidColors.SurfaceElevated.copy(alpha = 0.50f))
                    .border(
                        width = 0.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
                        ),
                        shape = ContinuousRoundedRectangle(20.dp)
                    )
            ) {
                LibraryNavRow(
                    title = "Playlists",
                    icon = Icons.Rounded.QueueMusic,
                    count = libraryState.playlists.size,
                    onClick = { onNavigateSub(LibrarySubDestination.PLAYLISTS) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
                LibraryNavRow(
                    title = "Artists",
                    icon = Icons.Rounded.Person,
                    count = libraryState.artists.size,
                    onClick = { onNavigateSub(LibrarySubDestination.ARTISTS) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
                LibraryNavRow(
                    title = "Albums",
                    icon = Icons.Rounded.Album,
                    count = libraryState.albums.size,
                    onClick = { onNavigateSub(LibrarySubDestination.ALBUMS) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
                LibraryNavRow(
                    title = "Songs",
                    icon = Icons.Rounded.MusicNote,
                    count = libraryState.songs.size,
                    onClick = { onNavigateSub(LibrarySubDestination.SONGS) }
                )
            }
        }

        // "Recently Added" Section Header (D-02)
        item(key = "recently_added_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently Added",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (libraryState.recentlyAdded.isNotEmpty()) {
                    Text(
                        text = "See All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .clickable { onNavigateSub(LibrarySubDestination.ALBUMS) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // "Recently Added" 2-Row Carousel (D-02)
        if (libraryState.recentlyAdded.isNotEmpty()) {
            item(key = "recently_added_grid") {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = libraryState.recentlyAdded,
                        key = { "${it.type}_${it.id}" }
                    ) { item ->
                        RecentlyAddedCard(
                            item = item,
                            onClick = {
                                when (item.type) {
                                    OnlineMediaType.TRACK -> {
                                        val track = libraryState.songs.find { it.id.toString() == item.id }
                                        if (track != null) {
                                            onTrackClick(track, libraryState.songs)
                                        }
                                    }
                                    OnlineMediaType.ALBUM -> {
                                        val album = libraryState.albums.find { it.id == item.id }
                                        if (album != null && onOpenAlbumDetail != null) {
                                            onOpenAlbumDetail(album)
                                        } else {
                                            onNavigateSub(LibrarySubDestination.ALBUMS)
                                        }
                                    }
                                    OnlineMediaType.PLAYLIST -> {
                                        val pl = libraryState.playlists.find { it.id == item.id }
                                        if (pl != null && onOpenPlaylistDetail != null) {
                                            onOpenPlaylistDetail(pl)
                                        } else {
                                            onNavigateSub(LibrarySubDestination.PLAYLISTS)
                                        }
                                    }
                                    OnlineMediaType.ARTIST -> onNavigateSub(LibrarySubDestination.ARTISTS)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Polished Empty State Card (D-04)
            item(key = "empty_library_card") {
                LibraryEmptyCard(
                    title = "Your online library is empty",
                    description = "Songs you stream, like, or save will appear here in your personal library.",
                    onExploreHome = onNavigateToHome,
                    onSearchMusic = onNavigateToSearch,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Single Apple Music navigation row item.
 */
@Composable
private fun LibraryNavRow(
    title: String,
    icon: ImageVector,
    count: Int,
    onClick: () -> Unit
) {
    val accent = LocalAccentColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tinted Icon Badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(ContinuousRoundedRectangle(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        if (count > 0) {
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 1:1 squircle artwork card for Recently Added 2-row carousel.
 */
@Composable
private fun RecentlyAddedCard(
    item: OnlineMediaItem,
    onClick: () -> Unit
) {
    val itemTrack = remember(item.id, item.artworkUrl) {
        Track(
            id = item.id.hashCode().toLong(),
            title = item.title,
            artist = item.subtitle,
            album = item.title,
            durationMs = 0L,
            path = item.artworkUrl ?: ""
        )
    }

    Column(
        modifier = Modifier
            .width(125.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(ContinuousRoundedRectangle(14.dp))
        ) {
            TrackArtwork(
                track = itemTrack,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.60f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Clean Liquid-Glass Empty State Card (D-04).
 */
@Composable
private fun LibraryEmptyCard(
    title: String,
    description: String,
    onExploreHome: () -> Unit,
    onSearchMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(24.dp))
            .background(LiquidColors.SurfaceElevated.copy(alpha = 0.65f))
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                ),
                shape = ContinuousRoundedRectangle(24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.65f),
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidButton(onClick = onExploreHome) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Explore Home",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            LiquidButton(onClick = onSearchMusic) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Search Music",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Sub-view: Online Songs list (LBRY-02).
 */
@Composable
private fun OnlineSongsSubView(
    songs: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    bottomPadding: Dp
) {
    val accent = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky Header with '‹ Library'
        SubViewHeader(
            title = "Songs",
            subtitle = "${songs.size} online tracks",
            onBack = onBack
        )

        if (songs.isEmpty()) {
            LibraryEmptyCard(
                title = "No online songs yet",
                description = "Songs you stream or like will be automatically collected here.",
                onExploreHome = onNavigateToHome,
                onSearchMusic = onNavigateToSearch,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                items(
                    items = songs,
                    key = { it.id }
                ) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, songs) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ContinuousRoundedRectangle(10.dp))
                        ) {
                            TrackArtwork(
                                track = track,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 10.dp
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
                            contentDescription = "Options",
                            onClick = { onAddToQueue(track) },
                            size = 36.dp,
                            lighterStyle = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sub-view: Online Playlists.
 */
@Composable
private fun OnlinePlaylistsSubView(
    playlists: List<OnlinePlaylist>,
    onBack: () -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit = {},
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    bottomPadding: Dp
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(
            title = "Playlists",
            subtitle = "${playlists.size} playlists",
            onBack = onBack
        )

        if (playlists.isEmpty()) {
            LibraryEmptyCard(
                title = "No online playlists",
                description = "Saved playlists and mixes will appear here.",
                onExploreHome = onNavigateToHome,
                onSearchMusic = onNavigateToSearch,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val plTrack = remember(pl.id, pl.artworkUrl) {
                        Track(
                            id = pl.id.hashCode().toLong(),
                            title = pl.title,
                            artist = pl.author,
                            album = pl.title,
                            durationMs = 0L,
                            path = pl.artworkUrl ?: ""
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(pl) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(ContinuousRoundedRectangle(12.dp))
                        ) {
                            TrackArtwork(
                                track = plTrack,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 12.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (pl.author.isNotBlank()) pl.author else "${pl.trackCount} songs",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.60f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sub-view: Online Albums.
 */
@Composable
private fun OnlineAlbumsSubView(
    albums: List<OnlineAlbum>,
    onBack: () -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit = {},
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    bottomPadding: Dp
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(
            title = "Albums",
            subtitle = "${albums.size} albums",
            onBack = onBack
        )

        if (albums.isEmpty()) {
            LibraryEmptyCard(
                title = "No online albums",
                description = "Albums from tracks you like and stream are organized here.",
                onExploreHome = onNavigateToHome,
                onSearchMusic = onNavigateToSearch,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    val albumTrack = remember(album.id, album.artworkUrl) {
                        Track(
                            id = album.id.hashCode().toLong(),
                            title = album.title,
                            artist = album.artist,
                            album = album.title,
                            durationMs = 0L,
                            path = album.artworkUrl ?: ""
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(ContinuousRoundedRectangle(16.dp))
                        ) {
                            TrackArtwork(
                                track = albumTrack,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 16.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = album.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.artist,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sub-view: Online Artists.
 */
@Composable
private fun OnlineArtistsSubView(
    artists: List<OnlineArtist>,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    bottomPadding: Dp
) {
    val accent = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(
            title = "Artists",
            subtitle = "${artists.size} artists",
            onBack = onBack
        )

        if (artists.isEmpty()) {
            LibraryEmptyCard(
                title = "No online artists",
                description = "Artists of your saved and streamed music will appear here.",
                onExploreHome = onNavigateToHome,
                onSearchMusic = onNavigateToSearch,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                items(artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* Detail navigation */ }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${artist.trackCount} saved songs",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sticky Glass Header for Sub-views ('‹ Library' back navigation).
 */
@Composable
private fun SubViewHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(ContinuousCapsule)
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Library",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.50f)
            )
        }
    }
}
