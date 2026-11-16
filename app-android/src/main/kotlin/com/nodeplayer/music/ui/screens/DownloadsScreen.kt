@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nodeplayer.music.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.data.AndroidDownloadsRepository
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%d KB".format(bytes / 1024)
        else -> "$bytes B"
    }
}

enum class DownloadSortOrder(val label: String) {
    RECENT("Recently Added"),
    TITLE("Title"),
    ARTIST("Artist"),
    SIZE("Size")
}

@Composable
fun DownloadsScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onOpenDownloadQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember {
        AndroidDownloadsRepository(context, NodePlayerApplication.instance.database)
    }

    val downloadedTracksFlow = remember { repo.observeDownloaded() }
    val downloadedItems by downloadedTracksFlow.collectAsState(initial = emptyList())

    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(DownloadSortOrder.RECENT) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var optionsTrack by remember { mutableStateOf<Track?>(null) }

    // Map DownloadedTrack to domain Track model
    val tracks = remember(downloadedItems) {
        downloadedItems.map { item ->
            Track(
                id = item.id.toLongOrNull() ?: item.filePath.hashCode().toLong(),
                title = item.title,
                artist = item.artist,
                album = item.album ?: "",
                durationMs = item.durationMs,
                path = item.filePath,
                size = item.sizeBytes,
                dateAdded = item.downloadedAt
            )
        }
    }

    val filteredTracks = remember(tracks, searchQuery, sortOrder) {
        val q = searchQuery.trim().lowercase()
        val searched = if (q.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
            }
        }
        when (sortOrder) {
            DownloadSortOrder.RECENT -> searched.sortedByDescending { it.dateAdded }
            DownloadSortOrder.TITLE -> searched.sortedBy { it.title.lowercase() }
            DownloadSortOrder.ARTIST -> searched.sortedBy { it.artist.lowercase() }
            DownloadSortOrder.SIZE -> searched.sortedByDescending { it.size }
        }
    }

    val totalSizeBytes = remember(tracks) { tracks.sumOf { it.size } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Downloads",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${tracks.size} tracks • ${formatBytes(totalSizeBytes)}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Toggle Grid/List",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenDownloadQueue,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Downloading,
                            contentDescription = "Active Queue",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Search Bar & Sort Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search downloads...", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )

                // Sort Dropdown Pill
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Sort, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sortOrder.label, fontSize = 11.sp, color = Color.White)
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DownloadSortOrder.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label, color = if (sortOrder == opt) accent else Color.White) },
                                onClick = {
                                    sortOrder = opt
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body List / Grid
            if (filteredTracks.isEmpty()) {
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
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching downloads" else "No downloaded tracks yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloaded songs from search and playlists appear here for 100% offline playback.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        DownloadedTrackGridItem(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            accent = accent,
                            onClick = { onTrackClick(track, filteredTracks) },
                            onOptionsClick = { optionsTrack = track }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        DownloadedTrackListItem(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            accent = accent,
                            onClick = { onTrackClick(track, filteredTracks) },
                            onOptionsClick = { optionsTrack = track }
                        )
                    }
                }
            }
        }

        // 3-Dot Track Options Bottom Sheet
        if (optionsTrack != null) {
            val t = optionsTrack!!
            ModalBottomSheet(
                onDismissRequest = { optionsTrack = null },
                containerColor = Color(0xFF1E1E22),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(track = t, modifier = Modifier.size(48.dp), cornerRadius = 8.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                    ListItem(
                        headlineContent = { Text("Play Next") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White) },
                        modifier = Modifier.clickable {
                            onPlayNext(t)
                            optionsTrack = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Add to Queue") },
                        leadingContent = { Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White) },
                        modifier = Modifier.clickable {
                            onAddToQueue(t)
                            optionsTrack = null
                        }
                    )
                    if (onAddToPlaylist != null) {
                        ListItem(
                            headlineContent = { Text("Add to Playlist") },
                            leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White) },
                            modifier = Modifier.clickable {
                                onAddToPlaylist(t)
                                optionsTrack = null
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Remove Download", color = Color(0xFFFF5252)) },
                        leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                        modifier = Modifier.clickable {
                            trackToDelete = t
                            optionsTrack = null
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Delete Confirmation Dialog
        if (trackToDelete != null) {
            val t = trackToDelete!!
            AlertDialog(
                onDismissRequest = { trackToDelete = null },
                title = { Text("Remove Download", fontWeight = FontWeight.Bold) },
                text = { Text("Remove \"${t.title}\" from offline downloads? The file will be deleted from storage.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                repo.delete(t.id.toString())
                            }
                            trackToDelete = null
                        }
                    ) {
                        Text("Remove", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trackToDelete = null }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF222226),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun DownloadedTrackListItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            track = track,
            modifier = Modifier.size(44.dp),
            cornerRadius = 8.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("•", fontSize = 10.sp, color = Color.White.copy(alpha = 0.35f))
                Text(
                    text = formatBytes(track.size),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Offline Download",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(16.dp)
        )
        IconButton(onClick = onOptionsClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DownloadedTrackGridItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10.dp
            )
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOptionsClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
