package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official Node Player Brand Mark.
 * Renders the curved 'N' musical note glyph with the white-to-slate gradient (#FFFFFF -> #F1F5F9 -> #94A3B8)
 * inside a continuous dark squircle (#131316) with an 8% white hairline rim.
 */
@Composable
fun NodePlayerLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    showSquircleBackground: Boolean = true
) {
    val glyphPathString = remember {
        "M 194 188 L 280 354 C 292 374 308 384 326 384 C 352 384 370 364 370 332 " +
        "L 370 178 C 374 183 382 186 391 186 C 405 186 415 175 415 161 " +
        "C 415 141 397 126 370 126 C 343 126 322 145 322 178 L 322 332 " +
        "L 232 154 C 220 135 206 126 188 126 C 162 126 146 146 146 178 " +
        "L 146 316 C 146 322 138 323 128 327 C 113 333 106 346 106 360 " +
        "C 106 376 120 384 138 384 C 166 384 194 364 194 334 L 194 188 Z"
    }

    val baseGlyphPath = remember {
        PathParser().parsePathString(glyphPathString).toPath()
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        if (showSquircleBackground) {
            val squircleRadius = CornerRadius(w * 0.242f, h * 0.242f)
            // 1. Matte #131316 Squircle fill
            drawRoundRect(
                color = Color(0xFF131316),
                size = Size(w, h),
                cornerRadius = squircleRadius
            )
            // 2. 8% white hairline rim
            val strokeWidth = (w * 0.015f).coerceIn(1f, 2.5f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = Size(w - strokeWidth, h - strokeWidth),
                cornerRadius = squircleRadius,
                style = Stroke(width = strokeWidth)
            )
        }

        // 3. Scaled Musical 'N' Glyph
        val glyphScale = (w / 512f) * (if (showSquircleBackground) 0.98f else 1.0f)
        val glyphBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFFFFF),
                Color(0xFFF1F5F9),
                Color(0xFF94A3B8)
            ),
            start = Offset(175f * glyphScale, 185f * glyphScale),
            end = Offset(342f * glyphScale, 368f * glyphScale)
        )

        scale(scale = glyphScale, pivot = Offset.Zero) {
            drawPath(
                path = baseGlyphPath,
                brush = glyphBrush
            )
        }
    }
}
