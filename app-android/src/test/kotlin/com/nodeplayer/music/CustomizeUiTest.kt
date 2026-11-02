package com.nodeplayer.music

import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.data.NodeThemeSerializer
import com.nodeplayer.music.ui.theme.resolveAppFontFamily
import org.junit.Assert.*
import org.junit.Test

class CustomizeUiTest {

    @Test
    fun testThemeSerializerRoundTrip() {
        val original = GlassThemeConfig(
            glassStrength = "BALANCED",
            themeMode = "DARK",
            accentSource = "PRESET",
            fixedAccentColorArgb = 0xFFF59E0B,
            glassOpacity = 0.58f,
            blurStrength = 0.82f,
            glassTexture = 0.33f,
            edgeHighlightStrength = 0.77f,
            isFullyTransparent = false,
            keepTextReadable = true,
            backgroundAnimationStyle = "AURORA",
            backgroundAnimationSpeed = 1.4f,
            backgroundAnimationIntensity = 0.9f,
            backgroundColorSource = "PALETTE",
            fontFamily = "OUTFIT",
            cornerRoundness = "PILL",
            spacingDensity = "ROOMY",
            showDockSearchButton = false,
            showMiniPlayerWave = false,
            formatBadgeVisible = true,
            lensRefractionStrength = 1.25f,
            lensRimSpecular = 0.95f
        )

        val json = NodeThemeSerializer.toJson(original)
        assertNotNull(json)
        assertTrue(json.contains("NodePlayerTheme"))
        assertTrue(json.contains("AURORA"))
        assertTrue(json.contains("OUTFIT"))

        val restored = NodeThemeSerializer.fromJson(json)
        assertNotNull(restored)
        assertEquals(original.themeMode, restored!!.themeMode)
        assertEquals(original.accentSource, restored.accentSource)
        assertEquals(original.fixedAccentColorArgb, restored.fixedAccentColorArgb)
        assertEquals(original.glassOpacity, restored.glassOpacity, 0.01f)
        assertEquals(original.blurStrength, restored.blurStrength, 0.01f)
        assertEquals(original.glassTexture, restored.glassTexture, 0.01f)
        assertEquals(original.edgeHighlightStrength, restored.edgeHighlightStrength, 0.01f)
        assertEquals(original.backgroundAnimationStyle, restored.backgroundAnimationStyle)
        assertEquals(original.fontFamily, restored.fontFamily)
        assertEquals(original.cornerRoundness, restored.cornerRoundness)
        assertEquals(original.spacingDensity, restored.spacingDensity)
        assertEquals(original.showDockSearchButton, restored.showDockSearchButton)
        assertEquals(original.showMiniPlayerWave, restored.showMiniPlayerWave)
        assertEquals(original.formatBadgeVisible, restored.formatBadgeVisible)
        assertEquals(original.lensRefractionStrength, restored.lensRefractionStrength, 0.01f)
        assertEquals(original.lensRimSpecular, restored.lensRimSpecular, 0.01f)
    }

    @Test
    fun testAllSixPresetsAreValid() {
        val presets = listOf(
            GlassThemeConfig.PRESET_OBSIDIAN_LIQUID,
            GlassThemeConfig.PRESET_FROST_PEARL,
            GlassThemeConfig.PRESET_AMBER_FLARE,
            GlassThemeConfig.PRESET_NEON_AURORA,
            GlassThemeConfig.PRESET_STEALTH_OLED,
            GlassThemeConfig.PRESET_MINIMAL_GLASS
        )

        assertEquals(6, presets.size)

        // Obsidian Liquid
        val obsidian = GlassThemeConfig.PRESET_OBSIDIAN_LIQUID
        assertEquals("DARK", obsidian.themeMode)
        assertEquals("SLOW_CAUSTICS", obsidian.backgroundAnimationStyle)
        assertEquals("INTER", obsidian.fontFamily)

        // Frost Pearl
        val frost = GlassThemeConfig.PRESET_FROST_PEARL
        assertEquals("LIGHT", frost.themeMode)
        assertEquals("AURORA", frost.backgroundAnimationStyle)
        assertEquals("OUTFIT", frost.fontFamily)

        // Amber Flare
        val amber = GlassThemeConfig.PRESET_AMBER_FLARE
        assertEquals("DARK", amber.themeMode)
        assertEquals("LIQUID_BLOBS", amber.backgroundAnimationStyle)
        assertEquals("INTER", amber.fontFamily)

        // Neon Aurora
        val neon = GlassThemeConfig.PRESET_NEON_AURORA
        assertEquals("DARK", neon.themeMode)
        assertEquals("AURORA", neon.backgroundAnimationStyle)
        assertEquals("OUTFIT", neon.fontFamily)

        // Stealth OLED
        val oled = GlassThemeConfig.PRESET_STEALTH_OLED
        assertEquals("OLED", oled.themeMode)
        assertTrue(oled.isOledBlack)
        assertEquals("STATIC", oled.backgroundAnimationStyle)
        assertEquals("JETBRAINS_MONO", oled.fontFamily)
        assertEquals("SHARP", oled.cornerRoundness)

        // Minimal Glass
        val minimal = GlassThemeConfig.PRESET_MINIMAL_GLASS
        assertEquals("DARK", minimal.themeMode)
        assertEquals("ALBUM_ART_BLUR", minimal.backgroundAnimationStyle)
        assertEquals("SYSTEM", minimal.fontFamily)
    }

    @Test
    fun testFontFamilyResolution() {
        assertNotNull(resolveAppFontFamily("INTER"))
        assertNotNull(resolveAppFontFamily("OUTFIT"))
        assertNotNull(resolveAppFontFamily("JETBRAINS_MONO"))
        assertNotNull(resolveAppFontFamily("SYSTEM"))
        assertNotNull(resolveAppFontFamily("unknown"))
    }
}
