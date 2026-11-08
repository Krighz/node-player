package com.nodeplayer.desktop.audio

import com.nodeplayer.common.AppModeState
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.platform.AudioFormatConfig
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.Track
import com.nodeplayer.network.innertube.StreamResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WindowsPlaybackEngine(
    val audioOutput: WasapiAudioOutput = WasapiAudioOutput(),
    private val decoder: CompositeAudioDecoder = CompositeAudioDecoder(),
    private val audioCache: DesktopAudioCache = DesktopAudioCache.default,
    private val streamResolver: StreamResolver = StreamResolver()
) {
    val dspChain = DspChain()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private val pendingSeekMs = AtomicLong(-1L)

    var preDspPcmTap: ((ByteArray, Long) -> Unit)? = null
    var onSeekFlushCallback: ((Long) -> Unit)? = null
    var onTrackStartedCallback: ((Track) -> Unit)? = null

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

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

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
        onTrackStartedCallback?.invoke(track)
        pendingSeekMs.set(-1L)
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _currentPositionMs.value = 0L

        // Look for sidecar LRC file
        loadLrcForTrack(track.path)

        playbackJob = engineScope.launch {
            playTrackInternal(this, track)
        }
    }

    private suspend fun playTrackInternal(scope: CoroutineScope, track: Track) {
        var effectivePlayPath = track.path

        if (track.path.startsWith("nodeplayer://youtube/", ignoreCase = true)) {
            val videoId = audioCache.extractVideoId(track.path)
            if (videoId == null) {
                NodeLog.e("WindowsPlaybackEngine", "Invalid YouTube synthetic URI: ${track.path}")
                _isPlaying.value = false
                return
            }

            val cachedFile = audioCache.getCachedFile(videoId)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                NodeLog.i("WindowsPlaybackEngine", "Playing cached track for $videoId: ${cachedFile.absolutePath}")
                effectivePlayPath = cachedFile.absolutePath
            } else {
                if (!AppModeState.isOnlineMode.value) {
                    NodeLog.w("WindowsPlaybackEngine", "Track $videoId not cached offline and online mode is disabled")
                    _isPlaying.value = false
                    return
                }

                NodeLog.i("WindowsPlaybackEngine", "Resolving stream for videoId=$videoId asynchronously")
                val streamResult = withContext(Dispatchers.IO) {
                    streamResolver.resolveStream(videoId)
                }

                if (streamResult.isFailure) {
                    NodeLog.e("WindowsPlaybackEngine", "Stream resolution failed for $videoId: ${streamResult.exceptionOrNull()?.message}")
                    _isPlaying.value = false
                    return
                }

                val resolved = streamResult.getOrThrow()
                NodeLog.i("WindowsPlaybackEngine", "Stream resolved for $videoId (${resolved.clientName}, itag=${resolved.itag})")

                // Cache stream asynchronously in background
                engineScope.launch {
                    val ext = if (resolved.mimeType.contains("mp4") || resolved.mimeType.contains("m4a")) "m4a" else "opus"
                    audioCache.cacheStream(videoId, resolved.streamUrl, ext)
                }

                effectivePlayPath = resolved.streamUrl
            }
        }

        val opened = decoder.open(effectivePlayPath)
        val meta = decoder.metadata
        if (!opened || meta == null) {
            _isPlaying.value = false
            return
        }

        if (meta.durationMs > 0L) {
            _durationMs.value = meta.durationMs
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
            while (scope.isActive) {
                if (isPaused.get()) {
                    delay(40)
                    continue
                }

                val seekTarget = pendingSeekMs.getAndSet(-1L)
                if (seekTarget >= 0L) {
                    decoder.seekTo(seekTarget)
                    audioOutput.flush()
                    framesPlayed = (seekTarget * meta.sampleRate) / 1000L
                    _currentPositionMs.value = seekTarget
                }

                val samplesRead = decoder.decodeFrame(buffer)
                if (samplesRead <= 0) {
                    break
                }

                // Pre-DSP PCM Audio Streaming Tap for Node Connect
                if (preDspPcmTap != null && samplesRead > 0) {
                    val pcmBytes = floatToBytes16(buffer, samplesRead)
                    preDspPcmTap?.invoke(pcmBytes, _currentPositionMs.value)
                }

                dspChain.processInterleavedFloat(buffer, 0, samplesRead)
                audioOutput.write(buffer, 0, samplesRead)

                framesPlayed += (samplesRead / meta.channels)
                val posMs = (framesPlayed * 1000L) / meta.sampleRate
                _currentPositionMs.value = posMs
            }

            if (scope.isActive) {
                onTrackCompleted()
            }
        } catch (e: Exception) {
            // Cancellation
        } finally {
            decoder.close()
            if (!scope.isActive) {
                _isPlaying.value = false
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
        _currentPositionMs.value = clamped
        pendingSeekMs.set(clamped)
        onSeekFlushCallback?.invoke(clamped)
    }

    private fun floatToBytes16(floatBuffer: FloatArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        var bIdx = 0
        for (i in 0 until count) {
            val s = (floatBuffer[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            bytes[bIdx++] = (s.toInt() and 0xFF).toByte()
            bytes[bIdx++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    fun playIncomingCastStream(jitter: com.nodeplayer.network.cast.JitterBuffer, session: com.nodeplayer.network.cast.ActiveCastSession) {
        playbackJob?.cancel()
        _currentTrack.value = Track(
            id = session.sequenceId,
            title = session.trackTitle,
            artist = session.artist,
            album = session.album,
            durationMs = session.durationMs,
            path = "nodeconnect://cast/${session.streamId}",
            sampleRate = session.sampleRate,
            bitDepth = session.bitDepth,
            channels = session.channels,
            formatDescription = "Cast PCM (${session.sampleRate / 1000}kHz)",
            isLossless = true
        )
        _durationMs.value = session.durationMs
        _currentPositionMs.value = 0L

        audioOutput.open(
            AudioFormatConfig(
                sampleRate = session.sampleRate,
                channelCount = session.channels,
                isFloat32 = true,
                isExclusiveMode = _isExclusiveMode.value
            )
        )
        _isPlaying.value = true
        isPaused.set(false)

        playbackJob = engineScope.launch {
            val byteBuffer = ByteArray(4096)
            val floatBuffer = FloatArray(2048)
            var framesPlayed = 0L

            try {
                // Wait for initial jitter buffer pre-buffering to eliminate start stutter
                while (isActive && !jitter.isReadyForPlayback) {
                    delay(15)
                }

                while (isActive) {
                    val read = jitter.read(byteBuffer, 0, byteBuffer.size)
                    if (read <= 0) {
                        // Re-buffer cleanly on underrun rather than stuttering on single packets
                        while (isActive && !jitter.isReadyForPlayback) {
                            delay(25)
                        }
                        continue
                    }

                    val sampleCount = read / 2
                    for (i in 0 until sampleCount) {
                        val low = byteBuffer[i * 2].toInt() and 0xFF
                        val high = byteBuffer[i * 2 + 1].toInt()
                        val s16 = ((high shl 8) or low).toShort()
                        floatBuffer[i] = s16 / 32768.0f
                    }

                    dspChain.processInterleavedFloat(floatBuffer, 0, sampleCount)
                    audioOutput.write(floatBuffer, 0, sampleCount)

                    framesPlayed += (sampleCount / session.channels)
                    val posMs = (framesPlayed * 1000L) / session.sampleRate
                    _currentPositionMs.value = posMs
                }
            } catch (e: Exception) {
                // Cancelled
            }
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        audioOutput.setVolume(clamped)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            return
        }
        val totalSec = minutes * 60L
        _sleepTimerRemainingSeconds.value = totalSec
        sleepTimerJob = engineScope.launch {
            var currentSec = totalSec
            while (isActive && currentSec > 0) {
                delay(1000L)
                currentSec--
                _sleepTimerRemainingSeconds.value = currentSec
                // Smooth fade during the last 10 seconds
                if (currentSec <= 10) {
                    val progress = (currentSec / 10f).coerceIn(0f, 1f)
                    audioOutput.setVolume(_volume.value * progress)
                }
            }
            if (isActive) {
                if (_isPlaying.value) {
                    playPause()
                }
                audioOutput.setVolume(_volume.value)
                _sleepTimerRemainingSeconds.value = null
            }
        }
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
