package com.nodeplayer.music.ui

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NodePlayerApplication
    private val dao = app.database.playerDao()
    private val scanner = app.libraryScanner

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

    val playlists = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    private var progressJob: Job? = null

    init {
        initMediaController()
        refreshLibrary()
        detectAudioOutput()
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
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionTracking() else stopPositionTracking()
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

    fun refreshLibrary() {
        viewModelScope.launch {
            val tracks = scanner.scanTracks()
            if (tracks.isNotEmpty()) {
                dao.insertTracks(tracks.map { TrackEntity.fromTrack(it) })
            }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _queue.value = newQueue
        _currentTrack.value = track
        updatePaletteAccent(track)
        updateSignalPath(track)

        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()

        val mediaItems = newQueue.map {
            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.path)
                .build()
        }
        val targetIndex = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        mediaController?.seekTo(positionMs)
    }

    private fun startPositionTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let {
                    _currentPositionMs.value = it.currentPosition
                }
                delay(300)
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
                val artUri = android.content.ContentUris.withAppendedId(albumArtUri, track.albumId)
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
                // Fallback to default accent
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
        val isResampled = currentInfo.sampleRate != track.sampleRate && !currentInfo.isBitPerfect
        _audioOutputInfo.value = currentInfo.copy(
            originalSampleRate = track.sampleRate,
            originalBitDepth = track.bitDepth,
            sampleRate = if (currentInfo.isBitPerfect) track.sampleRate else 48000,
            bitDepth = if (currentInfo.isBitPerfect) track.bitDepth else 16,
            format = if (track.isLossless) "FLAC/PCM" else "AAC/MP3",
            isResampled = isResampled
        )
    }

    fun updateEffectConfig(newConfig: AudioEffectConfig) {
        _effectConfig.value = newConfig
        PlaybackService.instance?.dspChain?.applyConfig(newConfig)
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
