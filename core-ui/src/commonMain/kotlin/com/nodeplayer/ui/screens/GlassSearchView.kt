package com.nodeplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.SharedInnerTubeProvider
import com.nodeplayer.network.innertube.YouTubeSearchResult
import com.nodeplayer.network.search.CategorizedSearchResults
import com.nodeplayer.network.search.SearchCategorizer
import com.nodeplayer.network.search.SearchCategory
import com.nodeplayer.network.search.SearchHistoryManager
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassSearchBar
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SEARCH_CAPSULE_SHAPE = ContinuousCapsule()
private val SEARCH_ROW_SHAPE = ContinuousRoundedRectangle(12.dp)
private val SEARCH_THUMB_SHAPE = ContinuousRoundedRectangle(10.dp)
private val TRENDING_SEARCH_TAGS = listOf("Top Hits", "Lo-Fi Beats", "Rock Classics", "Synthwave", "Jazz Piano", "Acoustic Pop")

/**
 * Full-bleed liquid-glass Search & Browse overlay screen.
 * Features:
 * - Animated top [GlassSearchBar] with immediate auto-focus.
 * - Pinned horizontal glass filter chips: All, Songs, Artists, Albums, Playlists.
 * - Pre-search recent search history tags with individual deletion and clear-all.
 * - Debounced query execution (~300ms) with client-side instant categorization.
 * - Tap-to-play on songs with automatic queueing of remaining results (`SRCH-03`).
 * - Clean 'No results found' glass card (`D-07`).
 */
