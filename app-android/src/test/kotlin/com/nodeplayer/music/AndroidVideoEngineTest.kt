package com.nodeplayer.music

import com.nodeplayer.model.video.VideoPlayerState
import com.nodeplayer.model.video.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVideoEngineTest {

    @Test
    fun testVideoPlayerStateDefaults() {
        val state = VideoPlayerState()
        assertFalse(state.isVideoActive)
        assertFalse(state.isAvailable)
        assertEquals(VideoQuality.P480, state.selectedQuality)
        assertFalse(state.isBuffering)
        assertFalse(state.isCinemaExpanded)
    }

    @Test
    fun testVideoQualityMapping() {
        assertEquals(VideoQuality.AUTO, VideoQuality.fromKey("auto"))
        assertEquals(VideoQuality.P1080, VideoQuality.fromKey("1080"))
        assertEquals(VideoQuality.P720, VideoQuality.fromKey("720"))
        assertEquals(VideoQuality.P480, VideoQuality.fromKey("480"))
        assertEquals(VideoQuality.P360, VideoQuality.fromKey("360"))
        assertEquals(VideoQuality.AUTO, VideoQuality.fromKey("unknown"))

        assertEquals(VideoQuality.P1080, VideoQuality.fromHeight(1080))
        assertEquals(VideoQuality.P720, VideoQuality.fromHeight(720))
        assertEquals(VideoQuality.P480, VideoQuality.fromHeight(480))
        assertEquals(VideoQuality.P360, VideoQuality.fromHeight(360))
        assertEquals(VideoQuality.AUTO, VideoQuality.fromHeight(null))
    }

    @Test
    fun testPipGatingLogic() {
        fun shouldEnterPip(isVideoActive: Boolean, pipSettingEnabled: Boolean): Boolean {
            return isVideoActive && pipSettingEnabled
        }

        assertTrue(shouldEnterPip(isVideoActive = true, pipSettingEnabled = true))
        assertFalse(shouldEnterPip(isVideoActive = false, pipSettingEnabled = true))
        assertFalse(shouldEnterPip(isVideoActive = true, pipSettingEnabled = false))
        assertFalse(shouldEnterPip(isVideoActive = false, pipSettingEnabled = false))
    }

    @Test
    fun testBackgroundDataSaverGatingLogic() {
        fun shouldDisableVideoTrack(dataSaverSetting: Boolean, inPip: Boolean): Boolean {
            return dataSaverSetting && !inPip
        }

        assertTrue(shouldDisableVideoTrack(dataSaverSetting = true, inPip = false))
        assertFalse(shouldDisableVideoTrack(dataSaverSetting = true, inPip = true))
        assertFalse(shouldDisableVideoTrack(dataSaverSetting = false, inPip = false))
    }

    @Test
    fun testVideoGestureZonesAndDoubleTap() {
        val width = 1080f
        val leftZone = com.nodeplayer.model.video.VideoMath.resolveGestureZone(x = 200f, width = width)
        val rightZone = com.nodeplayer.model.video.VideoMath.resolveGestureZone(x = 800f, width = width)
        assertEquals(com.nodeplayer.model.video.GestureZone.BRIGHTNESS, leftZone)
        assertEquals(com.nodeplayer.model.video.GestureZone.VOLUME, rightZone)

        val backwardSeekMs = com.nodeplayer.model.video.VideoMath.resolveDoubleTapSeekDeltaMs(x = 200f, width = width, seekStepSec = 10)
        val forwardSeekMs = com.nodeplayer.model.video.VideoMath.resolveDoubleTapSeekDeltaMs(x = 800f, width = width, seekStepSec = 10)
        assertEquals(-10000L, backwardSeekMs)
        assertEquals(10000L, forwardSeekMs)
    }
}
