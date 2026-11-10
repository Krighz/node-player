package com.nodeplayer.music.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.nodeplayer.model.Track
import com.nodeplayer.network.spotify.MatchResult
import com.nodeplayer.network.spotify.SpotifyPlaylistSummary
import com.nodeplayer.network.spotify.SpotifyTrack
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Review screen for Spotify transfer showing Matched, Uncertain, and Not Found tracks.
 * Conforms to SPOT-05 and SPOT-06.
 */
@Composable
fun SpotifyTransferReviewScreen(
    playlist: SpotifyPlaylistSummary,
    matchedTracks: List<Pair<SpotifyTrack, Track>>,
    uncertainTracks: List<Pair<SpotifyTrack, List<Track>>>,
    notFoundTracks: List<SpotifyTrack>,
    isMatchingActive: Boolean,
    progressFraction: Float,
    currentMatchingTitle: String?,
    onCancelMatching: () -> Unit,
    onResolveUncertain: (SpotifyTrack, Track) -> Unit,
    onManualSearchForTrack: (SpotifyTrack) -> Unit,
    onFinishTransfer: (resolvedMatches: List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Matched, 1: Uncertain, 2: Not Found

    // User-resolved tracks mapping
    val userResolvedMap = remember { mutableMapOf<String, Track>() }

    val allMatched = remember(matchedTracks, userResolvedMap.size) {
        val list = matchedTracks.map { it.second }.toMutableList()
        list.addAll(userResolvedMap.values)
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Transfer Review",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Card
        GlassControlSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF1DB954))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${allMatched.size} of ${playlist.trackCount} matched",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                }

                // If currently running matching pipeline
                if (isMatchingActive) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentMatchingTitle ?: "Matching songs...",
                            style = MaterialTheme.typography.labelSmall.copy(color = accent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progressFraction },
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LiquidButton(
                            onClick = onCancelMatching,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Cancel Matching", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs Row: Matched / Uncertain / Not Found
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "Matched (${matchedTracks.size + userResolvedMap.size})" to 0,
                "Uncertain (${uncertainTracks.size})" to 1,
                "Not Found (${notFoundTracks.size})" to 2
            )

            tabs.forEach { (title, idx) ->
                val isSel = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                        .clickable { selectedTab = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) accent else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    items(matchedTracks, key = { it.first.id }) { (spot, target) ->
                        TrackRowItem(
                            title = target.title,
                            subtitle = "${target.artist} • (Spotify: ${spot.title})",
                            icon = Icons.Rounded.Check,
                            iconTint = Color(0xFF4CAF50)
                        )
                    }
                }
                1 -> {
                    items(uncertainTracks, key = { it.first.id }) { (spot, candidates) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Spotify: ${spot.title} - ${spot.primaryArtist}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Top Suggestion: ${candidates.firstOrNull()?.title ?: "Unknown"} by ${candidates.firstOrNull()?.artist ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                candidates.firstOrNull()?.let { topCand ->
                                    LiquidButton(
                                        onClick = {
                                            userResolvedMap[spot.id] = topCand
                                            onResolveUncertain(spot, topCand)
                                        },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Accept Suggestion", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                LiquidButton(
                                    onClick = { onManualSearchForTrack(spot) },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Find Match", color = accent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    items(notFoundTracks, key = { it.id }) { spot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spot.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = spot.primaryArtist,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                                )
                            }
                            LiquidButton(
                                onClick = { onManualSearchForTrack(spot) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Search", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            LiquidButton(
                onClick = { onFinishTransfer(allMatched) },
                enabled = allMatched.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Finish Transfer (${allMatched.size} tracks)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
