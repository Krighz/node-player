package com.nodeplayer.library.lyrics

import com.nodeplayer.model.LyricAlignment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TtmlLyricsTest {

    @Test
    fun testParseValidAppleMusicTtmlWithMultiSyllableSpans() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:10.000" end="00:15.000" ttm:agent="v1">
                    <span begin="00:10.000" end="00:10.500">Hel</span>
                    <span begin="00:10.500" end="00:11.000">lo </span>
                    <span begin="00:11.000" end="00:12.000">world</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = TtmlLyrics.parse(ttml, trackId = 42L, source = "lrc.red")
        assertEquals(42L, lyrics.trackId)
        assertTrue(lyrics.isSynced)
        assertTrue(lyrics.hasWordTimings)
        assertEquals(1, lyrics.lines.size)

        val line = lyrics.lines[0]
        assertEquals(10_000L, line.timestampMs)
        assertEquals(15_000L, line.endTimeMs)
        assertEquals("Hello world", line.text)
        assertEquals(LyricAlignment.Start, line.alignment)
        assertEquals(2, line.words.size)

        val word1 = line.words[0]
        assertEquals("Hello", word1.text)
        assertEquals(10_000L, word1.startMs)
        assertEquals(11_000L, word1.endMs)
        assertEquals(2, word1.syllables.size)
        assertEquals(0, word1.syllables[0].startChar)
        assertEquals(3, word1.syllables[0].endChar)
        assertEquals(3, word1.syllables[1].startChar)
        assertEquals(5, word1.syllables[1].endChar)

        val word2 = line.words[1]
        assertEquals("world", word2.text)
        assertEquals(11_000L, word2.startMs)
        assertEquals(12_000L, word2.endMs)
    }

    @Test
    fun testParseBackingVocalsAttachedToBackground() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="20.0s" end="25.0s" ttm:agent="v1">
                    <span begin="20.0s" end="22.0s">Lead vocal</span>
                    <span ttm:role="x-bg" begin="22.5s" end="24.5s">
                      <span begin="22.5s" end="24.5s">echo echo</span>
                    </span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = TtmlLyrics.parse(ttml)
        assertEquals(1, lyrics.lines.size)
        val line = lyrics.lines[0]
        assertEquals("Lead vocal", line.text)
        assertNotNull(line.background)
        assertEquals("echo echo", line.background?.text)
        assertEquals(22_500L, line.background?.timestampMs)
    }

    @Test
    fun testDuetAlternationMapsV1AndV2() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <metadata>
                  <ttm:agent xml:id="v1" type="person" />
                  <ttm:agent xml:id="v2" type="person" />
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="1.0s" end="3.0s" ttm:agent="v1">
                    <span begin="1.0s" end="3.0s">Singer One</span>
                  </p>
                  <p begin="4.0s" end="6.0s" ttm:agent="v2">
                    <span begin="4.0s" end="6.0s">Singer Two</span>
                  </p>
                  <p begin="7.0s" end="9.0s" ttm:agent="v1">
                    <span begin="7.0s" end="9.0s">Singer One Again</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = TtmlLyrics.parse(ttml)
        assertEquals(3, lyrics.lines.size)
        assertEquals(LyricAlignment.Start, lyrics.lines[0].alignment)
        assertEquals(LyricAlignment.End, lyrics.lines[1].alignment)
        assertEquals(LyricAlignment.Start, lyrics.lines[2].alignment)
    }

    @Test
    fun testXxePayloadDisallowedSafely() {
        val maliciousXxe = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <!DOCTYPE foo [
              <!ELEMENT foo ANY >
              <!ENTITY xxe SYSTEM "file:///etc/passwd" >
            ]>
            <tt>
              <body>
                <div>
                  <p begin="1.0s" end="2.0s">&xxe;</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        // Hardened DocumentBuilderFactory must safely reject DOCTYPE declarations without throwing uncaught security crash
        val lyrics = TtmlLyrics.parse(maliciousXxe)
        // Parse safely catches the exception and returns empty lyrics
        assertTrue(lyrics.lines.isEmpty())
        assertFalse(lyrics.isSynced)
    }

    @Test
    fun testClockTimeParsingFormats() {
        assertEquals(27395L, TtmlLyrics.time("27.395"))
        assertEquals(27395L, TtmlLyrics.time("27.395s"))
        assertEquals(27395L, TtmlLyrics.time("27395ms"))
        assertEquals(65200L, TtmlLyrics.time("1:05.20"))
        assertEquals(3723400L, TtmlLyrics.time("1:02:03.4"))
    }
}
