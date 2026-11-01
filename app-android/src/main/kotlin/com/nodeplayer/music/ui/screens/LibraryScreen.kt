package com.nodeplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.SoundWaveVisualizer
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import java.io.File

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
    onAnalyzeTrack: ((Track) -> Unit)? = null,
    analyzingTrackId: Long? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(true) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    val accent = LocalAccentColor.current
    var viewingFolder by remember { mutableStateOf<MusicFolder?>(null) }
    var inspectingTrack by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Node Player",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "${tracks.size} tracks • ${folders.size} folders",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedTab == 1 || selectedTab == 2) {
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

        // Tab Selector Pills (Liquid Glass look)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTab == index && viewingFolder == null
                Box(
                    modifier = Modifier
                        .glassSurface(
                            hazeState = hazeState,
                            shape = RoundedCornerShape(GlassTokens.pillCornerRadius),
                            tintAlpha = if (isSelected) 0.85f else 0.35f,
                            customTint = if (isSelected) accent.copy(alpha = 0.4f) else null
                        )
                        .clickable {
                            viewingFolder = null
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

        // Tab Content
        if (viewingFolder != null) {
            val folderTracks = tracks.filter { File(it.path).parent == viewingFolder!!.path }
            FolderDetailView(
                folder = viewingFolder!!,
                tracks = folderTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onBack = { viewingFolder = null },
                onTrackClick = onTrackClick,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                hazeState = hazeState
            )
        } else if (tracks.isEmpty()) {
            EmptyLibraryView(onRescanClick)
        } else {
            when (selectedTab) {
                0 -> SongsList(
                    tracks = tracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    qualityBadgeMode = qualityBadgeMode,
                    onInspectTrack = { inspectingTrack = it },
                    hazeState = hazeState
                )
                1 -> AlbumsGrid(albums = albums, isGridView = isGridView, onAlbumClick = {}, hazeState = hazeState)
                2 -> ArtistsList(artists = artists, isGridView = isGridView, onArtistClick = {}, hazeState = hazeState)
                3 -> FoldersList(
                    folders = folders,
                    onFolderClick = { viewingFolder = it },
                    hazeState = hazeState
                )
            }
        }
    }

    if (inspectingTrack != null) {
        val targetTrack = tracks.find { it.id == inspectingTrack!!.id } ?: inspectingTrack!!
        com.nodeplayer.music.ui.components.TrackDetailsSheet(
            track = targetTrack,
            onDismiss = { inspectingTrack = null },
            hazeState = hazeState,
            onAnalyzeSong = onAnalyzeTrack,
            isAnalyzing = analyzingTrackId == targetTrack.id
        )
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
    hazeState: HazeState?
) {
    val accent = LocalAccentColor.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        com.nodeplayer.music.library.AudioAnalysisManager.setUserScrolling(listState.isScrollInProgress)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            val isCurrent = track.id == currentTrack?.id

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                onClick = { onTrackClick(track, tracks) },
                tintAlpha = if (isCurrent) 0.70f else 0.40f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork / Placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                            model = "content://media/external/audio/albumart/${track.albumId}",
                            contentDescription = track.album,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title & Artist
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
                                    // COMPACT
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

                    // 3-dot dropdown menu for song actions
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
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
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueue(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Track Details & Analysis", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onInspectTrack(track)
                                }
                            )
                        }
                    }

                    // Playing indicator soundwave bars
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
    hazeState: HazeState?
) {
    val accent = LocalAccentColor.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        com.nodeplayer.music.library.AudioAnalysisManager.setUserScrolling(listState.isScrollInProgress)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
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
                            .clip(RoundedCornerShape(12.dp))
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
fun FolderDetailView(
    folder: MusicFolder,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    hazeState: HazeState?
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
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
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
            hazeState = hazeState
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    hazeState = hazeState,
                    onClick = { onAlbumClick(album) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
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
                                model = album.artworkUri,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    hazeState = hazeState,
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
                                .clip(RoundedCornerShape(12.dp))
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
                                model = album.artworkUri,
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    hazeState = hazeState,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    hazeState = hazeState,
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
