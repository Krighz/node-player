package com.nodeplayer.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.GlassControlsSerializer

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "node_player_settings")

object AppSettingsRepository {

    // Feature On/Off Switches
    val KEY_FAKE_HIRES_ENABLED = booleanPreferencesKey("fake_hires_enabled")
    val KEY_LOUDNESS_SCAN_ENABLED = booleanPreferencesKey("loudness_scan_enabled")
    val KEY_DR_METER_ENABLED = booleanPreferencesKey("dr_meter_enabled")
    val KEY_SMART_PLAYLISTS_ENABLED = booleanPreferencesKey("smart_playlists_enabled")
    val KEY_DUPLICATE_FINDER_ENABLED = booleanPreferencesKey("duplicate_finder_enabled")
    val KEY_GENRE_CROSSFADE_ENABLED = booleanPreferencesKey("genre_crossfade_enabled")
    val KEY_LASTFM_SCROBBLE_ENABLED = booleanPreferencesKey("lastfm_scrobble_enabled")
    val KEY_LISTENING_STATS_ENABLED = booleanPreferencesKey("listening_stats_enabled")
    val KEY_QUALITY_BADGES_ENABLED = booleanPreferencesKey("quality_badges_enabled")
    val KEY_QUALITY_BADGES_MODE = stringPreferencesKey("quality_badges_mode") // "COMPACT", "DETAILED", "OFF"
    val KEY_SPECTROGRAM_ENABLED = booleanPreferencesKey("spectrogram_enabled")
    val KEY_SOUND_LAB_ENABLED = booleanPreferencesKey("sound_lab_enabled")
    val KEY_MINI_PLAYER_GESTURES_ENABLED = booleanPreferencesKey("mini_player_gestures_enabled")
    val KEY_CONTROLS_GLASS_ENABLED = booleanPreferencesKey("controls_glass_enabled")
    val KEY_IS_ONLINE_MODE = booleanPreferencesKey("is_online_mode")
    val KEY_GLOBAL_LYRICS_OFFSET_MS = longPreferencesKey("global_lyrics_offset_ms")
    val KEY_AUTO_FETCH_LYRICS = booleanPreferencesKey("auto_fetch_lyrics")

    // Settings
    val KEY_GLASS_CONTROLS_CONFIG = stringPreferencesKey("glass_controls_config_json")
    val KEY_LOUDNESS_TARGET_LUFS = floatPreferencesKey("loudness_target_lufs")
    val KEY_LOUDNESS_CLIP_GUARD = booleanPreferencesKey("loudness_clip_guard")
    val KEY_AUDIO_ANALYSIS_ENABLED = booleanPreferencesKey("audio_analysis_enabled")
    val KEY_ANALYSIS_SPEED = stringPreferencesKey("analysis_speed")
    val KEY_ANALYSIS_CHARGING_ONLY = booleanPreferencesKey("analysis_charging_only")
    val KEY_SLEEP_TIMER_FADE_SECONDS = intPreferencesKey("sleep_timer_fade_seconds")
    val KEY_SLEEP_TIMER_FINISH_TRACK = booleanPreferencesKey("sleep_timer_finish_track")
    val KEY_SLEEP_TIMER_SHAKE_EXTEND = booleanPreferencesKey("sleep_timer_shake_extend")
    val KEY_LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
    val KEY_LASTFM_USERNAME = stringPreferencesKey("lastfm_username")

    data class AppSettings(
        val fakeHiResEnabled: Boolean = true,
        val loudnessScanEnabled: Boolean = true,
        val drMeterEnabled: Boolean = true,
        val smartPlaylistsEnabled: Boolean = true,
        val duplicateFinderEnabled: Boolean = true,
        val genreCrossfadeEnabled: Boolean = true,
        val lastFmScrobbleEnabled: Boolean = false,
        val listeningStatsEnabled: Boolean = true,
        val qualityBadgesEnabled: Boolean = true,
        val qualityBadgesMode: String = "DETAILED",
        val spectrogramEnabled: Boolean = true,
        val soundLabEnabled: Boolean = true,
        val miniPlayerGesturesEnabled: Boolean = true,
        val loudnessTargetLufs: Float = -14.0f,
        val loudnessClipGuard: Boolean = true,
        val audioAnalysisEnabled: Boolean = true,
        val analysisSpeed: String = "Slow",
        val analysisChargingOnly: Boolean = false,
        val sleepTimerFadeSeconds: Int = 10,
        val sleepTimerFinishTrack: Boolean = true,
        val sleepTimerShakeExtend: Boolean = true,
        val lastFmSessionKey: String = "",
        val lastFmUsername: String = "",
        val glassControlsConfig: GlassControlsConfig = GlassControlsConfig.PRESET_BALANCED,
        val isOnlineMode: Boolean = true,
        val globalLyricsOffsetMs: Long = 0L,
        val autoFetchLyrics: Boolean = true
    )

