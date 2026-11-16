package com.nodeplayer.network.discord

import com.nodeplayer.model.presence.DiscordIpcConstants
import com.nodeplayer.model.presence.PresenceState
import org.junit.Assert.*
import org.junit.Test

class DiscordActivityBuilderTest {

    @Test
    fun testSanitizeFieldLengthBoundaries() {
        // Empty or 1 character padded to 2
        assertEquals("A ", DiscordActivityBuilder.sanitizeField("A"))
        assertEquals("  ", DiscordActivityBuilder.sanitizeField(""))

        // Normal text remains untouched
        assertEquals("Bohemian Rhapsody", DiscordActivityBuilder.sanitizeField("Bohemian Rhapsody"))

        // Over 128 characters is truncated
        val longText = "a".repeat(150)
        val sanitized = DiscordActivityBuilder.sanitizeField(longText)
        assertEquals(128, sanitized.length)
        assertEquals("a".repeat(128), sanitized)
    }

    @Test
    fun testBuildSetActivityJsonStandard() {
        val state = PresenceState(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            coverUrl = "https://example.com/cover.jpg",
            durationMs = 230_000L,
            positionMs = 30_000L,
            isPlaying = true
        )

        val json = DiscordActivityBuilder.buildSetActivityJson(
            state = state,
            processId = 1234,
            currentEpochMs = 1_700_000_000_000L,
            nonce = "test-nonce-1"
        )

        assertTrue(json.contains(""""cmd":"SET_ACTIVITY""""))
        assertTrue(json.contains(""""pid":1234"""))
        assertTrue(json.contains(""""details":"Starboy""""))
        assertTrue(json.contains(""""state":"by The Weeknd • Starboy""""))
        assertTrue(json.contains(""""large_image":"https://example.com/cover.jpg""""))
        assertTrue(json.contains(""""large_text":"Starboy""""))
        assertTrue(json.contains(""""nonce":"test-nonce-1""""))
        assertTrue(json.contains(""""type":2"""))
    }

    @Test
    fun testBuildSetActivityWithoutAlbumOrCover() {
        val state = PresenceState(
            title = "Single Track",
            artist = "Independent Artist",
            album = null,
            coverUrl = null,
            durationMs = 180_000L,
            positionMs = 0L,
            isPlaying = true
        )

        val json = DiscordActivityBuilder.buildSetActivityJson(
            state = state,
            processId = 1234,
            showCover = false,
            showAlbum = false,
            nonce = "test-nonce-2"
        )

        assertTrue(json.contains(""""details":"Single Track""""))
        assertTrue(json.contains(""""state":"by Independent Artist""""))
        assertTrue(json.contains(""""large_image":"app_logo""""))
    }

    @Test
    fun testBuildClearActivityJson() {
        val clearJson = DiscordActivityBuilder.buildClearActivityJson(
            processId = 1234,
            nonce = "test-nonce-clear"
        )

        assertTrue(clearJson.contains(""""cmd":"SET_ACTIVITY""""))
        assertTrue(clearJson.contains(""""pid":1234"""))
        assertTrue(clearJson.contains(""""activity":null"""))
        assertTrue(clearJson.contains(""""nonce":"test-nonce-clear""""))
    }
}
