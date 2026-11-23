package com.nodeplayer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeMatrixCharacterizationTest {

    @Test
    fun testAllFourThemesConfiguredCorrectly() {
        assertEquals(4, ThemeMatrixTestHelper.ACTIVE_THEME_MODES.size)
        assertTrue(ThemeMatrixTestHelper.ACTIVE_THEME_MODES.contains("OLED"))
        assertTrue(ThemeMatrixTestHelper.ACTIVE_THEME_MODES.contains("DARK"))
        assertTrue(ThemeMatrixTestHelper.ACTIVE_THEME_MODES.contains("LIGHT"))
        assertTrue(ThemeMatrixTestHelper.ACTIVE_THEME_MODES.contains("SYSTEM"))

        val oled = ThemeMatrixTestHelper.createConfig("OLED")
        assertTrue(oled.isOledBlack)
        assertEquals("OLED", oled.themeMode)

        val dark = ThemeMatrixTestHelper.createConfig("DARK")
        assertEquals(false, dark.isOledBlack)
        assertEquals("DARK", dark.themeMode)

        val light = ThemeMatrixTestHelper.createConfig("LIGHT")
        assertEquals(false, light.isOledBlack)
        assertEquals("LIGHT", light.themeMode)
    }

    @Test
    fun testContrastValidationHelper() {
        // White text on pure OLED black
        ThemeMatrixTestHelper.verifyContrastRatio(Color.White, LiquidColors.OledBlack, minRatio = 7.0f)
        // White text on dark slate surface
        ThemeMatrixTestHelper.verifyContrastRatio(Color.White, LiquidColors.SurfaceDark, minRatio = 4.5f)
    }

    @Test
    fun testYourMixShelfThemeMatrixCompliance() {
        // Verify Your Mix shelf labels maintain accessible contrast across all theme backgrounds
        for (mode in ThemeMatrixTestHelper.ACTIVE_THEME_MODES) {
            val config = ThemeMatrixTestHelper.createConfig(mode)
            val bg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceDark
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, bg, minRatio = 4.5f)
        }
    }

    @Test
    fun testPlaylistImportSheetThemeMatrixCompliance() {
        // Verify Playlist Import Sheet labels maintain accessible contrast across all theme backgrounds
        for (mode in ThemeMatrixTestHelper.ACTIVE_THEME_MODES) {
            val config = ThemeMatrixTestHelper.createConfig(mode)
            val bg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceDark
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, bg, minRatio = 4.5f)
            // Verify accent color against dialog surface maintains high visibility
            val surface = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceCard
            ThemeMatrixTestHelper.verifyContrastRatio(LiquidColors.ElectricCyan, surface, minRatio = 3.0f)
        }
    }

    @Test
    fun testStorageManagerAndDownloadQueueThemeMatrixCompliance() {
        // Verify Storage Manager and Download Queue badges & text maintain contrast across all theme modes
        for (mode in ThemeMatrixTestHelper.ACTIVE_THEME_MODES) {
            val config = ThemeMatrixTestHelper.createConfig(mode)
            val bg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceDark
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, bg, minRatio = 4.5f)

            val cardBg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceElevated
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, cardBg, minRatio = 4.5f)
            // Status badges (Green for COMPLETED, Cyan for DOWNLOADING)
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFF4CAF50), cardBg, minRatio = 3.0f)
            ThemeMatrixTestHelper.verifyContrastRatio(LiquidColors.ElectricCyan, cardBg, minRatio = 3.0f)
        }
    }

    @Test
    fun testDiscordRichPresenceCardThemeMatrixCompliance() {
        // Verify Discord Rich Presence card text and status badges maintain contrast across all theme modes
        for (mode in ThemeMatrixTestHelper.ACTIVE_THEME_MODES) {
            val config = ThemeMatrixTestHelper.createConfig(mode)
            val bg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceDark
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, bg, minRatio = 4.5f)

            val cardBg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceElevated
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, cardBg, minRatio = 4.5f)

            // Status badges: Connected (Green), Incognito (Blue), App ID Warning (Orange), Error (Red)
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFF81C784), cardBg, minRatio = 3.0f)
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFF64B5F6), cardBg, minRatio = 3.0f)
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFFFFB74D), cardBg, minRatio = 3.0f)
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFFE57373), cardBg, minRatio = 3.0f)
        }
    }

    @Test
    fun testAudioConverterThemeMatrixCompliance() {
        // Verify Audio Converter view cards, badges, and progress indicators maintain contrast across all theme modes
        for (mode in ThemeMatrixTestHelper.ACTIVE_THEME_MODES) {
            val config = ThemeMatrixTestHelper.createConfig(mode)
            val bg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceDark
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, bg, minRatio = 4.5f)

            val cardBg = if (config.isOledBlack) LiquidColors.OledBlack else LiquidColors.SurfaceElevated
            ThemeMatrixTestHelper.verifyContrastRatio(Color.White, cardBg, minRatio = 4.5f)

            // Accent against card
            ThemeMatrixTestHelper.verifyContrastRatio(LiquidColors.ElectricCyan, cardBg, minRatio = 3.0f)
            // Success green for savings & completed summary
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFF4CAF50), cardBg, minRatio = 3.0f)
            // Warning orange for destructive replace original
            ThemeMatrixTestHelper.verifyContrastRatio(Color(0xFFFF9800), cardBg, minRatio = 3.0f)
        }
    }
}
