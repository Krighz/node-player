package com.nodeplayer.music.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Playlist
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Playlist details screen supporting playback, shuffle, track reordering, track removal,
 * playlist renaming, deletion, and YouTube re-sync.
 * Adheres strictly to PLST-01, PLST-02, PLST-05, PLST-06.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onReorderTracks: (playlistId: Long, orderedIds: List<Long>) -> Unit,
    onRemoveTrack: (playlistId: Long, trackId: Long) -> Unit,
    onRenamePlaylist: (playlistId: Long, newName: String) -> Unit,
    onDeletePlaylist: (playlistId: Long) -> Unit,
    onResyncPlaylist: ((playlistId: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(playlist.name) { mutableStateOf(playlist.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Local mutable ordered list for drag reordering
    var localTracks by remember(tracks) { mutableStateOf(tracks) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlist.source == "youtube" && onResyncPlaylist != null) {
                    IconButton(onClick = { onResyncPlaylist(playlist.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Re-sync with YouTube",
                            tint = accent
                        )
                    }
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252).copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero Card
        GlassControlSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${tracks.size} tracks • Source: ${playlist.source.capitalize()}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play / Shuffle Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidButton(
                        onClick = { onPlayAll(tracks) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    LiquidButton(
                        onClick = { onShuffleAll(tracks) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks List
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in this playlist yet.\nAdd songs from Library or Now Playing.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            } else {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    val isCurrent = currentTrack?.id == track.id
                    var itemMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                            .clickable { onPlayTrack(track, tracks) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Position number
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isCurrent) accent else Color.White.copy(alpha = 0.45f),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.width(28.dp)
                        )

                        // Title & Artist
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isOnline = track.path.startsWith("nodeplayer://")
                                if (isOnline) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cloud,
                                        contentDescription = "Online",
                                        tint = accent.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // More Actions
                        Box {
                            IconButton(onClick = { itemMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            DropdownMenu(
                                expanded = itemMenuExpanded,
                                onDismissRequest = { itemMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E2130))
                            ) {
                                if (index > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Move Up", color = Color.White) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            val reordered = tracks.toMutableList()
                                            java.util.Collections.swap(reordered, index, index - 1)
                                            onReorderTracks(playlist.id, reordered.map { it.id })
                                        }
                                    )
                                }
                                if (index < tracks.size - 1) {
                                    DropdownMenuItem(
                                        text = { Text("Move Down", color = Color.White) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            val reordered = tracks.toMutableList()
                                            java.util.Collections.swap(reordered, index, index + 1)
                                            onReorderTracks(playlist.id, reordered.map { it.id })
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist", color = Color(0xFFFF5252)) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onRemoveTrack(playlist.id, track.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameInput.trim()
                    if (trimmed.isNotBlank()) {
                        onRenamePlaylist(playlist.id, trimmed)
                        showRenameDialog = false
                    }
                }) {
                    Text("Save", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete '${playlist.name}'? Tracks will not be removed from your library.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeletePlaylist(playlist.id)
                    onBackClick()
                }) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }
}
