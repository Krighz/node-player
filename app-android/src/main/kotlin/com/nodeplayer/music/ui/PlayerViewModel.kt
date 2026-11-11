package com.nodeplayer.music.ui

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.nodeplayer.dsp.*
import com.nodeplayer.model.*
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.data.TrackEntity
import com.nodeplayer.music.diagnostics.GlassShaderSelfTest
import com.nodeplayer.music.diagnostics.GraphicsEventTracker
import com.nodeplayer.music.diagnostics.ShaderSelfTestResult
import com.nodeplayer.music.service.PlaybackService
import com.nodeplayer.music.ui.theme.DefaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NodePlayerApplication
    private val dao = app.database.playerDao()
    private val scanner = app.libraryScanner
    private val prefs: SharedPreferences by lazy {
        app.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    // State flows
    val allTracks = dao.getAllTracks().map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums = allTracks.map { scanner.extractAlbums(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists = allTracks.map { scanner.extractArtists(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders = allTracks.map { scanner.extractFolders(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allScannedFolders = dao.getAllScannedFolders()
        .map { list -> list.map { it.toMusicFolder() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedFolder = MutableStateFlow<MusicFolder?>(null)
    val selectedFolder: StateFlow<MusicFolder?> = _selectedFolder.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _accentColor = MutableStateFlow(DefaultAccent)
    val accentColor: StateFlow<Color> = _accentColor.asStateFlow()

    private val _audioOutputInfo = MutableStateFlow(AudioOutputInfo())
    val audioOutputInfo: StateFlow<AudioOutputInfo> = _audioOutputInfo.asStateFlow()

    private val _effectConfig = MutableStateFlow(AudioEffectConfig())
    val effectConfig: StateFlow<AudioEffectConfig> = _effectConfig.asStateFlow()

    private val _themeConfig = MutableStateFlow(GlassThemeConfig())
    val themeConfig: StateFlow<GlassThemeConfig> = _themeConfig.asStateFlow()

    // Real audio amplitude tracking from DSP processor
    private val _currentRms = MutableStateFlow(0.0f)
    val currentRms: StateFlow<Float> = _currentRms.asStateFlow()

    // Crossfade configuration
    private val _crossfadeConfig = MutableStateFlow(CrossfadeConfig())
    val crossfadeConfig: StateFlow<CrossfadeConfig> = _crossfadeConfig.asStateFlow()

    // Shuffle state
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // Folder Filtering & Library Preferences
    private val _minDurationSeconds = MutableStateFlow(30)
    val minDurationSeconds: StateFlow<Int> = _minDurationSeconds.asStateFlow()

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _customFolders = MutableStateFlow<Set<String>>(emptySet())
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _whitelistedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val whitelistedTrackIds: StateFlow<Set<Long>> = _whitelistedTrackIds.asStateFlow()

    private val _excludedTracks = MutableStateFlow<List<Track>>(emptyList())
    val excludedTracks: StateFlow<List<Track>> = _excludedTracks.asStateFlow()

    // System-Wide Equalizer
    private val _systemEqEnabled = MutableStateFlow(false)
    val systemEqEnabled: StateFlow<Boolean> = _systemEqEnabled.asStateFlow()
    val systemEqSessions: StateFlow<List<com.nodeplayer.music.service.SystemWideEqService.SessionInfo>> =
        com.nodeplayer.music.service.SystemWideEqService.activeSessionsState

    // Output Device & Per-Device Profile
    private val _currentDeviceType = MutableStateFlow(OutputDeviceType.SPEAKER)
    val currentDeviceType: StateFlow<OutputDeviceType> = _currentDeviceType.asStateFlow()

    private val _autoDeviceProfilesEnabled = MutableStateFlow(true)
    val autoDeviceProfilesEnabled: StateFlow<Boolean> = _autoDeviceProfilesEnabled.asStateFlow()

    // Convolver CPU Tracking
    private val _convolverCpuPercent = MutableStateFlow(0.0f)
    val convolverCpuPercent: StateFlow<Float> = _convolverCpuPercent.asStateFlow()

    // App Settings DataStore Flow
    val appSettings = com.nodeplayer.music.data.AppSettingsRepository.getSettingsFlow(app)
        .stateIn(viewModelScope, SharingStarted.Lazily, com.nodeplayer.music.data.AppSettingsRepository.AppSettings())

    // Daily Listening Stats Flow
    val dailyListeningStats = dao.getAllDailyStats()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sessionListeningSeconds = MutableStateFlow(0L)
    private var pauseStartTimestamp = 0L
    private var lastSessionTickMs = System.currentTimeMillis()
    private var lastPrefsSaveMs = 0L

    // Resume on Headset Connect
    private val _resumeOnHeadsetConnect = MutableStateFlow(true)
    val resumeOnHeadsetConnect: StateFlow<Boolean> = _resumeOnHeadsetConnect.asStateFlow()

    // Resampler Output Rate
    private val _resamplerRateChoice = MutableStateFlow(0) // 0 = Auto
    val resamplerRateChoice: StateFlow<Int> = _resamplerRateChoice.asStateFlow()

    // Active Preset (Track or Album)
    private val _activePresetLabel = MutableStateFlow<String?>(null)
    val activePresetLabel: StateFlow<String?> = _activePresetLabel.asStateFlow()

    // Graphics Self-Test Result & Fallback State (hydrated asynchronously in init)
    private val _shaderSelfTestResult = MutableStateFlow(ShaderSelfTestResult(passed = true, reason = "Not executed yet"))
    val shaderSelfTestResult: StateFlow<ShaderSelfTestResult> = _shaderSelfTestResult.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var progressJob: Job? = null
    private val seekPersistDebouncer = TrailingSeekDebouncer(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        delayMs = 300L
    ) { positionMs ->
        _currentTrack.value?.let { track ->
            prefs.edit().putLong("last_pos", positionMs).putLong("last_track_id", track.id).apply()
        }
        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val audioParserVersion = prefs.getInt("audio_parser_version", 0)
            if (audioParserVersion < 2) {
                prefs.edit().putInt("audio_parser_version", 2).apply()
            }
            GraphicsEventTracker.init(app)
            loadFilterPreferences()
            detectAudioOutput()
        }
        runGraphicsSelfTest()
        initMediaController()
        refreshLibrary()
        registerAudioDeviceCallback()
        restoreLastPlaybackSession()

        // Sync and observe AppModeState with persistent settings
        viewModelScope.launch {
            appSettings.collect { settings ->
                com.nodeplayer.common.AppModeState.setOnlineMode(settings.isOnlineMode)
            }
        }
    }

    private fun runGraphicsSelfTest() {
        viewModelScope.launch(Dispatchers.Default) {
            val result = GlassShaderSelfTest.checkAndRunIfNeeded(app)
            _shaderSelfTestResult.value = result
            if (!result.passed) {
                // Refresh theme config so safe fallback is applied
                _themeConfig.value = loadThemeConfig()
            }
        }
    }

    fun toggleForceSelfTestFail(forceFail: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            GlassShaderSelfTest.setForceFail(app, forceFail)
            val updated = GlassShaderSelfTest.getLastResult(app)
            _shaderSelfTestResult.value = updated
            _themeConfig.value = loadThemeConfig()
        }
    }

    private suspend fun loadFilterPreferences() {
        val whitelistStrings = prefs.getStringSet("whitelisted_track_ids", emptySet()) ?: emptySet()
        _whitelistedTrackIds.value = whitelistStrings.mapNotNull { it.toLongOrNull() }.toSet()
        val custom = prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()
        _customFolders.value = custom
        val excluded = prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
        _excludedFolders.value = excluded
        _minDurationSeconds.value = prefs.getInt("min_duration_sec", 30)

        val cfEnabled = prefs.getBoolean("crossfade_enabled", true)
        val cfDuration = prefs.getFloat("crossfade_duration", 4.0f)
        val cfCurveStr = prefs.getString("crossfade_curve", FadeCurve.EQUAL_POWER.name) ?: FadeCurve.EQUAL_POWER.name
        val cfCurve = try { FadeCurve.valueOf(cfCurveStr) } catch (_: Exception) { FadeCurve.EQUAL_POWER }
        val cfAutoGapless = prefs.getBoolean("crossfade_auto_gapless", true)
        _crossfadeConfig.value = CrossfadeConfig(
            enabled = cfEnabled,
            durationSeconds = cfDuration,
            curve = cfCurve,
            autoGapless = cfAutoGapless
        )
        _isShuffleEnabled.value = prefs.getBoolean("shuffle_enabled", false)

        val sysEq = prefs.getBoolean("system_eq_enabled", false)
        _systemEqEnabled.value = sysEq
        if (sysEq) {
            withContext(Dispatchers.Main) {
                com.nodeplayer.music.service.SystemWideEqService.start(getApplication(), _effectConfig.value)
            }
        }

        _resumeOnHeadsetConnect.value = prefs.getBoolean("resume_on_headset_connect", true)
        _resamplerRateChoice.value = prefs.getInt("resampler_output_rate", 0)
        _autoDeviceProfilesEnabled.value = prefs.getBoolean("auto_device_profiles_enabled", true)
        _themeConfig.value = loadThemeConfig()
    }

    private fun loadThemeConfig(): GlassThemeConfig {
        val selfTestPassed = GlassShaderSelfTest.getLastResult(app).passed
        return buildThemeConfigFromValues(
            glassStrength = prefs.getString("glass_strength", "BALANCED") ?: "BALANCED",
            themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            accentSource = prefs.getString("accent_source", "ARTWORK") ?: "ARTWORK",
            spacingDensity = prefs.getString("spacing_density", "COMFORTABLE") ?: "COMFORTABLE",
            cornerRoundness = prefs.getString("corner_roundness", "ROUNDED") ?: "ROUNDED",
            animatedBackdrop = prefs.getBoolean("animated_backdrop", true),
            blurRadiusDp = prefs.getFloat("glass_blur_radius", 28.0f),
            tintAlpha = prefs.getFloat("glass_tint_alpha", 0.65f),
            borderAlpha = prefs.getFloat("glass_border_alpha", 0.30f),
            isOledBlack = prefs.getBoolean("is_oled_black", false),
            useDynamicAccent = prefs.getBoolean("use_dynamic_accent", true),
            fixedAccentColorArgb = prefs.getLong("fixed_accent_color", 0xFF6366F1),
            cornerRadiusDp = prefs.getFloat("corner_radius_dp", 16.0f),
            isPerformanceMode = prefs.getBoolean("is_performance_mode", false),
            refractionShaderEnabledPref = prefs.getBoolean("refraction_shader_enabled", true),
            selfTestPassed = selfTestPassed,
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            shaderMode = prefs.getString("glass_shader_mode", "AUTO") ?: "AUTO",
            glassOpacity = prefs.getFloat("glass_opacity", 0.65f),
            blurStrength = prefs.getFloat("glass_blur_strength", 0.70f),
            glassTexture = prefs.getFloat("glass_texture", 0.40f),
            edgeHighlightStrength = prefs.getFloat("glass_edge_highlight_strength", 0.60f),
            isFullyTransparent = prefs.getBoolean("glass_fully_transparent", false),
            keepTextReadable = prefs.getBoolean("glass_keep_text_readable", true),
            backgroundAnimationStyle = prefs.getString("background_animation_style", "LIQUID_BLOBS") ?: "LIQUID_BLOBS",
            backgroundAnimationSpeed = prefs.getFloat("background_animation_speed", 1.0f),
            backgroundAnimationIntensity = prefs.getFloat("background_animation_intensity", 0.8f),
            backgroundColorSource = prefs.getString("background_color_source", "ARTWORK") ?: "ARTWORK",
            animateOnlyWhilePlaying = prefs.getBoolean("animate_only_while_playing", false),
            frameRateCap = prefs.getString("frame_rate_cap", "AUTO") ?: "AUTO",
            renderQuality = prefs.getString("render_quality", "MAX_NATIVE") ?: "MAX_NATIVE",
            antiBandingDithering = prefs.getBoolean("anti_banding_dithering", true),
            fontFamily = prefs.getString("font_family", "SYSTEM") ?: "SYSTEM",
            showDockSearchButton = prefs.getBoolean("show_dock_search_button", true),
            showMiniPlayerWave = prefs.getBoolean("show_mini_player_wave", true),
            formatBadgeVisible = prefs.getBoolean("format_badge_visible", true),
            lensRefractionStrength = prefs.getFloat("lens_refraction_strength", 0.85f),
            lensRimSpecular = prefs.getFloat("lens_rim_specular", 0.85f),
            animationPreset = prefs.getString("animation_preset", "SMOOTH") ?: "SMOOTH",
            motionSpeed = prefs.getFloat("motion_speed", 1.0f),
            springBounciness = prefs.getFloat("spring_bounciness", 0.82f),
            springStiffness = prefs.getFloat("spring_stiffness", 380f),
            switchOvershootEnabled = prefs.getBoolean("switch_overshoot_enabled", true),
            pressScaleEnabled = prefs.getBoolean("press_scale_enabled", true),
            pressScaleStrength = prefs.getFloat("press_scale_strength", 0.96f),
            staggeredListAnimation = prefs.getBoolean("staggered_list_animation", true),
            lyricsAnimationEnabled = prefs.getBoolean("lyrics_animation_enabled", true),
            screenTransitionStyle = prefs.getString("screen_transition_style", "SLIDE") ?: "SLIDE",
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            dockGlassLensEnabled = prefs.getBoolean("dock_glass_lens_enabled", true),
            dockGlassRimEnabled = prefs.getBoolean("dock_glass_rim_enabled", true),
            dockGlassPuckEnabled = prefs.getBoolean("dock_glass_puck_enabled", true),
            dockAutoHideEnabled = prefs.getBoolean("dock_auto_hide_enabled", true),
            dockAutoHideMiniPlayer = prefs.getBoolean("dock_auto_hide_mini_player", true),
            dockScrollSensitivity = prefs.getFloat("dock_scroll_sensitivity", 1.0f),
            dockReturnDelayMs = prefs.getLong("dock_return_delay_ms", 1200L),
            lyricsFontSizeSp = prefs.getFloat("lyrics_font_size_sp", 28.0f),
            lyricsAutoHideDelaySec = prefs.getInt("lyrics_auto_hide_delay_sec", 5),
            controlsGlassEnabled = prefs.getBoolean("controls_glass_enabled", true)
        )
    }

    private fun registerAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    viewModelScope.launch(Dispatchers.IO) { detectAudioOutput() }
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    viewModelScope.launch(Dispatchers.IO) { detectAudioOutput() }
                }
            }
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
    }

    data class PendingPlayRequest(
        val track: Track,
        val queue: List<Track>,
        val targetIndex: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    private var pendingPlayRequest: PendingPlayRequest? = null

    private fun processPendingPlayRequest() {
        val pending = pendingPlayRequest ?: return
        pendingPlayRequest = null
        val waitTime = System.currentTimeMillis() - pending.timestamp
        Log.i("PlayerViewModel", "Executing queued tap for '${pending.track.title}' (queued ${waitTime}ms ago)")
        playTrack(pending.track, pending.queue)
    }

    private fun restoreLastPlaybackSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastTrackId = prefs.getLong("last_track_id", -1L)
            val lastPos = prefs.getLong("last_pos", 0L)
            val allList = dao.getAllTracksList().map { it.toTrack() }
            withContext(Dispatchers.Main) {
                if (allList.isNotEmpty()) {
                    _queue.value = allList
                }
                if (lastTrackId > 0) {
                    val track = allList.find { it.id == lastTrackId }
                    if (track != null) {
                        _currentTrack.value = track
                        _currentPositionMs.value = lastPos
                        updatePaletteAccent(track)
                        updateSignalPath(track)
                        if (allList.isNotEmpty()) {
                            val targetIdx = allList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                            PlaybackService.instance?.crossfadeManager?.setQueueIfEmpty(allList, targetIdx)
                        }
                    }
                }
            }
        }
    }

    private fun initMediaController() {
        val bindTimer: (com.nodeplayer.music.service.PlaybackService) -> Unit = { srv ->
            val timer = srv.sleepTimer
            if (timer != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    timer.remainingSeconds.collect { rem ->
                        _sleepTimerRemainingSeconds.value = rem
                    }
                }
            }
        }

        PlaybackService.instance?.let { bindTimer(it) }

        PlaybackService.onServiceCreated = { srv ->
            viewModelScope.launch(Dispatchers.Main) {
                setupPlayerListener()
                processPendingPlayRequest()
                bindTimer(srv)
            }
        }

        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
                processPendingPlayRequest()
            } catch (e: Exception) {
                Log.e("NodePlayer", "MediaController connect failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        PlaybackService.onAudioTrackConfigChangedCallback = { sampleRate, encoding, channels ->
            _audioOutputInfo.value = _audioOutputInfo.value.copy(
                sampleRate = sampleRate,
                format = encoding,
                channels = channels,
                isTrackInitialized = true,
                isResampled = _currentTrack.value?.let { it.sampleRate > 0 && it.sampleRate != sampleRate } ?: false
            )
        }

        PlaybackService.instance?.let { service ->
            service.onTrackTransitionCallback = { track ->
                viewModelScope.launch {
                    _currentTrack.value = track
                    updatePaletteAccent(track)
                    updateSignalPath(track)
                    dao.incrementPlayCount(track.id)
                    checkAndApplyTrackOrAlbumPreset(track)
                }
            }
            service.crossfadeManager.crossfadeConfig = _crossfadeConfig.value
            service.crossfadeManager.setShuffle(_isShuffleEnabled.value)
            service.crossfadeManager.applyEffectConfig(_effectConfig.value)
        }

        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionTracking() else stopPositionTracking()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("NodePlayer", "ExoPlayer error occurred: ${error.errorCodeName} - ${error.message}", error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: return
                viewModelScope.launch {
                    val trackEntity = dao.getTrackById(mediaId)
                    val track = trackEntity?.toTrack()
                    _currentTrack.value = track
                    track?.let {
                        updatePaletteAccent(it)
                        updateSignalPath(it)
                        dao.incrementPlayCount(it.id)
                        checkAndApplyTrackOrAlbumPreset(it)
                    }
                }
            }
        })
    }

    fun refreshLibrary(
        includedFolders: Set<String> = _customFolders.value,
        excludedFolders: Set<String> = _excludedFolders.value,
        minDurationSec: Int = _minDurationSeconds.value,
        whitelistedIds: Set<Long> = _whitelistedTrackIds.value
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val minMs = minDurationSec * 1000L
            val scannedTracks = scanner.scanAllTracks(
                minDurationMs = minMs,
                includedFolders = includedFolders,
                whitelistedTrackIds = whitelistedIds
            )

            // Index all detected folders in the separate folder index
            val detectedFolders = scanner.extractFolders(scannedTracks)
            val folderEntities = detectedFolders.map { f ->
                com.nodeplayer.music.data.ScannedFolderEntity(
                    path = f.path,
                    name = f.name,
                    trackCount = f.trackCount,
                    isEnabled = !excludedFolders.contains(f.path)
                )
            }
            dao.insertScannedFolders(folderEntities)

            // Mark tracks in excluded folders as hidden (do NOT delete them)
            val existingTracks = dao.getAllRawTracksList().associateBy { it.id }
            val entities = scannedTracks.map { track ->
                val isHidden = excludedFolders.any { excludedPath ->
                    track.path.startsWith(excludedPath, ignoreCase = true)
                }
                val existing = existingTracks[track.id]
                TrackEntity.fromTrack(track).copy(
                    isHidden = isHidden,
                    isFavorite = existing?.isFavorite ?: track.isFavorite,
                    playCount = existing?.playCount ?: track.playCount,
                    lastPlayedTimestamp = existing?.lastPlayedTimestamp ?: track.lastPlayedTimestamp,
                    hiResStatus = existing?.hiResStatus ?: track.hiResStatus.name,
                    hiResConfidence = existing?.hiResConfidence ?: track.hiResConfidence,
                    hiResCutoffHz = existing?.hiResCutoffHz ?: track.hiResCutoffHz,
                    loudnessLufs = existing?.loudnessLufs ?: track.loudnessLufs,
                    dynamicRangeScore = existing?.dynamicRangeScore ?: track.dynamicRangeScore,
                    dynamicRangeLabel = existing?.dynamicRangeLabel ?: track.dynamicRangeLabel,
                    analysisAttempts = existing?.analysisAttempts ?: track.analysisAttempts
                )
            }
            dao.insertTracks(entities)
            loadExcludedTracks(scannedTracks.filter { !it.isHidden })
        }
    }

    fun loadExcludedTracks(currentTracks: List<Track> = allTracks.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val excluded = scanner.scanExcludedTracks(currentTracks)
            _excludedTracks.value = excluded
        }
    }

    fun restoreExcludedTrack(trackId: Long) {
        val current = _whitelistedTrackIds.value.toMutableSet()
        current.add(trackId)
        _whitelistedTrackIds.value = current
        prefs.edit().putStringSet("whitelisted_track_ids", current.map { it.toString() }.toSet()).apply()
        refreshLibrary(whitelistedIds = current)
    }

    fun setMinDurationSeconds(seconds: Int) {
        _minDurationSeconds.value = seconds
        refreshLibrary(minDurationSec = seconds)
    }

    fun toggleFolderExclusion(folderPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _excludedFolders.value.toMutableSet()
            val willBeExcluded = !current.contains(folderPath)
            if (willBeExcluded) {
                current.add(folderPath)
            } else {
                current.remove(folderPath)
            }
            _excludedFolders.value = current
            prefs.edit().putStringSet("excluded_folders", current).apply()

            // Update tracks isHidden without deleting them
            val prefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
            dao.setTracksHiddenForFolder(prefix, isHidden = willBeExcluded)
            dao.setTracksHiddenForFolder(folderPath, isHidden = willBeExcluded)
            dao.setFolderEnabled(folderPath, isEnabled = !willBeExcluded)
        }
    }

    fun addCustomFolder(folderPath: String) {
        val current = _customFolders.value.toMutableSet()
        current.add(folderPath)
        _customFolders.value = current
        refreshLibrary(includedFolders = current)
    }

    fun removeCustomFolder(folderPath: String) {
        val current = _customFolders.value.toMutableSet()
        current.remove(folderPath)
        _customFolders.value = current
        refreshLibrary(includedFolders = current)
    }

    fun selectFolder(folder: MusicFolder?) {
        _selectedFolder.value = folder
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        val tapTime = System.currentTimeMillis()
        val isOnlineTrack = track.path.startsWith("nodeplayer://youtube/") || com.nodeplayer.common.AppModeState.isOnlineMode.value
        val effectiveQueue = if (!isOnlineTrack && newQueue.size <= 1 && allTracks.value.isNotEmpty()) {
            allTracks.value
        } else {
            newQueue
        }
        _queue.value = effectiveQueue
        _currentTrack.value = track
        _isPlaying.value = true
        updatePaletteAccent(track)
        updateSignalPath(track)

        val targetIndex = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.crossfadeConfig = _crossfadeConfig.value
            service.crossfadeManager.setShuffle(_isShuffleEnabled.value)
            service.crossfadeManager.playQueue(effectiveQueue, targetIndex, 0L)
            _isPlaying.value = true
            startPositionTracking()
            _currentTrack.value?.let { com.nodeplayer.music.widget.WidgetUpdateManager.saveLastKnownTrack(getApplication(), it) }
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
            Log.d("PlayerViewModel", "Play dispatched directly to service in ${System.currentTimeMillis() - tapTime}ms")
            return
        }

        val controller = mediaController
        if (controller != null) {
            viewModelScope.launch(Dispatchers.Default) {
                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
                val mediaItems = effectiveQueue.map {
                    val contentUri = if (it.path.startsWith("nodeplayer://")) {
                        Uri.parse(it.path)
                    } else if (it.path.startsWith("content:")) {
                        Uri.parse(it.path)
                    } else if (it.path.isNotBlank() && (it.path.startsWith("/") || it.path.startsWith("file:"))) {
                        val f = java.io.File(it.path)
                        if (f.exists() && f.canRead()) Uri.fromFile(f)
                        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id)
                    }
                    val artworkUri = if (it.path.startsWith("nodeplayer://")) {
                        Uri.parse(it.path)
                    } else {
                        ContentUris.withAppendedId(albumArtBaseUri, it.albumId)
                    }
                    val metadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(artworkUri)
                        .build()

                    MediaItem.Builder()
                        .setMediaId(if (it.path.startsWith("nodeplayer://")) it.path else it.id.toString())
                        .setUri(contentUri)
                        .setMediaMetadata(metadata)
                        .build()
                }
                withContext(Dispatchers.Main) {
                    controller.stop()
                    controller.clearMediaItems()
                    controller.setMediaItems(mediaItems, targetIndex, 0L)
                    controller.prepare()
                    controller.play()
                    Log.d("PlayerViewModel", "Play dispatched to MediaController in ${System.currentTimeMillis() - tapTime}ms")
                }
            }
            return
        }

        // Neither service nor mediaController is ready yet: QUEUE IT, NEVER DROP IT
        Log.i("PlayerViewModel", "Playback service/controller not ready; queued tap for '${track.title}'. Starting service eagerly...")
        pendingPlayRequest = PendingPlayRequest(track, effectiveQueue, targetIndex, tapTime)
        try {
            val serviceIntent = Intent(getApplication(), PlaybackService::class.java)
            getApplication<Application>().startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error starting PlaybackService for pending play", e)
        }
    }

    fun togglePlayPause() {
        val service = PlaybackService.instance
        if (service != null) {
            if (service.crossfadeManager.currentQueue.isEmpty()) {
                val trackToPlay = _currentTrack.value ?: allTracks.value.firstOrNull()
                if (trackToPlay != null) {
                    playTrack(trackToPlay, allTracks.value)
                    return
                }
            }
            service.crossfadeManager.togglePlayPause()
            _isPlaying.value = service.crossfadeManager.activePlayer.isPlaying
            if (_isPlaying.value) startPositionTracking() else stopPositionTracking()
            _currentTrack.value?.let { com.nodeplayer.music.widget.WidgetUpdateManager.saveLastKnownTrack(getApplication(), it) }
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
            return
        }

        val controller = mediaController
        if (controller != null) {
            if (controller.mediaItemCount == 0 && _currentTrack.value != null) {
                playTrack(_currentTrack.value!!, allTracks.value)
                return
            }
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_ENDED ||
                    (controller.duration > 0 && controller.currentPosition >= controller.duration)
                ) {
                    controller.seekTo(0L)
                }
                controller.play()
            }
            return
        }

        val trackToPlay = _currentTrack.value ?: allTracks.value.firstOrNull()
        if (trackToPlay != null) {
            playTrack(trackToPlay, allTracks.value)
        }
    }

    fun skipNext() {
        val service = PlaybackService.instance
        if (service != null) {
            if (service.crossfadeManager.currentQueue.isEmpty() && allTracks.value.isNotEmpty()) {
                val idx = _currentTrack.value?.let { t -> allTracks.value.indexOfFirst { it.id == t.id } } ?: 0
                service.crossfadeManager.setQueueIfEmpty(allTracks.value, idx)
            }
            service.crossfadeManager.skipNext()
        } else {
            mediaController?.seekToNextMediaItem()
        }
        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
    }

    fun skipPrevious() {
        val service = PlaybackService.instance
        if (service != null) {
            if (service.crossfadeManager.currentQueue.isEmpty() && allTracks.value.isNotEmpty()) {
                val idx = _currentTrack.value?.let { t -> allTracks.value.indexOfFirst { it.id == t.id } } ?: 0
                service.crossfadeManager.setQueueIfEmpty(allTracks.value, idx)
            }
            service.crossfadeManager.skipPrevious()
        } else {
            mediaController?.seekToPreviousMediaItem()
        }
        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        prefs.edit().putBoolean("shuffle_enabled", newShuffle).apply()
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.setShuffle(newShuffle)
            _queue.value = service.crossfadeManager.currentQueue
            _currentTrack.value?.let { com.nodeplayer.music.widget.WidgetUpdateManager.saveLastKnownTrack(getApplication(), it) }
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(getApplication())
        }
    }

    fun playNext(track: Track) {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.playNext(track)
            _queue.value = service.crossfadeManager.currentQueue
        } else {
            val q = _queue.value.toMutableList()
            val cur = _currentTrack.value
            val idx = if (cur != null) q.indexOfFirst { it.id == cur.id } else -1
            if (idx >= 0) {
                q.add(idx + 1, track)
            } else {
                q.add(track)
            }
            _queue.value = q
        }
    }

    fun addToQueue(track: Track) {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.addToQueue(track)
            _queue.value = service.crossfadeManager.currentQueue
        } else {
            val q = _queue.value.toMutableList()
            q.add(track)
            _queue.value = q
        }
    }

    fun removeFromQueue(index: Int) {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.removeFromQueue(index)
            _queue.value = service.crossfadeManager.currentQueue
            _currentTrack.value = service.crossfadeManager.currentPlayingTrack
        } else {
            val q = _queue.value.toMutableList()
            if (index in q.indices) {
                q.removeAt(index)
                _queue.value = q
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.moveQueueItem(fromIndex, toIndex)
            _queue.value = service.crossfadeManager.currentQueue
        } else {
            val q = _queue.value.toMutableList()
            if (fromIndex in q.indices && toIndex in q.indices && fromIndex != toIndex) {
                val item = q.removeAt(fromIndex)
                q.add(toIndex, item)
                _queue.value = q
            }
        }
    }

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        val srv = com.nodeplayer.music.service.PlaybackService.instance
        val timer = srv?.sleepTimer
        if (timer != null) {
            if (minutes == null || minutes <= 0) {
                timer.cancel()
                _sleepTimerRemainingSeconds.value = null
            } else {
                timer.startTimer(minutes, finishTrack = false)
                _sleepTimerRemainingSeconds.value = minutes * 60L
                viewModelScope.launch(Dispatchers.Main) {
                    timer.remainingSeconds.collect { rem ->
                        _sleepTimerRemainingSeconds.value = rem
                    }
                }
            }
            return
        }
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            return
        }
        val totalSec = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSec
        sleepTimerJob = viewModelScope.launch(Dispatchers.Default) {
            var rem = totalSec
            while (rem > 0) {
                kotlinx.coroutines.delay(1000L)
                rem--
                _sleepTimerRemainingSeconds.value = rem
            }
            withContext(Dispatchers.Main) {
                val service = PlaybackService.instance
                if (service != null) {
                    service.crossfadeManager.pause()
                } else {
                    mediaController?.pause()
                }
                _isPlaying.value = false
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.seekTo(positionMs)
        } else {
            mediaController?.seekTo(positionMs)
        }
        seekPersistDebouncer.schedule(positionMs)
    }

    private fun startPositionTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val service = PlaybackService.instance
                val pos = service?.player?.currentPosition ?: mediaController?.currentPosition ?: 0L
                val dur = service?.player?.duration ?: mediaController?.duration ?: 0L
                val playing = service?.player?.isPlaying ?: mediaController?.isPlaying ?: false
                if (_isPlaying.value != playing) {
                    _isPlaying.value = playing
                }
                if (_currentPositionMs.value != pos) {
                    _currentPositionMs.value = pos
                }
                _currentTrack.value?.let { track ->
                    if (shouldUpdateTrackDuration(track.durationMs, dur)) {
                        _currentTrack.value = track.copy(durationMs = dur)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastPrefsSaveMs >= 5000L || !playing) {
                        lastPrefsSaveMs = now
                        viewModelScope.launch(Dispatchers.IO) {
                            prefs.edit().putLong("last_pos", pos).putLong("last_track_id", track.id).apply()
                        }
                    }
                }

                if (playing) {
                    if (pauseStartTimestamp > 0L && System.currentTimeMillis() - pauseStartTimestamp > 30 * 60 * 1000L) {
                        sessionListeningSeconds.value = 0L
                    }
                    pauseStartTimestamp = 0L
                    val now = System.currentTimeMillis()
                    if (now - lastSessionTickMs >= 1000L) {
                        val deltaSec = (now - lastSessionTickMs) / 1000L
                        sessionListeningSeconds.value += deltaSec
                        lastSessionTickMs = now
                    }
                } else {
                    if (pauseStartTimestamp == 0L) {
                        pauseStartTimestamp = System.currentTimeMillis()
                    }
                    lastSessionTickMs = System.currentTimeMillis()
                }

                // Read real RMS audio level from DSP engine (suppress noise below threshold)
                val nextRms = if (playing) (service?.dspAudioProcessor?.currentRms ?: 0.0f) else 0.0f
                if (shouldEmitFloatDelta(_currentRms.value, nextRms, epsilon = 0.002f)) {
                    _currentRms.value = nextRms
                }
                val nextCpu = service?.crossfadeManager?.activeDspChain?.convolver?.lastCpuUsagePercent ?: 0.0f
                if (shouldEmitFloatDelta(_convolverCpuPercent.value, nextCpu, epsilon = 0.1f)) {
                    _convolverCpuPercent.value = nextCpu
                }
                delay(120)
            }
        }
    }

    private fun stopPositionTracking() {
        progressJob?.cancel()
    }

    private fun updatePaletteAccent(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val albumArtUri = Uri.parse("content://media/external/audio/albumart")
                val artUri = ContentUris.withAppendedId(albumArtUri, track.albumId)
                val bytes = getApplication<Application>().contentResolver.openInputStream(artUri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val boundsOpts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                    val sampleSize = com.nodeplayer.ui.components.computeBitmapSampleSize(
                        boundsOpts.outWidth,
                        boundsOpts.outHeight,
                        192
                    )
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    if (bitmap != null) {
                        try {
                            val palette = Palette.from(bitmap).generate()
                            val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
                            if (swatch != null) {
                                _accentColor.value = Color(swatch.rgb)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            } catch (_: Exception) {
                _accentColor.value = DefaultAccent
            }
        }
    }

    private fun detectAudioOutput() {
        val audioManager = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        var deviceName = "Internal Speaker"
        var deviceType = OutputDeviceType.SPEAKER
        var isBitPerfect = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                if (dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    deviceName = "USB DAC (${dev.productName})"
                    deviceType = OutputDeviceType.USB
                    isBitPerfect = false
                    break
                } else if (dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    deviceName = "Wired Headphones"
                    deviceType = OutputDeviceType.WIRED
                    break
                } else if (dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    deviceName = "Bluetooth A2DP"
                    deviceType = OutputDeviceType.BLUETOOTH
                    break
                }
            }
        }

        val previousType = _currentDeviceType.value
        _currentDeviceType.value = deviceType
        _audioOutputInfo.value = _audioOutputInfo.value.copy(
            outputDeviceName = deviceName,
            isBitPerfect = isBitPerfect
        )

        if (_autoDeviceProfilesEnabled.value && previousType != deviceType) {
            handleDeviceTypeChanged(previousType, deviceType)
        }
    }

    private fun handleDeviceTypeChanged(oldType: OutputDeviceType, newType: OutputDeviceType) {
        val oldJson = AudioProfileSerializer.toJson(_effectConfig.value)
        prefs.edit().putString("device_profile_${oldType.name}", oldJson).apply()

        val savedJson = prefs.getString("device_profile_${newType.name}", null)
        val targetConfig = if (savedJson != null) {
            try {
                AudioProfileSerializer.fromJson(savedJson)
            } catch (_: Exception) {
                _effectConfig.value
            }
        } else {
            when (newType) {
                OutputDeviceType.SPEAKER -> _effectConfig.value.copy(
                    toneControls = ToneControlConfig(bassGainDb = 2.0f, trebleGainDb = 1.0f),
                    harmonicBassConfig = HarmonicBassConfig(enabled = true, frequencyHz = 75f, harmonicGain = 0.5f, highPassOriginal = true)
                )
                OutputDeviceType.WIRED -> _effectConfig.value.copy(
                    crossfeedEnabled = true,
                    crossfeedStrength = 0.4f,
                    harmonicBassConfig = HarmonicBassConfig(enabled = false)
                )
                OutputDeviceType.BLUETOOTH -> _effectConfig.value.copy(
                    crossfeedEnabled = true,
                    crossfeedStrength = 0.3f
                )
                OutputDeviceType.USB -> _effectConfig.value.copy(
                    preampGainDb = 0.0f,
                    crossfeedEnabled = false,
                    harmonicBassConfig = HarmonicBassConfig(enabled = false)
                )
            }
        }
        updateEffectConfig(targetConfig)
        Log.i("NodePlayer", "Switched audio profile to: ${newType.displayName}")
    }

    private suspend fun checkAndApplyTrackOrAlbumPreset(track: Track) {
        val trackPreset = dao.getAudioPresetById("track_${track.id}")
        if (trackPreset != null) {
            try {
                val cfg = AudioProfileSerializer.fromJson(trackPreset.configJson)
                _activePresetLabel.value = "Preset: ${trackPreset.presetName} (Track)"
                updateEffectConfig(cfg)
                return
            } catch (_: Exception) {}
        }

        val albumPreset = dao.getAudioPresetById("album_${track.albumId}")
        if (albumPreset != null) {
            try {
                val cfg = AudioProfileSerializer.fromJson(albumPreset.configJson)
                _activePresetLabel.value = "Preset: ${albumPreset.presetName} (Album)"
                updateEffectConfig(cfg)
                return
            } catch (_: Exception) {}
        }

        _activePresetLabel.value = null
    }

    fun saveAsTrackPreset(track: Track, name: String = "Track Preset") {
        viewModelScope.launch(Dispatchers.IO) {
            val json = AudioProfileSerializer.toJson(_effectConfig.value)
            dao.insertAudioPreset(
                com.nodeplayer.music.data.AudioPresetEntity(
                    id = "track_${track.id}",
                    isAlbum = false,
                    presetName = name,
                    configJson = json
                )
            )
            _activePresetLabel.value = "Preset: $name (Track)"
        }
    }

    fun saveAsAlbumPreset(track: Track, name: String = "Album Preset") {
        viewModelScope.launch(Dispatchers.IO) {
            val json = AudioProfileSerializer.toJson(_effectConfig.value)
            dao.insertAudioPreset(
                com.nodeplayer.music.data.AudioPresetEntity(
                    id = "album_${track.albumId}",
                    isAlbum = true,
                    presetName = name,
                    configJson = json
                )
            )
            _activePresetLabel.value = "Preset: $name (Album)"
        }
    }

    fun clearTrackOrAlbumPreset(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAudioPresetById("track_${track.id}")
            dao.deleteAudioPresetById("album_${track.albumId}")
            _activePresetLabel.value = null
        }
    }

    fun importAutoEq(text: String, profileName: String = "Imported AutoEQ") {
        val profile = AutoEqParser.parse(text, profileName)
        val eqBands = AutoEqParser.toEqBandConfigs(profile)
        val updatedConfig = _effectConfig.value.copy(
            presetName = profile.name,
            preampGainDb = profile.preampDb,
            bands = eqBands,
            eqEnabled = true
        )
        updateEffectConfig(updatedConfig)
    }

    fun toggleAbCompare() {
        val current = _effectConfig.value
        val newAb = !current.isAbComparing
        val offset = if (newAb) {
            AbCompareHelper.calculateLoudnessOffsetDb(current.bands, current.preampGainDb)
        } else 0.0f
        val updated = current.copy(isAbComparing = newAb, abLoudnessOffsetDb = offset)
        updateEffectConfig(updated)
    }

    fun setConvolverPreset(presetName: String) {
        val updated = _effectConfig.value.copy(
            convolverConfig = _effectConfig.value.convolverConfig.copy(
                enabled = true,
                irName = presetName
            )
        )
        updateEffectConfig(updated)
        PlaybackService.instance?.crossfadeManager?.loadConvolverPreset(presetName)
    }

    fun setConvolverEnabled(enabled: Boolean) {
        val updated = _effectConfig.value.copy(
            convolverConfig = _effectConfig.value.convolverConfig.copy(enabled = enabled)
        )
        updateEffectConfig(updated)
    }

    fun setConvolverBlend(blend: Float) {
        val updated = _effectConfig.value.copy(
            convolverConfig = _effectConfig.value.convolverConfig.copy(blend = blend)
        )
        updateEffectConfig(updated)
    }

    fun setResumeOnHeadsetConnect(enabled: Boolean) {
        _resumeOnHeadsetConnect.value = enabled
        prefs.edit().putBoolean("resume_on_headset_connect", enabled).apply()
    }

    fun setAutoDeviceProfilesEnabled(enabled: Boolean) {
        _autoDeviceProfilesEnabled.value = enabled
        prefs.edit().putBoolean("auto_device_profiles_enabled", enabled).apply()
    }

    fun setResamplerRateChoice(rate: Int) {
        _resamplerRateChoice.value = rate
        prefs.edit().putInt("resampler_output_rate", rate).apply()
    }

    fun exportCurrentProfile(): String {
        return AudioProfileSerializer.toJson(_effectConfig.value)
    }

    fun importProfileJson(json: String) {
        try {
            val parsed = AudioProfileSerializer.fromJson(json)
            updateEffectConfig(parsed)
        } catch (e: Exception) {
            Log.e("NodePlayer", "Failed to import profile JSON", e)
        }
    }

    private fun updateSignalPath(track: Track) {
        val currentInfo = _audioOutputInfo.value
        val isResampled = if (currentInfo.isTrackInitialized && currentInfo.sampleRate > 0 && track.sampleRate > 0) {
            currentInfo.sampleRate != track.sampleRate
        } else false
        _audioOutputInfo.value = currentInfo.copy(
            originalSampleRate = track.sampleRate,
            originalBitDepth = track.bitDepth,
            isResampled = isResampled
        )
    }

    fun updateEffectConfig(newConfig: AudioEffectConfig) {
        _effectConfig.value = newConfig
        PlaybackService.instance?.crossfadeManager?.applyEffectConfig(newConfig)
        com.nodeplayer.music.service.SystemWideEqService.updateConfig(newConfig)
    }

    fun applyEqPreset(presetName: String) {
        val preset = AudioEffectConfig.createPreset(presetName).copy(
            preampGainDb = _effectConfig.value.preampGainDb,
            limiterEnabled = _effectConfig.value.limiterEnabled,
            limiterThresholdDb = _effectConfig.value.limiterThresholdDb,
            replayGainMode = _effectConfig.value.replayGainMode,
            stereoWidth = _effectConfig.value.stereoWidth,
            crossfeedEnabled = _effectConfig.value.crossfeedEnabled
        )
        updateEffectConfig(preset)
    }

    fun setSystemEqEnabled(enabled: Boolean) {
        _systemEqEnabled.value = enabled
        prefs.edit().putBoolean("system_eq_enabled", enabled).apply()
        if (enabled) {
            com.nodeplayer.music.service.SystemWideEqService.start(getApplication(), _effectConfig.value)
            PlaybackService.instance?.crossfadeManager?.onSystemEqStateChanged(true)
        } else {
            com.nodeplayer.music.service.SystemWideEqService.stop(getApplication())
            PlaybackService.instance?.crossfadeManager?.onSystemEqStateChanged(false)
        }
    }

    fun updateCrossfadeConfig(newConfig: CrossfadeConfig) {
        _crossfadeConfig.value = newConfig
        prefs.edit()
            .putBoolean("crossfade_enabled", newConfig.enabled)
            .putFloat("crossfade_duration", newConfig.durationSeconds)
            .putString("crossfade_curve", newConfig.curve.name)
            .putBoolean("crossfade_auto_gapless", newConfig.autoGapless)
            .apply()
        PlaybackService.instance?.crossfadeManager?.crossfadeConfig = newConfig
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        updateCrossfadeConfig(_crossfadeConfig.value.copy(enabled = enabled))
    }

    fun setCrossfadeDuration(seconds: Float) {
        updateCrossfadeConfig(_crossfadeConfig.value.copy(durationSeconds = seconds))
    }

    fun setCrossfadeCurve(curve: FadeCurve) {
        updateCrossfadeConfig(_crossfadeConfig.value.copy(curve = curve))
    }

    fun setAutoGapless(enabled: Boolean) {
        updateCrossfadeConfig(_crossfadeConfig.value.copy(autoGapless = enabled))
    }

    fun updateThemeConfig(newConfig: GlassThemeConfig) {
        _themeConfig.value = newConfig
        prefs.edit()
            .putString("glass_strength", newConfig.glassStrength)
            .putString("theme_mode", newConfig.themeMode)
            .putString("accent_source", newConfig.accentSource)
            .putString("spacing_density", newConfig.spacingDensity)
            .putString("corner_roundness", newConfig.cornerRoundness)
            .putBoolean("animated_backdrop", newConfig.animatedBackdrop)
            .putFloat("glass_blur_radius", newConfig.blurRadiusDp)
            .putFloat("glass_tint_alpha", newConfig.tintAlpha)
            .putFloat("glass_border_alpha", newConfig.borderAlpha)
            .putBoolean("is_oled_black", newConfig.isOledBlack)
            .putBoolean("use_dynamic_accent", newConfig.useDynamicAccent)
            .putLong("fixed_accent_color", newConfig.fixedAccentColorArgb)
            .putFloat("corner_radius_dp", newConfig.cornerRadiusDp)
            .putBoolean("is_performance_mode", newConfig.isPerformanceMode)
            .putBoolean("refraction_shader_enabled", newConfig.refractionShaderEnabled)
            .putBoolean("haptics_enabled", newConfig.hapticsEnabled)
            .putString("glass_shader_mode", newConfig.shaderMode)
            .putFloat("glass_opacity", newConfig.glassOpacity)
            .putFloat("glass_blur_strength", newConfig.blurStrength)
            .putFloat("glass_texture", newConfig.glassTexture)
            .putFloat("glass_edge_highlight_strength", newConfig.edgeHighlightStrength)
            .putBoolean("glass_fully_transparent", newConfig.isFullyTransparent)
            .putBoolean("glass_keep_text_readable", newConfig.keepTextReadable)
            .putString("background_animation_style", newConfig.backgroundAnimationStyle)
            .putFloat("background_animation_speed", newConfig.backgroundAnimationSpeed)
            .putFloat("background_animation_intensity", newConfig.backgroundAnimationIntensity)
            .putString("background_color_source", newConfig.backgroundColorSource)
            .putBoolean("animate_only_while_playing", newConfig.animateOnlyWhilePlaying)
            .putString("frame_rate_cap", newConfig.frameRateCap)
            .putString("render_quality", newConfig.renderQuality)
            .putBoolean("anti_banding_dithering", newConfig.antiBandingDithering)
            .putString("font_family", newConfig.fontFamily)
            .putBoolean("show_dock_search_button", newConfig.showDockSearchButton)
            .putBoolean("show_mini_player_wave", newConfig.showMiniPlayerWave)
            .putBoolean("format_badge_visible", newConfig.formatBadgeVisible)
            .putFloat("lens_refraction_strength", newConfig.lensRefractionStrength)
            .putFloat("lens_rim_specular", newConfig.lensRimSpecular)
            .putString("animation_preset", newConfig.animationPreset)
            .putFloat("motion_speed", newConfig.motionSpeed)
            .putFloat("spring_bounciness", newConfig.springBounciness)
            .putFloat("spring_stiffness", newConfig.springStiffness)
            .putBoolean("switch_overshoot_enabled", newConfig.switchOvershootEnabled)
            .putBoolean("press_scale_enabled", newConfig.pressScaleEnabled)
            .putFloat("press_scale_strength", newConfig.pressScaleStrength)
            .putBoolean("staggered_list_animation", newConfig.staggeredListAnimation)
            .putBoolean("lyrics_animation_enabled", newConfig.lyricsAnimationEnabled)
            .putString("screen_transition_style", newConfig.screenTransitionStyle)
            .putBoolean("reduce_motion", newConfig.reduceMotion)
            .putBoolean("dock_glass_lens_enabled", newConfig.dockGlassLensEnabled)
            .putBoolean("dock_glass_rim_enabled", newConfig.dockGlassRimEnabled)
            .putBoolean("dock_glass_puck_enabled", newConfig.dockGlassPuckEnabled)
            .putBoolean("dock_auto_hide_enabled", newConfig.dockAutoHideEnabled)
            .putBoolean("dock_auto_hide_mini_player", newConfig.dockAutoHideMiniPlayer)
            .putFloat("dock_scroll_sensitivity", newConfig.dockScrollSensitivity)
            .putLong("dock_return_delay_ms", newConfig.dockReturnDelayMs)
            .putBoolean("lyrics_animation_enabled", newConfig.lyricsAnimationEnabled)
            .putFloat("lyrics_font_size_sp", newConfig.lyricsViewConfig.fontSizeSp)
            .putInt("lyrics_auto_hide_delay_sec", newConfig.lyricsViewConfig.autoHideDelaySec)
            .putBoolean("controls_glass_enabled", newConfig.controlsGlassEnabled)
            .apply()

        // Sync widgets with current glass settings and re-render
        val widgetOpacity = if (newConfig.isFullyTransparent) 0.0f else newConfig.glassOpacity
        prefs.edit().putFloat("widget_glass_opacity", widgetOpacity).apply()
        try {
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(app)
        } catch (_: Throwable) {}
    }

    fun applySafeGraphicsMode() {
        updateThemeConfig(
            _themeConfig.value.copy(
                shaderMode = "BLUR_ONLY",
                keepTextReadable = true,
                isPerformanceMode = false,
                glassOpacity = 0.65f,
                blurStrength = 0.60f,
                glassTexture = 0.0f,
                edgeHighlightStrength = 0.40f,
                isFullyTransparent = false
            )
        )
    }

    fun resetThemeConfigToDefault() {
        updateThemeConfig(GlassThemeConfig())
    }

    fun <T> updateSetting(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            when (value) {
                is Boolean -> com.nodeplayer.music.data.AppSettingsRepository.updateSetting(app, key as androidx.datastore.preferences.core.Preferences.Key<Boolean>, value)
                is String -> com.nodeplayer.music.data.AppSettingsRepository.updateSetting(app, key as androidx.datastore.preferences.core.Preferences.Key<String>, value)
                is Float -> com.nodeplayer.music.data.AppSettingsRepository.updateSetting(app, key as androidx.datastore.preferences.core.Preferences.Key<Float>, value)
                is Int -> com.nodeplayer.music.data.AppSettingsRepository.updateSetting(app, key as androidx.datastore.preferences.core.Preferences.Key<Int>, value)
            }
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            com.nodeplayer.music.data.AppSettingsRepository.resetToDefaults(app)
        }
    }

    private val _analyzingTrackId = MutableStateFlow<Long?>(null)
    val analyzingTrackId: StateFlow<Long?> = _analyzingTrackId.asStateFlow()

    val isAnalysisRunning = com.nodeplayer.music.library.AudioAnalysisManager.isRunning
    val isAnalysisPaused = com.nodeplayer.music.library.AudioAnalysisManager.isPaused
    val analysisProgress = com.nodeplayer.music.library.AudioAnalysisManager.progress
    val analysisSongTitle = com.nodeplayer.music.library.AudioAnalysisManager.currentSongTitle

    fun analyzeTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            _analyzingTrackId.value = track.id
            try {
                com.nodeplayer.music.library.AudioAnalysisManager.analyzeSingleTrackDirect(track, dao)
            } catch (t: Throwable) {
                Log.e("PlayerViewModel", "Error analyzing track ${track.title}: ${t.message}")
            } finally {
                _analyzingTrackId.value = null
            }
        }
    }

    fun triggerAudioAnalysis(chargingOnly: Boolean = false) {
        com.nodeplayer.music.library.AudioAnalysisWorker.schedule(app, chargingOnly = chargingOnly)
    }

    fun pauseAudioAnalysis() {
        com.nodeplayer.music.library.AudioAnalysisManager.pause(app)
    }

    fun resumeAudioAnalysis() {
        com.nodeplayer.music.library.AudioAnalysisManager.resume(app)
    }

    fun cancelAudioAnalysis() {
        com.nodeplayer.music.library.AudioAnalysisWorker.cancel(app)
    }

    fun startSleepTimer(minutes: Int, finishTrack: Boolean = true, fadeSeconds: Int = 10, stopAfterTracks: Int = 0, shakeExtend: Boolean = true) {
        val intent = android.content.Intent(app, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SLEEP_START
            putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes)
            putExtra(PlaybackService.EXTRA_SLEEP_FINISH_TRACK, finishTrack)
            putExtra(PlaybackService.EXTRA_SLEEP_FADE_SECONDS, fadeSeconds)
            putExtra(PlaybackService.EXTRA_SLEEP_STOP_TRACKS, stopAfterTracks)
            putExtra(PlaybackService.EXTRA_SLEEP_SHAKE_EXTEND, shakeExtend)
        }
        app.startService(intent)
    }

    fun cancelSleepTimer() {
        val intent = android.content.Intent(app, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SLEEP_CANCEL
        }
        app.startService(intent)
    }

    val isNodeConnectEnabled = MutableStateFlow(prefs.getBoolean("node_connect_enabled", true))

    val nodeConnectHub get() = PlaybackService.instance?.nodeConnectHub

    fun setNodeConnectEnabled(enabled: Boolean) {
        isNodeConnectEnabled.value = enabled
        PlaybackService.instance?.setNodeConnectEnabled(enabled)
    }

    fun castAudioToPeer(peer: com.nodeplayer.network.discovery.DiscoveredNodePeer) {
        PlaybackService.instance?.startAudioCasting(peer)
    }

    fun stopAudioCasting() {
        PlaybackService.instance?.stopAudioCasting()
    }

    fun createPlaylist(name: String, initialTracks: List<Track> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = dao.insertPlaylist(com.nodeplayer.music.data.PlaylistEntity(name = name.trim()))
            if (initialTracks.isNotEmpty()) {
                dao.insertTracks(initialTracks.map { com.nodeplayer.music.data.TrackEntity.fromTrack(it) })
                val refs = initialTracks.mapIndexed { index, t ->
                    com.nodeplayer.music.data.PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = t.id,
                        position = index
                    )
                }
                dao.insertPlaylistTracks(refs)
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertTracks(listOf(com.nodeplayer.music.data.TrackEntity.fromTrack(track)))
            val existingIds = dao.getPlaylistTrackIds(playlistId)
            if (!existingIds.contains(track.id)) {
                val ref = com.nodeplayer.music.data.PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = existingIds.size
                )
                dao.insertPlaylistTracks(listOf(ref))
            }
        }
    }

    fun deletePlaylist(playlist: com.nodeplayer.music.data.PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePlaylist(playlist)
        }
    }

    fun deletePlaylistById(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = dao.getAllPlaylistsList().find { it.id == playlistId }
            if (p != null) dao.deletePlaylist(p)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.renamePlaylist(playlistId, newName)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPlaylistTracks(playlistId)
            val refs = orderedTrackIds.mapIndexed { idx, tid ->
                com.nodeplayer.music.data.PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = tid,
                    position = idx
                )
            }
            dao.insertPlaylistTracks(refs)
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return dao.getTracksForPlaylist(playlistId).map { entities ->
            entities.map { it.toTrack() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        seekPersistDebouncer.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        fun shouldEmitFloatDelta(prev: Float, next: Float, epsilon: Float = 0.002f): Boolean {
            if (next == 0.0f && prev != 0.0f) return true
            return kotlin.math.abs(next - prev) >= epsilon
        }

        fun shouldUpdateTrackDuration(currentMs: Long, newMs: Long): Boolean {
            return newMs > 0L && (currentMs <= 0L || kotlin.math.abs(currentMs - newMs) > 1000L)
        }

        fun buildThemeConfigFromValues(
            glassStrength: String = "BALANCED",
            themeMode: String = "SYSTEM",
            accentSource: String = "ARTWORK",
            spacingDensity: String = "COMFORTABLE",
            cornerRoundness: String = "ROUNDED",
            animatedBackdrop: Boolean = true,
            blurRadiusDp: Float = 28.0f,
            tintAlpha: Float = 0.65f,
            borderAlpha: Float = 0.30f,
            isOledBlack: Boolean = false,
            useDynamicAccent: Boolean = true,
            fixedAccentColorArgb: Long = 0xFF6366F1,
            cornerRadiusDp: Float = 16.0f,
            isPerformanceMode: Boolean = false,
            refractionShaderEnabledPref: Boolean = true,
            selfTestPassed: Boolean = true,
            hapticsEnabled: Boolean = true,
            shaderMode: String = "AUTO",
            glassOpacity: Float = 0.65f,
            blurStrength: Float = 0.70f,
            glassTexture: Float = 0.40f,
            edgeHighlightStrength: Float = 0.60f,
            isFullyTransparent: Boolean = false,
            keepTextReadable: Boolean = true,
            backgroundAnimationStyle: String = "LIQUID_BLOBS",
            backgroundAnimationSpeed: Float = 1.0f,
            backgroundAnimationIntensity: Float = 0.8f,
            backgroundColorSource: String = "ARTWORK",
            animateOnlyWhilePlaying: Boolean = false,
            frameRateCap: String = "AUTO",
            renderQuality: String = "MAX_NATIVE",
            antiBandingDithering: Boolean = true,
            fontFamily: String = "SYSTEM",
            showDockSearchButton: Boolean = true,
            showMiniPlayerWave: Boolean = true,
            formatBadgeVisible: Boolean = true,
            lensRefractionStrength: Float = 0.85f,
            lensRimSpecular: Float = 0.85f,
            animationPreset: String = "SMOOTH",
            motionSpeed: Float = 1.0f,
            springBounciness: Float = 0.82f,
            springStiffness: Float = 380f,
            switchOvershootEnabled: Boolean = true,
            pressScaleEnabled: Boolean = true,
            pressScaleStrength: Float = 0.96f,
            staggeredListAnimation: Boolean = true,
            lyricsAnimationEnabled: Boolean = true,
            screenTransitionStyle: String = "SLIDE",
            reduceMotion: Boolean = false,
            dockGlassLensEnabled: Boolean = true,
            dockGlassRimEnabled: Boolean = true,
            dockGlassPuckEnabled: Boolean = true,
            dockAutoHideEnabled: Boolean = true,
            dockAutoHideMiniPlayer: Boolean = true,
            dockScrollSensitivity: Float = 1.0f,
            dockReturnDelayMs: Long = 1200L,
            lyricsFontSizeSp: Float = 28.0f,
            lyricsAutoHideDelaySec: Int = 5,
            controlsGlassEnabled: Boolean = true
        ): GlassThemeConfig {
            val refractionEnabled = refractionShaderEnabledPref && selfTestPassed
            return GlassThemeConfig(
                glassStrength = glassStrength,
                themeMode = themeMode,
                accentSource = accentSource,
                spacingDensity = spacingDensity,
                cornerRoundness = cornerRoundness,
                animatedBackdrop = animatedBackdrop,
                blurRadiusDp = blurRadiusDp,
                tintAlpha = tintAlpha,
                borderAlpha = borderAlpha,
                isOledBlack = isOledBlack,
                useDynamicAccent = useDynamicAccent,
                fixedAccentColorArgb = fixedAccentColorArgb,
                cornerRadiusDp = cornerRadiusDp,
                isPerformanceMode = isPerformanceMode,
                refractionShaderEnabled = refractionEnabled,
                hapticsEnabled = hapticsEnabled,
                shaderMode = shaderMode,
                glassOpacity = glassOpacity,
                blurStrength = blurStrength,
                glassTexture = glassTexture,
                edgeHighlightStrength = edgeHighlightStrength,
                isFullyTransparent = isFullyTransparent,
                keepTextReadable = keepTextReadable,
                backgroundAnimationStyle = backgroundAnimationStyle,
                backgroundAnimationSpeed = backgroundAnimationSpeed,
                backgroundAnimationIntensity = backgroundAnimationIntensity,
                backgroundColorSource = backgroundColorSource,
                animateOnlyWhilePlaying = animateOnlyWhilePlaying,
                frameRateCap = frameRateCap,
                renderQuality = renderQuality,
                antiBandingDithering = antiBandingDithering,
                fontFamily = fontFamily,
                showDockSearchButton = showDockSearchButton,
                showMiniPlayerWave = showMiniPlayerWave,
                formatBadgeVisible = formatBadgeVisible,
                lensRefractionStrength = lensRefractionStrength,
                lensRimSpecular = lensRimSpecular,
                animationPreset = animationPreset,
                motionSpeed = motionSpeed,
                springBounciness = springBounciness,
                springStiffness = springStiffness,
                switchOvershootEnabled = switchOvershootEnabled,
                pressScaleEnabled = pressScaleEnabled,
                pressScaleStrength = pressScaleStrength,
                staggeredListAnimation = staggeredListAnimation,
                lyricsAnimationEnabled = lyricsAnimationEnabled,
                screenTransitionStyle = screenTransitionStyle,
                reduceMotion = reduceMotion,
                dockGlassLensEnabled = dockGlassLensEnabled,
                dockGlassRimEnabled = dockGlassRimEnabled,
                dockGlassPuckEnabled = dockGlassPuckEnabled,
                dockAutoHideEnabled = dockAutoHideEnabled,
                dockAutoHideMiniPlayer = dockAutoHideMiniPlayer,
                dockScrollSensitivity = dockScrollSensitivity,
                dockReturnDelayMs = dockReturnDelayMs,
                lyricsViewConfig = com.nodeplayer.model.LyricsViewConfig(
                    fontSizeSp = lyricsFontSizeSp,
                    autoHideDelaySec = lyricsAutoHideDelaySec
                ),
                controlsGlassEnabled = controlsGlassEnabled
            )
        }
    }
}

class TrailingSeekDebouncer(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val delayMs: Long = 300L,
    private val onPersist: suspend (Long) -> Unit
) {
    private var job: Job? = null

    fun schedule(positionMs: Long) {
        job?.cancel()
        job = scope.launch(dispatcher) {
            delay(delayMs)
            onPersist(positionMs)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
