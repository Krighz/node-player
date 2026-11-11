package com.nodeplayer.ui.theme

import androidx.compose.ui.geometry.Rect
import com.nodeplayer.ui.player.ContainerTransformMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainerTransformMathTest {

    @Test
    fun testLerpClamping() {
        assertEquals(10f, ContainerTransformMath.lerp(10f, 50f, 0.0f))
        assertEquals(30f, ContainerTransformMath.lerp(10f, 50f, 0.5f))
        assertEquals(50f, ContainerTransformMath.lerp(10f, 50f, 1.0f))

        // Overshoot / undershoot clamping
        assertEquals(10f, ContainerTransformMath.lerp(10f, 50f, -0.5f))
        assertEquals(50f, ContainerTransformMath.lerp(10f, 50f, 1.5f))
    }

    @Test
    fun testCornerRadiusInterpolation() {
        assertEquals(20f, ContainerTransformMath.calculateCornerRadius(0.0f, 20f, 0f))
        assertEquals(10f, ContainerTransformMath.calculateCornerRadius(0.5f, 20f, 0f))
        assertEquals(0f, ContainerTransformMath.calculateCornerRadius(1.0f, 20f, 0f))

        // Clamping bounds
        assertEquals(20f, ContainerTransformMath.calculateCornerRadius(-0.2f, 20f, 0f))
        assertEquals(0f, ContainerTransformMath.calculateCornerRadius(1.2f, 20f, 0f))
    }

    @Test
    fun testAlphaLadderProgression() {
        // Mini player alpha: 1.0 down to 0.0 at t in [0.0, 0.25]
        assertEquals(1.0f, ContainerTransformMath.calculateMiniPlayerAlpha(0.0f), 0.0001f)
        assertEquals(0.5f, ContainerTransformMath.calculateMiniPlayerAlpha(0.125f), 0.0001f)
        assertEquals(0.0f, ContainerTransformMath.calculateMiniPlayerAlpha(0.25f), 0.0001f)
        assertEquals(0.0f, ContainerTransformMath.calculateMiniPlayerAlpha(0.5f), 0.0001f)
        assertEquals(0.0f, ContainerTransformMath.calculateMiniPlayerAlpha(1.0f), 0.0001f)
        // Undershoot
        assertEquals(1.0f, ContainerTransformMath.calculateMiniPlayerAlpha(-0.1f), 0.0001f)

        // Full player alpha: 0.0 up to 1.0 at t in [0.20, 1.0]
        assertEquals(0.0f, ContainerTransformMath.calculateFullPlayerAlpha(0.0f), 0.0001f)
        assertEquals(0.0f, ContainerTransformMath.calculateFullPlayerAlpha(0.20f), 0.0001f)
        assertEquals(0.5f, ContainerTransformMath.calculateFullPlayerAlpha(0.60f), 0.0001f)
        assertEquals(1.0f, ContainerTransformMath.calculateFullPlayerAlpha(1.0f), 0.0001f)
        // Overshoot
        assertEquals(1.0f, ContainerTransformMath.calculateFullPlayerAlpha(1.5f), 0.0001f)
    }

    @Test
    fun testCalculateBoundsInterpolation() {
        val startRect = Rect(16f, 700f, 384f, 756f) // 56dp miniplayer pill
        val endRect = Rect(0f, 0f, 400f, 800f) // Fullscreen player

        // At t = 0.0
        val bounds0 = ContainerTransformMath.calculateBounds(0.0f, startRect, endRect)
        assertEquals(startRect.left, bounds0.left, 0.0001f)
        assertEquals(startRect.top, bounds0.top, 0.0001f)
        assertEquals(startRect.right, bounds0.right, 0.0001f)
        assertEquals(startRect.bottom, bounds0.bottom, 0.0001f)

        // At t = 0.5
        val boundsHalf = ContainerTransformMath.calculateBounds(0.5f, startRect, endRect)
        assertEquals(8f, boundsHalf.left, 0.0001f)
        assertEquals(350f, boundsHalf.top, 0.0001f)
        assertEquals(392f, boundsHalf.right, 0.0001f)
        assertEquals(778f, boundsHalf.bottom, 0.0001f)

        // At t = 1.0
        val bounds1 = ContainerTransformMath.calculateBounds(1.0f, startRect, endRect)
        assertEquals(endRect.left, bounds1.left, 0.0001f)
        assertEquals(endRect.top, bounds1.top, 0.0001f)
        assertEquals(endRect.right, bounds1.right, 0.0001f)
        assertEquals(endRect.bottom, bounds1.bottom, 0.0001f)

        // Same rect start and end (zero division check)
        val sameRect = ContainerTransformMath.calculateBounds(0.5f, startRect, startRect)
        assertEquals(startRect.left, sameRect.left, 0.0001f)
        assertEquals(startRect.top, sameRect.top, 0.0001f)
        assertTrue(!sameRect.left.isNaN())
    }
}
