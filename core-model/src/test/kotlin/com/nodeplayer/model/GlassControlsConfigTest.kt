package com.nodeplayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassControlsConfigTest {

    @Test
    fun testDefaultValues() {
        val master = MasterGlassConfig()
        assertEquals(8f, master.blurDp)
        assertEquals(24f, master.lensHeightDp)
        assertEquals(24f, master.lensAmountDp)
        assertEquals(1.0f, master.vibrancy)
        assertEquals(0.40f, master.tintOpacity)
        assertEquals(0.85f, master.highlightStrength)
        assertEquals(0.35f, master.shadowStrength)
        assertEquals(0.25f, master.chromaticAberration)
        assertEquals(16f, master.cornerShapeDp)
        assertEquals(0.96f, master.pressScale)
        assertEquals(380f, master.springStiffness)
        assertEquals(0.82f, master.springDamping)

        val component = ComponentGlassConfig()
        assertTrue(component.useGlobal)
        assertFalse(component.forceLighterInLists)

        val config = GlassControlsConfig()
        assertTrue(config.masterEnabled)
        assertEquals(GlassControlsPreset.BALANCED, config.preset)
        assertEquals(9, GlassComponentType.values().size)
    }

    @Test
    fun testPresets() {
        val full = GlassControlsConfig.PRESET_FULL
        assertTrue(full.masterEnabled)
        assertEquals(GlassControlsPreset.FULL, full.preset)
        assertEquals(8f, full.global.blurDp)
        assertEquals(24f, full.global.lensHeightDp)
        assertEquals(24f, full.global.lensAmountDp)
        assertEquals(380f, full.global.springStiffness)

        val balanced = GlassControlsConfig.PRESET_BALANCED
        assertTrue(balanced.masterEnabled)
        assertEquals(GlassControlsPreset.BALANCED, balanced.preset)
        assertEquals(4f, balanced.global.blurDp)
        assertEquals(14f, balanced.global.lensHeightDp)
        assertEquals(12f, balanced.global.lensAmountDp)
        assertEquals(450f, balanced.global.springStiffness)

        val light = GlassControlsConfig.PRESET_LIGHT
        assertFalse(light.masterEnabled)
        assertEquals(GlassControlsPreset.LIGHT, light.preset)
        assertEquals(0f, light.global.blurDp)
        assertEquals(0f, light.global.lensHeightDp)
    }

    @Test
    fun testInheritanceCascadeWhenUseGlobalIsTrue() {
        val customGlobal = MasterGlassConfig(
            blurDp = 12f,
            lensHeightDp = 18f,
            springStiffness = 600f
        )
        val config = GlassControlsConfig(global = customGlobal)

        // With useGlobal = true, resolving any component uses the global values
        for (type in GlassComponentType.values()) {
            val resolved = config.resolve(type)
            assertEquals(12f, resolved.blurDp, "blurDp should match global for $type")
            assertEquals(18f, resolved.lensHeightDp, "lensHeightDp should match global for $type")
            assertEquals(600f, resolved.springStiffness, "springStiffness should match global for $type")
            assertFalse(resolved.isPlainSquircle)
        }
    }

    @Test
    fun testIndependentValuesWhenUseGlobalIsFalse() {
        val customGlobal = MasterGlassConfig(blurDp = 10f, springStiffness = 400f)
        val buttonOverride = ComponentGlassConfig(
            useGlobal = false,
            blurDp = 2f,
            springStiffness = 800f
        )

        var config = GlassControlsConfig(global = customGlobal)
        config = config.updateComponent(GlassComponentType.BUTTONS, buttonOverride)

        val resolvedButton = config.resolve(GlassComponentType.BUTTONS)
        assertEquals(2f, resolvedButton.blurDp)
        assertEquals(800f, resolvedButton.springStiffness)

        // Other components still inherit global
        val resolvedToggle = config.resolve(GlassComponentType.TOGGLES)
        assertEquals(10f, resolvedToggle.blurDp)
        assertEquals(400f, resolvedToggle.springStiffness)
    }

    @Test
    fun testForceLighterInListsResolution() {
        val buttonWithListOverride = ComponentGlassConfig(
            forceLighterInLists = true,
            blurDp = 16f
        )
        val config = GlassControlsConfig(
            buttons = buttonWithListOverride
        )

        // When inList = true and forceLighterInLists = true -> returns LIGHTER_SQUIRCLE
        val inListResolved = config.resolve(GlassComponentType.BUTTONS, inList = true)
        assertEquals(ResolvedGlassParams.LIGHTER_SQUIRCLE, inListResolved)
        assertTrue(inListResolved.isPlainSquircle)
        assertEquals(0f, inListResolved.blurDp)

        // When inList = false -> resolves standard optical values
        val notInListResolved = config.resolve(GlassComponentType.BUTTONS, inList = false)
        assertFalse(notInListResolved.isPlainSquircle)
        assertEquals(8f, notInListResolved.blurDp) // inherited from default global
    }

    @Test
    fun testMasterDisabledReturnsLighterSquircle() {
        val config = GlassControlsConfig(masterEnabled = false)

        for (type in GlassComponentType.values()) {
            val resolved = config.resolve(type)
            assertEquals(ResolvedGlassParams.LIGHTER_SQUIRCLE, resolved)
            assertTrue(resolved.isPlainSquircle)
        }
    }

    @Test
    fun testComponentTypeSupportsListOverride() {
        assertTrue(GlassComponentType.BUTTONS.supportsListOverride)
        assertTrue(GlassComponentType.TOGGLES.supportsListOverride)
        assertTrue(GlassComponentType.CHIPS.supportsListOverride)

        assertFalse(GlassComponentType.SLIDERS.supportsListOverride)
        assertFalse(GlassComponentType.DOCK.supportsListOverride)
        assertFalse(GlassComponentType.DIALOGS.supportsListOverride)
        assertFalse(GlassComponentType.MINI_PLAYER.supportsListOverride)
        assertFalse(GlassComponentType.NOW_PLAYING.supportsListOverride)
        assertFalse(GlassComponentType.WIDGETS.supportsListOverride)
    }

    @Test
    fun testGetComponentAndUpdateComponentAllTypes() {
        var config = GlassControlsConfig()
        for (type in GlassComponentType.values()) {
            val original = config.getComponent(type)
            assertTrue(original.useGlobal)

            val updated = original.copy(useGlobal = false, blurDp = 99f)
            config = config.updateComponent(type, updated)
            assertEquals(99f, config.getComponent(type).blurDp)
            assertFalse(config.getComponent(type).useGlobal)
        }
    }

    @Test
    fun testGlassThemeConfigEmbedding() {
        val theme = GlassThemeConfig()
        assertFalse(theme.controlsGlassEnabled)
        assertFalse(theme.controlsConfig.masterEnabled)

        val themeWithControls = GlassThemeConfig(controlsGlassEnabled = true)
        assertTrue(themeWithControls.controlsGlassEnabled)
        assertTrue(themeWithControls.controlsConfig.masterEnabled)
    }
}
