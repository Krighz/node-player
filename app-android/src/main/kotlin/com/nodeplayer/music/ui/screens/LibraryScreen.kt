package com.nodeplayer.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.HazeState
import com.nodeplayer.music.ui.components.SoundWaveVisualizer
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.music.ui.theme.pressScale
import com.nodeplayer.ui.component.OfflineMixtapeHomeCard
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.screens.uniqueLazyKeys
import com.nodeplayer.ui.screens.uniqueLazyTrackKeys
import java.io.File

private val SONG_ART_SHAPE = ContinuousRoundedRectangle(12.dp)
private val SONG_ART_BORDER_BRUSH = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
)
private val FOLDER_ICON_SHAPE = RoundedCornerShape(12.dp)
private val ALBUM_GRID_SHAPE = RoundedCornerShape(14.dp)
private val ALBUM_LIST_SHAPE = RoundedCornerShape(12.dp)
private val PLAYLIST_PILL_SHAPE = ContinuousRoundedRectangle(GlassTokens.pillCornerRadius)
private val PLAYLIST_ICON_SHAPE = RoundedCornerShape(10.dp)
private val PLAYLIST_FILTER_OPTIONS = listOf("All", "Local", "YouTube", "Spotify")

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<MusicFolder>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRescanClick: () -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    qualityBadgeMode: String = "DETAILED",
    hazeState: HazeState? = null,
    audioOutputInfo: com.nodeplayer.model.AudioOutputInfo = com.nodeplayer.model.AudioOutputInfo(),
    onAnalyzeTrack: ((Track) -> Unit)? = null,
    analyzingTrackId: Long? = null,
    playlists: List<com.nodeplayer.model.Playlist> = emptyList(),
    onOpenPlaylist: ((com.nodeplayer.model.Playlist) -> Unit)? = null,
    onCreatePlaylist: ((String) -> Unit)? = null,
    onImportYouTubePlaylists: (() -> Unit)? = null,
    youTubeSyncing: Boolean = false,
    youTubeSyncError: String? = null,
    onImportSpotifyPlaylists: (() -> Unit)? = null,
    onImportPlaylistFile: (() -> Unit)? = null,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    dailyStats: List<com.nodeplayer.music.data.DailyListeningStatEntity> = emptyList(),
    onOpenStats: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenDownloadQueue: () -> Unit = {},
    onDownloadTrack: ((Track) -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isBrowsingLocalMusic by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(true) }
    var playlistFilter by remember { mutableStateOf("All") }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders", "Downloads")
    val accent = LocalAccentColor.current
    var viewingFolder by remember { mutableStateOf<MusicFolder?>(null) }
    var viewingAlbum by remember { mutableStateOf<Album?>(null) }
    var viewingArtist by remember { mutableStateOf<Artist?>(null) }
    var inspectingTrack by remember { mutableStateOf<Track?>(null) }
    val tabScrollState = rememberScrollState()
    val context = LocalContext.current
    val mixtapeManager = remember { (context.applicationContext as? com.nodeplayer.music.NodePlayerApplication)?.smartMixtapeManager }
    val cachedMixtapeMap by (mixtapeManager?.cachedKeyMap ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()

    // Automatically trigger sync of YouTube playlists when user is logged in (Task 5)
    LaunchedEffect(Unit) {
        if (com.nodeplayer.music.auth.YouTubeAccountManager.authState.value is com.nodeplayer.model.AuthState.LoggedIn) {
            onImportYouTubePlaylists?.invoke()
        }
    }

    val totalListeningMinutes = remember(dailyStats, tracks) {
        val totalSec = dailyStats.sumOf { it.listeningSeconds }
        if (totalSec > 0) totalSec / 60L else (tracks.sumOf { it.durationMs } / 60000L).coerceAtLeast(0L)
    }
    val totalPlayCount = remember(tracks) {
        val count = tracks.sumOf { it.playCount.toLong() }
        if (count > 0) count else tracks.size.toLong()
    }

    val themeConfig = com.nodeplayer.music.ui.theme.LocalGlassThemeConfig.current
    val isOled = themeConfig.isOledBlack || themeConfig.themeMode == "OLED"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isOled) Color.Black else Color(0xFF262626))
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = isBrowsingLocalMusic,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "LocalMusicBrowserTransition"
        ) { browsingLocal ->
            if (browsingLocal) {
                // Local Music Drilldown Sub-view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (viewingFolder != null) viewingFolder = null
                                else if (viewingAlbum != null) viewingAlbum = null
                                else if (viewingArtist != null) viewingArtist = null
                                else isBrowsingLocalMusic = false
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Local Music",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "${tracks.size} tracks • ${albums.size} albums • ${folders.size} folders",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if ((selectedTab == 1 || selectedTab == 2) && viewingAlbum == null && viewingArtist == null) {
                                IconButton(onClick = { isGridView = !isGridView }) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                        tint = accent
                                    )
                                }
                            }
                            IconButton(onClick = onRescanClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Rescan",
                                    tint = accent
                                )
                            }
                        }
                    }

                    // Tab Selector Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tabScrollState)
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pillShape = remember { ContinuousRoundedRectangle(GlassTokens.pillCornerRadius) }
                        tabs.forEachIndexed { index, tabTitle ->
                            val isSelected = selectedTab == index && viewingFolder == null && viewingAlbum == null && viewingArtist == null
                            Box(
                                modifier = Modifier
                                    .glassSurface(
                                        shape = pillShape,
                                        tintAlpha = if (isSelected) 0.85f else 0.35f,
                                        customTint = if (isSelected) accent.copy(alpha = 0.4f) else null
                                    )
                                    .pressScale(targetScale = 0.95f) {
                                        viewingFolder = null
                                        viewingAlbum = null
                                        viewingArtist = null
                                        selectedTab = index
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tabTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab & Drilldown Content
                    when {
                        viewingFolder != null -> {
                            val activeFolder = viewingFolder!!
                            val folderTracks = remember(tracks, activeFolder.path) {
                                tracks.filter { File(it.path).parent == activeFolder.path }
                            }
                            CollectionTracksDetailView(
                                title = activeFolder.name,
                                subtitle = "${folderTracks.size} tracks",
                                tracks = folderTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onBack = { viewingFolder = null },
                                onTrackClick = onTrackClick,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                qualityBadgeMode = qualityBadgeMode,
                                onInspectTrack = { inspectingTrack = it },
                                onAddToPlaylist = onAddToPlaylist,
                                onDownloadTrack = onDownloadTrack
                            )
                        }
                        viewingAlbum != null -> {
                            val activeAlbum = viewingAlbum!!
                            val albumTracks = remember(tracks, activeAlbum.id, activeAlbum.title) {
                                tracks.filter { it.albumId == activeAlbum.id || it.album.equals(activeAlbum.title, ignoreCase = true) }
                            }
                            CollectionTracksDetailView(
                                title = activeAlbum.title,
                                subtitle = "${activeAlbum.artist} • ${albumTracks.size} tracks",
                                tracks = albumTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onBack = { viewingAlbum = null },
                                onTrackClick = onTrackClick,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                qualityBadgeMode = qualityBadgeMode,
                                onInspectTrack = { inspectingTrack = it },
                                onAddToPlaylist = onAddToPlaylist,
                                onDownloadTrack = onDownloadTrack
                            )
                        }
                        viewingArtist != null -> {
                            val activeArtist = viewingArtist!!
                            val artistTracks = remember(tracks, activeArtist.id, activeArtist.name) {
                                tracks.filter { it.artistId == activeArtist.id || it.artist.equals(activeArtist.name, ignoreCase = true) }
                            }
                            CollectionTracksDetailView(
                                title = activeArtist.name,
                                subtitle = "${artistTracks.size} tracks • ${activeArtist.albumCount} albums",
                                tracks = artistTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onBack = { viewingArtist = null },
                                onTrackClick = onTrackClick,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                qualityBadgeMode = qualityBadgeMode,
                                onInspectTrack = { inspectingTrack = it },
                                onAddToPlaylist = onAddToPlaylist,
                                onDownloadTrack = onDownloadTrack
                            )
                        }
                        tracks.isEmpty() -> {
                            EmptyLibraryView(onRescanClick)
                        }
                        else -> {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "LibraryTabTransition"
                            ) { tab ->
                                when (tab) {
                                    0 -> SongsList(
                                        tracks = tracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        onTrackClick = onTrackClick,
                                        onPlayNext = onPlayNext,
                                        onAddToQueue = onAddToQueue,
                                        qualityBadgeMode = qualityBadgeMode,
                                        onInspectTrack = { inspectingTrack = it },
                                        onAddToPlaylist = onAddToPlaylist,
                                        onDownloadTrack = onDownloadTrack
                                    )
                                    1 -> AlbumsGrid(
                                        albums = albums,
                                        isGridView = isGridView,
                                        onAlbumClick = { viewingAlbum = it }
                                    )
                                    2 -> ArtistsList(
                                        artists = artists,
                                        isGridView = isGridView,
                                        onArtistClick = { viewingArtist = it }
                                    )
                                    3 -> FoldersList(
                                        folders = folders,
                                        onFolderClick = { viewingFolder = it }
                                    )
                                    4 -> DownloadedSongsList(
                                        tracks = tracks.filter { it.isDownloaded },
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        onTrackClick = onTrackClick,
                                        onPlayNext = onPlayNext,
                                        onAddToQueue = onAddToQueue,
                                        qualityBadgeMode = qualityBadgeMode,
                                        onInspectTrack = { inspectingTrack = it },
                                        onAddToPlaylist = onAddToPlaylist,
                                        onDownloadTrack = onDownloadTrack,
                                        onOpenDownloadQueue = onOpenDownloadQueue
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Root Library View (Matching Reference Screenshot 1)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Top Action Pill (Top-Right)
                    item(key = "lib_top_pill") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF1C1C1E))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { onOpenStats?.invoke() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = "History & Stats",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.25f))
                                        .clickable { onOpenSettings?.invoke() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "Profile & Settings",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Page Title: "Library" 32.sp ExtraBold white
                    item(key = "lib_title") {
                        Text(
                            text = "Library",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    // 3. Hero Listening Experience Carousel (Wired to Real Stats)
                    item(key = "lib_experience_carousel") {
                        val authState = com.nodeplayer.music.auth.YouTubeAccountManager.authState.collectAsState().value
                        val accountName = (authState as? com.nodeplayer.model.AuthState.LoggedIn)?.account?.name ?: "Node Listener"

                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(295.dp)
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    accent.copy(alpha = 0.45f),
                                                    Color(0xFF1E1B4B).copy(alpha = 0.85f),
                                                    Color(0xFF0F172A).copy(alpha = 0.95f)
                                                )
                                            )
                                        )
                                        .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                                        .clickable { onOpenStats?.invoke() }
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Top Row: Caption & Wave Icon
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "YOUR LISTENING EXPERIENCE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                            // Mini white wave logo
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.size(width = 3.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                                                Box(modifier = Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                                                Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                                            }
                                        }

                                        // Center: Big minutes count
                                        Column {
                                            Text(
                                                text = "$totalListeningMinutes",
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "MINUTES LISTENED",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }

                                        // Bottom Row: Username + Plays on left, Member Since on right
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = accountName.uppercase(),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$totalPlayCount plays",
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.65f)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "EXPERIENCE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.65f)
                                                )
                                                Text(
                                                    text = "STATS VIEW",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Quick Navigation Rows: Downloads & Local Music
                    item(key = "lib_nav_rows") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Downloads Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable {
                                        onOpenDownloads?.invoke() ?: run {
                                            isBrowsingLocalMusic = true
                                            selectedTab = 4
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Downloads",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Downloads",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Local Music Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable { isBrowsingLocalMusic = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = "Local Music",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Local Music",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (cachedMixtapeMap.isNotEmpty()) {
                        item(key = "lib_offline_mixtape_card") {
                            OfflineMixtapeHomeCard(
                                cachedSongCount = cachedMixtapeMap.size,
                                onPlayMixtape = {
                                    val mixtapeTracks = mixtapeManager?.getCachedTracks() ?: emptyList()
                                    if (mixtapeTracks.isNotEmpty()) {
                                        onTrackClick(mixtapeTracks.first(), mixtapeTracks)
                                    }
                                }
                            )
                        }
                    }

                    // 5. Playlists Section
                    item(key = "lib_playlists_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playlists",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Show all",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.clickable { isBrowsingLocalMusic = true; selectedTab = 3 }
                                )
                            }

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Card 1: New Playlist
                                item {
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1C1E))
                                                .clickable { onCreatePlaylist?.invoke("New playlist") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "New playlist",
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "New playlist",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Saved to YouTube Music",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Card 2: Import Spotify
                                item {
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1C1E))
                                                .clickable { onImportSpotifyPlaylists?.invoke() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Circular Spotify icon badge
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.GraphicEq,
                                                    contentDescription = "Spotify",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Import Spotify",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "From a playlist link",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Card 3: Import Playlist File (CSV / TXT)
                                item {
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1C1E))
                                                .clickable { onImportPlaylistFile?.invoke() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.FileDownload,
                                                    contentDescription = "Import",
                                                    tint = accent,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Import Playlist",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "From CSV or TXT file",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Remaining User Playlists
                                items(playlists.size) { pIndex ->
                                    val playlist = playlists[pIndex]
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1C1E))
                                                .clickable { onOpenPlaylist?.invoke(playlist) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(playlist.thumbnailUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = playlist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                                    contentDescription = playlist.name,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = playlist.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.trackCount} tracks",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Artists Section
                    item(key = "lib_artists_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Artists",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val displayArtists = artists.take(15)
                                items(displayArtists.size) { aIndex ->
                                    val artist = displayArtists[aIndex]
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1C1E))
                                                .clickable {
                                                    viewingArtist = artist
                                                    isBrowsingLocalMusic = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = artist.name,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = artist.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${artist.trackCount} songs",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. Subscriptions Section
                    item(key = "lib_subscriptions_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Subscriptions",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val sampleSubs = listOf(
                                    Triple("Alok", "7.22M subscribers", Color(0xFFDC2626)),
                                    Triple("LLusion", "1.9M subscribers", Color(0xFFDB2777)),
                                    Triple("BoyWithUke", "5.14M subscribers", Color(0xFF4F46E5))
                                )
                                items(sampleSubs.size) { sIdx ->
                                    val (name, subs, tintColor) = sampleSubs[sIdx]
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(tintColor.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name.take(2).uppercase(),
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = subs,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 8. Podcasts Section
                    item(key = "lib_podcasts_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Podcasts",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Column(modifier = Modifier.width(144.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF818CF8), Color(0xFFC084FC))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Podcasts,
                                                contentDescription = "Episodes for Later",
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Episodes for Later",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Your queued episodes",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
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
        }

        if (inspectingTrack != null) {
            val targetTrack = tracks.find { it.id == inspectingTrack!!.id } ?: inspectingTrack!!
            com.nodeplayer.music.ui.components.TrackDetailsSheet(
                track = targetTrack,
                audioOutputInfo = audioOutputInfo,
                onDismiss = { inspectingTrack = null },
                onAnalyzeSong = onAnalyzeTrack,
                isAnalyzing = analyzingTrackId == targetTrack.id
            )
        }
    }
}

@Composable
fun DownloadedSongsList(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    qualityBadgeMode: String = "DETAILED",
    onInspectTrack: (Track) -> Unit = {},
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onDownloadTrack: ((Track) -> Unit)? = null,
    onOpenDownloadQueue: () -> Unit = {}
) {
    val accent = LocalAccentColor.current
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Downloaded Tracks",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tracks downloaded for offline playback will appear here.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                LiquidButton(onClick = onOpenDownloadQueue) {
                    Icon(
                        imageVector = Icons.Rounded.Downloading,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Download Queue", color = Color.White)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tracks.size} downloaded tracks",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                LiquidButton(
                    onClick = onOpenDownloadQueue,
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Downloading,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Queue", color = Color.White, fontSize = 12.sp)
                }
            }

            SongsList(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = onTrackClick,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                qualityBadgeMode = qualityBadgeMode,
                onInspectTrack = onInspectTrack,
                onAddToPlaylist = onAddToPlaylist,
                onDownloadTrack = onDownloadTrack
            )
        }
    }
}

@Composable
fun SongsList(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    qualityBadgeMode: String = "DETAILED",
    onInspectTrack: (Track) -> Unit = {},
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onDownloadTrack: ((Track) -> Unit)? = null,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        com.nodeplayer.music.library.AudioAnalysisManager.setUserScrolling(listState.isScrollInProgress)
    }

    val context = LocalContext.current
    val songKeys = remember(tracks) { uniqueLazyTrackKeys(tracks, "lib_song") }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = tracks,
            key = { index, _ -> songKeys[index] },
            contentType = { _, _ -> "song_row" }
        ) { _, track ->
            val isCurrent = track.id == currentTrack?.id

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                onClick = { onTrackClick(track, tracks) },
                tintAlpha = if (isCurrent) 0.70f else 0.40f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val artworkRequest = remember(track.id, track.path, track.albumId, context) {
                        ImageRequest.Builder(context)
                            .data(track)
                            .size(144, 144)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(SONG_ART_SHAPE)
                            .border(
                                1.dp,
                                SONG_ART_BORDER_BRUSH,
                                SONG_ART_SHAPE
                            )
                            .background(Color(0xFF1E2130)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(24.dp)
                        )
                        AsyncImage(
                            model = artworkRequest,
                            contentDescription = track.album,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCurrent) accent else Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (qualityBadgeMode != "OFF") {
                                if (qualityBadgeMode == "DETAILED") {
                                    FormatBadge(track.formatBadgeText.ifBlank { track.mimeType.substringAfterLast("/").uppercase() }, Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    when (track.hiResStatus) {
                                        com.nodeplayer.model.HiResStatus.GENUINE -> {
                                            FormatBadge("Genuine", Color(0xFF10B981))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        com.nodeplayer.model.HiResStatus.LIKELY_LOSSY -> {
                                            FormatBadge("Lossy", Color(0xFFF59E0B))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        com.nodeplayer.model.HiResStatus.LIKELY_UPSAMPLED -> {
                                            FormatBadge("Upsampled", Color(0xFFEF4444))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        else -> {}
                                    }
                                    if (track.dynamicRangeScore != null) {
                                        FormatBadge("DR${track.dynamicRangeScore}", Color(0xFF818CF8))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                } else {
                                    if (track.isHiRes) {
                                        FormatBadge("Hi-Res", Color(0xFF10B981))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (track.isLossless) {
                                        FormatBadge("Lossless", Color(0xFF818CF8))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else {
                                        FormatBadge(track.formatBadgeText.ifBlank { "Audio" }, Color(0xFF94A3B8))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (track.dynamicRangeScore != null) {
                                        FormatBadge("DR${track.dynamicRangeScore}", Color(0xFF818CF8))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                            }
                            Text(
                                text = "${track.artist} • ${track.durationFormatted}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Song options",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E2130))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Next", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = accent)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = accent)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueue(track)
                                }
                            )
                            if (onAddToPlaylist != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = Color.White) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = accent)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist(track)
                                    }
                                )
                            }
                            if (onDownloadTrack != null && !track.isDownloaded) {
                                DropdownMenuItem(
                                    text = { Text("Download Song", color = Color.White) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Download, contentDescription = null, tint = accent)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDownloadTrack(track)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Track Details & Analysis", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onInspectTrack(track)
                                }
                            )
                        }
                    }

                    if (isCurrent) {
                        SoundWaveVisualizer(isPlaying = isPlaying, barColor = accent)
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersList(
    folders: List<MusicFolder>,
    onFolderClick: (MusicFolder) -> Unit,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        com.nodeplayer.music.library.AudioAnalysisManager.setUserScrolling(listState.isScrollInProgress)
    }

    val folderKeys = remember(folders) { uniqueLazyKeys(folders) { "fld_${it.path}" } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = folders,
            key = { index, _ -> folderKeys[index] },
            contentType = { _, _ -> "folder_row" }
        ) { _, folder ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                onClick = { onFolderClick(folder) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(FOLDER_ICON_SHAPE)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${folder.trackCount} songs • ${folder.path}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.60f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionTracksDetailView(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    qualityBadgeMode: String = "DETAILED",
    onInspectTrack: (Track) -> Unit = {},
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onDownloadTrack: ((Track) -> Unit)? = null
) {
    val accent = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tracks.isNotEmpty()) {
                Button(
                    onClick = { onTrackClick(tracks.first(), tracks) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(GlassTokens.pillCornerRadius)
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SongsList(
            tracks = tracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onTrackClick = onTrackClick,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            qualityBadgeMode = qualityBadgeMode,
            onInspectTrack = onInspectTrack,
            onAddToPlaylist = onAddToPlaylist,
            onDownloadTrack = onDownloadTrack
        )
    }
}

@Composable
fun FormatBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    isGridView: Boolean = true,
    onAlbumClick: (Album) -> Unit,
    hazeState: HazeState? = null
) {
    if (isGridView) {
        val context = LocalContext.current
        val albumKeys = remember(albums) { uniqueLazyKeys(albums) { "alb_${it.id}" } }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItemsIndexed(
                items = albums,
                key = { index, _ -> albumKeys[index] },
                contentType = { _, _ -> "album_grid_card" }
            ) { _, album ->
                val artworkRequest = remember(album.artworkUri, context) {
                    ImageRequest.Builder(context)
                        .data(album.artworkUri)
                        .size(256, 256)
                        .build()
                }
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    onClick = { onAlbumClick(album) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(ALBUM_GRID_SHAPE)
                                .background(Color(0xFF222433)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            )
                            AsyncImage(
                                model = artworkRequest,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.trackCount} songs",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        val context = LocalContext.current
        val albumKeys = remember(albums) { uniqueLazyKeys(albums) { "alb_${it.id}" } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = albums,
                key = { index, _ -> albumKeys[index] },
                contentType = { _, _ -> "album_list_row" }
            ) { _, album ->
                val artworkRequest = remember(album.artworkUri, context) {
                    ImageRequest.Builder(context)
                        .data(album.artworkUri)
                        .size(144, 144)
                        .build()
                }
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    onClick = { onAlbumClick(album) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(ALBUM_LIST_SHAPE)
                                .background(Color(0xFF222433)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                            AsyncImage(
                                model = artworkRequest,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${album.artist} • ${album.trackCount} songs",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsList(
    artists: List<Artist>,
    isGridView: Boolean = false,
    onArtistClick: (Artist) -> Unit,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    if (isGridView) {
        val artistKeys = remember(artists) { uniqueLazyKeys(artists) { "art_${it.id}" } }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItemsIndexed(
                items = artists,
                key = { index, _ -> artistKeys[index] },
                contentType = { _, _ -> "artist_grid_card" }
            ) { _, artist ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    onClick = { onArtistClick(artist) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${artist.trackCount} songs • ${artist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        val artistKeys = remember(artists) { uniqueLazyKeys(artists) { "art_${it.id}" } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = artists,
                key = { index, _ -> artistKeys[index] },
                contentType = { _, _ -> "artist_list_row" }
            ) { _, artist ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    onClick = { onArtistClick(artist) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = accent
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "${artist.trackCount} songs • ${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<com.nodeplayer.model.Playlist>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onPlaylistClick: (com.nodeplayer.model.Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportYouTubeClick: (() -> Unit)? = null,
    onImportSpotifyClick: (() -> Unit)? = null,
    onImportPlaylistClick: (() -> Unit)? = null
) {
    val accent = LocalAccentColor.current
    val filterOptions = PLAYLIST_FILTER_OPTIONS

    val filtered = remember(playlists, filter) {
        when (filter) {
            "Local" -> playlists.filter { it.source == "local" }
            "YouTube" -> playlists.filter { it.source == "youtube" }
            "Spotify" -> playlists.filter { it.source == "spotify" }
            else -> playlists
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filterOptions.forEach { opt ->
                    val isSel = filter == opt
                    Box(
                        modifier = Modifier
                            .clip(PLAYLIST_PILL_SHAPE)
                            .background(if (isSel) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                            .clickable { onFilterChange(opt) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = opt,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) accent else Color.White.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onImportYouTubeClick != null) {
                    Box(
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(GlassTokens.pillCornerRadius))
                            .background(accent.copy(alpha = 0.2f))
                            .clickable { onImportYouTubeClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync YouTube",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            )
                        }
                    }
                }

                if (onImportSpotifyClick != null) {
                    Box(
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(GlassTokens.pillCornerRadius))
                            .background(Color(0xFF1DB954).copy(alpha = 0.2f))
                            .clickable { onImportSpotifyClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Spotify",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1DB954)
                                )
                            )
                        }
                    }
                }

                if (onImportPlaylistClick != null) {
                    Box(
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(GlassTokens.pillCornerRadius))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onImportPlaylistClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Import",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(ContinuousRoundedRectangle(GlassTokens.pillCornerRadius))
                        .background(accent)
                        .clickable { onCreatePlaylistClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (filter == "All") "No playlists created yet" else "No $filter playlists found",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                )
            }
        } else {
            val playlistKeys = remember(filtered) {
                uniqueLazyKeys(filtered) { "pl_${it.source}_${it.id}" }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { index, _ -> playlistKeys[index] },
                    contentType = { _, _ -> "playlist_row" }
                ) { _, playlist ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = { onPlaylistClick(playlist) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(PLAYLIST_ICON_SHAPE)
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (playlist.source) {
                                        "youtube" -> Icons.Rounded.Cloud
                                        "spotify" -> Icons.Rounded.MusicNote
                                        else -> Icons.Rounded.Folder
                                    },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${playlist.trackCount} tracks • ${playlist.source.replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryView(onRescanClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Music Found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add audio files to your device storage or rescan your library.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRescanClick,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)
            ) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Storage")
            }
        }
    }
}
