package com.nodeplayer.music

import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.LyricsViewConfig
import com.nodeplayer.music.ui.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupThemeConfigCharacterizationTest {

    @Test
    fun testGlassThemeConfigSafeInitialDefaultsPreHydration() {
        val defaults = GlassThemeConfig()
        assertEquals("BALANCED", defaults.glassStrength)
        assertEquals("OLED", defaults.themeMode)
        assertEquals("ARTWORK", defaults.accentSource)
        assertEquals("COMFORTABLE", defaults.spacingDensity)
        assertEquals("ROUNDED", defaults.cornerRoundness)
        assertTrue(defaults.animatedBackdrop)
        assertEquals(28.0f, defaults.blurRadiusDp, 0.001f)
        assertEquals(0.65f, defaults.tintAlpha, 0.001f)
        assertEquals(0.30f, defaults.borderAlpha, 0.001f)
        assertTrue(defaults.isOledBlack)
        assertTrue(defaults.useDynamicAccent)
        assertEquals(0xFFE8D5B7L, defaults.fixedAccentColorArgb)
        assertEquals(16.0f, defaults.cornerRadiusDp, 0.001f)
        assertFalse(defaults.isPerformanceMode)
        assertTrue(defaults.refractionShaderEnabled)
        assertTrue(defaults.hapticsEnabled)
        assertEquals("AUTO", defaults.shaderMode)
        assertTrue(defaults.animateOnlyWhilePlaying)
        assertTrue(defaults.dockAutoHideEnabled)
        assertTrue(defaults.dockAutoHideMiniPlayer)
        assertFalse(defaults.controlsGlassEnabled)
        assertEquals(28.0f, defaults.lyricsViewConfig.fontSizeSp, 0.001f)
        assertEquals(5, defaults.lyricsViewConfig.autoHideDelaySec)
    }

    @Test
    fun testBuildThemeConfigFromValuesMapsAllFieldsIdentically() {
        val fromPrefFallbacks = PlayerViewModel.buildThemeConfigFromValues()
        assertEquals(
            GlassThemeConfig(
                themeMode = "SYSTEM",
                isOledBlack = false,
                fixedAccentColorArgb = 0xFF6366F1,
                animateOnlyWhilePlaying = true,
                controlsGlassEnabled = true
            ),
            fromPrefFallbacks
        )

        val custom = PlayerViewModel.buildThemeConfigFromValues(
            glassStrength = "HIGH",
            themeMode = "OLED",
            accentSource = "FIXED",
            spacingDensity = "COMPACT",
            cornerRoundness = "PILL",
            animatedBackdrop = false,
            blurRadiusDp = 18.0f,
            tintAlpha = 0.45f,
            borderAlpha = 0.15f,
            isOledBlack = true,
            useDynamicAccent = false,
            fixedAccentColorArgb = 0xFF10B981L,
            cornerRadiusDp = 24.0f,
            isPerformanceMode = true,
            refractionShaderEnabledPref = true,
            selfTestPassed = true,
            hapticsEnabled = false,
            shaderMode = "SIMPLE",
            glassOpacity = 0.5f,
            blurStrength = 0.4f,
            glassTexture = 0.2f,
            edgeHighlightStrength = 0.3f,
            isFullyTransparent = true,
            keepTextReadable = false,
            backgroundAnimationStyle = "AURORA",
            backgroundAnimationSpeed = 1.5f,
            backgroundAnimationIntensity = 0.5f,
            backgroundColorSource = "FIXED",
            animateOnlyWhilePlaying = true,
            frameRateCap = "60HZ",
            renderQuality = "BALANCED",
            antiBandingDithering = false,
            fontFamily = "SERIF",
            showDockSearchButton = false,
            showMiniPlayerWave = false,
            formatBadgeVisible = false,
            lensRefractionStrength = 0.55f,
            lensRimSpecular = 0.45f,
            animationPreset = "SNAPPY",
            motionSpeed = 1.25f,
            springBounciness = 0.6f,
            springStiffness = 500f,
            switchOvershootEnabled = false,
            pressScaleEnabled = false,
            pressScaleStrength = 0.92f,
            staggeredListAnimation = false,
            lyricsAnimationEnabled = false,
            screenTransitionStyle = "FADE",
            reduceMotion = true,
            dockGlassLensEnabled = false,
            dockGlassRimEnabled = false,
            dockGlassPuckEnabled = false,
            dockAutoHideEnabled = false,
            dockAutoHideMiniPlayer = false,
            dockScrollSensitivity = 1.8f,
            dockReturnDelayMs = 2000L,
            lyricsFontSizeSp = 32.0f,
            lyricsAutoHideDelaySec = 8,
            controlsGlassEnabled = false
        )

        val expected = GlassThemeConfig(
            glassStrength = "HIGH",
            themeMode = "OLED",
            accentSource = "FIXED",
            spacingDensity = "COMPACT",
            cornerRoundness = "PILL",
            animatedBackdrop = false,
            blurRadiusDp = 18.0f,
            tintAlpha = 0.45f,
            borderAlpha = 0.15f,
            isOledBlack = true,
            useDynamicAccent = false,
            fixedAccentColorArgb = 0xFF10B981L,
            cornerRadiusDp = 24.0f,
            isPerformanceMode = true,
            refractionShaderEnabled = true,
            hapticsEnabled = false,
            shaderMode = "SIMPLE",
            glassOpacity = 0.5f,
            blurStrength = 0.4f,
            glassTexture = 0.2f,
            edgeHighlightStrength = 0.3f,
            isFullyTransparent = true,
            keepTextReadable = false,
            backgroundAnimationStyle = "AURORA",
            backgroundAnimationSpeed = 1.5f,
            backgroundAnimationIntensity = 0.5f,
            backgroundColorSource = "FIXED",
            animateOnlyWhilePlaying = true,
            frameRateCap = "60HZ",
            renderQuality = "BALANCED",
            antiBandingDithering = false,
            fontFamily = "SERIF",
            showDockSearchButton = false,
            showMiniPlayerWave = false,
            formatBadgeVisible = false,
            lensRefractionStrength = 0.55f,
            lensRimSpecular = 0.45f,
            animationPreset = "SNAPPY",
            motionSpeed = 1.25f,
            springBounciness = 0.6f,
            springStiffness = 500f,
            switchOvershootEnabled = false,
            pressScaleEnabled = false,
            pressScaleStrength = 0.92f,
            staggeredListAnimation = false,
            lyricsAnimationEnabled = false,
            screenTransitionStyle = "FADE",
            reduceMotion = true,
            dockGlassLensEnabled = false,
            dockGlassRimEnabled = false,
            dockGlassPuckEnabled = false,
            dockAutoHideEnabled = false,
            dockAutoHideMiniPlayer = false,
            dockScrollSensitivity = 1.8f,
            dockReturnDelayMs = 2000L,
            lyricsViewConfig = LyricsViewConfig(
                fontSizeSp = 32.0f,
                autoHideDelaySec = 8
            ),
            controlsGlassEnabled = false
        )
        assertEquals(expected, custom)
    }

    @Test
    fun testBuildThemeConfigFromValuesDisablesRefractionWhenSelfTestFails() {
        val result = PlayerViewModel.buildThemeConfigFromValues(
            refractionShaderEnabledPref = true,
            selfTestPassed = false
        )
        assertFalse(result.refractionShaderEnabled)
    }
}
