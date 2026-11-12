package com.kyant.backdrop.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Vertical Liquid Glass Slider designed for audiophile Equalizer faders.
 * Features:
 * - Fluid vertical drag gestures isolated from parent scroll containers via explicit pointer consumption.
 * - Upward drag increases value, downward drag decreases value.
 * - Center 0 dB detent notch line.
 * - Double-tap shortcut to instantly zero/center the value.
 * - Full backdrop sampling, lens refraction, and velocity-responsive squish.
 */
@Composable
fun LiquidVerticalSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    accentColor: Color? = null,
    onDoubleTap: (() -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val isLightTheme = !isSystemInDarkTheme()
    val effectiveAccent = accentColor ?: if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(alpha = 0.2f) else Color(0xFF787880).copy(alpha = 0.36f)
    val notchColor = if (isLightTheme) Color(0xFF404040).copy(alpha = 0.45f) else Color(0xFFFFFFFF).copy(alpha = 0.45f)

    val trackBackdrop = rememberLayerBackdrop()
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var didDrag by remember { mutableStateOf(false) }
    var targetValue by remember { mutableFloatStateOf(value().coerceIn(valueRange)) }

    val valueAnim = remember { Animatable(targetValue, visibilityThreshold) }
    val pressProgressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        snapshotFlow { value() }.collectLatest { externalVal ->
            if (!isDragging && abs(targetValue - externalVal) > 0.001f) {
                targetValue = externalVal.coerceIn(valueRange)
                valueAnim.animateTo(targetValue, spring(1f, 1000f, visibilityThreshold))
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .width(48.dp),
        contentAlignment = Alignment.Center
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)

        // Track & Bipolar Fill Layer
        Box(
            modifier = Modifier
                .layerBackdrop(trackBackdrop)
                .fillMaxHeight()
                .width(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background track capsule
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(trackHeightPx, valueRange) {
                        detectTapGestures(
                            onDoubleTap = {
                                onDoubleTap?.invoke() ?: run {
                                    val zero = 0f.coerceIn(valueRange)
                                    targetValue = zero
                                    coroutineScope.launch {
                                        valueAnim.animateTo(zero, spring(1f, 800f, visibilityThreshold))
                                        onValueChange(zero)
                                        onValueChangeFinished?.invoke()
                                    }
                                }
                            },
                            onTap = { position ->
                                val rawFraction = (1f - (position.y / trackHeightPx)).coerceIn(0f, 1f)
                                val tappedValue = (valueRange.start + rangeSpan * rawFraction).coerceIn(valueRange)
                                targetValue = tappedValue
                                coroutineScope.launch {
                                    valueAnim.animateTo(tappedValue, spring(1f, 800f, visibilityThreshold))
                                    onValueChange(tappedValue)
                                    onValueChangeFinished?.invoke()
                                }
                            }
                        )
                    }
            )

            // Center 0.0 dB detent notch
            val zeroFraction = ((0f - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // Fraction 0 is bottom, fraction 1 is top
                        val notchY = trackHeightPx * (1f - zeroFraction) - (trackHeightPx / 2f)
                        translationY = notchY
                    }
                    .width(14.dp)
                    .height(2.dp)
                    .clip(Capsule())
                    .background(notchColor)
            )

            // Bipolar active accent fill (extends from center 0 dB to current value)
            val currentProgress = ((valueAnim.value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val totalH = constraints.maxHeight.toFloat()
                        val zeroY = totalH * (1f - zeroFraction)
                        val currentY = totalH * (1f - currentProgress)
                        val fillTop = minOf(zeroY, currentY).fastRoundToInt().coerceIn(0, constraints.maxHeight)
                        val fillBottom = maxOf(zeroY, currentY).fastRoundToInt().coerceIn(0, constraints.maxHeight)
                        val fillHeight = (fillBottom - fillTop).coerceAtLeast(0)

                        layout(placeable.width, fillHeight) {
                            placeable.place(0, fillTop)
                        }
                    }
                    .clip(Capsule())
                    .background(effectiveAccent)
            )
        }

        // Draggable Thumb with isolated vertical gesture handling
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val thumbHeight = 48.dp.toPx()
                    val safeProgress = ((valueAnim.value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                    // At progress 1 (max boost), translationY is at top; at progress 0, translationY is at bottom
                    val minTransY = -trackHeightPx / 2f + thumbHeight / 2f
                    val maxTransY = trackHeightPx / 2f - thumbHeight / 2f
                    val rawTransY = (trackHeightPx / 2f - thumbHeight / 2f) - (safeProgress * (trackHeightPx - thumbHeight))
                    translationY = rawTransY.coerceIn(minTransY, maxTransY)
                }
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .pointerInput(trackHeightPx, valueRange) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            didDrag = false
                            coroutineScope.launch {
                                pressProgressAnim.animateTo(1f, spring(1f, 1000f, 0.001f))
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            coroutineScope.launch {
                                pressProgressAnim.animateTo(0f, spring(1f, 600f, 0.001f))
                            }
                            if (didDrag) {
                                onValueChange(targetValue)
                                onValueChangeFinished?.invoke()
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                pressProgressAnim.animateTo(0f, spring(1f, 600f, 0.001f))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // CRITICAL: Consume pointer event so parent scroll containers ignore this drag!
                            change.consume()
                            didDrag = true
                            // Upward drag is negative Y, which increases gain
                            val rawDelta = -rangeSpan * (dragAmount / trackHeightPx)
                            val delta = if (rawDelta.isFinite()) rawDelta else 0f
                            val newValue = (targetValue + delta).coerceIn(valueRange)
                            targetValue = newValue
                            coroutineScope.launch {
                                valueAnim.snapTo(newValue)
                            }
                            onValueChange(newValue)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onDoubleTap?.invoke() ?: run {
                                val zero = 0f.coerceIn(valueRange)
                                targetValue = zero
                                coroutineScope.launch {
                                    valueAnim.animateTo(zero, spring(1f, 800f, visibilityThreshold))
                                    onValueChange(zero)
                                    onValueChangeFinished?.invoke()
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Glass Thumb visual capsule
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = pressProgressAnim.value
                                val scaleX = lerp(0.85f, 1.15f, progress)
                                val scaleY = lerp(0.9f, 1.1f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        shape = { Capsule() },
                        effects = {
                            val progress = pressProgressAnim.value
                            blur(8f.dp.toPx() * (1f - progress))
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = pressProgressAnim.value
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 4f.dp,
                                color = Color.Black.copy(alpha = 0.15f)
                            )
                        },
                        innerShadow = {
                            val progress = pressProgressAnim.value
                            InnerShadow(
                                radius = 4f.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            val progress = pressProgressAnim.value
                            scaleX = lerp(1f, 1.12f, progress)
                            scaleY = lerp(1f, 1.08f, progress)
                        },
                        onDrawSurface = {
                            val progress = pressProgressAnim.value
                            drawRect(Color.White.copy(alpha = 0.95f - 0.4f * progress))
                        }
                    )
                    .size(width = 38.dp, height = 24.dp)
            )
        }
    }
}
