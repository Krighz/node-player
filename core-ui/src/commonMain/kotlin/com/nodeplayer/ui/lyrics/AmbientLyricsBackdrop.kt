package com.nodeplayer.ui.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.AlbumArtPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient drifting radial gradient background for full-screen lyrics (State 2 & 3).
 *
 * Conforms to LYR-01:
 * - 2 to 3 large radial gradients drifting smoothly on 40s to 90s cycles over a deep dark base.
 * - Colors extracted dynamically from AlbumArtPalette (dominant, vibrant, dark muted).
 * - Zero per-frame bitmap allocations; executed entirely via Compose Canvas radial gradient brushes.
 */
@Composable
fun AmbientLyricsBackdrop(
    palette: AlbumArtPalette,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LyricsBackdropDrift")

    // Slow drifting angle phase 1 (cycle 60s)
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "driftAngle1"
    )

    // Secondary drifting angle phase 2 (cycle 84s)
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 180f,
        targetValue = 540f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 84000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "driftAngle2"
    )

    val baseDark = Color(palette.darkMuted).copy(alpha = 1.0f)
    val primaryOrb = Color(palette.dominant).copy(alpha = 0.45f)
    val secondaryOrb = Color(palette.vibrant).copy(alpha = 0.35f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Deep solid dark base
        drawRect(color = baseDark)

        // 2. Primary drifting radial orb (upper quadrant)
        val rad1 = Math.toRadians(angle1.toDouble())
        val orb1Center = Offset(
            x = (width * 0.45f) + (width * 0.25f * cos(rad1).toFloat()),
            y = (height * 0.30f) + (height * 0.15f * sin(rad1).toFloat())
        )
        val orb1Radius = (width * 0.85f).coerceAtLeast(100f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryOrb, primaryOrb.copy(alpha = 0.15f), Color.Transparent),
                center = orb1Center,
                radius = orb1Radius
            ),
            center = orb1Center,
            radius = orb1Radius
        )

        // 3. Secondary drifting radial orb (lower-right quadrant)
        val rad2 = Math.toRadians(angle2.toDouble())
        val orb2Center = Offset(
            x = (width * 0.55f) + (width * 0.30f * cos(rad2).toFloat()),
            y = (height * 0.65f) + (height * 0.18f * sin(rad2).toFloat())
        )
        val orb2Radius = (width * 0.95f).coerceAtLeast(120f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryOrb, secondaryOrb.copy(alpha = 0.10f), Color.Transparent),
                center = orb2Center,
                radius = orb2Radius
            ),
            center = orb2Center,
            radius = orb2Radius
        )

        // 4. Subtle top-to-bottom vignette overlay for text legibility
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.35f),
                0.5f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.55f)
            )
        )
    }
}
