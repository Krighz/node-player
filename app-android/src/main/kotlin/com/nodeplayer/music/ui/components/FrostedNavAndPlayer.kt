package com.nodeplayer.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun FrostedMiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    progressFraction: Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    hazeState: HazeState? = null,
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val shape = RoundedCornerShape(GlassTokens.cardCornerRadius)
    val accent = LocalAccentColor.current

    val gestureModifier = if (gesturesEnabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onPlayPauseClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (abs(totalDragX) > abs(totalDragY)) {
                            if (totalDragX < -40f) {
                                onSkipNextClick()
                            } else if (totalDragX > 40f) {
                                onSkipPreviousClick()
                            }
                        } else {
                            if (totalDragY < -40f) {
                                onClick() // Swipe up opens Now Playing
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glassRefraction(shape = shape, enabled = !isPerformanceMode, refractionIndex = 0.65f)
            .glassSurface(hazeState = hazeState, shape = shape, tintAlpha = 0.65f)
            .then(gestureModifier)
    ) {
        // Progress track at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(accent)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork thumbnail with fallback
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222433)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(24.dp)
                )
                AsyncImage(
                    model = currentTrack,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Soundwave visualizer in mini player (real audio reactive)
            SoundWaveVisualizer(isPlaying = isPlaying, barColor = accent, maxHeight = 16.dp, rmsLevel = rmsLevel)
            Spacer(modifier = Modifier.width(6.dp))

            // Play / Pause
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Skip Next
            IconButton(onClick = onSkipNextClick) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Skip Next",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun FrostedBottomBar(
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    hazeState: HazeState? = null,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("home", "Home", Icons.Rounded.Home),
        Triple("library", "Library", Icons.Rounded.LibraryMusic),
        Triple("equalizer", "Equalizer", Icons.Rounded.GraphicEq),
        Triple("settings", "Settings", Icons.Rounded.Settings)
    )

    val shape = RoundedCornerShape(32.dp)
    val accent = LocalAccentColor.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .glassRefraction(shape = shape, enabled = !isPerformanceMode, refractionIndex = 0.85f)
            .glassSurface(hazeState = hazeState, shape = shape, tintAlpha = 0.70f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (route, label, icon) ->
                val isSelected = selectedRoute == route
                val itemColor = if (isSelected) accent else Color.White.copy(alpha = 0.55f)

                Column(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
                        ) { onRouteChange(route) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = itemColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = itemColor,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
