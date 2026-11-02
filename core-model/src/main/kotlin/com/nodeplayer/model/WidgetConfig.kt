package com.nodeplayer.model

enum class WidgetAccentMode(val title: String) {
    DYNAMIC_PALETTE("Dynamic Palette (from Album Art)"),
    WARM_AMBER_COOL_CYAN("Amber & Cyan Rim Light"),
    ELECTRIC_VIOLET("Electric Violet & Neon Blue"),
    MONOCHROME_GLASS("Obsidian Monochrome")
}

data class WidgetSettings(
    val accentMode: WidgetAccentMode = WidgetAccentMode.DYNAMIC_PALETTE,
    val glassOpacity: Float = 0.52f,
    val cornerRadiusDp: Float = 24.0f,
    val isPerformanceMode: Boolean = false,
    val floatingOverlayEnabled: Boolean = false,
    val showArt: Boolean = true,
    val showWaveform: Boolean = true,
    val showTitleArtist: Boolean = true,
    val showSeekbarTimes: Boolean = true,
    val showTransport: Boolean = true,
    val showLyrics: Boolean = true,
    val edgeHighlightStrength: Float = 0.85f,
    val refractionStyle: String = "LIQUID_OBSIDIAN",
    val highDpi: Boolean = true
)
