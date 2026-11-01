package com.nodeplayer.music.audio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.nodeplayer.dsp.CrossfadeCurveMath
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.FadeCurve
import com.nodeplayer.model.Track
import kotlin.math.min

/**
 * Dual ExoPlayer Crossfade & Gapless Audio Engine.
 *
 * Implements:
 * - Dual ExoPlayer architecture (playerA & playerB)
 * - Independent DspAudioProcessor & DspChain per player to prevent filter state collisions & clicks
 * - Dynamic volume ramps: Linear, Equal Power (constant power cos/sin), and Logarithmic (smoothstep)
 * - Automatic gapless detection for consecutive album tracks and live/continuous mix recordings
 * - Robust handling for seeking, skipping, pausing/resuming mid-fade, and short tracks
 */
@UnstableApi
class CrossfadePlayerManager(
    private val context: Context,
    private val onTrackTransition: (Track) -> Unit,
    private val onMediaSessionPlayerSwapped: (ExoPlayer) -> Unit
) {

    companion object {
        private const val TAG = "CrossfadeManager"
        private const val TICK_INTERVAL_MS = 25L
    }

    val dspChainA = DspChain()
    val dspChainB = DspChain()

    val dspAudioProcessorA = DspAudioProcessor(dspChainA)
    val dspAudioProcessorB = DspAudioProcessor(dspChainB)

    val playerA: ExoPlayer
    val playerB: ExoPlayer

    private var activeIndex = 0 // 0 for A, 1 for B

    val activePlayer: ExoPlayer
        get() = if (activeIndex == 0) playerA else playerB

    val standbyPlayer: ExoPlayer
        get() = if (activeIndex == 0) playerB else playerA

    val activeDspProcessor: DspAudioProcessor
        get() = if (activeIndex == 0) dspAudioProcessorA else dspAudioProcessorB

    val activeDspChain: DspChain
        get() = if (activeIndex == 0) dspChainA else dspChainB

    var crossfadeConfig: CrossfadeConfig = CrossfadeConfig()
    var currentQueue: List<Track> = emptyList()
        private set
    var currentQueueIndex: Int = 0
        private set

    @Volatile
    var isCrossfading: Boolean = false
        private set

    private var fadeStartTimeMs = 0L
    private var currentEffectiveFadeMs = 0L
    private var fadePaused = false
    private var fadePauseTimestamp = 0L
    private var lastLoggedPercent = -1

    private val handler = Handler(Looper.getMainLooper())

    private val monitorRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    init {
        playerA = createPlayer(dspAudioProcessorA)
        playerB = createPlayer(dspAudioProcessorB)
        setupPlayerListeners()
        handler.post(monitorRunnable)
    }

    private fun createPlayer(processor: DspAudioProcessor): ExoPlayer {
        val customAudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(processor))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = customAudioSink
        }.apply {
            setEnableAudioFloatOutput(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false // Allow dual players to play concurrently during crossfade
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun setupPlayerListeners() {
        val listenerA = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activeIndex == 0) handlePlaybackState(playbackState)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (activeIndex == 0) handlePlayWhenReadyChanged(playWhenReady)
            }
        }
        val listenerB = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activeIndex == 1) handlePlaybackState(playbackState)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (activeIndex == 1) handlePlayWhenReadyChanged(playWhenReady)
            }
        }
        playerA.addListener(listenerA)
        playerB.addListener(listenerB)
    }

    private fun handlePlayWhenReadyChanged(playWhenReady: Boolean) {
        if (isCrossfading) {
            if (playWhenReady && fadePaused) {
                Log.d(TAG, "[Crossfade] Active player resumed mid-fade")
                fadePaused = false
                val pausedDuration = SystemClock.uptimeMillis() - fadePauseTimestamp
                fadeStartTimeMs += pausedDuration
                standbyPlayer.play()
            } else if (!playWhenReady && !fadePaused) {
                Log.d(TAG, "[Crossfade] Active player paused mid-fade")
                fadePaused = true
                fadePauseTimestamp = SystemClock.uptimeMillis()
                standbyPlayer.pause()
            }
        }
    }

    private fun handlePlaybackState(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            if (isCrossfading) {
                completeCrossfade()
            } else if (currentQueueIndex + 1 < currentQueue.size) {
                Log.d(TAG, "[Gapless] Natural end of track reached, advancing gapless to track ${currentQueueIndex + 1}")
                playIndex(currentQueueIndex + 1, 0L)
            }
        }
    }

    var currentEffectConfig: AudioEffectConfig = AudioEffectConfig()
        private set

    fun applyEffectConfig(config: AudioEffectConfig) {
        currentEffectConfig = config
        dspChainA.applyConfig(config)
        dspChainB.applyConfig(config)
        if (com.nodeplayer.music.service.SystemWideEqService.isRunning) {
            // Bypass internal equalizer to prevent double equalization
            dspChainA.equalizer.enabled = false
            dspChainB.equalizer.enabled = false
        }
    }

    fun onSystemEqStateChanged(running: Boolean) {
        if (running) {
            dspChainA.equalizer.enabled = false
            dspChainB.equalizer.enabled = false
            val sA = playerA.audioSessionId
            val sB = playerB.audioSessionId
            if (sA > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.attachSession(sA, context.packageName)
            if (sB > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.attachSession(sB, context.packageName)
            Log.i(TAG, "[SystemEQ] Active -> Bypassed internal software EQ to prevent double EQ (sessions $sA, $sB)")
        } else {
            dspChainA.equalizer.enabled = currentEffectConfig.eqEnabled
            dspChainB.equalizer.enabled = currentEffectConfig.eqEnabled
            val sA = playerA.audioSessionId
            val sB = playerB.audioSessionId
            if (sA > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.releaseSession(sA)
            if (sB > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.releaseSession(sB)
            Log.i(TAG, "[SystemEQ] Inactive -> Restored internal software EQ (eqEnabled=${currentEffectConfig.eqEnabled})")
        }
    }

    fun playQueue(queue: List<Track>, startIndex: Int, startPositionMs: Long = 0L) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        if (isCrossfading) {
            abortCrossfade(restoreActive = false)
        }
        currentQueue = queue
        currentQueueIndex = startIndex
        val track = queue[startIndex]

        val player = activePlayer
        player.stop()
        player.clearMediaItems()
        player.volume = 1.0f
        val mediaItem = createMediaItem(track)
        player.setMediaItem(mediaItem)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.play()

        if (com.nodeplayer.music.service.SystemWideEqService.isRunning) {
            val s = player.audioSessionId
            if (s > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.attachSession(s, context.packageName)
            dspChainA.equalizer.enabled = false
            dspChainB.equalizer.enabled = false
        }

        Log.d(TAG, "[Playback] Playing track [idx=$startIndex]: \"${track.title}\" on ${if (activeIndex == 0) "PlayerA" else "PlayerB"}")
        onTrackTransition(track)
    }

    fun playIndex(index: Int, startPositionMs: Long = 0L) {
        if (index !in currentQueue.indices) return
        playQueue(currentQueue, index, startPositionMs)
    }

    fun togglePlayPause() {
        val player = activePlayer
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (isCrossfading && fadePaused) {
            fadePaused = false
            val pausedDuration = SystemClock.uptimeMillis() - fadePauseTimestamp
            fadeStartTimeMs += pausedDuration
            activePlayer.play()
            standbyPlayer.play()
            Log.d(TAG, "[Crossfade] Resumed both players mid-fade")
        } else {
            activePlayer.play()
        }
    }

    fun pause() {
        if (isCrossfading && !fadePaused) {
            fadePaused = true
            fadePauseTimestamp = SystemClock.uptimeMillis()
            activePlayer.pause()
            standbyPlayer.pause()
            Log.d(TAG, "[Crossfade] Paused both players mid-fade")
        } else {
            activePlayer.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = activePlayer
        if (isCrossfading) {
            Log.d(TAG, "[Crossfade] Mid-fade seek requested to ${positionMs}ms (effectiveFade=${currentEffectiveFadeMs}ms)")
            val duration = if (player.duration > 0) player.duration else currentQueue.getOrNull(currentQueueIndex)?.durationMs ?: 0L
            if (duration > 0 && positionMs < duration - currentEffectiveFadeMs) {
                Log.d(TAG, "[Crossfade] Mid-fade seek was before fade window. Aborting crossfade & restoring active player.")
                abortCrossfade(restoreActive = true)
                player.seekTo(positionMs)
            } else {
                Log.d(TAG, "[Crossfade] Mid-fade seek was near end. Completing crossfade immediately.")
                completeCrossfade()
            }
        } else {
            player.seekTo(positionMs)
        }
    }

    fun skipNext() {
        if (isCrossfading) {
            Log.d(TAG, "[Crossfade] Mid-fade skip next requested. Completing handoff immediately.")
            completeCrossfade()
        } else if (currentQueueIndex + 1 < currentQueue.size) {
            playIndex(currentQueueIndex + 1, 0L)
        }
    }

    fun skipPrevious() {
        if (isCrossfading) {
            Log.d(TAG, "[Crossfade] Mid-fade skip previous requested. Cancelling crossfade and restarting track.")
            abortCrossfade(restoreActive = true)
            activePlayer.seekTo(0L)
        } else if (activePlayer.currentPosition > 3000L) {
            activePlayer.seekTo(0L)
        } else if (currentQueueIndex > 0) {
            playIndex(currentQueueIndex - 1, 0L)
        } else {
            activePlayer.seekTo(0L)
        }
    }

    private fun tick() {
        val primary = activePlayer
        if (!primary.playWhenReady && !fadePaused) return

        if (isCrossfading) {
            if (fadePaused) return
            val elapsed = SystemClock.uptimeMillis() - fadeStartTimeMs
            val progress = (elapsed.toFloat() / currentEffectiveFadeMs).coerceIn(0f, 1f)
            val (outGain, inGain) = CrossfadeCurveMath.calculateGains(progress, crossfadeConfig.curve)

            primary.volume = outGain
            standbyPlayer.volume = inGain

            val pct = (progress * 100).toInt()
            if (pct % 25 == 0 && pct != lastLoggedPercent) {
                lastLoggedPercent = pct
                val outStr = "%.3f".format(outGain)
                val inStr = "%.3f".format(inGain)
                Log.d(TAG, "[Crossfade] Progress $pct% | OutVol: $outStr | InVol: $inStr | Elapsed: ${elapsed}ms / ${currentEffectiveFadeMs}ms")
            }

            if (progress >= 1.0f) {
                completeCrossfade()
            }
            return
        }

        // Check whether to initiate crossfade
        if (crossfadeConfig.enabled && currentQueueIndex + 1 < currentQueue.size) {
            val currentTrack = currentQueue[currentQueueIndex]
            val nextTrack = currentQueue[currentQueueIndex + 1]
            val duration = if (primary.duration > 0) primary.duration else currentTrack.durationMs
            val position = primary.currentPosition
            if (duration > 0) {
                val remaining = duration - position

                val isGaplessDetected = crossfadeConfig.autoGapless && isGaplessTransition(currentTrack, nextTrack)
                if (isGaplessDetected) {
                    // Suppress crossfade for gapless albums
                    return
                }

                val requestedFadeMs = (crossfadeConfig.durationSeconds * 1000).toLong()
                // Edge case: if track is shorter than fade length, clamp to at most half track duration
                val effectiveFadeMs = min(requestedFadeMs, (duration / 2)).coerceAtLeast(500L)

                if (remaining <= effectiveFadeMs && remaining > 0) {
                    startCrossfade(nextTrack, effectiveFadeMs)
                }
            }
        }
    }

    private fun startCrossfade(nextTrack: Track, effectiveFadeMs: Long) {
        val currentTrack = currentQueue[currentQueueIndex]
        Log.d(TAG, "[Crossfade] >>> STARTING CROSSFADE >>>")
        Log.d(TAG, "[Crossfade] From: \"${currentTrack.title}\" -> To: \"${nextTrack.title}\"")
        Log.d(TAG, "[Crossfade] Configured curve: ${crossfadeConfig.curve.name}, Duration: ${effectiveFadeMs}ms")

        isCrossfading = true
        fadePaused = false
        fadeStartTimeMs = SystemClock.uptimeMillis()
        currentEffectiveFadeMs = effectiveFadeMs
        lastLoggedPercent = -1

        val secondary = standbyPlayer
        secondary.stop()
        secondary.clearMediaItems()
        secondary.volume = 0.0f
        secondary.setMediaItem(createMediaItem(nextTrack))
        secondary.prepare()
        secondary.play()
    }

    private fun completeCrossfade() {
        val newTrack = currentQueue.getOrNull(currentQueueIndex + 1)
        Log.d(TAG, "[Crossfade] <<< COMPLETED CROSSFADE <<< Swapping active player")

        isCrossfading = false
        fadePaused = false
        lastLoggedPercent = -1

        val oldPlayer = activePlayer
        oldPlayer.stop()
        oldPlayer.clearMediaItems()
        oldPlayer.volume = 1.0f

        standbyPlayer.volume = 1.0f

        // Swap active index
        activeIndex = 1 - activeIndex
        currentQueueIndex++

        onMediaSessionPlayerSwapped(activePlayer)
        if (com.nodeplayer.music.service.SystemWideEqService.isRunning) {
            val s = activePlayer.audioSessionId
            if (s > 0) com.nodeplayer.music.service.SystemWideEqService.instance?.attachSession(s, context.packageName)
        }
        if (newTrack != null) {
            onTrackTransition(newTrack)
        }
    }

    private fun abortCrossfade(restoreActive: Boolean) {
        Log.d(TAG, "[Crossfade] Aborting crossfade")
        isCrossfading = false
        fadePaused = false
        lastLoggedPercent = -1

        standbyPlayer.stop()
        standbyPlayer.clearMediaItems()
        standbyPlayer.volume = 1.0f

        if (restoreActive) {
            activePlayer.volume = 1.0f
        }
    }

    fun isGaplessTransition(trackA: Track?, trackB: Track?): Boolean {
        if (trackA == null || trackB == null) return false
        val sameAlbum = trackA.album.isNotBlank() && trackA.album.equals(trackB.album, ignoreCase = true)
        if (sameAlbum) {
            if (trackA.discNumber == trackB.discNumber && trackB.trackNumber == trackA.trackNumber + 1 && trackA.trackNumber > 0) {
                Log.d(TAG, "[Gapless] Detected consecutive tracks in album \"${trackA.album}\" (#${trackA.trackNumber} -> #${trackB.trackNumber}) - crossfade bypassed")
                return true
            }
            if (trackA.replayGainAlbumGain != null && trackA.replayGainAlbumGain == trackB.replayGainAlbumGain) {
                Log.d(TAG, "[Gapless] Detected matching album ReplayGain (${trackA.replayGainAlbumGain} dB) - crossfade bypassed")
                return true
            }
        }
        val continuousKeywords = listOf("live", "mix", "continuous", "soundtrack", "classical", "opera", "dj")
        val gA = trackA.genre?.lowercase() ?: ""
        val gB = trackB.genre?.lowercase() ?: ""
        if (continuousKeywords.any { gA.contains(it) } || continuousKeywords.any { gB.contains(it) }) {
            Log.d(TAG, "[Gapless] Detected continuous/live genre keyword - crossfade bypassed")
            return true
        }
        return false
    }

    private fun createMediaItem(track: Track): MediaItem {
        val contentUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            track.id
        )
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
        val artworkUri = ContentUris.withAppendedId(albumArtBaseUri, track.albumId)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        handler.removeCallbacks(monitorRunnable)
        playerA.release()
        playerB.release()
    }
}
