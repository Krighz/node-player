package com.nodeplayer.library.playlist

import com.nodeplayer.model.playlist.ArtistTitleOrder
import com.nodeplayer.model.playlist.ImportConfig
import org.junit.Assert.*
import org.junit.Test

class UniversalPlaylistParserTest {

    @Test
    fun testCsvRfc4180QuotesAndEscapedQuotes() {
        val csv = "Title,Artist\n\"Song with \"\"Quotes\"\"\",Artist A"
        val rows = UniversalPlaylistParser.parseCsv(csv)
        assertEquals(2, rows.size)
        assertEquals("Song with \"Quotes\"", rows[1][0])
        assertEquals("Artist A", rows[1][1])
    }

    @Test
    fun testCsvCommaAndNewlineInsideQuotes() {
        val csv = "Title,Artist\n\"Song, With, Commas\nAnd Newline\",\"Artist, Duo\""
        val rows = UniversalPlaylistParser.parseCsv(csv)
        assertEquals(2, rows.size)
        assertEquals("Song, With, Commas\nAnd Newline", rows[1][0])
        assertEquals("Artist, Duo", rows[1][1])
    }

    @Test
    fun testCsvUtf8BomAndCrlf() {
        val csv = "\uFEFFTitle,Artist\r\nSong One,Artist One\r\nSong Two,Artist Two"
        val rows = UniversalPlaylistParser.parseCsv(csv)
        assertEquals(3, rows.size)
        assertEquals("Title", rows[0][0])
        assertEquals("Song One", rows[1][0])
        assertEquals("Song Two", rows[2][0])
    }

    @Test
    fun testCsvDelimiterAutodetectionSemicolonAndTab() {
        val semiCsv = "Title;Artist;Duration\nTrack A;Artist A;180"
        assertEquals(';', UniversalPlaylistParser.detectDelimiter("Title;Artist;Duration"))
        val semiParsed = UniversalPlaylistParser.parseCsvContent(semiCsv)
        assertEquals(1, semiParsed.size)
        assertEquals("Track A", semiParsed[0].title)
        assertEquals("Artist A", semiParsed[0].artist)

        val tabCsv = "Title\tArtist\tAlbum\nTrack B\tArtist B\tAlbum B"
        assertEquals('\t', UniversalPlaylistParser.detectDelimiter("Title\tArtist\tAlbum"))
        val tabParsed = UniversalPlaylistParser.parseCsvContent(tabCsv)
        assertEquals(1, tabParsed.size)
        assertEquals("Track B", tabParsed[0].title)
    }

