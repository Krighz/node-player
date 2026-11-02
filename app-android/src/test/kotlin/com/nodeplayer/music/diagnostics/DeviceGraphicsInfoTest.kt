package com.nodeplayer.music.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGraphicsInfoTest {

    @Test
    fun testSanitizedPlainTextContainsGraphicsInfoAndZeroPersonalData() {
        val info = DeviceGraphicsInfo(
            manufacturer = "realme",
            model = "RMX3851",
            device = "RE58C4L1",
            androidVersion = "14",
            apiLevel = 34,
            socModel = "SM7675",
            hardware = "qcom",
            gpuRenderer = "Adreno (TM) 732",
            screenWidthPx = 1264,
            screenHeightPx = 2780,
            densityDpi = 480,
            densityScale = 3.0f,
            refreshRateHz = 120.0f
        )

        val selfTest = ShaderSelfTestResult(passed = true, reason = "Passed: zero spill, avg lum 0.45")
        val plainText = info.toSanitizedPlainText(selfTest, "AUTO")

        // Verify hardware details are present
        assertTrue(plainText.contains("realme RMX3851"))
        assertTrue(plainText.contains("Adreno (TM) 732"))
        assertTrue(plainText.contains("SM7675"))
        assertTrue(plainText.contains("1264x2780 @ 120.0 Hz"))
        assertTrue(plainText.contains("PASSED"))
        assertTrue(plainText.contains("AUTO"))

        // Verify strict absence of any personal data
        assertFalse(plainText.contains("/storage/"))
        assertFalse(plainText.contains("/sdcard/"))
        assertFalse(plainText.contains(".mp3"))
        assertFalse(plainText.contains(".flac"))
        assertFalse(plainText.contains("user", ignoreCase = true))
        assertFalse(plainText.contains("lastfm", ignoreCase = true))
        assertFalse(plainText.contains("token", ignoreCase = true))
    }
}
