package com.nodeplayer.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCacheLruTest {

    @BeforeTest
    fun setUp() {
        ArtworkCache.clear()
    }

    @AfterTest
    fun tearDown() {
        ArtworkCache.clear()
    }

    @Test
    fun testBitmapCacheEvictsOnlyLeastRecentlyUsedWithoutFullWipe() {
        val capacity = ArtworkCache.MAX_MEMORY_ENTRIES
        repeat(capacity) { idx ->
            ArtworkCache.put("track_$idx", ImageBitmap(2, 2))
        }
        assertEquals(capacity, ArtworkCache.memorySize())

        // Promote track_0 as most-recently-used by reading it
        assertNotNull(ArtworkCache.get("track_0"))

        // Insert one entry beyond capacity -> should evict track_1 (now LRU), NOT wipe all 300
        ArtworkCache.put("track_overflow", ImageBitmap(2, 2))

        assertEquals(capacity, ArtworkCache.memorySize())
        assertNotNull(ArtworkCache.get("track_0"), "Recently accessed track_0 must be promoted and retained")
        assertNull(ArtworkCache.get("track_1"), "Oldest unaccessed track_1 must be evicted")
        assertNotNull(ArtworkCache.get("track_2"), "Remaining entries must not be wiped on overflow")
        assertNotNull(ArtworkCache.get("track_overflow"), "Newly inserted entry must be retained")
    }

    @Test
    fun testByteCacheEvictsOnlyLeastRecentlyUsedWithoutFullWipe() {
        val capacity = ArtworkCache.MAX_BYTE_ENTRIES
        repeat(capacity) { idx ->
            ArtworkCache.putBytes("bytes_$idx", byteArrayOf(idx.toByte(), 1, 2))
        }
        assertEquals(capacity, ArtworkCache.byteSize())

        // Promote bytes_0 via getBytes
        assertNotNull(ArtworkCache.getBytes("bytes_0"))

        // Insert one entry beyond capacity -> should evict bytes_1, NOT clear all 200
        ArtworkCache.putBytes("bytes_overflow", byteArrayOf(99, 100))

        assertEquals(capacity, ArtworkCache.byteSize())
        assertNotNull(ArtworkCache.getBytes("bytes_0"), "Recently accessed bytes_0 must be promoted and retained")
        assertNull(ArtworkCache.getBytes("bytes_1"), "Oldest unaccessed bytes_1 must be evicted")
        assertNotNull(ArtworkCache.getBytes("bytes_2"), "Remaining byte entries must not be wiped on overflow")
        assertNotNull(ArtworkCache.getBytes("bytes_overflow"), "Newly inserted byte entry must be retained")
    }

    @Test
    fun testSizedKeySeparatesResolutionBuckets() {
        val smallKey = ArtworkCache.sizedKey("song_1", 144)
        val heroKey = ArtworkCache.sizedKey("song_1", 512)
        assertTrue(smallKey != heroKey, "Different target resolutions should map to distinct cache buckets")

        val smallBmp = ImageBitmap(4, 4)
        val heroBmp = ImageBitmap(8, 8)
        ArtworkCache.put(smallKey, smallBmp)
        ArtworkCache.put(heroKey, heroBmp)

        assertEquals(4, ArtworkCache.get(smallKey)?.width)
        assertEquals(8, ArtworkCache.get(heroKey)?.width)
    }
}
