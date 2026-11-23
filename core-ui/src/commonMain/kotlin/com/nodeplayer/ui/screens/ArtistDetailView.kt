package com.nodeplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.ArtistPageData
import com.nodeplayer.model.ArtistPlaylistItem
import com.nodeplayer.model.ArtistReleaseItem
import com.nodeplayer.model.ArtistSectionShowAll
import com.nodeplayer.model.ArtistVideoItem
import com.nodeplayer.model.SimilarArtistItem
import com.nodeplayer.model.Track
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.PlatformArtworkProvider
import com.nodeplayer.ui.components.TrackArtwork
import com.nodeplayer.ui.theme.CarbonZincPalette
import com.nodeplayer.ui.theme.LocalAccentColor

private val HERO_HEIGHT = 380.dp
private val GLASS_BORDER = Color.White.copy(alpha = 0.14f)
private val GLASS_FILL = Color.White.copy(alpha = 0.08f)
private val CARD_SHAPE_18 = ContinuousRoundedRectangle(18.dp)
private val CARD_SHAPE_14 = ContinuousRoundedRectangle(14.dp)
private val CAPSULE_SHAPE = ContinuousCapsule()

@Composable
fun ArtistDetailView(
    artistData: ArtistPageData?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onShuffleArtist: () -> Unit,
    onToggleFollow: () -> Unit,
    onSelectRelease: (ArtistReleaseItem) -> Unit = {},
    onSelectVideo: (ArtistVideoItem) -> Unit = {},
    onSelectPlaylist: (ArtistPlaylistItem) -> Unit = {},
    onSelectSimilarArtist: (SimilarArtistItem) -> Unit = {},
    onOptionsClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val listState = rememberLazyListState()

    var activeShowAll by remember { mutableStateOf<ArtistSectionShowAll?>(null) }

    // Header title opacity when scrolled
    val showHeaderTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 400
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonZincPalette.Background)
    ) {
        if (isLoading && artistData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
        } else if (artistData != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Item 0: Hero & Actions & Highlight Card
                item(key = "artist_hero", contentType = "hero") {
                    ArtistHeroSection(
                        artistData = artistData,
                        accentColor = accent,
                        onShuffle = onShuffleArtist,
                        onPlay = { artistData.topSongs.firstOrNull()?.let { onPlayTrack(it) } },
                        onToggleFollow = onToggleFollow,
                        onSelectRelease = onSelectRelease
                    )
                }

                // Item 1: Top songs (4-row horizontally paged)
                if (artistData.topSongs.isNotEmpty()) {
                    item(key = "artist_top_songs", contentType = "top_songs") {
                        ArtistTopSongsShelf(
                            songs = artistData.topSongs,
                            onPlayTrack = onPlayTrack,
                            onOptionsClick = onOptionsClick,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Top songs", artistData.topSongs, "songs")
                            }
                        )
                    }
                }

                // Item 2: Albums
                if (artistData.albums.isNotEmpty()) {
                    item(key = "artist_albums", contentType = "albums") {
                        ArtistReleasesShelf(
                            title = "Albums",
                            releases = artistData.albums,
                            onSelectRelease = onSelectRelease,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Albums", artistData.albums, "releases")
                            }
                        )
                    }
                }

                // Item 3: Singles & EPs
                if (artistData.singlesAndEPs.isNotEmpty()) {
                    item(key = "artist_singles", contentType = "singles") {
                        ArtistReleasesShelf(
                            title = "Singles & EPs",
                            releases = artistData.singlesAndEPs,
                            onSelectRelease = onSelectRelease,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Singles & EPs", artistData.singlesAndEPs, "releases")
                            }
                        )
                    }
                }

                // Item 4: Videos
                if (artistData.videos.isNotEmpty()) {
                    item(key = "artist_videos", contentType = "videos") {
                        ArtistVideosShelf(
                            title = "Videos",
                            videos = artistData.videos,
                            onSelectVideo = onSelectVideo,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Videos", artistData.videos, "videos")
                            }
                        )
                    }
                }

                // Item 5: Live performances
                if (artistData.livePerformances.isNotEmpty()) {
                    item(key = "artist_live", contentType = "live") {
                        ArtistVideosShelf(
                            title = "Live performances",
                            videos = artistData.livePerformances,
                            onSelectVideo = onSelectVideo,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Live performances", artistData.livePerformances, "videos")
                            }
                        )
                    }
                }

                // Item 6: Featured on
                if (artistData.featuredOn.isNotEmpty()) {
                    item(key = "artist_featured", contentType = "featured") {
                        ArtistPlaylistsShelf(
                            title = "Featured on",
                            playlists = artistData.featuredOn,
                            onSelectPlaylist = onSelectPlaylist,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Featured on", artistData.featuredOn, "playlists")
                            }
                        )
                    }
                }

                // Item 7: Playlists by Artist
                if (artistData.playlists.isNotEmpty()) {
                    item(key = "artist_playlists", contentType = "playlists") {
                        ArtistPlaylistsShelf(
                            title = "Playlists by ${artistData.artistName}",
                            playlists = artistData.playlists,
                            onSelectPlaylist = onSelectPlaylist,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Playlists by ${artistData.artistName}", artistData.playlists, "playlists")
                            }
                        )
                    }
                }

                // Item 8: Fans might also like
                if (artistData.similarArtists.isNotEmpty()) {
                    item(key = "artist_similar", contentType = "similar") {
                        ArtistSimilarShelf(
                            title = "Fans might also like",
                            artists = artistData.similarArtists,
                            onSelectArtist = onSelectSimilarArtist,
                            onShowAll = {
                                activeShowAll = ArtistSectionShowAll("Fans might also like", artistData.similarArtists, "artists")
                            }
                        )
                    }
                }

                // Item 9: About the artist card
                if (!artistData.bio.isNullOrBlank() || !artistData.subscribersText.isNullOrBlank()) {
                    item(key = "artist_about", contentType = "about") {
                        ArtistAboutCard(
                            artistData = artistData,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                        )
                    }
                }

                // Bottom padding
                item(key = "artist_bottom_spacing") {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Pinned Top Bar (Floating above scrollable content)
        ArtistTopBar(
            title = artistData?.artistName ?: "",
            showTitle = showHeaderTitle,
            onBack = onBack
        )

        // Show All Dialog / Sub-Sheet
        if (activeShowAll != null) {
            ArtistShowAllSheet(
                section = activeShowAll!!,
                onDismiss = { activeShowAll = null },
                onPlayTrack = onPlayTrack,
                onSelectRelease = onSelectRelease,
                onSelectVideo = onSelectVideo,
                onSelectPlaylist = onSelectPlaylist,
                onSelectSimilarArtist = onSelectSimilarArtist
            )
        }
    }
}

