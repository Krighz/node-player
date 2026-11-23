package com.nodeplayer.model.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMathTest {

    @Test
    fun testCalculateRateNudgeDeadzone() {
        // Under 25ms: ignore (0 drift nudge)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(0L), 0.001f)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(15L), 0.001f)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(-25L), 0.001f)
    }

    @Test
    fun testCalculateRateNudgeGradualAndClamped() {
        // 50ms drift -> -50 / 1000 = -0.05f
        assertEquals(-0.05f, VideoAspectMath.calculateRateNudge(50L), 0.001f)
        // -50ms drift -> -(-50) / 1000 = +0.05f
        assertEquals(0.05f, VideoAspectMath.calculateRateNudge(-50L), 0.001f)

        // 200ms drift -> clamped to -0.10f
        assertEquals(-0.10f, VideoAspectMath.calculateRateNudge(200L), 0.001f)
        // -200ms drift -> clamped to +0.10f
        assertEquals(0.10f, VideoAspectMath.calculateRateNudge(-200L), 0.001f)

        // Extreme drift beyond threshold
        assertEquals(-0.10f, VideoAspectMath.calculateRateNudge(1000L), 0.001f)
        assertEquals(0.10f, VideoAspectMath.calculateRateNudge(-1000L), 0.001f)
    }

    @Test
    fun testCalculateDimensionsLetterboxFit() {
        val aspect = 16f / 9f
        // Portrait phone: width = 1080, height = 1920.
        // fit = true: letterboxes height -> 1080 x 607
        val (w, h) = VideoAspectMath.calculateDimensions(
            containerWidth = 1080,
            containerHeight = 1920,
            videoAspect = aspect,
            fit = true
        )
        assertEquals(1080, w)
        assertEquals(607, h)
    }

    @Test
    fun testCalculateDimensionsCropCover() {
        val aspect = 16f / 9f
        // Portrait phone: width = 1080, height = 1920.
        // fit = false: crops width -> 3413 x 1920
        val (w, h) = VideoAspectMath.calculateDimensions(
            containerWidth = 1080,
            containerHeight = 1920,
            videoAspect = aspect,
            fit = false
        )
        assertEquals(3413, w)
        assertEquals(1920, h)
    }

    @Test
    fun testSurfacePriorityOrdering() {
        val mini = SurfacePriority.MINI_PLAYER
        val card = SurfacePriority.NOW_PLAYING_CARD
        val cinema = SurfacePriority.FULLSCREEN_CINEMA

        assert(cinema.level > card.level)
        assert(card.level > mini.level)
    }
}
