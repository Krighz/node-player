package com.nodeplayer.music.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.EmptyBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.ui.screens.CustomizeUiScreen
import com.nodeplayer.music.ui.screens.EqualizerScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.screens.WidgetSettingsScreen
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
import com.nodeplayer.ui.theme.LocalControlBackdrop
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenStabilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testEqualizerScreen_rendersWithoutCrash_andInteracts() {
        var effectConfig by mutableStateOf(AudioEffectConfig())

        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            val themeConfig = remember { GlassThemeConfig.PRESET_OBSIDIAN_LIQUID }
            val controlsConfig = remember { GlassControlsConfig() }

            CompositionLocalProvider(
                LocalGlassThemeConfig provides themeConfig,
                LocalGlassControlsConfig provides controlsConfig
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                ) {
                    CompositionLocalProvider(
                        LocalControlBackdrop provides remember { emptyBackdrop() }
                    ) {
                        EqualizerScreen(
                            config = effectConfig,
                            onConfigChange = { effectConfig = it },
                            systemEqActive = false,
                            systemSessionCount = 0,
                            currentTrack = null
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Interact: flip at least one toggle
        val toggles = composeTestRule.onAllNodes(isToggleable())
        if (toggles.fetchSemanticsNodes().isNotEmpty()) {
            toggles.onFirst().performClick()
            composeTestRule.waitForIdle()
        }

        // Interact: drag at least one slider
        val sliders = composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(current = effectConfig.preampGainDb, range = -12f..12f, steps = 0)))
        if (sliders.fetchSemanticsNodes().isNotEmpty()) {
            sliders.onFirst().performTouchInput { swipeRight() }
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testSettingsScreen_rendersWithoutCrash_andInteracts() {
        var themeConfig by mutableStateOf(GlassThemeConfig.PRESET_OBSIDIAN_LIQUID)
        var crossfadeConfig by mutableStateOf(CrossfadeConfig())

        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            val controlsConfig = remember { GlassControlsConfig() }

            CompositionLocalProvider(
                LocalGlassThemeConfig provides themeConfig,
                LocalGlassControlsConfig provides controlsConfig
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                ) {
                    CompositionLocalProvider(
                        LocalControlBackdrop provides remember { emptyBackdrop() }
                    ) {
                        SettingsScreen(
                            outputInfo = AudioOutputInfo(),
                            themeConfig = themeConfig,
                            crossfadeConfig = crossfadeConfig,
                            onCrossfadeConfigChange = { crossfadeConfig = it },
                            onThemeConfigChange = { themeConfig = it },
                            onRescanLibrary = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Interact with a toggle in Settings
        val toggles = composeTestRule.onAllNodes(isToggleable())
        if (toggles.fetchSemanticsNodes().isNotEmpty()) {
            toggles.onFirst().performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testCustomizeUiScreen_rendersWithoutCrash_andInteracts() {
        var themeConfig by mutableStateOf(GlassThemeConfig.PRESET_OBSIDIAN_LIQUID)

        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            val controlsConfig = remember { GlassControlsConfig() }

            CompositionLocalProvider(
                LocalGlassThemeConfig provides themeConfig,
                LocalGlassControlsConfig provides controlsConfig
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                ) {
                    CompositionLocalProvider(
                        LocalControlBackdrop provides remember { emptyBackdrop() }
                    ) {
                        CustomizeUiScreen(
                            themeConfig = themeConfig,
                            onThemeConfigChange = { themeConfig = it }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Interact with a toggle
        val toggles = composeTestRule.onAllNodes(isToggleable())
        if (toggles.fetchSemanticsNodes().isNotEmpty()) {
            toggles.onFirst().performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testWidgetSettingsScreen_rendersWithoutCrash_andInteracts() {
        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            val themeConfig = remember { GlassThemeConfig.PRESET_OBSIDIAN_LIQUID }
            val controlsConfig = remember { GlassControlsConfig() }

            CompositionLocalProvider(
                LocalGlassThemeConfig provides themeConfig,
                LocalGlassControlsConfig provides controlsConfig
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                ) {
                    CompositionLocalProvider(
                        LocalControlBackdrop provides remember { emptyBackdrop() }
                    ) {
                        WidgetSettingsScreen(
                            currentTrack = null,
                            isPlaying = false,
                            onBack = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Interact with a toggle or button
        val toggles = composeTestRule.onAllNodes(isToggleable())
        if (toggles.fetchSemanticsNodes().isNotEmpty()) {
            toggles.onFirst().performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun testNegativeCheck_selfSamplingRecursionGuardInterceptsCycle() {
        // This test simulates what the bug did before the fix:
        // A child inside layerBackdrop(parentBackdrop) directly attempts to drawBackdrop(parentBackdrop).
        // Before the fix, this caused infinite Skia prepareTreeImpl recursion (SIGSEGV stack overflow).
        // With the fix, parentBackdrop.isRecording is true during child drawing,
        // and drawBackdrop detects this and returns immediately, preventing the recursive cycle.

        var selfSamplingDetected = false
        var childDrawn = false

        composeTestRule.setContent {
            val parentBackdrop = rememberLayerBackdrop()

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .layerBackdrop(parentBackdrop)
            ) {
                var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .onGloballyPositioned { coords = it }
                        .drawWithContent {
                            childDrawn = true
                            if (parentBackdrop.isRecording) {
                                selfSamplingDetected = true
                            }
                            // Attempt to sample parentBackdrop while it is being recorded
                            with(parentBackdrop) {
                                drawBackdrop(density = this@drawWithContent, coordinates = coords, layerBlock = null)
                            }
                            drawContent()
                        }
                )
            }
        }

        composeTestRule.waitForIdle()

        assertTrue("Child should have been drawn during layer recording", childDrawn)
        assertTrue(
            "Self-sampling cycle was successfully detected while isRecording was true, preventing SIGSEGV recursion",
            selfSamplingDetected
        )
    }

    @Test
    fun testLocalControlBackdrop_insidePageContainer_resolvesToEmptyBackdrop() {
        var backdropType: String? = null

        composeTestRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop)
            ) {
                CompositionLocalProvider(
                    LocalControlBackdrop provides remember { emptyBackdrop() }
                ) {
                    val currentBackdrop = LocalControlBackdrop.current
                    backdropType = currentBackdrop::class.simpleName
                }
            }
        }

        composeTestRule.waitForIdle()

        assertTrue(
            "LocalControlBackdrop inside page container must be EmptyBackdrop to isolate in-page controls from parent layer",
            backdropType?.contains("EmptyBackdrop") == true
        )
    }
}
