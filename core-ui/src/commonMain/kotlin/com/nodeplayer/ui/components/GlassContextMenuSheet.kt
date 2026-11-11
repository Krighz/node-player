package com.nodeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.model.ContextMenuConfig
import com.nodeplayer.model.Track
import com.nodeplayer.model.TrackActionId
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Standardized liquid-glass bottom sheet context menu for tracks, albums, playlists, and artists.
 * Meets requirements MENU-01 and MENU-03:
 * - Fluid spring open/close with backdrop sampling.
 * - Media header row with squircle artwork, title, subtitle, and badges.
 * - Filtered and ordered according to ContextMenuConfig.
 */
@Composable
fun GlassContextMenuSheet(
    track: Track?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onActionClick: (TrackActionId, Track) -> Unit,
    config: ContextMenuConfig = ContextMenuConfig.Defaults,
    isSavedInLibrary: Boolean = false,
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    if (track == null) return

    val actions = remember(config) { config.getActiveTrackActions() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { it },
        exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { it },
        modifier = modifier
    ) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val sheetWidth = if (maxWidth > 600.dp) 520.dp else maxWidth
                val sheetShape = remember { ContinuousRoundedRectangle(28.dp) }
                val borderBrush = remember {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = sheetWidth)
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(Color(0xFF101420).copy(alpha = 0.92f))
                        .border(1.dp, borderBrush, sheetShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume click
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // 1. Hero Media Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            track = track,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            cornerRadius = 12.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist.ifBlank { "Unknown Artist" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        AudioFormatBadge(track = track)
                    }

                    Divider(
                        color = Color.White.copy(alpha = 0.10f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 2. Action List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(actions, key = { it.name }) { actionId ->
                            val icon = getActionIcon(actionId, isSavedInLibrary)
                            val title = if (actionId == TrackActionId.LIKE_SAVE && isSavedInLibrary) {
                                "Remove from library"
                            } else {
                                actionId.title
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onActionClick(actionId, track)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (actionId == TrackActionId.LIKE_SAVE && isSavedInLibrary) accent else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun getActionIcon(actionId: TrackActionId, isSaved: Boolean): ImageVector {
    return when (actionId) {
        TrackActionId.PLAY_NEXT -> Icons.Rounded.QueueMusic
        TrackActionId.ADD_TO_QUEUE -> Icons.Rounded.PlaylistAdd
        TrackActionId.ADD_TO_PLAYLIST -> Icons.Rounded.PlaylistAddCheck
        TrackActionId.LIKE_SAVE -> if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
        TrackActionId.START_RADIO -> Icons.Rounded.Radio
        TrackActionId.GO_TO_ARTIST -> Icons.Rounded.Person
        TrackActionId.GO_TO_ALBUM -> Icons.Rounded.Album
        TrackActionId.VIEW_LYRICS -> Icons.Rounded.Lyrics
        TrackActionId.SHARE -> Icons.Rounded.Share
        TrackActionId.SONG_INFO -> Icons.Rounded.Info
        TrackActionId.NOT_INTERESTED -> Icons.Rounded.Block
    }
}
