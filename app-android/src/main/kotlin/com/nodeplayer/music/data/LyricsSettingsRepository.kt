package com.nodeplayer.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nodeplayer.model.LyricsGlowPalette
import com.nodeplayer.model.LyricsSettings
import com.nodeplayer.model.LyricsStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object LyricsSettingsRepository {
    val KEY_LYRICS_STYLE = stringPreferencesKey("lyrics_style")
    val KEY_LYRICS_COMMON_ZOOM = floatPreferencesKey("lyrics_common_zoom")
    val KEY_LYRICS_SPARKLE_DENSITY = floatPreferencesKey("lyrics_sparkle_density")
    val KEY_LYRICS_LR_SWING = floatPreferencesKey("lyrics_lr_swing")
    val KEY_LYRICS_VERTICAL_SPACING = floatPreferencesKey("lyrics_vertical_spacing")
    val KEY_LYRICS_CAMERA_GLIDE = floatPreferencesKey("lyrics_camera_glide")
    val KEY_LYRICS_BOUNCE = floatPreferencesKey("lyrics_bounce")
    val KEY_LYRICS_GLOW_PALETTE = stringPreferencesKey("lyrics_glow_palette")
    val KEY_LYRICS_SHOW_TOP_BAR = booleanPreferencesKey("lyrics_show_top_bar")
    val KEY_LYRICS_PREFERRED_PROVIDER = stringPreferencesKey("lyrics_preferred_provider")
    val KEY_LYRICS_ROMANIZE_FOREIGN = booleanPreferencesKey("lyrics_romanize_foreign")
    val KEY_LYRICS_DISPLAY_MODE = stringPreferencesKey("lyrics_display_mode")
    val KEY_LYRICS_ALBUM_ART_SHAPE = stringPreferencesKey("lyrics_album_art_shape")
    val KEY_LYRICS_HIDE_STATUS_BAR = booleanPreferencesKey("lyrics_hide_status_bar")

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settingsState = MutableStateFlow(LyricsSettings())
    val settingsState: StateFlow<LyricsSettings> = _settingsState.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        repositoryScope.launch {
            getSettingsFlow(context).collect { settings ->
                _settingsState.value = settings
            }
        }
    }

    fun getSettingsFlow(context: Context): Flow<LyricsSettings> {
        return context.dataStore.data.map { prefs ->
            val styleStr = prefs[KEY_LYRICS_STYLE] ?: LyricsStyle.CINEMATIC_KINETIC.name
            val style = try {
                LyricsStyle.valueOf(styleStr)
            } catch (_: Exception) {
                LyricsStyle.CINEMATIC_KINETIC
            }
            val paletteId = prefs[KEY_LYRICS_GLOW_PALETTE] ?: "white"
            val palette = LyricsGlowPalette.fromId(paletteId)
            val providerId = prefs[KEY_LYRICS_PREFERRED_PROVIDER] ?: "auto"
            val provider = com.nodeplayer.model.LyricsProviderChoice.fromId(providerId)
            val romanize = prefs[KEY_LYRICS_ROMANIZE_FOREIGN] ?: true
            val displayModeStr = prefs[KEY_LYRICS_DISPLAY_MODE] ?: com.nodeplayer.model.LyricsDisplayMode.DUAL_LINE.name
            val displayMode = try {
                com.nodeplayer.model.LyricsDisplayMode.valueOf(displayModeStr)
            } catch (_: Exception) {
                com.nodeplayer.model.LyricsDisplayMode.DUAL_LINE
            }

            val shapeId = prefs[KEY_LYRICS_ALBUM_ART_SHAPE] ?: "squircle"
            val shape = com.nodeplayer.model.AlbumArtShape.fromId(shapeId)
            val hideBar = prefs[KEY_LYRICS_HIDE_STATUS_BAR] ?: false

            LyricsSettings(
                style = style,
                commonZoom = prefs[KEY_LYRICS_COMMON_ZOOM] ?: 1.00f,
                sparkleDensity = prefs[KEY_LYRICS_SPARKLE_DENSITY] ?: 0.35f,
                lrSwing = prefs[KEY_LYRICS_LR_SWING] ?: 0.80f,
                verticalSpacing = prefs[KEY_LYRICS_VERTICAL_SPACING] ?: 1.15f,
                cameraGlide = prefs[KEY_LYRICS_CAMERA_GLIDE] ?: 1.00f,
                bounce = prefs[KEY_LYRICS_BOUNCE] ?: 1.00f,
                palette = palette,
                showTopBar = prefs[KEY_LYRICS_SHOW_TOP_BAR] ?: true,
                preferredProvider = provider,
                romanizeForeignLyrics = romanize,
                lyricsDisplayMode = displayMode,
                albumArtShape = shape,
                hideStatusBar = hideBar
            )
        }.distinctUntilChanged()
    }

    suspend fun updateSettings(context: Context, settings: LyricsSettings) {
        _settingsState.value = settings
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRICS_STYLE] = settings.style.name
            prefs[KEY_LYRICS_COMMON_ZOOM] = settings.commonZoom
            prefs[KEY_LYRICS_SPARKLE_DENSITY] = settings.sparkleDensity
            prefs[KEY_LYRICS_LR_SWING] = settings.lrSwing
            prefs[KEY_LYRICS_VERTICAL_SPACING] = settings.verticalSpacing
            prefs[KEY_LYRICS_CAMERA_GLIDE] = settings.cameraGlide
            prefs[KEY_LYRICS_BOUNCE] = settings.bounce
            prefs[KEY_LYRICS_GLOW_PALETTE] = settings.palette.id
            prefs[KEY_LYRICS_SHOW_TOP_BAR] = settings.showTopBar
            prefs[KEY_LYRICS_PREFERRED_PROVIDER] = settings.preferredProvider.id
            prefs[KEY_LYRICS_ROMANIZE_FOREIGN] = settings.romanizeForeignLyrics
            prefs[KEY_LYRICS_DISPLAY_MODE] = settings.lyricsDisplayMode.name
            prefs[KEY_LYRICS_ALBUM_ART_SHAPE] = settings.albumArtShape.id
            prefs[KEY_LYRICS_HIDE_STATUS_BAR] = settings.hideStatusBar
        }
    }

    fun updateSettingsAsync(context: Context, update: (LyricsSettings) -> LyricsSettings) {
        val updated = update(_settingsState.value)
        _settingsState.value = updated
        repositoryScope.launch {
            updateSettings(context, updated)
        }
    }
}
