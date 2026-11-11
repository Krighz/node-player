package com.nodeplayer.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.MoodGenre
import com.nodeplayer.model.MoodGenreSection
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.MOOD_CARD_ASPECT
import com.nodeplayer.ui.components.MOOD_CORNER_RADIUS
import com.nodeplayer.ui.components.MOOD_STRIPE_FRACTION
import com.nodeplayer.ui.components.MoodGenreCard
import com.nodeplayer.ui.components.rememberDockScrollConnection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EXPLORE_RETRY_CARD_SHAPE = ContinuousRoundedRectangle(18.dp)
private val EXPLORE_SHIMMER_CARD_SHAPE = ContinuousRoundedRectangle(MOOD_CORNER_RADIUS)

/**
 * Responsive Explore & Category Browsing View per D-03 and D-04.
 *
 * Provides BitChord 1-to-1 visual presentation for YouTube Music mood and genre browsing:
 * - Dynamic 2 to 4 responsive columns:
 *     < 600dp -> 2 columns
 *     < 900dp -> 3 columns
 *     >= 900dp -> 4 columns
 * - Multiplatform pull-to-refresh swipe gesture via NestedScrollConnection
 * - Shimmering duotone skeleton loading grid
 * - Resilient liquid-glass error state with retry button
 */
@Composable
fun ExploreView(
    onCategoryClick: (MoodGenre) -> Unit,
    modifier: Modifier = Modifier,
    innerTubeClient: InnerTubeClient = remember { InnerTubeClient() },
    dockScrollController: DockScrollController? = null
) {
    var sections by remember { mutableStateOf<List<MoodGenreSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Pull-to-refresh state
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val pullTriggerThresholdPx = with(density) { 72.dp.toPx() }
    val maxPullDistancePx = with(density) { 120.dp.toPx() }

    fun refreshData(showSkeleton: Boolean) {
        coroutineScope.launch {
            if (showSkeleton) isLoading = true else isRefreshing = true
            errorMessage = null
            innerTubeClient.browseMoodAndGenres()
                .onSuccess { resultSections ->
                    sections = resultSections
                    isLoading = false
                    isRefreshing = false
                    pullDistancePx = 0f
                }
                .onFailure { error ->
                    if (sections.isEmpty()) {
                        errorMessage = error.message ?: "Failed to load explore categories"
                    }
                    isLoading = false
                    isRefreshing = false
                    pullDistancePx = 0f
                }
        }
    }

    LaunchedEffect(Unit) {
        refreshData(showSkeleton = true)
    }

    // Dock scroll connection for intelligent bottom bar hide/show
    val dockConnection = rememberDockScrollConnection(dockScrollController)

    // Pull-to-refresh nested scroll connection
    val pullRefreshConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistancePx > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistancePx)
                    pullDistancePx += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isLoading && !isRefreshing && available.y > 0f && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
                    val newPull = (pullDistancePx + available.y * 0.5f).coerceIn(0f, maxPullDistancePx)
                    val consumedY = newPull - pullDistancePx
                    pullDistancePx = newPull
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistancePx >= pullTriggerThresholdPx && !isRefreshing && !isLoading) {
                    refreshData(showSkeleton = false)
                } else {
                    pullDistancePx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val bottomPadding = LocalBottomBarPadding.current + 24.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dockConnection)
            .nestedScroll(pullRefreshConnection)
    ) {
        val columns = when {
            maxWidth < 600.dp -> 2
            maxWidth < 900.dp -> 3
            else -> 4
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    ExploreSkeletonGrid(
                        columns = columns,
                        bottomPadding = bottomPadding,
                        gridState = gridState
                    )
                }
                errorMessage != null && sections.isEmpty() -> {
                    ExploreErrorState(
                        errorMessage = errorMessage!!,
                        onRetry = { refreshData(showSkeleton = true) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, pullDistancePx.roundToInt()) },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        sections.forEachIndexed { sectionIndex, section ->
                            // Section header spanning all columns
                            item(
                                key = "section_header_${section.title}_$sectionIndex",
                                span = { GridItemSpan(columns) },
                                contentType = "section_header"
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (sectionIndex == 0) 0.dp else 16.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (sectionIndex == 0) {
                                        IconButton(
                                            onClick = { refreshData(showSkeleton = false) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Refresh",
                                                tint = Color.White.copy(alpha = 0.70f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Category items
                            gridItemsIndexed(
                                items = section.items,
                                key = { index, item -> "category_${item.browseId}_${sectionIndex}_$index" },
                                contentType = { _, _ -> "category_card" }
                            ) { _, category ->
                                MoodGenreCard(
                                    item = category,
                                    onClick = { onCategoryClick(category) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Pull-to-refresh spinner indicator
            if (pullDistancePx > 0f || isRefreshing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .offset { IntOffset(0, (pullDistancePx * 0.4f).roundToInt()) }
                        .clip(ContinuousCapsule())
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), ContinuousCapsule())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isRefreshing) "Refreshing..." else "Pull to refresh",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shimmering skeleton grid matching explore category layout per D-03.
 */
@Composable
private fun ExploreSkeletonGrid(
    columns: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    gridState: LazyGridState
) {
    val transition = rememberInfiniteTransition(label = "ExploreSkeletonShimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        listOf("Moods & moments", "Genres").forEachIndexed { sectionIdx, sectionTitle ->
            item(
                key = "skeleton_section_$sectionIdx",
                span = { GridItemSpan(columns) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(24.dp)
                        .padding(top = if (sectionIdx == 0) 0.dp else 16.dp)
                        .clip(ContinuousCapsule())
                        .background(Color.White.copy(alpha = 0.10f))
                )
            }

            items(count = if (sectionIdx == 0) 8 else 12) { itemIdx ->
                ExploreSkeletonCard(shimmerTranslate = shimmerTranslate)
            }
        }
    }
}

/**
 * Single skeleton card maintaining 1.72 aspect ratio and 0.08f left stripe.
 */
@Composable
private fun ExploreSkeletonCard(shimmerTranslate: Float) {
    val shimmerBrush = remember(shimmerTranslate) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.06f)
            ),
            start = Offset(shimmerTranslate * 300f - 150f, 0f),
            end = Offset(shimmerTranslate * 300f + 150f, 300f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(MOOD_CARD_ASPECT)
            .clip(EXPLORE_SHIMMER_CARD_SHAPE)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), EXPLORE_SHIMMER_CARD_SHAPE)
    ) {
        // Shimmer gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush)
        )

        // Left accent stripe placeholder
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(MOOD_STRIPE_FRACTION)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Title placeholder bars
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(13.dp)
                    .clip(ContinuousCapsule())
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(10.dp)
                    .clip(ContinuousCapsule())
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

/**
 * Liquid-glass styled error state with retry action.
 */
@Composable
private fun ExploreErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(24.dp)
            .clip(EXPLORE_RETRY_CARD_SHAPE)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), EXPLORE_RETRY_CARD_SHAPE)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Could not load categories",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.60f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.14f)
                ),
                shape = ContinuousCapsule()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Retry", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
