package com.nodeplayer.music.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPrivacyTest {

    @Test
    fun testSanitizeRemovesStoragePathsAndMusicFilenames() {
        val rawLog = """
            2026-11-02 11:20:15.123 I/AudioScanner: Found file at /storage/emulated/0/Music/Arctic Monkeys/505.flac
            2026-11-02 11:20:16.456 D/ExoPlayer: Opening source /sdcard/Download/My_Secret_Song.mp3 with format audio/mp3
            2026-11-02 11:20:17.789 I/TagReader: Reading embedded metadata for /storage/0000-0000/Audio/Track_01.wav
        """.trimIndent()

        val sanitized = DiagnosticsDumpManager.sanitizeText(rawLog)

        // Verify paths and filenames are scrubbed
        assertFalse(sanitized.contains("/storage/emulated/0/Music/Arctic Monkeys/505.flac"))
        assertFalse(sanitized.contains("505.flac"))
        assertFalse(sanitized.contains("My_Secret_Song.mp3"))
        assertFalse(sanitized.contains("/sdcard/Download/"))
        assertFalse(sanitized.contains("Track_01.wav"))

        assertTrue(sanitized.contains("[REDACTED_PATH]"))
    }

    @Test
    fun testSanitizeRemovesCredentialsAndTokens() {
        val rawLog = """
            2026-11-02 11:25:01.000 D/LastFm: session_key=abcdef1234567890abcdef1234567890
            2026-11-02 11:25:02.000 I/LastFm: lastfm_username=cool_music_fan_99
            2026-11-02 11:25:03.000 D/Auth: token: secret_api_token_value_xyz
        """.trimIndent()

        val sanitized = DiagnosticsDumpManager.sanitizeText(rawLog)

        assertFalse(sanitized.contains("abcdef1234567890abcdef1234567890"))
        assertFalse(sanitized.contains("cool_music_fan_99"))
        assertFalse(sanitized.contains("secret_api_token_value_xyz"))

        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testDeviceInfoContainsZeroPersonalData() {
        val info = DeviceGraphicsInfo(
            manufacturer = "Tecno",
            model = "CK8n",
            device = "Tecno-CK8n",
            androidVersion = "14",
            apiLevel = 34,
            socModel = "Dimensity 8020",
            hardware = "mt6891",
            gpuRenderer = "Mali-G77 MC9",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            densityDpi = 480,
            densityScale = 3.0f,
            refreshRateHz = 120.0f
        )
        val result = ShaderSelfTestResult(passed = false, reason = "Spill check failure at (380, 50)")
        val text = info.toSanitizedPlainText(result, "AUTO")

        assertFalse(text.contains("/storage/"))
        assertFalse(text.contains("/sdcard/"))
        assertFalse(text.contains(".mp3"))
        assertFalse(text.contains(".flac"))
        assertFalse(text.contains("user", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("key", ignoreCase = true))

        assertTrue(text.contains("Tecno CK8n"))
        assertTrue(text.contains("Mali-G77 MC9"))
        assertTrue(text.contains("Dimensity 8020"))
    }
}