    @Test
    fun testCsvMissingTitleColumnThrowsException() {
        val csv = "Artist,Album,Duration\nArtist A,Album A,200"
        try {
            UniversalPlaylistParser.parseCsvContent(csv)
            fail("Expected IllegalArgumentException for missing title")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("missing required Title", ignoreCase = true))
        }
    }

    @Test
    fun testSpotifyLikeHeaderMapping() {
        val spotifyCsv = """
            Track Name,Artist Name(s),Album Name,Duration (ms),ISRC
            "Solar Echo","Nova Collective","Astral Journey",215000,"USABC1234567"
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseCsvContent(spotifyCsv)
        assertEquals(1, parsed.size)
        assertEquals("Solar Echo", parsed[0].title)
        assertEquals("Nova Collective", parsed[0].artist)
        assertEquals("Astral Journey", parsed[0].album)
        assertEquals(215000L, parsed[0].durationMs)
        assertEquals("USABC1234567", parsed[0].isrc)
    }

    @Test
    fun testTuneMyMusicLikeHeaderMapping() {
        val tmmCsv = """
            Track name,Artist name,Album,Playlist name
            "Echo Drift","Lunar Bloom","Nightfall Session","Focus Beats"
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseCsvContent(tmmCsv)
        assertEquals(1, parsed.size)
        assertEquals("Echo Drift", parsed[0].title)
        assertEquals("Lunar Bloom", parsed[0].artist)
        assertEquals("Nightfall Session", parsed[0].album)
    }

    @Test
    fun testAppleMusicLikeHeaderMapping() {
        val appleCsv = """
            Name,Artist,Album,Time
            "Velvet Dusk","Starlight Orchestra","Symphony No. 5","3:45"
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseCsvContent(appleCsv)
        assertEquals(1, parsed.size)
        assertEquals("Velvet Dusk", parsed[0].title)
        assertEquals("Starlight Orchestra", parsed[0].artist)
        assertEquals("Symphony No. 5", parsed[0].album)
        assertEquals(225000L, parsed[0].durationMs)
    }

    @Test
    fun testDurationParsingVariations() {
        assertEquals(240000L, UniversalPlaylistParser.parseDuration("240000")) // Pure ms (> 10000)
        assertEquals(185000L, UniversalPlaylistParser.parseDuration("3:05")) // mm:ss
        assertEquals(3723000L, UniversalPlaylistParser.parseDuration("1:02:03")) // h:mm:ss
        assertEquals(150000L, UniversalPlaylistParser.parseDuration("150")) // Pure seconds (< 10000)
        assertEquals(182500L, UniversalPlaylistParser.parseDuration("182.5")) // Float seconds
        assertEquals(0L, UniversalPlaylistParser.parseDuration("invalid_text"))
        assertEquals(0L, UniversalPlaylistParser.parseDuration(null))
        assertEquals(0L, UniversalPlaylistParser.parseDuration(""))
    }

    @Test
    fun testTxtNumberedListsAndDashVariants() {
        val txt = """
            # This is a comment
            
            1. Tycho - Awake
            02 – Bonobo – Cirrus
            3) Kiasmos — Blurred
            4: Olafur Arnalds - Spiral
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseTxtContent(txt)
        assertEquals(4, parsed.size)
        assertEquals("Awake", parsed[0].title)
        assertEquals("Tycho", parsed[0].artist)
        assertEquals("Cirrus", parsed[1].title)
        assertEquals("Bonobo", parsed[1].artist)
        assertEquals("Blurred", parsed[2].title)
        assertEquals("Kiasmos", parsed[2].artist)
        assertEquals("Spiral", parsed[3].title)
        assertEquals("Olafur Arnalds", parsed[3].artist)
    }

    @Test
    fun testTxtTitleByArtistWithInternalBy() {
        val txt = """
            Stand By Me by Ben E. King
            Driven by You by Brian May
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseTxtContent(txt)
        assertEquals(2, parsed.size)
        assertEquals("Stand By Me", parsed[0].title)
        assertEquals("Ben E. King", parsed[0].artist)
        assertEquals("Driven by You", parsed[1].title)
        assertEquals("Brian May", parsed[1].artist)
    }

    @Test
    fun testTxtTitleArtistOrderConfiguration() {
        val txt = "Song Title - Famous Singer"
        val parsed = UniversalPlaylistParser.parseTxtContent(
            txt,
            ImportConfig(artistTitleOrder = ArtistTitleOrder.TITLE_ARTIST)
        )
        assertEquals(1, parsed.size)
        assertEquals("Song Title", parsed[0].title)
        assertEquals("Famous Singer", parsed[0].artist)
    }

    @Test
    fun testTxtYouTubeUrls() {
        val txt = """
            https://youtu.be/dQw4w9WgXcQ
            https://music.youtube.com/watch?v=9bZkp7q19f0&feature=share
        """.trimIndent()

        val parsed = UniversalPlaylistParser.parseTxtContent(txt)
        assertEquals(2, parsed.size)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsed[0].url)
        assertEquals("https://www.youtube.com/watch?v=9bZkp7q19f0", parsed[1].url)
    }

    @Test
    fun testTxtMaxLinesEnforcement() {
        val txt = (1..50).joinToString("\n") { "$it. Track $it - Artist $it" }
        val parsed = UniversalPlaylistParser.parseTxtContent(txt, ImportConfig(maxLines = 10))
        assertEquals(10, parsed.size)
    }

    @Test
    fun testDecodeBytesUtf8WithBom() {
        val original = "Title,Artist\nSong A,Artist A"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + original.toByteArray(Charsets.UTF_8)
        val decoded = UniversalPlaylistParser.decodeBytesWithFallback(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun testDecodeBytesUtf16LeWithBom() {
        val original = "Title\tArtist\r\nExcel Song\tExcel Artist"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + original.toByteArray(Charsets.UTF_16LE)
        val decoded = UniversalPlaylistParser.decodeBytesWithFallback(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun testDecodeBytesUtf16BeWithBom() {
        val original = "Title\tArtist\r\nSong BE\tArtist BE"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + original.toByteArray(Charsets.UTF_16BE)
        val decoded = UniversalPlaylistParser.decodeBytesWithFallback(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun testDecodeBytesWindows1252Fallback() {
        // "Café" in Windows-1252: 'C', 'a', 'f', 0xE9
        val bytes = byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        val decoded = UniversalPlaylistParser.decodeBytesWithFallback(bytes)
        assertEquals("Café", decoded)
    }

    @Test
    fun testFileAndPastedContentYieldIdenticalCandidates() {
        val content = "Title,Artist\nStarboy,The Weeknd\nBlinding Lights,The Weeknd"
        // Simulate file bytes (e.g. UTF-8 with BOM)
        val fileBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content.toByteArray(Charsets.UTF_8)
        val fileDecoded = UniversalPlaylistParser.decodeBytesWithFallback(fileBytes)
        
        val fileCandidates = UniversalPlaylistParser.parseAuto(fileDecoded)
        val pastedCandidates = UniversalPlaylistParser.parseAuto(content)

        assertEquals(pastedCandidates.size, fileCandidates.size)
        assertEquals(pastedCandidates[0].title, fileCandidates[0].title)
        assertEquals(pastedCandidates[0].artist, fileCandidates[0].artist)
        assertEquals(pastedCandidates[1].title, fileCandidates[1].title)
        assertEquals(pastedCandidates[1].artist, fileCandidates[1].artist)
    }
}
