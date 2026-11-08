package com.nodeplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig

/**
 * Online Home View displaying curated YouTube Music flow, recommendations, and trending shelves.
 * Displays smooth Frosted Liquid shimmer placeholder cards while network data resolves.
 */
@Composable
fun OnlineHomeView(
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    dockScrollController: DockScrollController? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val themeConfig = LocalGlassThemeConfig.current

    val dockConnection = rememberDockScrollConnection(
        controller = dockScrollController,
        enabled = themeConfig.dockAutoHideEnabled
    )

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            dockScrollController?.onListScrolledToTop(0, 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dockConnection),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = LocalBottomBarPadding.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Online Curated Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Text(
                        text = "ONLINE FLOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                            color = Color(0xFF00E5FF)
                        )
                    )
                }

                Text(
                    text = "YouTube Music",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Curated recommendations, trending hits & endless radio",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
        }

        // Section 1: QUICK PICKS (Shimmer Shelves)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "QUICK PICKS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(5) {
                        OnlineTrackShimmerCard()
                    }
                }
            }
        }

        // Section 2: TRENDING NOW (Shimmer Shelves)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Whatshot,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TRENDING CHARTS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(5) {
                        OnlineTrackShimmerCard()
                    }
                }
            }
        }

        // Section 3: MOODS & GENRES (Shimmer Mixes)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFFFFAB00),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MOOD MIXES",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(4) {
                        OnlineMixShimmerCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackShimmerCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )

    val cardShape = remember { ContinuousRoundedRectangle(14.dp) }
    val lineShape = remember { ContinuousCapsule }

    Column(
        modifier = Modifier.width(132.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(cardShape)
                .background(Color.White.copy(alpha = alpha.value * 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(lineShape)
                .background(Color.White.copy(alpha = alpha.value * 0.15f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(10.dp)
                .clip(lineShape)
                .background(Color.White.copy(alpha = alpha.value * 0.10f))
        )
    }
}

@Composable
private fun OnlineMixShimmerCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerMixTransition")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerMixAlpha"
    )

    val cardShape = remember { ContinuousRoundedRectangle(20.dp) }
    val lineShape = remember { ContinuousCapsule }

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(130.dp)
            .clip(cardShape)
            .background(Color.White.copy(alpha = alpha.value * 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(14.dp)
                    .clip(lineShape)
                    .background(Color.White.copy(alpha = alpha.value * 0.20f))
            )
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(10.dp)
                    .clip(lineShape)
                    .background(Color.White.copy(alpha = alpha.value * 0.12f))
            )
        }
    }
}
