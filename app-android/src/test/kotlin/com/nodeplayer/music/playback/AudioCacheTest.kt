package com.nodeplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioCacheTest {

    @Test
    fun testExtractVideoIdFromSyntheticUri() {
        val url = "nodeplayer://youtube/dQw4w9WgXcQ"
        val videoId = AudioCache.extractVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun testExtractVideoIdFromYouTubeWatchUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"
        val videoId = AudioCache.extractVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun testExtractVideoIdFromYouTuBeUrl() {
        val url = "https://youtu.be/dQw4w9WgXcQ?si=abcdef"
        val videoId = AudioCache.extractVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun testExtractVideoIdFromShortsUrl() {
        val url = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val videoId = AudioCache.extractVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun testExtractVideoIdFromVideoPlaybackQuery() {
        val url = "https://rr1---sn-4g5edn6s.googlevideo.com/videoplayback?expire=12345&v=dQw4w9WgXcQ&itag=251"
        val videoId = AudioCache.extractVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun testExtractVideoIdFromLocalContentUriReturnsNull() {
        val url = "content://media/external/audio/media/12345"
        val videoId = AudioCache.extractVideoId(url)
        assertNull(videoId)
    }

    @Test
    fun testGetEffectiveCacheKey() {
        val syntheticUrl = "nodeplayer://youtube/dQw4w9WgXcQ"
        val key = AudioCache.getEffectiveCacheKey(syntheticUrl)
        assertEquals("nodeplayer_yt_dQw4w9WgXcQ", key)

        val localUrl = "file:///sdcard/Music/song.flac"
        val localKey = AudioCache.getEffectiveCacheKey(localUrl)
        assertEquals("file:///sdcard/Music/song.flac", localKey)
    }
    @Test
    fun expiredStreamUrlsAndTheirUserAgentsAreRemoved() {
        val url = "https://example.com/audio?expire=1"
        AudioCache.cacheResolvedUrl("expired_video", url, "test-agent")
        assertNull(AudioCache.getCachedResolvedUrl("expired_video"))
        assertNull(AudioCache.getUserAgentForUrl(url))
    }

    @Test
    fun unexpiredStreamUrlsKeepTheirClientUserAgent() {
        val expiry = System.currentTimeMillis() / 1000L + 3600L
        val url = "https://example.com/audio?expire=$expiry"
        AudioCache.cacheResolvedUrl("live_video", url, "test-agent")
        assertEquals(url, AudioCache.getCachedResolvedUrl("live_video"))
        assertEquals("test-agent", AudioCache.getUserAgentForUrl(url))
        AudioCache.invalidateUrl("live_video")
        assertNull(AudioCache.getUserAgentForUrl(url))
    }

}
