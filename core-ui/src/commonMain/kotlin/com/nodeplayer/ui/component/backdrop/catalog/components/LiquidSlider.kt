/*
 * Vendored from Kyant0/AndroidLiquidGlass (kmp branch)
 * https://github.com/Kyant0/AndroidLiquidGlass - Copyright 2025 Kyant0, Apache License 2.0
 * app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidSlider.kt
 *
 * Modified for Node Player:
 * - Full row gesture capturing for seamless dragging on touchscreens & desktop.
 * - Thumb velocity squish, stretch, and spring release.
 * - Guaranteed >= 48dp touch interaction target.
 */
package com.nodeplayer.ui.component.backdrop.catalog.components

import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

import com.nodeplayer.ui.component.backdrop.Backdrop
import com.nodeplayer.ui.component.backdrop.backdrops.layerBackdrop
import com.nodeplayer.ui.component.backdrop.backdrops.rememberBackdrop
import com.nodeplayer.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.nodeplayer.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.nodeplayer.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.backdrop.shadow.InnerShadow
import com.nodeplayer.ui.component.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !com.nodeplayer.ui.component.backdrop.isGlassDark()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(0)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.35f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / effectiveTrackWidth)
                    val nextVal = (if (isLtr) targetValue + delta else targetValue - delta).coerceIn(valueRange)
                    onValueChange(nextVal)
                }
            )
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { valNow ->
                    if (dampedDragAnimation.targetValue != valNow) {
                        dampedDragAnimation.updateValue(valNow)
                    }
                }
        }

        // Full-width gesture detection for immediate dragging anywhere on the slider row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(animationScope, trackWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            didDrag = true
                            dampedDragAnimation.press()
                            val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
                            val delta = (valueRange.endInclusive - valueRange.start) * (offset.x / effectiveTrackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                        },
                        onDragEnd = {
                            didDrag = false
                            dampedDragAnimation.release()
                            onValueChange(dampedDragAnimation.targetValue)
                        },
                        onDragCancel = {
                            didDrag = false
                            dampedDragAnimation.release()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
                            val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / effectiveTrackWidth)
                            val targetValue =
                                (if (isLtr) dampedDragAnimation.targetValue + delta
                                else dampedDragAnimation.targetValue - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track Layer
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                // Background Track
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(trackColor)
                        .fillMaxSize()
                )

                // Filled Accent Track
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accentColor)
                        .height(6.dp)
                        .layout { measurable: Measurable, trackConstraints: Constraints ->
                            val placeable = measurable.measure(trackConstraints)
                            val maxW = if (trackConstraints.hasBoundedWidth && trackConstraints.maxWidth > 0) {
                                trackConstraints.maxWidth
                            } else {
                                placeable.width.coerceAtLeast(0)
                            }
                            val safeProgress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                            val width = (maxW * safeProgress).roundToInt().coerceIn(0, maxW)
                            val height = placeable.height.coerceAtLeast(0)
                            layout(width, height) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }

            // Draggable Liquid Glass Thumb
            Box(
                Modifier
                    .graphicsLayer {
                        val thumbW = size.width.coerceAtLeast(0f)
                        val safeProgress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                        val minTrans = if (thumbW > 0f) -thumbW / 4f else 0f
                        val maxTrans = (trackWidth - thumbW * 3f / 4f).coerceAtLeast(minTrans)
                        translationX =
                            (-thumbW / 2f + trackWidth * safeProgress)
                                .coerceIn(minTrans, maxTrans) * if (isLtr) 1f else -1f
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(percent = 50) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            blur(8f.dp.toPx() * (1f - progress))
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 4f.dp,
                                color = Color.Black.copy(alpha = 0.05f)
                            )
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 4f.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(Color.White.copy(alpha = 1f - progress))
                        }
                    )
                    .size(40f.dp, 24f.dp)
            )
        }
    }
}
