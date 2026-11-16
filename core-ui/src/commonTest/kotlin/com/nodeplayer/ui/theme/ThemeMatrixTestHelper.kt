package com.nodeplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig
import kotlin.test.assertTrue

/**
 * Reusable test helper for Theme Matrix verification across all 4 active themes:
 * OLED, DARK, LIGHT, and SYSTEM per F00 Section 4, Section 10, and decisions-F00.md.
 */
object ThemeMatrixTestHelper {

    val ACTIVE_THEME_MODES: List<String> = listOf("OLED", "DARK", "LIGHT", "SYSTEM")

    fun createConfig(mode: String): GlassThemeConfig {
        return when (mode.uppercase()) {
            "OLED" -> GlassThemeConfig(
                themeMode = "OLED",
                isOledBlack = true,
                fixedAccentColorArgb = 0xFF6366F1,
                glassQuality = GlassQuality.BALANCED
            )
            "DARK" -> GlassThemeConfig(
                themeMode = "DARK",
                isOledBlack = false,
                fixedAccentColorArgb = 0xFF6366F1,
                glassQuality = GlassQuality.BALANCED
            )
            "LIGHT" -> GlassThemeConfig(
                themeMode = "LIGHT",
                isOledBlack = false,
                fixedAccentColorArgb = 0xFF4F46E5,
                glassQuality = GlassQuality.BALANCED
            )
            "SYSTEM" -> GlassThemeConfig(
                themeMode = "SYSTEM",
                isOledBlack = false,
                fixedAccentColorArgb = 0xFF6366F1,
                glassQuality = GlassQuality.BALANCED
            )
            else -> GlassThemeConfig()
        }
    }

    @Composable
    fun ThemedTestScope(
        mode: String,
        content: @Composable () -> Unit
    ) {
        val config = createConfig(mode)
        val isDark = mode != "LIGHT"
        val tokens = GlassDesignTokens.resolve(config, isDark = isDark)
        val accentColor = Color(config.fixedAccentColorArgb)

        CompositionLocalProvider(
            LocalGlassThemeConfig provides config,
            LocalAccentColor provides accentColor,
            content = content
        )
    }

    fun verifyContrastRatio(foreground: Color, background: Color, minRatio: Float = 3.0f) {
        val ratio = PaletteExtractor.calculateContrastRatio(foreground, background)
        assertTrue(
            ratio >= minRatio,
            "Contrast ratio $ratio between $foreground and $background must be >= $minRatio"
        )
    }
}
