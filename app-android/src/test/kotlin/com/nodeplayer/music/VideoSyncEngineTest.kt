package com.nodeplayer.music

import com.nodeplayer.model.video.SurfacePriority
import com.nodeplayer.model.video.VideoAspectMath
import com.nodeplayer.model.video.VideoPlayerState
import com.nodeplayer.model.video.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VideoSyncEngineTest {

    @Test
    fun testSurfacePriorityOrderingAndPreemption() {
        val mini = SurfacePriority.MINI_PLAYER
        val card = SurfacePriority.NOW_PLAYING_CARD
        val cinema = SurfacePriority.FULLSCREEN_CINEMA

        assertTrue(cinema.level > card.level)
        assertTrue(card.level > mini.level)
        assertEquals(2, cinema.level)
        assertEquals(1, card.level)
        assertEquals(0, mini.level)

        val surfaces = listOf(mini, cinema, card)
        val highest = surfaces.maxByOrNull { it.level }
        assertEquals(SurfacePriority.FULLSCREEN_CINEMA, highest)
    }

    @Test
    fun testClockDriftSyncGatingMath() {
        val seekDriftThresholdMs = 450L

        fun requiresSeekJump(driftMs: Long): Boolean {
            return abs(driftMs) > seekDriftThresholdMs
        }

        // Inside deadzone (<= 25ms)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(0L), 0.001f)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(20L), 0.001f)
        assertEquals(0f, VideoAspectMath.calculateRateNudge(-25L), 0.001f)
        assertFalse(requiresSeekJump(20L))

        // Micro-drift (25ms to 450ms) -> rate nudging, no seek
        val nudgePositive = VideoAspectMath.calculateRateNudge(50L) // -0.05f
        assertEquals(-0.05f, nudgePositive, 0.001f)
        assertFalse(requiresSeekJump(50L))

        val nudgeNegative = VideoAspectMath.calculateRateNudge(-80L) // +0.08f
        assertEquals(0.08f, nudgeNegative, 0.001f)
        assertFalse(requiresSeekJump(-80L))

        // Clamped nudging between 25ms and 450ms
        val nudgeClamped = VideoAspectMath.calculateRateNudge(300L)
        assertEquals(-0.10f, nudgeClamped, 0.001f)
        assertFalse(requiresSeekJump(300L))

        // Macro-drift (> 450ms) -> triggers seek jump
        assertTrue(requiresSeekJump(500L))
        assertTrue(requiresSeekJump(-600L))
        assertTrue(requiresSeekJump(15000L))
    }

    @Test
    fun testSeekCooldownGating() {
        val cooldownMs = 1500L
        fun canSeek(lastSeekMs: Long, currentMs: Long): Boolean {
            return (currentMs - lastSeekMs) > cooldownMs
        }

        val t0 = 10000L
        // 500ms later -> inside cooldown, cannot seek
        assertFalse(canSeek(lastSeekMs = t0, currentMs = t0 + 500L))
        // 1499ms later -> inside cooldown
        assertFalse(canSeek(lastSeekMs = t0, currentMs = t0 + 1499L))
        // 1501ms later -> cooldown elapsed, can seek
        assertTrue(canSeek(lastSeekMs = t0, currentMs = t0 + 1501L))
    }

    @Test
    fun testVideoAspectMathContainAndCrop() {
        val aspect = 16f / 9f

        // Portrait 1080x1920: fit = true -> 1080x607 (letterboxed)
        val fitDims = VideoAspectMath.calculateDimensions(
            containerWidth = 1080,
            containerHeight = 1920,
            videoAspect = aspect,
            fit = true
        )
        assertEquals(1080, fitDims.first)
        assertEquals(607, fitDims.second)

        // Portrait 1080x1920: fit = false -> 3413x1920 (cropped)
        val cropDims = VideoAspectMath.calculateDimensions(
            containerWidth = 1080,
            containerHeight = 1920,
            videoAspect = aspect,
            fit = false
        )
        assertEquals(3413, cropDims.first)
        assertEquals(1920, cropDims.second)

        // Landscape 1920x1080: fit = true with 16:9 -> exactly 1920x1080
        val landscapeDims = VideoAspectMath.calculateDimensions(
            containerWidth = 1920,
            containerHeight = 1080,
            videoAspect = aspect,
            fit = true
        )
        assertEquals(1920, landscapeDims.first)
        assertEquals(1080, landscapeDims.second)
    }

    @Test
    fun testVideoPlayerStateDecouplingDefaults() {
        val state = VideoPlayerState()
        assertFalse(state.isVideoActive)
        assertFalse(state.isAvailable)
        assertEquals(VideoQuality.P480, state.selectedQuality)
        assertEquals(16f / 9f, state.aspect, 0.001f)
        assertFalse(state.hasRenderedFirstFrame)
        assertEquals(null, state.activeSurfacePriority)
    }
}
