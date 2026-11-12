package com.nodeplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodeplayer.model.Playlist
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.components.PlatformArtworkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Categories contain playlist cards; only a selected playlist opens a song list. */
@Composable
fun CategoryPlaylistsView(
    title: String,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    loadingPlaylistId: String? = null
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack) { Text("‹  Categories") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
        if (playlists.isEmpty()) Text("No playlists available in this category.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(playlists.distinctBy { it.externalId ?: it.id.toString() }, key = { it.externalId ?: it.id.toString() }) { playlist ->
                Column(Modifier.clickable(enabled = loadingPlaylistId == null) { onOpenPlaylist(playlist) }) {
                    var artwork by remember(playlist.thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(playlist.thumbnailUrl) {
                        artwork = playlist.thumbnailUrl?.let { url ->
                            withContext(Dispatchers.IO) { PlatformArtworkProvider.loadArtwork(url, 384) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        val image = artwork
                        if (image != null) Image(image, playlist.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(40.dp))
                        if (loadingPlaylistId == playlist.externalId) CircularProgressIndicator(Modifier.size(32.dp))
                    }
                    Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
