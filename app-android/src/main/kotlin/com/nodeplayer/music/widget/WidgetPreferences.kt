package com.nodeplayer.music.widget

import android.content.Context
import com.nodeplayer.model.WidgetAccentMode
import com.nodeplayer.model.WidgetSettings

object WidgetPreferences {
    private const val PREFS_NAME = "node_player_prefs"
    private const val KEY_ACCENT_MODE = "widget_accent_mode"
    private const val KEY_GLASS_OPACITY = "widget_glass_opacity"
    private const val KEY_CORNER_RADIUS = "widget_corner_radius"
    private const val KEY_PERF_MODE = "widget_perf_mode"
    private const val KEY_FLOATING_OVERLAY = "widget_floating_overlay"
    private const val KEY_SHOW_ART = "widget_show_art"
    private const val KEY_SHOW_WAVEFORM = "widget_show_waveform"
    private const val KEY_SHOW_TITLE_ARTIST = "widget_show_title_artist"
    private const val KEY_SHOW_SEEKBAR_TIMES = "widget_show_seekbar_times"
    private const val KEY_SHOW_TRANSPORT = "widget_show_transport"
    private const val KEY_SHOW_LYRICS = "widget_show_lyrics"
    private const val KEY_EDGE_HIGHLIGHT = "widget_edge_highlight"
    private const val KEY_REFRACTION_STYLE = "widget_refraction_style"
    private const val KEY_HIGH_DPI = "widget_high_dpi"

    fun getSettings(context: Context): WidgetSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_ACCENT_MODE, WidgetAccentMode.DYNAMIC_PALETTE.name)
            ?: WidgetAccentMode.DYNAMIC_PALETTE.name
        val accentMode = try {
            WidgetAccentMode.valueOf(modeStr)
        } catch (_: Exception) {
            WidgetAccentMode.DYNAMIC_PALETTE
        }
        val opacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.52f).coerceIn(0.0f, 1.0f)
        val radius = prefs.getFloat(KEY_CORNER_RADIUS, 24.0f).coerceIn(12f, 36f)
        val perfMode = prefs.getBoolean(KEY_PERF_MODE, false)
        val floatingOverlay = prefs.getBoolean(KEY_FLOATING_OVERLAY, false)
        val showArt = prefs.getBoolean(KEY_SHOW_ART, true)
        val showWaveform = prefs.getBoolean(KEY_SHOW_WAVEFORM, true)
        val showTitleArtist = prefs.getBoolean(KEY_SHOW_TITLE_ARTIST, true)
        val showSeekbarTimes = prefs.getBoolean(KEY_SHOW_SEEKBAR_TIMES, true)
        val showTransport = prefs.getBoolean(KEY_SHOW_TRANSPORT, true)
        val showLyrics = prefs.getBoolean(KEY_SHOW_LYRICS, true)
        val edgeHighlight = prefs.getFloat(KEY_EDGE_HIGHLIGHT, 0.85f).coerceIn(0.0f, 1.0f)
        val refractionStyle = prefs.getString(KEY_REFRACTION_STYLE, "LIQUID_OBSIDIAN") ?: "LIQUID_OBSIDIAN"
        val highDpi = prefs.getBoolean(KEY_HIGH_DPI, true)

        return WidgetSettings(
            accentMode = accentMode,
            glassOpacity = opacity,
            cornerRadiusDp = radius,
            isPerformanceMode = perfMode,
            floatingOverlayEnabled = floatingOverlay,
            showArt = showArt,
            showWaveform = showWaveform,
            showTitleArtist = showTitleArtist,
            showSeekbarTimes = showSeekbarTimes,
            showTransport = showTransport,
            showLyrics = showLyrics,
            edgeHighlightStrength = edgeHighlight,
            refractionStyle = refractionStyle,
            highDpi = highDpi
        )
    }

    fun saveSettings(context: Context, settings: WidgetSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCENT_MODE, settings.accentMode.name)
            .putFloat(KEY_GLASS_OPACITY, settings.glassOpacity)
            .putFloat(KEY_CORNER_RADIUS, settings.cornerRadiusDp)
            .putBoolean(KEY_PERF_MODE, settings.isPerformanceMode)
            .putBoolean(KEY_FLOATING_OVERLAY, settings.floatingOverlayEnabled)
            .putBoolean(KEY_SHOW_ART, settings.showArt)
            .putBoolean(KEY_SHOW_WAVEFORM, settings.showWaveform)
            .putBoolean(KEY_SHOW_TITLE_ARTIST, settings.showTitleArtist)
            .putBoolean(KEY_SHOW_SEEKBAR_TIMES, settings.showSeekbarTimes)
            .putBoolean(KEY_SHOW_TRANSPORT, settings.showTransport)
            .putBoolean(KEY_SHOW_LYRICS, settings.showLyrics)
            .putFloat(KEY_EDGE_HIGHLIGHT, settings.edgeHighlightStrength)
            .putString(KEY_REFRACTION_STYLE, settings.refractionStyle)
            .putBoolean(KEY_HIGH_DPI, settings.highDpi)
            .apply()
    }
}
