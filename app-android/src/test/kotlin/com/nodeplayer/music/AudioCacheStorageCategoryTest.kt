package com.nodeplayer.music

import com.nodeplayer.library.storage.StorageCategoryRegistry
import com.nodeplayer.music.playback.AudioCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

class AudioCacheStorageCategoryTest {

    private lateinit var fakeCacheDir: File

    @Before
    fun setUp() {
        fakeCacheDir = File(System.getProperty("java.io.tmpdir"), "np_cache_test_${System.currentTimeMillis()}")
        fakeCacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        fakeCacheDir.deleteRecursively()
        StorageCategoryRegistry.unregisterCategory(StorageCategoryRegistry.CATEGORY_AUDIO_CACHE)
    }

    @Test
    fun testAudioCacheCategoryUsesCorrectDirectoryName() = runBlocking {
        assertEquals("audio_cache", AudioCache.CACHE_DIR_NAME)

        val audioCacheDir = fakeCacheDir.resolve(AudioCache.CACHE_DIR_NAME).apply { mkdirs() }
        val dummyFile = File(audioCacheDir, "chunk_0.exo")
        dummyFile.writeBytes(ByteArray(2048)) // 2 KB

        val reg = StorageCategoryRegistry
        reg.registerCategory(
            reg.createDirectoryCategory(
                id = reg.CATEGORY_AUDIO_CACHE,
                displayName = "Audio & Stream Cache",
                description = "ExoPlayer LRU stream cache",
                directory = fakeCacheDir.resolve(AudioCache.CACHE_DIR_NAME)
            )
        )

        val cat = reg.getCategory(reg.CATEGORY_AUDIO_CACHE)
        assertNotNull(cat)
        val size = cat!!.sizeBytes()
        assertEquals("Audio cache size should reflect contents of audio_cache folder", 2048L, size)
    }
}
