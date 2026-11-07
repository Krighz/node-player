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
}
