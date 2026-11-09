package com.nodeplayer.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Apple Music-style fullscreen glass lyrics view.
 * Meets requirements LYR-08 through LYR-12 and UI-SPEC design contract.
 */
@Composable
fun AppleMusicLyricsView(
    track: Track?,
    lyrics: Lyrics?,
    currentPositionMs: Long,
    isLoading: Boolean,
    isOnlineMode: Boolean,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onSearchAgain: () -> Unit,
    onOpenOffsetControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalAccentColor.current
    val baseTextColor = MaterialTheme.colorScheme.onBackground

    var userScrolledManually by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }

    // Active line detection
    val lines = lyrics?.lines ?: emptyList()
    val activeIndex by remember(lines, currentPositionMs) {
        derivedStateOf {
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
        }
    }

    // Auto-scroll loop with spring positioning, pausing for ~4s upon user touch
    LaunchedEffect(userScrolledManually, lastInteractionTime) {
        if (userScrolledManually) {
            delay(4000L)
            userScrolledManually = false
        }
    }

    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && activeIndex < lines.size) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    // Background color wash gradient
    val backgroundBrush = remember(accentColor) {
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.22f),
                Color(0xFF0F1424).copy(alpha = 0.94f)
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        val isWide = maxWidth >= 600.dp
        val columnWidth = if (isWide) maxWidth * 0.60f else maxWidth

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close Lyrics",
                        tint = baseTextColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track?.title ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = baseTextColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = baseTextColor.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenOffsetControls, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Timing Offset",
                            tint = if ((lyrics?.offsetMs ?: 0L) != 0L) accentColor else baseTextColor.copy(alpha = 0.70f)
                        )
                    }
                }
            }

            // Center Stage: Lyrics or Empty/Loading State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Looking for lyrics…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = baseTextColor.copy(alpha = 0.70f))
                        )
                    }
                } else if (lyrics == null || (!lyrics.isSynced && lyrics.plainText.isEmpty())) {
                    // Empty / Not Found State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = if (!isOnlineMode) "No lyrics available offline" else "No lyrics",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = baseTextColor
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (!isOnlineMode) {
                                "Lyrics for this track aren't saved on this device. Turn on online mode, then search again."
                            } else {
                                "We couldn't find lyrics for this track. Check that the title and artist are correct, or search again."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = baseTextColor.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LiquidButton(
                            onClick = onSearchAgain,
                            enabled = isOnlineMode
                        ) {
                            Text(
                                text = "Search again",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = baseTextColor
                                )
                            )
                        }
                    }
                } else if (!lyrics.isSynced && lyrics.plainText.isNotEmpty()) {
                    // Plain Text Lyrics Fallback
                    Box(
                        modifier = Modifier
                            .widthIn(max = columnWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 48.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "Synced lyrics unavailable — showing plain text.",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = baseTextColor.copy(alpha = 0.50f)
                                    ),
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                            item {
                                Text(
                                    text = lyrics.plainText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 18.sp,
                                        lineHeight = 28.sp,
                                        color = baseTextColor.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Synced Lyrics Apple Music View
                    Box(
                        modifier = Modifier
                            .width(columnWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { _, _ ->
                                    userScrolledManually = true
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 120.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = lines,
                                key = { index, line -> "${line.timestampMs}_$index" }
                            ) { index, line ->
                                LyricLineItem(
                                    line = line,
                                    index = index,
                                    activeIndex = activeIndex,
                                    currentPositionMs = currentPositionMs,
                                    baseColor = baseTextColor,
                                    onSeek = onSeekTo
                                )
                            }
                        }

                        // Floating "Jump to current" pill
                        androidx.compose.animation.AnimatedVisibility(
                            visible = userScrolledManually,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        ) {
                            GlassControlSurface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        userScrolledManually = false
                                        if (activeIndex >= 0 && activeIndex < lines.size) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Navigation,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Jump to current",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = baseTextColor
                                        )
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
