package com.nodeplayer.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlin.math.roundToInt

/**
 * Pure mathematical calculations for geometry, squircle corner radii, and alpha ladders
 * during container transformation between ConvxMiniPlayer and NowPlayingView.
 */
object ContainerTransformMath {

    /**
     * Standard linear interpolation between [start] and [stop] clamped to [0.0f, 1.0f] fraction.
     */
    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        val f = fraction.coerceIn(0.0f, 1.0f)
        return start + (stop - start) * f
    }

    /**
     * Calculates the continuous squircle corner radius in Dp across the progress fraction.
     * At fraction = 0f: startRadiusDp (default 20dp for ConvxMiniPlayer).
     * At fraction = 1f: endRadiusDp (default 0dp for fullscreen NowPlayingView).
     */
    fun calculateCornerRadius(
        fraction: Float,
        startRadiusDp: Float = 20f,
        endRadiusDp: Float = 0f
    ): Float {
        return lerp(startRadiusDp, endRadiusDp, fraction)
    }

    /**
     * MiniPlayer content alpha ladder:
     * Fades from 1.0f down to 0.0f strictly within t in [0.0, 0.25].
     * Clamped to 0.0f for any t >= 0.25f.
     */
    fun calculateMiniPlayerAlpha(fraction: Float): Float {
        val f = fraction.coerceIn(0.0f, 1.0f)
        if (f >= 0.25f) return 0.0f
        return 1.0f - (f / 0.25f)
    }

    /**
     * Fullscreen NowPlaying content alpha ladder:
     * Stays at 0.0f for t in [0.0, 0.20].
     * Fades from 0.0f up to 1.0f strictly within t in [0.20, 1.0].
     */
    fun calculateFullPlayerAlpha(fraction: Float): Float {
        val f = fraction.coerceIn(0.0f, 1.0f)
        if (f <= 0.20f) return 0.0f
        return (f - 0.20f) / 0.80f
    }

    /**
     * Computes the bounding rectangle [Rect] between [startBounds] and [endBounds].
     * Clamped to fraction [0.0f, 1.0f] with zero division-by-zero or NaN issues.
     */
    fun calculateBounds(fraction: Float, startBounds: Rect, endBounds: Rect): Rect {
        val f = fraction.coerceIn(0.0f, 1.0f)
        val left = startBounds.left + (endBounds.left - startBounds.left) * f
        val top = startBounds.top + (endBounds.top - startBounds.top) * f
        val right = startBounds.right + (endBounds.right - startBounds.right) * f
        val bottom = startBounds.bottom + (endBounds.bottom - startBounds.bottom) * f
        return Rect(left = left, top = top, right = right, bottom = bottom)
    }
}

/**
 * High-performance, zero-allocation container transform composable bridging ConvxMiniPlayer
 * and NowPlayingView.
 *
 * @param progress 0.0f = collapsed ConvxMiniPlayer, 1.0f = expanded NowPlayingView.
 * @param miniPlayerContent Composable content for the miniplayer capsule.
 * @param fullPlayerContent Composable content for the expanded fullscreen player.
 * @param modifier Root container modifier.
 * @param startCornerRadiusDp Squircle corner radius when collapsed (default 20dp).
 * @param endCornerRadiusDp Squircle corner radius when expanded (default 0dp).
 */
@Composable
fun ProgressContainerTransform(
    progress: Float,
    miniPlayerContent: @Composable () -> Unit,
    fullPlayerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    startCornerRadiusDp: Float = 20f,
    endCornerRadiusDp: Float = 0f
) {
    val clampedProgress = progress.coerceIn(0.0f, 1.0f)
    val currentCornerRadius = ContainerTransformMath.calculateCornerRadius(
        fraction = clampedProgress,
        startRadiusDp = startCornerRadiusDp,
        endRadiusDp = endCornerRadiusDp
    )

    val shape = remember(currentCornerRadius) {
        ContinuousRoundedRectangle(currentCornerRadius.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
    ) {
        // MiniPlayer layer (active when progress < 0.25)
        if (clampedProgress < 0.25f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ContainerTransformMath.calculateMiniPlayerAlpha(clampedProgress)
                    }
            ) {
                miniPlayerContent()
            }
        }

        // FullPlayer layer (active when progress > 0.20)
        if (clampedProgress > 0.20f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ContainerTransformMath.calculateFullPlayerAlpha(clampedProgress)
                    }
            ) {
                fullPlayerContent()
            }
        }
    }
}
