package com.nodeplayer.library

import org.junit.Assert.*
import org.junit.Test

class LastFmSignerTest {

    @Test
    fun testLastFmSignatureComputation() {
        val params = mapOf(
            "method" to "auth.getSession",
            "api_key" to "test_api_key",
            "token" to "test_token"
        )
        val secret = "test_secret"

        // Expected sorted string: api_keytest_api_keymethodauth.getSessiontokentest_tokentest_secret
        // MD5 of this string
        val sig = LastFmSigner.createApiSignature(params, secret)
        assertNotNull(sig)
        assertEquals(32, sig.length)
        assertTrue(sig.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun testScrobbleEligibilityRules() {
        // Less than 30s: never eligible
        assertFalse(LastFmSigner.isEligibleForScrobble(durationSeconds = 25, listenedSeconds = 25))

        // 100s track: eligible after 50s (half)
        assertFalse(LastFmSigner.isEligibleForScrobble(durationSeconds = 100, listenedSeconds = 49))
        assertTrue(LastFmSigner.isEligibleForScrobble(durationSeconds = 100, listenedSeconds = 50))
        assertTrue(LastFmSigner.isEligibleForScrobble(durationSeconds = 100, listenedSeconds = 90))

        // 10 minute track (600s): eligible after 4 minutes (240s) even before half duration
        assertFalse(LastFmSigner.isEligibleForScrobble(durationSeconds = 600, listenedSeconds = 239))
        assertTrue(LastFmSigner.isEligibleForScrobble(durationSeconds = 600, listenedSeconds = 240))
        assertTrue(LastFmSigner.isEligibleForScrobble(durationSeconds = 600, listenedSeconds = 300))
    }
}
