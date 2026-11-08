/*
 * Vendored from Kyant0/Capsule
 * https://github.com/Kyant0/Capsule - Copyright 2025 Kyant0, Apache License 2.0
 *
 * Provides ContinuousRoundedRectangle and ContinuousCapsule continuous squircle geometry
 * for Compose Multiplatform (Android & Desktop).
 */
package com.nodeplayer.ui.component.shapes

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Immutable
open class ContinuousRoundedRectangle(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize
) : CornerBasedShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart
) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }

        val maxRadius = min(size.width, size.height) * 0.5f
        val topLeft = (if (layoutDirection == Ltr) topStart else topEnd).coerceIn(0f, maxRadius)
        val topRight = (if (layoutDirection == Ltr) topEnd else topStart).coerceIn(0f, maxRadius)
        val bottomRight = (if (layoutDirection == Ltr) bottomEnd else bottomStart).coerceIn(0f, maxRadius)
        val bottomLeft = (if (layoutDirection == Ltr) bottomStart else bottomEnd).coerceIn(0f, maxRadius)

        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = CornerRadius(topLeft),
                topRight = CornerRadius(topRight),
                bottomRight = CornerRadius(bottomRight),
                bottomLeft = CornerRadius(bottomLeft)
            )
        )
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): ContinuousRoundedRectangle {
        return ContinuousRoundedRectangle(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart
        )
    }
}

private val FullCornerSize = CornerSize(50)

@Stable
val ContinuousCapsule: ContinuousRoundedRectangle = ContinuousCapsule()

@Suppress("FunctionName")
@Stable
fun ContinuousCapsule(): ContinuousRoundedRectangle = ContinuousCapsuleImpl()

@Immutable
private class ContinuousCapsuleImpl : ContinuousRoundedRectangle(
    topStart = FullCornerSize,
    topEnd = FullCornerSize,
    bottomEnd = FullCornerSize,
    bottomStart = FullCornerSize
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val radius = size.minDimension * 0.5f
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = CornerRadius(radius),
                topRight = CornerRadius(radius),
                bottomRight = CornerRadius(radius),
                bottomLeft = CornerRadius(radius)
            )
        )
    }
}

@Stable
fun ContinuousRoundedRectangle(corner: CornerSize): ContinuousRoundedRectangle =
    ContinuousRoundedRectangle(
        topStart = corner,
        topEnd = corner,
        bottomEnd = corner,
        bottomStart = corner
    )

@Stable
fun ContinuousRoundedRectangle(size: Dp): ContinuousRoundedRectangle =
    ContinuousRoundedRectangle(corner = CornerSize(size))

@Stable
fun ContinuousRoundedRectangle(size: Float): ContinuousRoundedRectangle =
    ContinuousRoundedRectangle(corner = CornerSize(size))

@Stable
fun ContinuousRoundedRectangle(percent: Int): ContinuousRoundedRectangle =
    ContinuousRoundedRectangle(corner = CornerSize(percent))

@Stable
fun ContinuousRoundedRectangle(
    topStart: Dp = 0f.dp,
    topEnd: Dp = 0f.dp,
    bottomEnd: Dp = 0f.dp,
    bottomStart: Dp = 0f.dp
): ContinuousRoundedRectangle =
    ContinuousRoundedRectangle(
        topStart = CornerSize(topStart),
        topEnd = CornerSize(topEnd),
        bottomEnd = CornerSize(bottomEnd),
        bottomStart = CornerSize(bottomStart)
    )
