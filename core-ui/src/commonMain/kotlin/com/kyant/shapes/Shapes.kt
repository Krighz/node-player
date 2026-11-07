/*
 * Copyright 2025 Kyant / Node Player Contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kyant.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlin.math.min

enum class RoundedCornerStyle {
    Circular,
    Continuous
}

interface RoundedRectangularShape : Shape {
    val style: RoundedCornerStyle get() = RoundedCornerStyle.Continuous

    fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Corners

    fun copy(style: RoundedCornerStyle): RoundedRectangularShape

    @Immutable
    data class Corners(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float
    )
}

@Immutable
class Capsule(
    override val style: RoundedCornerStyle = RoundedCornerStyle.Continuous
) : RoundedRectangularShape {

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): RoundedRectangularShape.Corners {
        val radius = min(size.width, size.height) * 0.5f
        return RoundedRectangularShape.Corners(radius, radius, radius, radius)
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return ContinuousCapsule.createOutline(size, layoutDirection, density)
    }

    override fun copy(style: RoundedCornerStyle): RoundedRectangularShape = Capsule(style = style)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Capsule) return false
        return style == other.style
    }

    override fun hashCode(): Int = style.hashCode()

    override fun toString(): String = "Capsule(style=$style)"
}

@Immutable
class RoundedRectangle(
    val cornerRadius: Dp = 0.dp,
    override val style: RoundedCornerStyle = RoundedCornerStyle.Continuous
) : RoundedRectangularShape {

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): RoundedRectangularShape.Corners {
        val rPx = with(density) { cornerRadius.toPx() }
        val maxRadius = min(size.width, size.height) * 0.5f
        val clamped = rPx.coerceIn(0f, maxRadius)
        return RoundedRectangularShape.Corners(clamped, clamped, clamped, clamped)
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shape = ContinuousRoundedRectangle(cornerRadius)
        return shape.createOutline(size, layoutDirection, density)
    }

    fun copy(cornerRadius: Dp = this.cornerRadius, style: RoundedCornerStyle = this.style): RoundedRectangle =
        RoundedRectangle(cornerRadius, style)

    override fun copy(style: RoundedCornerStyle): RoundedRectangularShape = copy(cornerRadius = cornerRadius, style = style)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoundedRectangle) return false
        return cornerRadius == other.cornerRadius && style == other.style
    }

    override fun hashCode(): Int = 31 * cornerRadius.hashCode() + style.hashCode()

    override fun toString(): String = "RoundedRectangle(cornerRadius=$cornerRadius, style=$style)"
}
