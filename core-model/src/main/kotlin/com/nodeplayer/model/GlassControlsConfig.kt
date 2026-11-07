package com.nodeplayer.model

enum class GlassControlsPreset(val displayName: String) {
    FULL("Full Glass (120fps Springs & High Optics)"),
    BALANCED("Balanced Glass (Recommended)"),
    LIGHT("Light Glass (Plain Squircle Controls)")
}

data class MasterGlassConfig(
    val blurDp: Float = 8f,
    val lensHeightDp: Float = 24f,
    val lensAmountDp: Float = 24f,
    val vibrancy: Float = 1.0f,
    val tintOpacity: Float = 0.40f,
    val highlightStrength: Float = 0.85f,
    val shadowStrength: Float = 0.35f,
    val chromaticAberration: Float = 0.25f,
    val cornerShapeDp: Float = 16f,
    val pressScale: Float = 0.96f,
    val springStiffness: Float = 380f,
    val springDamping: Float = 0.82f
)

data class ComponentGlassConfig(
    val useGlobal: Boolean = true,
    val blurDp: Float = 8f,
    val lensHeightDp: Float = 24f,
    val lensAmountDp: Float = 24f,
    val vibrancy: Float = 1.0f,
    val tintOpacity: Float = 0.40f,
    val highlightStrength: Float = 0.85f,
    val shadowStrength: Float = 0.35f,
    val chromaticAberration: Float = 0.25f,
    val cornerShapeDp: Float = 16f,
    val pressScale: Float = 0.96f,
    val springStiffness: Float = 380f,
    val springDamping: Float = 0.82f,
    val forceLighterInLists: Boolean = false
) {
    fun resolve(global: MasterGlassConfig, inList: Boolean = false): ResolvedGlassParams {
        if (inList && forceLighterInLists) {
            return ResolvedGlassParams.LIGHTER_SQUIRCLE
        }
        return ResolvedGlassParams(
            blurDp = if (useGlobal) global.blurDp else this.blurDp,
            lensHeightDp = if (useGlobal) global.lensHeightDp else this.lensHeightDp,
            lensAmountDp = if (useGlobal) global.lensAmountDp else this.lensAmountDp,
            vibrancy = if (useGlobal) global.vibrancy else this.vibrancy,
            tintOpacity = if (useGlobal) global.tintOpacity else this.tintOpacity,
            highlightStrength = if (useGlobal) global.highlightStrength else this.highlightStrength,
            shadowStrength = if (useGlobal) global.shadowStrength else this.shadowStrength,
            chromaticAberration = if (useGlobal) global.chromaticAberration else this.chromaticAberration,
            cornerShapeDp = if (useGlobal) global.cornerShapeDp else this.cornerShapeDp,
            pressScale = if (useGlobal) global.pressScale else this.pressScale,
            springStiffness = if (useGlobal) global.springStiffness else this.springStiffness,
            springDamping = if (useGlobal) global.springDamping else this.springDamping,
            isPlainSquircle = false
        )
    }
}

data class ResolvedGlassParams(
    val blurDp: Float,
    val lensHeightDp: Float,
    val lensAmountDp: Float,
    val vibrancy: Float,
    val tintOpacity: Float,
    val highlightStrength: Float,
    val shadowStrength: Float,
    val chromaticAberration: Float,
    val cornerShapeDp: Float,
    val pressScale: Float,
    val springStiffness: Float,
    val springDamping: Float,
    val isPlainSquircle: Boolean = false
) {
    companion object {
        val LIGHTER_SQUIRCLE = ResolvedGlassParams(
            blurDp = 0f,
            lensHeightDp = 0f,
            lensAmountDp = 0f,
            vibrancy = 0f,
            tintOpacity = 0.08f,
            highlightStrength = 0.25f,
            shadowStrength = 0f,
            chromaticAberration = 0f,
            cornerShapeDp = 12f,
            pressScale = 0.96f,
            springStiffness = 500f,
            springDamping = 0.75f,
            isPlainSquircle = true
        )
    }
}

enum class GlassComponentType(val displayName: String, val supportsListOverride: Boolean) {
    BUTTONS("Buttons & Icon Buttons", true),
    TOGGLES("Toggles & Switches", true),
    SLIDERS("Continuous Sliders", false),
    DOCK("Dock & Navigation Tabs", false),
    CHIPS("Filter & Action Chips", true),
    DIALOGS("Dialogs & Modal Sheets", false),
    MINI_PLAYER("Mini Player Capsule", false),
    NOW_PLAYING("Now Playing Deck & Hero", false),
    WIDGETS("Widgets & System Overlays", false)
}