@Composable
fun GlassSearchView(
    initialQuery: String = "",
    onDismiss: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (Track) -> Unit = {},
    onArtistClick: (Track) -> Unit = {},
    onPlaylistClick: (Track) -> Unit = {},
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scope = rememberCoroutineScope()
    val historyManager = remember { SearchHistoryManager() }
    val recentQueries by historyManager.queriesState.collectAsState()

    var query by remember { mutableStateOf(initialQuery) }
    var activeCategory by remember { mutableStateOf(SearchCategory.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var rawResult by remember { mutableStateOf<YouTubeSearchResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Instant client-side categorized results
    val categorized = remember(rawResult, activeCategory) {
        if (rawResult != null) {
            SearchCategorizer.categorize(rawResult!!, activeCategory)
        } else {
            CategorizedSearchResults(activeCategory = activeCategory, displayItems = emptyList())
        }
    }

    // Debounced search query trigger
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            rawResult = null
            isLoading = false
            return@LaunchedEffect
        }

        delay(300L) // 300ms debounce
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val innerClient = SharedInnerTubeProvider.get()
                val result = innerClient.search(trimmed)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        rawResult = result.getOrNull()
                        historyManager.addQuery(trimmed)
                        errorMessage = null
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Search failed"
                    }
                    isLoading = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    errorMessage = t.message ?: "Network error"
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Pinned Search Bar
            GlassSearchBar(
                query = query,
                onQueryChange = { query = it },
                visible = true,
                onDismiss = onDismiss,
                backdrop = backdrop,
                placeholder = "Search songs, albums, artists..."
            )

            // Pinned Horizontal Filter Chips Row
            SearchFilterChipsRow(
                selectedCategory = activeCategory,
                onCategorySelect = { activeCategory = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            val resultKeys = remember(categorized.displayItems, activeCategory) {
                uniqueLazyTrackKeys(categorized.displayItems, "search_${activeCategory.name}")
            }

            // Scrollable Search Body
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 10.dp,
                    bottom = LocalBottomBarPadding.current + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (query.isBlank()) {
                    // Pre-Search State: Recent Search Queries
                    if (recentQueries.isNotEmpty()) {
                        item(key = "recent_header", contentType = "search_section_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECENT SEARCHES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                )
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    ),
                                    modifier = Modifier
                                        .clip(SEARCH_CAPSULE_SHAPE)
                                        .clickable { historyManager.clearAll() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        item(key = "recent_row", contentType = "search_recent_row") {
                            val recentKeys = remember(recentQueries) {
                                uniqueLazyKeys(recentQueries) { "recent_$it" }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    items = recentQueries,
                                    key = { index, _ -> recentKeys[index] },
                                    contentType = { _, _ -> "recent_query_chip" }
                                ) { _, recentQuery ->
                                    RecentQueryChip(
                                        query = recentQuery,
                                        onClick = { query = recentQuery },
                                        onRemove = { historyManager.removeQuery(recentQuery) }
                                    )
                                }
                            }
                        }
                    }

                    // Trending suggestions tags
                    item(key = "trending_section", contentType = "search_trending_row") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "TRENDING TOPICS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    items = TRENDING_SEARCH_TAGS,
                                    key = { "tag_$it" },
                                    contentType = { "trending_tag" }
                                ) { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(SEARCH_CAPSULE_SHAPE)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), SEARCH_CAPSULE_SHAPE)
                                            .clickable { query = tag }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = tag, color = Color.White, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                } else if (isLoading) {
                    // Shimmer skeleton results
                    items(
                        count = 6,
                        key = { index -> "search_shimmer_$index" },
                        contentType = { "search_shimmer" }
                    ) {
                        SearchItemShimmer()
                    }
                } else if (!categorized.isEmpty) {
                    // Results list: categorized items
                    itemsIndexed(
                        items = categorized.displayItems,
                        key = { index, _ -> resultKeys[index] },
                        contentType = { _, _ -> "search_result_row" }
                    ) { _, track ->
                        SearchResultRow(
                            track = track,
                            onClick = {
                                val isPlaylist = activeCategory == SearchCategory.PLAYLISTS ||
                                    (activeCategory == SearchCategory.ALL && track in categorized.playlists) ||
                                    track.mimeType == "playlist" ||
                                    track.formatDescription.contains("playlist", ignoreCase = true)
                                val isAlbum = (activeCategory == SearchCategory.ALBUMS || (activeCategory == SearchCategory.ALL && track in categorized.albums))
                                val isArtist = (activeCategory == SearchCategory.ARTISTS || (activeCategory == SearchCategory.ALL && track in categorized.artists))
                                if (isPlaylist) {
                                    onPlaylistClick(track)
                                } else if (isAlbum) {
                                    onAlbumClick(track)
                                } else if (isArtist) {
                                    onArtistClick(track)
                                } else {
                                    // Song tap: play immediately and queue remaining search songs
                                    val songQueue = categorized.songs.ifEmpty { categorized.displayItems.filter { it.durationMs > 0L } }
                                    onPlayTrack(track, songQueue)
                                }
                            }
                        )
                    }
                } else {
                    // Empty state: No results found
                    item(key = "no_results", contentType = "search_no_results") {
                        NoResultsCard(query = query, onClear = { query = "" })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChipsRow(
    selectedCategory: SearchCategory,
    onCategorySelect: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val categories = remember { SearchCategory.values() }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(
            items = categories,
            key = { "filter_${it.name}" },
            contentType = { "search_filter_chip" }
        ) { category ->
            val isSelected = selectedCategory == category
            val chipShape = SEARCH_CAPSULE_SHAPE

            Box(
                modifier = Modifier
                    .clip(chipShape)
                    .background(if (isSelected) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
                        shape = chipShape
                    )
                    .clickable { onCategorySelect(category) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun RecentQueryChip(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val chipShape = remember { ContinuousCapsule() }

    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), chipShape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = query,
            fontSize = 12.5.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(18.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    track: Track,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowShape = SEARCH_ROW_SHAPE

    val accent = LocalAccentColor.current
    val badgeLabel = remember(track) {
        when {
            track.mimeType == "playlist" || track.formatDescription.contains("playlist", ignoreCase = true) -> "Playlist"
            track.mimeType == "album" || track.formatDescription.contains("album", ignoreCase = true) -> "Album"
            track.mimeType == "artist" || track.formatDescription.contains("artist", ignoreCase = true) -> "Artist"
            else -> null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SEARCH_THUMB_SHAPE)
        ) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10.dp,
                targetSizePx = 144
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badgeLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(SEARCH_CAPSULE_SHAPE)
                            .background(accent.copy(alpha = 0.20f))
                            .border(0.6.dp, accent.copy(alpha = 0.40f), SEARCH_CAPSULE_SHAPE)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.artist.ifEmpty { track.album.ifEmpty { "YouTube Music" } },
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (track.durationMs > 0L) {
            Text(
                text = track.durationFormatted,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (track.durationMs > 0L) Icons.Rounded.PlayArrow else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NoResultsCard(query: String, onClear: () -> Unit) {
    val cardShape = remember { ContinuousRoundedRectangle(16.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No results found for \"$query\"",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = "Check spelling or try a different song or artist title",
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                shape = ContinuousCapsule()
            ) {
                Text("Clear search", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SearchItemShimmer() {
    val rowShape = SEARCH_ROW_SHAPE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ContinuousRoundedRectangle(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(13.dp)
                    .clip(ContinuousCapsule())
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(ContinuousCapsule())
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
    }
}
