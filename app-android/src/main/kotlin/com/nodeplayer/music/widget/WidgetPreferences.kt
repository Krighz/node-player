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

    fun getSettings(context: Context): WidgetSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_ACCENT_MODE, WidgetAccentMode.DYNAMIC_PALETTE.name)
            ?: WidgetAccentMode.DYNAMIC_PALETTE.name
        val accentMode = try {
            WidgetAccentMode.valueOf(modeStr)
        } catch (_: Exception) {
            WidgetAccentMode.DYNAMIC_PALETTE
        }
        val opacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.85f).coerceIn(0.0f, 1.0f)
        val radius = prefs.getFloat(KEY_CORNER_RADIUS, 24.0f).coerceIn(12f, 36f)
        val perfMode = prefs.getBoolean(KEY_PERF_MODE, false)
        val floatingOverlay = prefs.getBoolean(KEY_FLOATING_OVERLAY, false)

        return WidgetSettings(
            accentMode = accentMode,
            glassOpacity = opacity,
            cornerRadiusDp = radius,
            isPerformanceMode = perfMode,
            floatingOverlayEnabled = floatingOverlay
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
            .apply()
    }
}