    fun getSettingsFlow(context: Context): Flow<AppSettings> {
        return context.dataStore.data.map { prefs ->
            val glassControlsConfig = prefs[KEY_GLASS_CONTROLS_CONFIG]?.let {
                GlassControlsSerializer.fromJson(it)
            } ?: GlassControlsConfig.PRESET_BALANCED

            AppSettings(
                fakeHiResEnabled = prefs[KEY_FAKE_HIRES_ENABLED] ?: true,
                loudnessScanEnabled = prefs[KEY_LOUDNESS_SCAN_ENABLED] ?: true,
                drMeterEnabled = prefs[KEY_DR_METER_ENABLED] ?: true,
                smartPlaylistsEnabled = prefs[KEY_SMART_PLAYLISTS_ENABLED] ?: true,
                duplicateFinderEnabled = prefs[KEY_DUPLICATE_FINDER_ENABLED] ?: true,
                genreCrossfadeEnabled = prefs[KEY_GENRE_CROSSFADE_ENABLED] ?: true,
                lastFmScrobbleEnabled = prefs[KEY_LASTFM_SCROBBLE_ENABLED] ?: false,
                listeningStatsEnabled = prefs[KEY_LISTENING_STATS_ENABLED] ?: true,
                qualityBadgesEnabled = prefs[KEY_QUALITY_BADGES_ENABLED] ?: true,
                qualityBadgesMode = prefs[KEY_QUALITY_BADGES_MODE] ?: "DETAILED",
                spectrogramEnabled = prefs[KEY_SPECTROGRAM_ENABLED] ?: true,
                soundLabEnabled = prefs[KEY_SOUND_LAB_ENABLED] ?: true,
                miniPlayerGesturesEnabled = prefs[KEY_MINI_PLAYER_GESTURES_ENABLED] ?: true,
                loudnessTargetLufs = prefs[KEY_LOUDNESS_TARGET_LUFS] ?: -14.0f,
                loudnessClipGuard = prefs[KEY_LOUDNESS_CLIP_GUARD] ?: true,
                audioAnalysisEnabled = prefs[KEY_AUDIO_ANALYSIS_ENABLED] ?: true,
                analysisSpeed = prefs[KEY_ANALYSIS_SPEED] ?: "Slow",
                analysisChargingOnly = prefs[KEY_ANALYSIS_CHARGING_ONLY] ?: false,
                sleepTimerFadeSeconds = prefs[KEY_SLEEP_TIMER_FADE_SECONDS] ?: 10,
                sleepTimerFinishTrack = prefs[KEY_SLEEP_TIMER_FINISH_TRACK] ?: true,
                sleepTimerShakeExtend = prefs[KEY_SLEEP_TIMER_SHAKE_EXTEND] ?: true,
                lastFmSessionKey = prefs[KEY_LASTFM_SESSION_KEY] ?: "",
                lastFmUsername = prefs[KEY_LASTFM_USERNAME] ?: "",
                glassControlsConfig = glassControlsConfig,
                isOnlineMode = prefs[KEY_IS_ONLINE_MODE] ?: true,
                globalLyricsOffsetMs = prefs[KEY_GLOBAL_LYRICS_OFFSET_MS] ?: 0L,
                autoFetchLyrics = prefs[KEY_AUTO_FETCH_LYRICS] ?: true
            )
        }
    }

    suspend fun updateGlassControlsConfig(context: Context, config: GlassControlsConfig) {
        val json = GlassControlsSerializer.toJson(config)
        context.dataStore.edit { prefs ->
            prefs[KEY_GLASS_CONTROLS_CONFIG] = json
            prefs[KEY_CONTROLS_GLASS_ENABLED] = config.masterEnabled
        }
    }

    suspend fun updateSetting(context: Context, key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun updateSetting(context: Context, key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun updateSetting(context: Context, key: Preferences.Key<Float>, value: Float) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun updateSetting(context: Context, key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun updateSetting(context: Context, key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    fun getTrackOffsetKey(trackId: Long): Preferences.Key<Long> {
        return longPreferencesKey("lyrics_offset_track_$trackId")
    }

    suspend fun resetToDefaults(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
