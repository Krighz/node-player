package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
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
    val isLightTheme = !isSystemInDarkTheme()
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
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    isDragging = true
                },
                onDragStopped = {
                    isDragging = false
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
                    val rawDelta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / effectiveTrackWidth)
                    val delta = if (rawDelta.isFinite()) rawDelta else 0f
                    val newValue =
                        (if (isLtr) (targetValue + delta)
                        else (targetValue - delta))
                            .coerceIn(valueRange)
                    updateValue(newValue)
                    onValueChange(newValue)
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { externalVal ->
                    if (!isDragging && dampedDragAnimation.targetValue != externalVal) {
                        dampedDragAnimation.updateValue(externalVal)
                    }
                }
        }

        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .fillMaxWidth()
        ) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope, trackWidth, isLtr, valueRange) {
                        detectTapGestures { position ->
                            val effectiveTrackWidth = trackWidth.coerceAtLeast(1)
                            val rawFraction = (position.x / effectiveTrackWidth).coerceIn(0f, 1f)
                            val delta = (valueRange.endInclusive - valueRange.start) * rawFraction
                            val targetVal =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetVal)
                            onValueChange(targetVal)
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val safeProgress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                        val width = (constraints.maxWidth * safeProgress).fastRoundToInt().coerceIn(0, constraints.maxWidth)
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        // Hitbox wrapper for thumb: enforces minimum 48dp touch bounds
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val thumbWidth = 48f.dp.toPx()
                    val minTrans = if (thumbWidth > 0f) -thumbWidth / 4f else 0f
                    val maxTrans = (trackWidth - thumbWidth * 3f / 4f).coerceAtLeast(minTrans)
                    val safeProgress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                    val rawTrans = (-thumbWidth / 2f + trackWidth * safeProgress)
                    translationX = rawTrans.coerceIn(minTrans, maxTrans) * if (isLtr) 1f else -1f
                }
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(dampedDragAnimation.modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 1f, progress)
                                val scaleY = lerp(0f, 1f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        shape = { Capsule() },
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
                            val rawVelocity = dampedDragAnimation.velocity
                            val velocity = if (rawVelocity.isFinite()) rawVelocity / 10f else 0f
                            val stretchX = (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            val squishY = (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            scaleX /= (1f - stretchX)
                            scaleY *= (1f - squishY)
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
