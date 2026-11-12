package com.nodeplayer.library.lyrics

import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.LyricsLine
import com.nodeplayer.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsRepositoryTest {

    private fun sampleTrack(
        id: Long = 1L,
        title: String = "Starboy",
        artist: String = "The Weeknd",
        album: String = "Starboy",
        durationMs: Long = 230_000L,
        path: String = ""
    ): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = path
        )
    }

    @Test
    fun testMemoryCacheHits() = runBlocking {
        val memoryCache = LyricsMemoryCache(50)
        val repo = LyricsRepository(memoryCache = memoryCache)
        val track = sampleTrack()

        val mockLyrics = Lyrics(
            trackId = track.id,
            lines = listOf(LyricsLine(timestampMs = 1000L, text = "Hello")),
            isSynced = true,
            hasWordTimings = true
        )

        val key = "starboy_the_weeknd"
        memoryCache.put(key, mockLyrics, isNegative = false)

        val fetched = repo.getLyrics(track)
        assertNotNull(fetched)
        assertEquals("Hello", fetched.lines[0].text)
        assertEquals(1, memoryCache.size())
    }

    @Test
    fun testWordEstimationFallbackForLineSyncedLyrics() = runBlocking {
        val memoryCache = LyricsMemoryCache(50)
        val fakeProvider = object : LyricsProvider {
            override val name: String = "FakeLrcLib"
            override suspend fun fetchLyrics(
                title: String,
                artist: String,
                album: String?,
                durationSec: Int?
            ): Result<Lyrics?> {
                val lines = listOf(
                    LyricsLine(timestampMs = 1000L, text = "I am singing words", endTimeMs = 5000L)
                )
                return Result.success(Lyrics(trackId = 1L, lines = lines, isSynced = true, hasWordTimings = false))
            }
        }

        val repo = LyricsRepository(providers = listOf(fakeProvider), memoryCache = memoryCache)
        val track = sampleTrack(title = "Uncached Title", artist = "Uncached Artist")

        val lyrics = repo.getLyrics(track)
        assertNotNull(lyrics)
        assertTrue(lyrics.hasWordTimings)
        assertEquals(1, lyrics.lines.size)

        val line = lyrics.lines[0]
        assertTrue(line.isEstimatedWords)
        assertEquals(4, line.words.size)
        assertEquals("I ", line.words[0].text)
        assertEquals("words", line.words[3].text)
        assertEquals(1000L, line.words[0].startMs)
        assertEquals(5000L, line.words[3].endMs)
    }

    @Test
    fun testMemoryCacheEvictionAt50() {
        val cache = LyricsMemoryCache(maxSize = 2)
        cache.put("k1", null, isNegative = true)
        cache.put("k2", null, isNegative = true)
        assertEquals(2, cache.size())

        cache.put("k3", null, isNegative = true)
        assertEquals(2, cache.size())
        assertNull(cache.get("k1"))
        assertNotNull(cache.get("k2"))
        assertNotNull(cache.get("k3"))
    }
}