data class GlassControlsConfig(
    val masterEnabled: Boolean = true,
    val preset: GlassControlsPreset = GlassControlsPreset.BALANCED,
    val global: MasterGlassConfig = MasterGlassConfig(),
    val buttons: ComponentGlassConfig = ComponentGlassConfig(),
    val toggles: ComponentGlassConfig = ComponentGlassConfig(),
    val sliders: ComponentGlassConfig = ComponentGlassConfig(),
    val dock: ComponentGlassConfig = ComponentGlassConfig(),
    val chips: ComponentGlassConfig = ComponentGlassConfig(),
    val dialogs: ComponentGlassConfig = ComponentGlassConfig(),
    val miniPlayer: ComponentGlassConfig = ComponentGlassConfig(),
    val nowPlaying: ComponentGlassConfig = ComponentGlassConfig(),
    val widgets: ComponentGlassConfig = ComponentGlassConfig()
) {
    fun getComponent(type: GlassComponentType): ComponentGlassConfig = when (type) {
        GlassComponentType.BUTTONS -> buttons
        GlassComponentType.TOGGLES -> toggles
        GlassComponentType.SLIDERS -> sliders
        GlassComponentType.DOCK -> dock
        GlassComponentType.CHIPS -> chips
        GlassComponentType.DIALOGS -> dialogs
        GlassComponentType.MINI_PLAYER -> miniPlayer
        GlassComponentType.NOW_PLAYING -> nowPlaying
        GlassComponentType.WIDGETS -> widgets
    }

    fun updateComponent(type: GlassComponentType, config: ComponentGlassConfig): GlassControlsConfig = when (type) {
        GlassComponentType.BUTTONS -> copy(buttons = config)
        GlassComponentType.TOGGLES -> copy(toggles = config)
        GlassComponentType.SLIDERS -> copy(sliders = config)
        GlassComponentType.DOCK -> copy(dock = config)
        GlassComponentType.CHIPS -> copy(chips = config)
        GlassComponentType.DIALOGS -> copy(dialogs = config)
        GlassComponentType.MINI_PLAYER -> copy(miniPlayer = config)
        GlassComponentType.NOW_PLAYING -> copy(nowPlaying = config)
        GlassComponentType.WIDGETS -> copy(widgets = config)
    }

    fun resolve(type: GlassComponentType, inList: Boolean = false): ResolvedGlassParams {
        if (!masterEnabled) return ResolvedGlassParams.LIGHTER_SQUIRCLE
        return getComponent(type).resolve(global, inList)
    }

    companion object {
        val PRESET_FULL = GlassControlsConfig(
            masterEnabled = true,
            preset = GlassControlsPreset.FULL,
            global = MasterGlassConfig(
                blurDp = 8f,
                lensHeightDp = 24f,
                lensAmountDp = 24f,
                vibrancy = 1.0f,
                tintOpacity = 0.40f,
                highlightStrength = 0.90f,
                shadowStrength = 0.40f,
                chromaticAberration = 0.30f,
                cornerShapeDp = 16f,
                pressScale = 0.95f,
                springStiffness = 380f,
                springDamping = 0.82f
            )
        )

        val PRESET_BALANCED = GlassControlsConfig(
            masterEnabled = true,
            preset = GlassControlsPreset.BALANCED,
            global = MasterGlassConfig(
                blurDp = 4f,
                lensHeightDp = 14f,
                lensAmountDp = 12f,
                vibrancy = 0.85f,
                tintOpacity = 0.35f,
                highlightStrength = 0.75f,
                shadowStrength = 0.25f,
                chromaticAberration = 0.15f,
                cornerShapeDp = 16f,
                pressScale = 0.96f,
                springStiffness = 450f,
                springDamping = 0.80f
            )
        )

        val PRESET_LIGHT = GlassControlsConfig(
            masterEnabled = false,
            preset = GlassControlsPreset.LIGHT,
            global = MasterGlassConfig(
                blurDp = 0f,
                lensHeightDp = 0f,
                lensAmountDp = 0f,
                vibrancy = 0f,
                tintOpacity = 0.12f,
                highlightStrength = 0.30f,
                shadowStrength = 0f,
                chromaticAberration = 0f,
                cornerShapeDp = 16f,
                pressScale = 0.96f,
                springStiffness = 500f,
                springDamping = 0.75f
            )
        )
    }
}
