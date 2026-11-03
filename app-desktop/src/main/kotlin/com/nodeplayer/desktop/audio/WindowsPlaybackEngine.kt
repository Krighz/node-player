package com.nodeplayer.desktop.audio

import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WindowsPlaybackEngine(
    val audioOutput: WasapiAudioOutput = WasapiAudioOutput(),
    private val decoder: CompositeAudioDecoder = CompositeAudioDecoder()
) {
    val dspChain = DspChain()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(0) // 0=Off, 1=All, 2=One
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentLrc = MutableStateFlow<String?>(null)
    val currentLrc: StateFlow<String?> = _currentLrc.asStateFlow()

    private val _isExclusiveMode = MutableStateFlow(false)
    val isExclusiveMode: StateFlow<Boolean> = _isExclusiveMode.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private var currentEffectConfig = AudioEffectConfig()

    init {
        audioOutput.open(
            AudioFormatConfig(
                sampleRate = 48000,
                channelCount = 2,
                isFloat32 = true,
                isExclusiveMode = false
            )
        )
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            _currentQueueIndex.value = startIndex
            playTrack(tracks[startIndex])
        }
    }

    fun playIndex(index: Int) {
        val q = _queue.value
        if (q.isNotEmpty() && index in q.indices) {
            _currentQueueIndex.value = index
            playTrack(q[index])
        }
    }

    fun playTrack(track: Track) {
        playbackJob?.cancel()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _currentPositionMs.value = 0L

        // Look for sidecar LRC file
        loadLrcForTrack(track.path)

        playbackJob = engineScope.launch {
            val opened = decoder.open(track.path)
            val meta = decoder.metadata
            if (!opened || meta == null) {
                _isPlaying.value = false
                return@launch
            }

            dspChain.sampleRate = meta.sampleRate
            dspChain.applyConfig(currentEffectConfig)

            audioOutput.open(
                AudioFormatConfig(
                    sampleRate = meta.sampleRate,
                    channelCount = meta.channels,
                    isFloat32 = true,
                    isExclusiveMode = _isExclusiveMode.value
                )
            )

            _isPlaying.value = true
            isPaused.set(false)

            val bufferSize = 8192
            val buffer = FloatArray(bufferSize)
            var framesPlayed = 0L

            try {
                while (isActive) {
                    if (isPaused.get()) {
                        delay(40)
                        continue
                    }

                    val samplesRead = decoder.decodeFrame(buffer)
                    if (samplesRead <= 0) {
                        break
                    }

                    dspChain.processInterleavedFloat(buffer, 0, samplesRead)
                    audioOutput.write(buffer, 0, samplesRead)

                    framesPlayed += (samplesRead / meta.channels)
                    val posMs = (framesPlayed * 1000L) / meta.sampleRate
                    _currentPositionMs.value = posMs
                }

                if (isActive) {
                    onTrackCompleted()
                }
            } catch (e: Exception) {
                // Cancellation
            } finally {
                decoder.close()
                if (!isActive) {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun loadLrcForTrack(filePath: String) {
        try {
            val audioFile = File(filePath)
            val lrcCandidate = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcCandidate.exists() && lrcCandidate.canRead()) {
                _currentLrc.value = lrcCandidate.readText(Charsets.UTF_8)
            } else {
                _currentLrc.value = null
            }
        } catch (e: Exception) {
            _currentLrc.value = null
        }
    }

    fun playPause() {
        if (_isPlaying.value) {
            isPaused.set(true)
            audioOutput.pause()
            _isPlaying.value = false
        } else {
            if (_currentTrack.value != null && isPaused.get()) {
                isPaused.set(false)
                audioOutput.resume()
                _isPlaying.value = true
            } else {
                val q = _queue.value
                val idx = _currentQueueIndex.value
                if (q.isNotEmpty() && idx in q.indices) {
                    playTrack(q[idx])
                }
            }
        }
    }

    fun next() {
        val q = _queue.value
        if (q.isEmpty()) return
        when (_repeatMode.value) {
            2 -> { // Repeat ONE
                _currentTrack.value?.let { playTrack(it) }
            }
            1 -> { // Repeat ALL
                val nextIdx = (_currentQueueIndex.value + 1) % q.size
                _currentQueueIndex.value = nextIdx
                playTrack(q[nextIdx])
            }
            else -> { // Repeat OFF
                val nextIdx = _currentQueueIndex.value + 1
                if (nextIdx < q.size) {
                    _currentQueueIndex.value = nextIdx
                    playTrack(q[nextIdx])
                } else {
                    stop()
                }
            }
        }
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_currentPositionMs.value > 3000L) {
            _currentTrack.value?.let { playTrack(it) }
        } else if (_currentQueueIndex.value > 0) {
            val prevIdx = _currentQueueIndex.value - 1
            _currentQueueIndex.value = prevIdx
            playTrack(q[prevIdx])
        } else if (_repeatMode.value == 1) {
            val lastIdx = q.size - 1
            _currentQueueIndex.value = lastIdx
            playTrack(q[lastIdx])
        }
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value ?: return
        val clamped = positionMs.coerceIn(0L, track.durationMs)
        decoder.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        audioOutput.setVolume(clamped)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun setExclusiveMode(enabled: Boolean) {
        _isExclusiveMode.value = enabled
        val track = _currentTrack.value
        if (track != null && _isPlaying.value) {
            playTrack(track)
        }
    }

    fun applyEffectConfig(config: AudioEffectConfig) {
        currentEffectConfig = config
        dspChain.applyConfig(config)
    }

    fun stop() {
        playbackJob?.cancel()
        audioOutput.flush()
        audioOutput.pause()
        decoder.close()
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    fun release() {
        stop()
        audioOutput.close()
        engineScope.cancel()
    }

    private fun onTrackCompleted() {
        next()
    }
}
