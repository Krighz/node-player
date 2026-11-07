package com.nodeplayer.ui.components

import com.nodeplayer.model.ComponentGlassConfig
import com.nodeplayer.model.GlassComponentType
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassControlsPreset
import com.nodeplayer.model.GlassQuality
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassCustomizationConfigTest {

    @Test
    fun testDefaultGlassCustomizationValues() {
        val config = GlassThemeConfig()

        assertTrue(config.dockGlassLensEnabled)
        assertTrue(config.dockGlassRimEnabled)
        assertTrue(config.dockGlassPuckEnabled)
        assertTrue(config.miniPlayerGlassEnabled)
        assertTrue(config.nowPlayingGlassEnabled)
        assertFalse(config.controlsGlassEnabled) // Plain-compose controls by default
        assertTrue(config.widgetsGlassEnabled)

        assertEquals(18.0f, config.glassRefractionHeightDp)
        assertEquals(16.0f, config.glassRefractionAmountDp)
        assertEquals(0.25f, config.glassChromaticFringe)
        assertEquals(0.50f, config.glassBackdropScale)
    }

    @Test
    fun testResetToRecommendedPreset() {
        val obsidian = GlassThemeConfig.PRESET_OBSIDIAN_LIQUID
        assertEquals(GlassQuality.BALANCED, obsidian.glassQuality)
        assertEquals(MotionLevel.FULL, obsidian.motionLevel)
        assertEquals(0.85f, obsidian.lensRefractionStrength)
    }

    @Test
    fun testGpuCostCalculationMasterDisabled() {
        val config = GlassControlsConfig(masterEnabled = false)
        val cost = calculateGpuCost(config)
        assertEquals(0.8f, cost.estimatedMs)
        assertEquals(GpuCostCategory.LOW, cost.category)
    }

    @Test
    fun testGpuCostCalculationPresets() {
        val fullCost = calculateGpuCost(GlassControlsConfig.PRESET_FULL)
        val balancedCost = calculateGpuCost(GlassControlsConfig.PRESET_BALANCED)
        val lightCost = calculateGpuCost(GlassControlsConfig.PRESET_LIGHT)

        // Light has masterEnabled = false -> 0.8ms (LOW)
        assertEquals(0.8f, lightCost.estimatedMs)
        assertEquals(GpuCostCategory.LOW, lightCost.category)

        // Full has high blur (8dp) and lens (24dp+24dp)
        assertTrue(fullCost.estimatedMs > balancedCost.estimatedMs)
        assertTrue(balancedCost.estimatedMs > lightCost.estimatedMs)
        assertTrue(fullCost.estimatedMs >= 3.0f) // Moderate or above
    }

    @Test
    fun testGpuCostCalculationWithOverrides() {
        val baseConfig = GlassControlsConfig.PRESET_BALANCED
        val baseCost = calculateGpuCost(baseConfig)

        // Add 2 non-lightened overrides
        val overriddenConfig = baseConfig.copy(
            buttons = ComponentGlassConfig(useGlobal = false, forceLighterInLists = false),
            toggles = ComponentGlassConfig(useGlobal = false, forceLighterInLists = false)
        )
        val overriddenCost = calculateGpuCost(overriddenConfig)
        assertEquals(baseCost.estimatedMs + 0.50f, overriddenCost.estimatedMs, 0.001f)

        // Override with forceLighterInLists = true does not add full glass pass cost
        val lighterConfig = baseConfig.copy(
            buttons = ComponentGlassConfig(useGlobal = false, forceLighterInLists = true)
        )
        val lighterCost = calculateGpuCost(lighterConfig)
        assertEquals(baseCost.estimatedMs, lighterCost.estimatedMs, 0.001f)
    }

    @Test
    fun testPresetLoadingAndMasterSwitch() {
        val full = GlassControlsConfig.PRESET_FULL
        assertTrue(full.masterEnabled)
        assertEquals(GlassControlsPreset.FULL, full.preset)
        assertEquals(8f, full.global.blurDp)
        assertEquals(24f, full.global.lensHeightDp)

        val light = GlassControlsConfig.PRESET_LIGHT
        assertFalse(light.masterEnabled)
        assertEquals(GlassControlsPreset.LIGHT, light.preset)
        val resolved = light.resolve(GlassComponentType.BUTTONS)
        assertTrue(resolved.isPlainSquircle)
    }

    @Test
    fun testOverrideCascadingAndInheritance() {
        val config = GlassControlsConfig.PRESET_BALANCED
        val defaultButtons = config.resolve(GlassComponentType.BUTTONS)
        assertEquals(config.global.blurDp, defaultButtons.blurDp)
        assertEquals(config.global.cornerShapeDp, defaultButtons.cornerShapeDp)

        // Customize with override
        val customConfig = config.copy(
            buttons = ComponentGlassConfig(
                useGlobal = false,
                blurDp = 18f,
                cornerShapeDp = 24f,
                pressScale = 0.90f
            )
        )
        val overriddenButtons = customConfig.resolve(GlassComponentType.BUTTONS)
        assertEquals(18f, overriddenButtons.blurDp)
        assertEquals(24f, overriddenButtons.cornerShapeDp)
        assertEquals(0.90f, overriddenButtons.pressScale)

        // Other components still inherit global
        val dockResolved = customConfig.resolve(GlassComponentType.DOCK)
        assertEquals(config.global.blurDp, dockResolved.blurDp)
    }

    @Test
    fun testListPerformanceOverride() {
        val config = GlassControlsConfig.PRESET_FULL.copy(
            buttons = ComponentGlassConfig(useGlobal = false, forceLighterInLists = true)
        )
        val normalResolved = config.resolve(GlassComponentType.BUTTONS, inList = false)
        assertFalse(normalResolved.isPlainSquircle)

        val listResolved = config.resolve(GlassComponentType.BUTTONS, inList = true)
        assertTrue(listResolved.isPlainSquircle)
    }

    @Test
    fun testResetToGlobalRestoration() {
        var config = GlassControlsConfig.PRESET_BALANCED
        config = config.updateComponent(
            GlassComponentType.TOGGLES,
            ComponentGlassConfig(useGlobal = false, blurDp = 20f, springStiffness = 800f)
        )
        assertFalse(config.getComponent(GlassComponentType.TOGGLES).useGlobal)
        assertEquals(20f, config.resolve(GlassComponentType.TOGGLES).blurDp)

        // Reset to global
        config = config.updateComponent(
            GlassComponentType.TOGGLES,
            ComponentGlassConfig(useGlobal = true)
        )
        assertTrue(config.getComponent(GlassComponentType.TOGGLES).useGlobal)
        assertEquals(config.global.blurDp, config.resolve(GlassComponentType.TOGGLES).blurDp)
    }
}
