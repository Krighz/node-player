package com.nodeplayer.model.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityMathTest {

    @Test
    fun testChooseHeightAuto() {
        assertNull(VideoMath.chooseHeight(listOf(360, 480, 720, 1080), null))
        assertNull(VideoMath.chooseHeight(emptyList(), 720))
    }

    @Test
    fun testChooseHeightExactMatch() {
        val available = listOf(360, 480, 720, 1080)
        assertEquals(720, VideoMath.chooseHeight(available, 720))
        assertEquals(480, VideoMath.chooseHeight(available, 480))
        assertEquals(360, VideoMath.chooseHeight(available, 360))
        assertEquals(1080, VideoMath.chooseHeight(available, 1080))
    }

    @Test
    fun testChooseHeightPreferredAboveMax() {
        val available = listOf(360, 480, 720)
        assertEquals(720, VideoMath.chooseHeight(available, 1080))
        assertEquals(720, VideoMath.chooseHeight(available, 1440))
    }

    @Test
    fun testChooseHeightPreferredBelowMin() {
        val available = listOf(480, 720, 1080)
        assertEquals(480, VideoMath.chooseHeight(available, 240))
        assertEquals(480, VideoMath.chooseHeight(available, 360))
    }

    @Test
    fun testChooseHeightDuplicatesAndUnsorted() {
        val available = listOf(1080, 480, 720, 360, 480, 1080)
        assertEquals(720, VideoMath.chooseHeight(available, 800))
        assertEquals(480, VideoMath.chooseHeight(available, 500))
    }

    @Test
    fun testResolveGestureZone() {
        assertEquals(GestureZone.BRIGHTNESS, VideoMath.resolveGestureZone(100f, 1080f))
        assertEquals(GestureZone.BRIGHTNESS, VideoMath.resolveGestureZone(539f, 1080f))
        assertEquals(GestureZone.VOLUME, VideoMath.resolveGestureZone(541f, 1080f))
        assertEquals(GestureZone.VOLUME, VideoMath.resolveGestureZone(900f, 1080f))
    }

    @Test
    fun testResolveDoubleTapSeekDeltaMs() {
        assertEquals(-10000L, VideoMath.resolveDoubleTapSeekDeltaMs(200f, 1080f, 10))
        assertEquals(10000L, VideoMath.resolveDoubleTapSeekDeltaMs(800f, 1080f, 10))
        assertEquals(-15000L, VideoMath.resolveDoubleTapSeekDeltaMs(300f, 1080f, 15))
        assertEquals(15000L, VideoMath.resolveDoubleTapSeekDeltaMs(700f, 1080f, 15))
    }
}
