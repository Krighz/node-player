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

    @Test
    fun testSerializerRoundtrip() {
        val original = GlassControlsConfig(
            masterEnabled = true,
            preset = GlassControlsPreset.FULL,
            global = MasterGlassConfig(
                blurDp = 10f,
                lensHeightDp = 20f,
                lensAmountDp = 22f,
                vibrancy = 0.95f,
                tintOpacity = 0.45f,
                highlightStrength = 0.88f,
                shadowStrength = 0.32f,
                chromaticAberration = 0.28f,
                cornerShapeDp = 18f,
                pressScale = 0.94f,
                springStiffness = 420f,
                springDamping = 0.85f
            ),
            buttons = ComponentGlassConfig(
                useGlobal = false,
                blurDp = 5f,
                forceLighterInLists = true,
                springStiffness = 600f
            ),
            dock = ComponentGlassConfig(
                useGlobal = false,
                blurDp = 14f,
                lensHeightDp = 28f
            )
        )

        val json = GlassControlsSerializer.toJson(original)
        assertTrue(json.contains("\"format\": \"NodePlayerGlassControls\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"preset\": \"FULL\""))

        val parsed = GlassControlsSerializer.fromJson(json)
        assertEquals(original.masterEnabled, parsed.masterEnabled)
        assertEquals(original.preset, parsed.preset)
        assertEquals(original.global.blurDp, parsed.global.blurDp)
        assertEquals(original.global.lensHeightDp, parsed.global.lensHeightDp)
        assertEquals(original.global.springStiffness, parsed.global.springStiffness)

        // Buttons override check
        val parsedButtons = parsed.buttons
        assertFalse(parsedButtons.useGlobal)
        assertTrue(parsedButtons.forceLighterInLists)
        assertEquals(5f, parsedButtons.blurDp)
        assertEquals(600f, parsedButtons.springStiffness)

        // Dock override check
        val parsedDock = parsed.dock
        assertFalse(parsedDock.useGlobal)
        assertEquals(14f, parsedDock.blurDp)
        assertEquals(28f, parsedDock.lensHeightDp)

        // Toggles default check
        assertTrue(parsed.toggles.useGlobal)
    }

    @Test
    fun testSerializerFallbackOnCorruptOrInvalidFormat() {
        // Completely invalid string
        val corrupt = "{ not valid json at all :::"
        val fromCorrupt = GlassControlsSerializer.fromJson(corrupt)
        assertEquals(GlassControlsConfig.PRESET_BALANCED, fromCorrupt)

        // Wrong format
        val wrongFormat = """{"format": "UnknownFormat", "version": 1}"""
        val fromWrongFormat = GlassControlsSerializer.fromJson(wrongFormat)
        assertEquals(GlassControlsConfig.PRESET_BALANCED, fromWrongFormat)

        // Future version
        val futureVersion = """{"format": "NodePlayerGlassControls", "version": 999}"""
        val fromFuture = GlassControlsSerializer.fromJson(futureVersion)
        assertEquals(GlassControlsConfig.PRESET_BALANCED, fromFuture)

        // Oversized JSON (T-04-02)
        val hugeJson = "a".repeat(70000)
        val fromHuge = GlassControlsSerializer.fromJson(hugeJson)
        assertEquals(GlassControlsConfig.PRESET_BALANCED, fromHuge)
    }

    @Test
    fun testSerializerIncompleteJsonFallback() {
        // Incomplete JSON with only header and masterEnabled
        val minimalJson = """
        {
            "format": "NodePlayerGlassControls",
            "version": 1,
            "masterEnabled": false,
            "preset": "LIGHT"
        }
        """.trimIndent()

        val parsed = GlassControlsSerializer.fromJson(minimalJson)
        assertFalse(parsed.masterEnabled)
        assertEquals(GlassControlsPreset.LIGHT, parsed.preset)
        // Global and components should use defaults without crashing
        assertEquals(8f, parsed.global.blurDp)
        assertTrue(parsed.buttons.useGlobal)
    }

    @Test
    fun testSerializerNumericClamping() {
        val outOfBoundsJson = """
        {
            "format": "NodePlayerGlassControls",
            "version": 1,
            "masterEnabled": true,
            "preset": "BALANCED",
            "global": {
                "blurDp": 99999.0,
                "lensHeightDp": -50.0,
                "springStiffness": 999999.0
            }
        }
        """.trimIndent()

        val parsed = GlassControlsSerializer.fromJson(outOfBoundsJson)
        // blurDp clamped to 64f max
        assertEquals(64f, parsed.global.blurDp)
        // lensHeightDp clamped to 0f min
        assertEquals(0f, parsed.global.lensHeightDp)
        // springStiffness clamped to 2000f max
        assertEquals(2000f, parsed.global.springStiffness)
    }

    @Test
    fun testTwoTierResetEngine() {
        // 1. Reset component to global
        val customGlobal = MasterGlassConfig(blurDp = 12f, springStiffness = 500f)
        val customizedButton = ComponentGlassConfig(
            useGlobal = false,
            blurDp = 3f,
            forceLighterInLists = true,
            springStiffness = 900f
        )
        val config = GlassControlsConfig(
            global = customGlobal,
            buttons = customizedButton
        )

        val resetConfig = GlassControlsSerializer.resetComponentToGlobal(config, GlassComponentType.BUTTONS)
        val resetButton = resetConfig.buttons
        assertTrue(resetButton.useGlobal)
        assertFalse(resetButton.forceLighterInLists)
        assertEquals(customGlobal.blurDp, resetButton.blurDp)
        assertEquals(customGlobal.springStiffness, resetButton.springStiffness)

        // 2. Reset all to factory defaults
        val factoryDefaults = GlassControlsSerializer.resetAllToFactoryDefaults()
        assertEquals(GlassControlsConfig.PRESET_BALANCED, factoryDefaults)
    }
}
