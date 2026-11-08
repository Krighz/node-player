package com.nodeplayer.desktop.audio

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopAudioCacheTest {

    private lateinit var tempCacheDir: File
    private lateinit var audioCache: DesktopAudioCache

    @Before
    fun setUp() {
        tempCacheDir = Files.createTempDirectory("desktop_audio_cache_test").toFile()
        audioCache = DesktopAudioCache(cacheDir = tempCacheDir, maxCacheBytes = 1000L)
    }

    @After
    fun tearDown() {
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testExtractVideoId() {
        // Synthetic URI
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("nodeplayer://youtube/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("nodeplayer://youtube/dQw4w9WgXcQ?extra=param"))
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("nodeplayer://youtube/dQw4w9WgXcQ/"))

        // Standard watch URL
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"))

        // Short URL
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))

        // Shorts URL
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))

        // Raw 11-char ID
        assertEquals("dQw4w9WgXcQ", audioCache.extractVideoId("dQw4w9WgXcQ"))

        // Invalid or non-youtube
        assertNull(audioCache.extractVideoId("https://example.com/audio.mp3"))
        assertNull(audioCache.extractVideoId("C:/Music/test.flac"))
    }

    @Test
    fun testCacheFileAccessAndTracking() {
        val videoId = "testVid1234"
        assertFalse(audioCache.isCached(videoId))
        assertNull(audioCache.getCachedFile(videoId))

        // Create a cache file manually
        val file = audioCache.getCacheFileForWriting(videoId, "opus")
        assertEquals("$videoId.opus", file.name)
        file.writeBytes(ByteArray(256) { 0x42 })

        assertTrue(audioCache.isCached(videoId))
        assertTrue(audioCache.isCached("nodeplayer://youtube/$videoId"))

        val cachedFile = audioCache.getCachedFile(videoId)
        assertNotNull(cachedFile)
        assertEquals(256L, cachedFile?.length())
    }

    @Test
    fun testLruEvictionWhenQuotaExceeded() {
        // Limit is 1000 bytes
        // Create 3 files of 400 bytes each (total 1200 bytes)
        val file1 = File(tempCacheDir, "vid00000001.opus").apply {
            writeBytes(ByteArray(400) { 1 })
            setLastModified(1000L)
        }
        val file2 = File(tempCacheDir, "vid00000002.opus").apply {
            writeBytes(ByteArray(400) { 2 })
            setLastModified(2000L)
        }
        val file3 = File(tempCacheDir, "vid00000003.opus").apply {
            writeBytes(ByteArray(400) { 3 })
            setLastModified(3000L)
        }

        assertEquals(1200L, audioCache.getCacheSize())

        // Enforce LRU limit
        audioCache.enforceLruLimit()

        // Total bytes must be <= 1000L
        assertTrue(audioCache.getCacheSize() <= 1000L)

        // file1 was oldest (lastModified=1000), so it should have been deleted
        assertFalse(file1.exists())
        // file2 and file3 should still exist (400 + 400 = 800 bytes <= 1000)
        assertTrue(file2.exists())
        assertTrue(file3.exists())
    }

    @Test
    fun testDefaultCacheDirectoryResolved() {
        val dir = DesktopAudioCache.defaultCacheDirectory()
        assertTrue(dir.path.contains("audio_cache"))
    }
}
