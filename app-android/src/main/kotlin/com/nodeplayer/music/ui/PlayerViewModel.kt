package com.nodeplayer.music.ui

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
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
import com.nodeplayer.model.*
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.data.TrackEntity
import com.nodeplayer.music.service.PlaybackService
import com.nodeplayer.music.ui.theme.DefaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NodePlayerApplication
    private val dao = app.database.playerDao()
    private val scanner = app.libraryScanner
    private val prefs: SharedPreferences = app.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    // State flows
    val allTracks = dao.getAllTracks().map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums = allTracks.map { scanner.extractAlbums(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists = allTracks.map { scanner.extractArtists(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders = allTracks.map { scanner.extractFolders(it) }
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

    private var progressJob: Job? = null

    init {
        loadFilterPreferences()
        initMediaController()
        refreshLibrary()
        detectAudioOutput()
        restoreLastPlaybackSession()
    }

    private fun loadFilterPreferences() {
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

        val sysEq = prefs.getBoolean("system_eq_enabled", false)
        _systemEqEnabled.value = sysEq
        if (sysEq) {
            com.nodeplayer.music.service.SystemWideEqService.start(getApplication(), _effectConfig.value)
        }
    }

    private fun restoreLastPlaybackSession() {
        viewModelScope.launch {
            val lastTrackId = prefs.getLong("last_track_id", -1L)
            val lastPos = prefs.getLong("last_pos", 0L)
            if (lastTrackId > 0) {
                val track = dao.getTrackById(lastTrackId)?.toTrack()
                if (track != null) {
                    _currentTrack.value = track
                    _currentPositionMs.value = lastPos
                    updatePaletteAccent(track)
                    updateSignalPath(track)
                }
            }
        }
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                Log.e("NodePlayer", "MediaController connect failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        PlaybackService.instance?.let { service ->
            service.onTrackTransitionCallback = { track ->
                viewModelScope.launch {
                    _currentTrack.value = track
                    updatePaletteAccent(track)
                    updateSignalPath(track)
                    dao.incrementPlayCount(track.id)
                }
            }
            service.crossfadeManager.crossfadeConfig = _crossfadeConfig.value
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
        viewModelScope.launch {
            val minMs = minDurationSec * 1000L
            val tracks = scanner.scanTracks(
                minDurationMs = minMs,
                includedFolders = includedFolders,
                excludedFolders = excludedFolders,
                whitelistedTrackIds = whitelistedIds
            )
            dao.clearTracks()
            if (tracks.isNotEmpty()) {
                dao.insertTracks(tracks.map { TrackEntity.fromTrack(it) })
            }
            loadExcludedTracks(tracks)
        }
    }

    fun loadExcludedTracks(currentTracks: List<Track> = allTracks.value) {
        viewModelScope.launch {
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
        val current = _excludedFolders.value.toMutableSet()
        if (current.contains(folderPath)) {
            current.remove(folderPath)
        } else {
            current.add(folderPath)
        }
        _excludedFolders.value = current
        refreshLibrary(excludedFolders = current)
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
        _queue.value = newQueue
        _currentTrack.value = track
        updatePaletteAccent(track)
        updateSignalPath(track)

        val targetIndex = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.crossfadeConfig = _crossfadeConfig.value
            service.crossfadeManager.playQueue(newQueue, targetIndex, 0L)
            _isPlaying.value = true
            startPositionTracking()
            return
        }

        val controller = mediaController
        if (controller == null) {
            Log.e("NodePlayer", "Cannot play track: mediaController is null")
            return
        }

        controller.stop()
        controller.clearMediaItems()

        // ALWAYS use ContentUris with MediaStore Content URI for scoped storage compatibility
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
        val mediaItems = newQueue.map {
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                it.id
            )
            val artworkUri = ContentUris.withAppendedId(albumArtBaseUri, it.albumId)
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(it.title)
                .setArtist(it.artist)
                .setAlbumTitle(it.album)
                .setArtworkUri(artworkUri)
                .build()

            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(contentUri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val service = PlaybackService.instance
        if (service != null) {
            if (service.crossfadeManager.currentQueue.isEmpty() && _currentTrack.value != null) {
                playTrack(_currentTrack.value!!)
                return
            }
            service.crossfadeManager.togglePlayPause()
            _isPlaying.value = service.crossfadeManager.activePlayer.isPlaying
            if (_isPlaying.value) startPositionTracking() else stopPositionTracking()
            return
        }

        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && _currentTrack.value != null) {
            playTrack(_currentTrack.value!!)
            return
        }
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.skipNext()
        } else {
            mediaController?.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val service = PlaybackService.instance
        if (service != null) {
            service.crossfadeManager.skipPrevious()
        } else {
            mediaController?.seekToPreviousMediaItem()
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
        _currentTrack.value?.let {
            prefs.edit().putLong("last_pos", positionMs).putLong("last_track_id", it.id).apply()
        }
    }

    private fun startPositionTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val service = PlaybackService.instance
                val pos = service?.player?.currentPosition ?: mediaController?.currentPosition ?: 0L
                val playing = service?.player?.isPlaying ?: mediaController?.isPlaying ?: false
                _isPlaying.value = playing
                _currentPositionMs.value = pos
                _currentTrack.value?.let { track ->
                    prefs.edit().putLong("last_pos", pos).putLong("last_track_id", track.id).apply()
                }
                // Read real RMS audio level from DSP engine
                _currentRms.value = service?.dspAudioProcessor?.currentRms ?: 0.0f
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
                val inputStream = getApplication<Application>().contentResolver.openInputStream(artUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
                    if (swatch != null) {
                        _accentColor.value = Color(swatch.rgb)
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
        var isBitPerfect = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                if (dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    deviceName = "USB DAC (${dev.productName})"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        isBitPerfect = true
                    }
                    break
                } else if (dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    deviceName = "Wired Headphones"
                    break
                } else if (dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    deviceName = "Bluetooth A2DP"
                    break
                }
            }
        }

        _audioOutputInfo.value = _audioOutputInfo.value.copy(
            outputDeviceName = deviceName,
            isBitPerfect = isBitPerfect
        )
    }

    private fun updateSignalPath(track: Track) {
        val currentInfo = _audioOutputInfo.value
        _audioOutputInfo.value = currentInfo.copy(
            originalSampleRate = track.sampleRate,
            originalBitDepth = track.bitDepth,
            sampleRate = track.sampleRate,
            bitDepth = track.bitDepth,
            format = if (track.isHiRes) "Hi-Res FLAC" else if (track.isLossless) "FLAC/Lossless" else "AAC/MP3",
            isResampled = false
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
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
