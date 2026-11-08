package com.nodeplayer.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
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
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.common.AppModeState
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.SearchFilter
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null
) {
    var query by remember { mutableStateOf("") }
    val accent = LocalAccentColor.current
    val isOnlineMode by AppModeState.isOnlineMode.collectAsState()
    var searchOnlineTab by remember(isOnlineMode) { mutableStateOf(isOnlineMode) }

    val searchScope = rememberCoroutineScope()
    var remoteTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var onlineTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    val hub = com.nodeplayer.music.service.PlaybackService.instance?.nodeConnectHub
    val innerTubeClient = remember { InnerTubeClient() }

    // Search query debounced effect for YouTube / NodeConnect
    LaunchedEffect(query, searchOnlineTab, isOnlineMode) {
        if (query.isBlank()) {
            remoteTracks = emptyList()
            onlineTracks = emptyList()
            isSearchingOnline = false
            onlineError = null
            return@LaunchedEffect
        }

        if (searchOnlineTab && isOnlineMode) {
            isSearchingOnline = true
            onlineError = null
            delay(350) // Debounce 350ms
            try {
                val res = innerTubeClient.search(query.trim(), SearchFilter.SONGS)
                if (res.isSuccess) {
                    onlineTracks = res.getOrThrow().songs
                } else {
                    onlineError = res.exceptionOrNull()?.message ?: "Search failed"
                }
            } catch (e: Exception) {
                onlineError = e.message
            } finally {
                isSearchingOnline = false
            }
        } else if (!searchOnlineTab && hub != null) {
            val list = mutableListOf<Track>()
            hub.activeConnections.forEach { conn ->
                try {
                    val res = hub.libraryService.queryRemoteLibrary(conn, query = query, limit = 30)
                    if (res != null) {
                        list.addAll(res.tracks)
                        for (remoteTrack in res.tracks) {
                            searchScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val artBytes = hub.libraryService.fetchRemoteArtwork(conn, remoteTrack.id)
                                    if (artBytes != null && artBytes.isNotEmpty()) {
                                        com.nodeplayer.ui.components.ArtworkCache.putBytes(remoteTrack.path, artBytes)
                                        com.nodeplayer.ui.components.ArtworkCache.putBytes(remoteTrack.id.toString(), artBytes)
                                        com.nodeplayer.ui.components.ArtworkCache.putBytes(remoteTrack.title + remoteTrack.artist, artBytes)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            remoteTracks = list
        } else {
            remoteTracks = emptyList()
        }
    }

    val filteredTracks = remember(query, tracks, remoteTracks, onlineTracks, searchOnlineTab) {
        if (query.isBlank()) {
            emptyList()
        } else if (searchOnlineTab) {
            onlineTracks
        } else {
            val q = query.trim().lowercase()
            val local = tracks.filter { track ->
                track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q)
            }.take(30)
            (local + remoteTracks).distinctBy { it.id to it.title }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12141A).copy(alpha = 0.95f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        shape = ContinuousRoundedRectangle(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Search",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search input field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { 
                    Text(
                        if (searchOnlineTab) "Search YouTube Music..." else "Search songs, artists, albums...", 
                        color = Color.White.copy(alpha = 0.45f)
                    ) 
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = accent)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                },
                singleLine = true,
                shape = ContinuousRoundedRectangle(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Segmented Tab Selector if Online Mode is enabled
            if (isOnlineMode) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(ContinuousCapsule)
                        .background(Color(0xFF14171A).copy(alpha = 0.65f))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(if (!searchOnlineTab) accent.copy(alpha = 0.32f) else Color.Transparent)
                            .clickable { searchOnlineTab = false }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Local Library",
                            fontSize = 11.5.sp,
                            fontWeight = if (!searchOnlineTab) FontWeight.Bold else FontWeight.Normal,
                            color = if (!searchOnlineTab) Color.White else Color.White.copy(alpha = 0.60f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(if (searchOnlineTab) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color.Transparent)
                            .clickable { searchOnlineTab = true }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "YouTube Music",
                            fontSize = 11.5.sp,
                            fontWeight = if (searchOnlineTab) FontWeight.Bold else FontWeight.Normal,
                            color = if (searchOnlineTab) Color(0xFFE0F7FA) else Color.White.copy(alpha = 0.60f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search results list
            if (query.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchOnlineTab) "Search millions of tracks on YouTube Music" else "Type to search your library of ${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.4f))
                    )
                }
            } else if (isSearchingOnline) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (onlineError != null) "Error: $onlineError" else "No tracks found for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTracks, key = { "${it.path}_${it.id}" }) { track ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTrackClick(track)
                                    onDismiss()
                                },
                            
                            tintAlpha = 0.35f
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF222433)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    AsyncImage(
                                        model = track,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (track.path.startsWith("nodeconnect://")) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "NODE CONNECT",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF06B6D4)
                                                ),
                                                modifier = Modifier
                                                    .background(Color(0xFF06B6D4).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        } else if (track.path.startsWith("nodeplayer://youtube/")) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "YOUTUBE",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF00E5FF)
                                                ),
                                                modifier = Modifier
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        ),
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
}