@Composable
private fun ArtistHeroSection(
    artistData: ArtistPageData,
    accentColor: Color,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onToggleFollow: () -> Unit,
    onSelectRelease: (ArtistReleaseItem) -> Unit
) {
    val heroUrl = artistData.heroImageUrl
    val heroBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        heroUrl
    ) {
        if (!heroUrl.isNullOrBlank()) {
            value = PlatformArtworkProvider.loadArtwork(heroUrl, 1080)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero Photo with Dark Multi-Stop Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT)
        ) {
            if (heroBitmap != null) {
                Image(
                    bitmap = heroBitmap!!,
                    contentDescription = artistData.artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF26262B), CarbonZincPalette.Background)
                            )
                        )
                )
            }

            // Multi-stop fade to dark
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.50f to Color(0xFF09090B).copy(alpha = 0.45f),
                                0.82f to Color(0xFF09090B).copy(alpha = 0.88f),
                                1.0f to Color(0xFF09090B)
                            )
                        )
                    )
            )

            // Centered Artist Name at bottom of hero
            Text(
                text = artistData.artistName,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Trio: Shuffle (48.dp), Solid White Play (64.dp), Follow/Star (48.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Shuffle button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onShuffle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            // Center: Solid White Large Play button (64.dp)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            // Right: Follow / Star button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (artistData.isFollowed) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f)
                    )
                    .border(
                        1.dp,
                        if (artistData.isFollowed) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .clickable { onToggleFollow() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (artistData.isFollowed) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Follow",
                    tint = if (artistData.isFollowed) accentColor else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Latest Release Highlight Card
        val rel = artistData.latestRelease
        if (rel != null) {
            val relArtworkUrl = rel.artworkUrl
            val releaseArt by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null,
                relArtworkUrl
            ) {
                if (!relArtworkUrl.isNullOrBlank()) {
                    value = PlatformArtworkProvider.loadArtwork(relArtworkUrl, 300)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(CARD_SHAPE_18)
                    .background(GLASS_FILL)
                    .border(1.dp, GLASS_BORDER, CARD_SHAPE_18)
                    .clickable { onSelectRelease(rel) }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Release Cover (68.dp with 12.dp rounded corners)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                    ) {
                        if (releaseArt != null) {
                            Image(
                                bitmap = releaseArt!!,
                                contentDescription = rel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${rel.type} • ${rel.year.ifBlank { "Latest" }}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Text(
                            text = rel.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Recent Release",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                    }

                    // Plus / Save Circle Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Save",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button (40.dp circle)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Animated Scrolled Title
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Capsule (Share + Profile)
        Box(
            modifier = Modifier
                .clip(CAPSULE_SHAPE)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CAPSULE_SHAPE)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistTopSongsShelf(
    songs: List<Track>,
    onPlayTrack: (Track) -> Unit,
    onOptionsClick: (Track) -> Unit,
    onShowAll: () -> Unit
) {
    val songColumns = remember(songs) { songs.chunked(4) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        ShelfHeader(title = "Top songs", onShowAll = onShowAll)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = songColumns,
                key = { col -> col.firstOrNull()?.id ?: 0L }
            ) { column ->
                Column(
                    modifier = Modifier.width(300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    column.forEach { track ->
                        TopSongItem(
                            track = track,
                            onPlay = { onPlayTrack(track) },
                            onOptions = { onOptionsClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSongItem(
    track: Track,
    onPlay: () -> Unit,
    onOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPlay() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            TrackArtwork(track = track, modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOptions, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ArtistReleasesShelf(
    title: String,
    releases: List<ArtistReleaseItem>,
    onSelectRelease: (ArtistReleaseItem) -> Unit,
    onShowAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        ShelfHeader(title = title, onShowAll = onShowAll)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = releases,
                key = { it.id }
            ) { rel ->
                val artUrl = rel.artworkUrl
                val art by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, artUrl) {
                    if (!artUrl.isNullOrBlank()) {
                        value = PlatformArtworkProvider.loadArtwork(artUrl, 320)
                    }
                }

                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectRelease(rel) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CARD_SHAPE_14)
                            .background(Color(0xFF1E1E24))
                    ) {
                        if (art != null) {
                            Image(
                                bitmap = art!!,
                                contentDescription = rel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = rel.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${rel.type} • ${rel.year}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistVideosShelf(
    title: String,
    videos: List<ArtistVideoItem>,
    onSelectVideo: (ArtistVideoItem) -> Unit,
    onShowAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        ShelfHeader(title = title, onShowAll = onShowAll)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = videos,
                key = { it.videoId }
            ) { vid ->
                val thumbUrl = vid.thumbnailUrl
                val art by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, thumbUrl) {
                    if (!thumbUrl.isNullOrBlank()) {
                        value = PlatformArtworkProvider.loadArtwork(thumbUrl, 400)
                    }
                }

                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectVideo(vid) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(CARD_SHAPE_14)
                            .background(Color(0xFF1E1E24))
                    ) {
                        if (art != null) {
                            Image(
                                bitmap = art!!,
                                contentDescription = vid.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = vid.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val views = vid.viewsText
                    if (!views.isNullOrBlank()) {
                        Text(
                            text = views,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPlaylistsShelf(
    title: String,
    playlists: List<ArtistPlaylistItem>,
    onSelectPlaylist: (ArtistPlaylistItem) -> Unit,
    onShowAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        ShelfHeader(title = title, onShowAll = onShowAll)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = playlists,
                key = { it.playlistId }
            ) { pl ->
                val thumbUrl = pl.thumbnailUrl
                val art by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, thumbUrl) {
                    if (!thumbUrl.isNullOrBlank()) {
                        value = PlatformArtworkProvider.loadArtwork(thumbUrl, 320)
                    }
                }

                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPlaylist(pl) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CARD_SHAPE_14)
                            .background(Color(0xFF1E1E24))
                    ) {
                        if (art != null) {
                            Image(
                                bitmap = art!!,
                                contentDescription = pl.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = pl.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val author = pl.author
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = author,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistSimilarShelf(
    title: String,
    artists: List<SimilarArtistItem>,
    onSelectArtist: (SimilarArtistItem) -> Unit,
    onShowAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        ShelfHeader(title = title, onShowAll = onShowAll)

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = artists,
                key = { it.browseId }
            ) { artist ->
                val avatarUrl = artist.avatarUrl
                val avatar by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, avatarUrl) {
                    if (!avatarUrl.isNullOrBlank()) {
                        value = PlatformArtworkProvider.loadArtwork(avatarUrl, 240)
                    }
                }

                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectArtist(artist) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF26262B))
                    ) {
                        if (avatar != null) {
                            Image(
                                bitmap = avatar!!,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = artist.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    val audience = artist.audienceText
                    if (!audience.isNullOrBlank()) {
                        Text(
                            text = audience,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.50f),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfHeader(
    title: String,
    onShowAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Show all",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.60f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onShowAll() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ArtistAboutCard(
    artistData: ArtistPageData,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE_18)
            .background(GLASS_FILL)
            .border(1.dp, GLASS_BORDER, CARD_SHAPE_18)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "About the artist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            val bio = artistData.bio
            if (!bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = bio,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 19.sp,
                    maxLines = if (isExpanded) 50 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isExpanded) "Less" else "More",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Pills: Subscribers & Listeners
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!artistData.subscribersText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CAPSULE_SHAPE)
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CAPSULE_SHAPE)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "👤 ${artistData.subscribersText}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                if (!artistData.monthlyListenersText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CAPSULE_SHAPE)
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CAPSULE_SHAPE)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🎧 ${artistData.monthlyListenersText}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistShowAllSheet(
    section: ArtistSectionShowAll,
    onDismiss: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onSelectRelease: (ArtistReleaseItem) -> Unit,
    onSelectVideo: (ArtistVideoItem) -> Unit,
    onSelectPlaylist: (ArtistPlaylistItem) -> Unit,
    onSelectSimilarArtist: (SimilarArtistItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.60f))
            .clickable { onDismiss() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    when (section.type) {
                        "songs" -> {
                            @Suppress("UNCHECKED_CAST")
                            val list = section.items as List<Track>
                            items(list, key = { it.id }) { track ->
                                TopSongItem(track = track, onPlay = { onPlayTrack(track); onDismiss() }, onOptions = {})
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                        "releases" -> {
                            @Suppress("UNCHECKED_CAST")
                            val list = section.items as List<ArtistReleaseItem>
                            items(list, key = { it.id }) { rel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectRelease(rel); onDismiss() }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(rel.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("${rel.type} • ${rel.year}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                        "videos" -> {
                            @Suppress("UNCHECKED_CAST")
                            val list = section.items as List<ArtistVideoItem>
                            items(list, key = { it.videoId }) { vid ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectVideo(vid); onDismiss() }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(vid.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        val views = vid.viewsText
                                        if (views != null) Text(views, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                        "playlists" -> {
                            @Suppress("UNCHECKED_CAST")
                            val list = section.items as List<ArtistPlaylistItem>
                            items(list, key = { it.playlistId }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectPlaylist(pl); onDismiss() }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(pl.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        val author = pl.author
                                        if (author != null) Text(author, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                        "artists" -> {
                            @Suppress("UNCHECKED_CAST")
                            val list = section.items as List<SimilarArtistItem>
                            items(list, key = { it.browseId }) { art ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectSimilarArtist(art); onDismiss() }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(art.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        val audience = art.audienceText
                                        if (audience != null) Text(audience, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
