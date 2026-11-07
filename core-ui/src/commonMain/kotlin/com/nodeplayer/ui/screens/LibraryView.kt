package com.nodeplayer.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.components.LiquidSegmentedControl
import com.nodeplayer.ui.components.SoundWaveVisualizer
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.theme.LocalAccentColor

@Composable
fun LibraryView(
    tracks: List<Track>,
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    folders: List<MusicFolder> = emptyList(),
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRescanClick: () -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    remoteTracks: List<Track> = emptyList(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<MusicFolder?>(null) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")

    val filteredTracks = remember(tracks, remoteTracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else {
            val local = tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
            val remote = remoteTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
            local + remote
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search & Rescan Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search songs, artists, albums...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accent) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        LiquidIconButton(
                            icon = Icons.Rounded.Clear,
                            onClick = { onSearchChange("") },
                            contentDescription = "Clear",
                            size = 36.dp,
                            lighterStyle = true
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            LiquidIconButton(
                icon = Icons.Rounded.Refresh,
                onClick = onRescanClick,
                contentDescription = "Rescan",
                size = 48.dp,
                tint = accent,
                lighterStyle = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs: Songs, Albums, Artists, Folders + Grid/List toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidSegmentedControl(
                items = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.weight(1f)
            ) { tab ->
                val count = when (tabs.indexOf(tab)) {
                    0 -> filteredTracks.size
                    1 -> albums.size
                    2 -> artists.size
                    else -> folders.size
                }
                if (count > 0) "$tab ($count)" else tab
            }

            Spacer(modifier = Modifier.width(8.dp))

            LiquidIconButton(
                icon = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                onClick = { isGridView = !isGridView },
                contentDescription = "Toggle Grid View",
                size = 48.dp,
                lighterStyle = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        when (selectedTab) {
            0 -> {
                // Songs Tab
                if (filteredTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No tracks matching \"$searchQuery\"" else "Library is empty. Click refresh to scan your music folder.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val isCurrent = currentTrack?.id == track.id
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                isDark = true,
                                onClick = { onTrackClick(track, filteredTracks) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isCurrent) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent && isPlaying) {
                                            SoundWaveVisualizer(isPlaying = true, barColor = accent, maxHeight = 30.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = if (isCurrent) accent else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) accent else Color.White
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val isCurrent = currentTrack?.id == track.id
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                isDark = true,
                                onClick = { onTrackClick(track, filteredTracks) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtwork(
                                        track = track,
                                        modifier = Modifier.size(42.dp),
                                        cornerRadius = 8.dp
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent) accent else Color.White
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (track.isHiRes || track.sampleRate >= 88200 || track.bitDepth > 16) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                GlassBadge(
                                                    text = if (track.bitDepth > 16) "${track.bitDepth}b/${track.sampleRate / 1000}k" else "HI-RES",
                                                    color = Color(0xFFF59E0B)
                                                )
                                            } else if (track.isLossless) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                GlassBadge(text = "LOSSLESS", color = accent)
                                            }
                                            if (track.path.startsWith("nodeconnect://")) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                GlassBadge(text = "NODE CONNECT", color = Color(0xFF06B6D4))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "${track.artist} • ${track.album}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = track.durationFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Albums Tab
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val albumTracks = tracks.filter { it.albumId == album.id || it.album.equals(album.title, ignoreCase = true) }
                                if (albumTracks.isNotEmpty()) onTrackClick(albumTracks.first(), albumTracks)
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Album, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(album.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${album.artist} • ${album.trackCount} tracks", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
            2 -> {
                // Artists Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(artists, key = { it.id }) { artist ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val artistTracks = tracks.filter { it.artistId == artist.id || it.artist.equals(artist.name, ignoreCase = true) }
                                if (artistTracks.isNotEmpty()) onTrackClick(artistTracks.first(), artistTracks)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = accent)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(artist.name, fontWeight = FontWeight.Bold)
                                    Text("${artist.trackCount} tracks", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                // Folders Tab: Drill down to folder songs or show folder list
                val activeFolder = selectedFolder
                if (activeFolder != null) {
                    val folderTracks = remember(tracks, activeFolder) {
                        tracks.filter { it.path.startsWith(activeFolder.path) }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Folder Header with Back button and Play All
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                LiquidIconButton(
                                    icon = Icons.Rounded.ArrowBack,
                                    onClick = { selectedFolder = null },
                                    contentDescription = "Back to Folders",
                                    size = 42.dp,
                                    lighterStyle = true
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = activeFolder.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folderTracks.size} tracks • ${activeFolder.path}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f)),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (folderTracks.isNotEmpty()) {
                                LiquidButton(
                                    onClick = { onTrackClick(folderTracks.first(), folderTracks) },
                                    lighterStyle = true
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play All", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(folderTracks, key = { it.id }) { track ->
                                val isCurrent = currentTrack?.id == track.id
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    isDark = true,
                                    onClick = { onTrackClick(track, folderTracks) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TrackArtwork(
                                            track = track,
                                            modifier = Modifier.size(42.dp),
                                            cornerRadius = 8.dp
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent) accent else Color.White
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${track.artist} • ${track.album}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (track.isLossless) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            GlassBadge(text = "LOSSLESS", color = accent)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = track.durationFormatted,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(folders, key = { it.path }) { folder ->
                            val folderSongCount = remember(tracks, folder.path) {
                                tracks.count { it.path.startsWith(folder.path) }
                            }
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedFolder = folder }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folder.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "$folderSongCount tracks • ${folder.path}",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Folder", tint = Color.White.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
