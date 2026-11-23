package com.nodeplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nodeplayer.model.MoodGenre
import com.nodeplayer.model.Playlist
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.rememberDockScrollConnection
import kotlinx.coroutines.launch

/**
 * Mood & Genre Category Playlists Screen.
 * Shows a grid of playlists belonging to the selected category.
 * Tapping a playlist fires onPlaylistClick.
 */
@Composable
fun CategoryPlaylistsScreen(
    category: MoodGenre,
    innerTubeClient: InnerTubeClient,
    onPlaylistClick: (Playlist) -> Unit,
    onBack: () -> Unit,
    dockScrollController: DockScrollController? = null,
    modifier: Modifier = Modifier
) {
    var playlists by remember(category.browseId, category.params) { mutableStateOf<List<Playlist>?>(null) }
    var isLoading by remember(category.browseId, category.params) { mutableStateOf(true) }
    var errorMessage by remember(category.browseId, category.params) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val loadPlaylists: () -> Unit = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            innerTubeClient.browseCategoryShelves(category.browseId, category.params)
                .onSuccess { list ->
                    playlists = list
                    isLoading = false
                }
                .onFailure { e ->
                    errorMessage = e.message ?: "Failed to load playlists"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(category.browseId, category.params) {
        loadPlaylists()
    }

    val gridState = rememberLazyGridState()
    val scrollConnection = dockScrollController?.let { rememberDockScrollConnection(it) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "YouTube Music Category",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            // Body
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C5CE7))
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage ?: "Error loading category",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = loadPlaylists,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                playlists.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists found in this category",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    val bottomPadding = LocalBottomBarPadding.current + 88.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playlists.orEmpty(), key = { it.id }) { playlist ->
                            CategoryPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = ContinuousRoundedRectangle(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        // Thumbnail with fallback
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Color(0xFF1E2235)),
            contentAlignment = Alignment.Center
        ) {
            val artworkUrl = playlist.thumbnailUrl
                ?: playlist.externalId?.let { extId ->
                    "https://i.ytimg.com/vi/$extId/hqdefault.jpg"
                }
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        // Log image load error for diagnostics
                        com.nodeplayer.common.logging.NodeLog.w("CategoryPlaylistCard", "Failed to load art for ${playlist.name}: $artworkUrl")
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                fontSize = 14.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (playlist.trackCount > 0) "${playlist.trackCount} songs" else "Playlist",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}
