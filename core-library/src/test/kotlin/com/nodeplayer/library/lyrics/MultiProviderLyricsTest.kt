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

class MultiProviderLyricsTest {

    @Test
    fun testBiniLyricsParseIdentifyResponse() {
        val sampleJson = """
            {
                "total": 1,
                "source": "HIT-EXACT",
                "results": [
                    {
                        "track_name": "Blinding Lights",
                        "artist_name": "The Weeknd",
                        "album_name": "After Hours",
                        "isrc": "USUG11904206",
                        "timing_type": "word",
                        "lyricsUrl": "https://lrc.red/s/USUG11904206.ttml"
                    }
                ]
            }
        """.trimIndent()

        val hit = BiniLyrics.parseIdentifyResponse(sampleJson)
        assertNotNull(hit)
        assertEquals("USUG11904206", hit.isrc)
        assertEquals("https://lrc.red/s/USUG11904206.ttml", hit.lyricsUrl)
        assertEquals("Blinding Lights", hit.trackName)
        assertEquals("The Weeknd", hit.artistName)
        assertEquals("word", hit.timingType)
    }

    @Test
    fun testLyricsPlusParseSyllablesIntoWords() {
        val sampleJson = """
            {
                "type": "syllables",
                "lyrics": [
                    {
                        "time": 1000,
                        "duration": 2000,
                        "text": "Hello world",
                        "syllabus": [
                            {"time": 1000, "duration": 400, "text": "Hel"},
                            {"time": 1400, "duration": 400, "text": "lo "},
                            {"time": 1800, "duration": 800, "text": "world "}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val lyrics = LyricsPlus.parseLyricsPlusJson(sampleJson, trackId = 42L)
        assertNotNull(lyrics)
        assertTrue(lyrics.hasWordTimings)
        assertEquals(1, lyrics.lines.size)

        val line = lyrics.lines[0]
        assertEquals(1000L, line.timestampMs)
        assertEquals(3000L, line.endTimeMs)
        assertEquals(2, line.words.size)
        assertEquals("Hello ", line.words[0].text)
        assertEquals("world ", line.words[1].text)
        assertEquals(1000L, line.words[0].startMs)
        assertEquals(1800L, line.words[0].endMs)
    }

    @Test
    fun testKuGouSanitizeLrcStripsCreditsAndDetectsInstrumental() {
        val lrcWithCredits = """
            [00:00.00]作词: 张三
            [00:01.00]作曲: 李四
            [00:02.00]编曲: 王五
            [00:05.50]First real sung line of the song
            [00:10.00]Second real sung line
            [00:20.00]Lyrics by Contributor
        """.trimIndent()

        val sanitized = KuGou.sanitizeLrc(lrcWithCredits)
        assertTrue(sanitized.contains("First real sung line"))
        assertTrue(sanitized.contains("Second real sung line"))
        assertFalse(sanitized.contains("作词:"))
        assertFalse(sanitized.contains("作曲:"))
        assertFalse(sanitized.contains("编曲:"))
        assertFalse(sanitized.contains("Lyrics by"))

        val instrumentalLrc = """
            [00:00.00]纯音乐，请欣赏
        """.trimIndent()
        val emptySanitized = KuGou.sanitizeLrc(instrumentalLrc)
        assertEquals("", emptySanitized)
    }

    @Test
    fun testKuGouParseCandidate() {
        val sampleSearchJson = """
            {
                "errcode": 0,
                "candidates": [
                    {
                        "id": "12345678",
                        "accesskey": "87654321fedcba",
                        "duration": 215000,
                        "singer": "Artist",
                        "song": "Song"
                    }
                ]
            }
        """.trimIndent()

        val candidate = KuGou.parseCandidate(sampleSearchJson)
        assertNotNull(candidate)
        assertEquals("12345678", candidate.id)
        assertEquals("87654321fedcba", candidate.accessKey)
    }

    @Test
    fun testLyricsRepositoryPrioritizesWordSyncedOverLineSynced() = runBlocking {
        val memoryCache = LyricsMemoryCache(50)
        val repo = LyricsRepository(memoryCache = memoryCache)

        val track = Track(
            id = 100L,
            title = "Dummy Title",
            artist = "Dummy Artist",
            album = "Dummy Album",
            durationMs = 180_000L,
            path = ""
        )

        // Seed cache with word-synced lyrics
        val wordSyncedLyrics = Lyrics(
            trackId = track.id,
            lines = listOf(LyricsLine(timestampMs = 500L, text = "Word synced line")),
            isSynced = true,
            hasWordTimings = true
        )
        memoryCache.put("dummy_title_dummy_artist", wordSyncedLyrics, isNegative = false)

        val result = repo.getLyrics(track)
        assertNotNull(result)
        assertTrue(result.hasWordTimings)
        assertEquals("Word synced line", result.lines[0].text)
    }
}
