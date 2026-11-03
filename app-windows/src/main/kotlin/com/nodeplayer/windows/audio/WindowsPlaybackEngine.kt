package com.nodeplayer.windows.audio

import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class RepeatMode {
    OFF, ALL, ONE
}

class WindowsPlaybackEngine(
    private val audioOutput: WasapiAudioOutput = WasapiAudioOutput(),
    private val decoder: CompositeAudioDecoder = CompositeAudioDecoder()
) {
    val dspChain = DspChain()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null

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

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private var queue = mutableListOf<Track>()
    private var currentIndex = -1

    init {
        audioOutput.open(
            AudioFormatConfig(
                sampleRate = 44100,
                channelCount = 2,
                isFloat32 = true,
                isExclusiveMode = false
            )
        )
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(tracks)
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            currentIndex = startIndex
            playTrack(tracks[startIndex])
        }
    }

    fun playTrack(track: Track) {
        playbackJob?.cancel()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _currentPositionMs.value = 0L

        playbackJob = engineScope.launch {
            val opened = decoder.open(track.path)
            val meta = decoder.metadata
            if (!opened || meta == null) {
                _isPlaying.value = false
                return@launch
            }

            dspChain.sampleRate = meta.sampleRate

            audioOutput.open(
                AudioFormatConfig(
                    sampleRate = meta.sampleRate,
                    channelCount = meta.channels,
                    isFloat32 = true,
                    isExclusiveMode = false
                )
            )

            _isPlaying.value = true
            isPaused.set(false)

            val bufferSize = 4096
            val buffer = FloatArray(bufferSize)
            var framesPlayed = 0L

            try {
                while (isActive) {
                    if (isPaused.get()) {
                        delay(50)
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
            } else if (queue.isNotEmpty() && currentIndex in queue.indices) {
                playTrack(queue[currentIndex])
            }
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentTrack.value?.let { playTrack(it) }
            }
            RepeatMode.ALL -> {
                currentIndex = (currentIndex + 1) % queue.size
                playTrack(queue[currentIndex])
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < queue.size) {
                    currentIndex++
                    playTrack(queue[currentIndex])
                } else {
                    stop()
                }
            }
        }
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (_currentPositionMs.value > 3000L) {
            _currentTrack.value?.let { playTrack(it) }
        } else if (currentIndex > 0) {
            currentIndex--
            playTrack(queue[currentIndex])
        } else if (_repeatMode.value == RepeatMode.ALL) {
            currentIndex = queue.size - 1
            playTrack(queue[currentIndex])
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
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun applyEffectConfig(config: AudioEffectConfig) {
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
