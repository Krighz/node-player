package com.nodeplayer.music.ui.video

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.video.SurfacePriority
import com.nodeplayer.model.video.VideoAspectMath
import com.nodeplayer.music.playback.AudioCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

@OptIn(UnstableApi::class)
object MusicVideoEngine {
    private const val TAG = "MusicVideoEngine"
    private const val SYNC_INTERVAL_MS = 250L
    private const val SEEK_DRIFT_THRESHOLD_MS = 450L
    private const val SEEK_COOLDOWN_MS = 1500L
    private const val RELEASE_GRACE_PERIOD_MS = 4000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var videoPlayer: ExoPlayer? = null
    private var audioController: Player? = null

    private data class SurfaceEntry(val view: TextureView, val priority: SurfacePriority)
    private val surfaces = ArrayList<SurfaceEntry>()

    private val _aspect = MutableStateFlow(16f / 9f)
    val aspect: StateFlow<Float> = _aspect.asStateFlow()

    private val _hasRenderedFirstFrame = MutableStateFlow(false)
    val hasRenderedFirstFrame: StateFlow<Boolean> = _hasRenderedFirstFrame.asStateFlow()

    private var syncJob: Job? = null
    private var unmountGraceJob: Job? = null
    private var lastSeekTimestamp = 0L

    fun initialize(context: Context) {
        if (videoPlayer == null) {
            val appContext = context.applicationContext
            val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(AudioCache.sharedOkHttpClient)
                .setUserAgent(com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent)
            val rangedFactory = com.nodeplayer.music.playback.RangedUpstreamDataSource.Factory(httpFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(rangedFactory)

            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 4000,
                    /* maxBufferMs = */ 25000,
                    /* bufferForPlaybackMs = */ 2000,
                    /* bufferForPlaybackAfterRebufferMs = */ 3500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            videoPlayer = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build().apply {
                    volume = 0f
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            NodeLog.i(TAG, "Video first frame rendered successfully")
                            _hasRenderedFirstFrame.value = true
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                val par = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                                _aspect.value = (videoSize.width * par) / videoSize.height
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            NodeLog.e(TAG, "Video player error: ${error.message}", error)
                        }
                    })
                }
            NodeLog.i(TAG, "Initialized decoupled silent MusicVideoEngine with direct Ranged DataSourceFactory")
        }
    }

    fun attachAudioController(controller: Player?) {
        if (this.audioController === controller) return
        this.audioController = controller
        startSyncLoop()
    }

    fun claimSurface(view: TextureView, priority: SurfacePriority): () -> Unit {
        unmountGraceJob?.cancel()
        unmountGraceJob = null
        surfaces.removeAll { it.view === view }
        surfaces.add(SurfaceEntry(view, priority))
        rebindTopSurface()

        return {
            surfaces.removeAll { it.view === view }
            if (surfaces.isEmpty()) {
                // Surface unmounted grace period (4 seconds) before stopping decoder
                unmountGraceJob?.cancel()
                unmountGraceJob = scope.launch {
                    delay(RELEASE_GRACE_PERIOD_MS)
                    if (surfaces.isEmpty()) {
                        rebindTopSurface()
                    }
                }
            } else {
                rebindTopSurface()
            }
        }
    }

    private fun rebindTopSurface() {
        val top = surfaces.maxByOrNull { it.priority.level }?.view
        val exo = videoPlayer ?: return
        if (top != null) {
            exo.setVideoTextureView(top)
            if (audioController?.isPlaying == true && !exo.playWhenReady) {
                exo.play()
            }
        } else {
            exo.clearVideoTextureView(null)
            exo.pause()
            if (surfaces.isEmpty()) {
                syncJob?.cancel()
            }
        }
    }

    fun loadVideoStream(url: String, startPositionMs: Long, userAgent: String? = null, videoId: String? = null) {
        val exo = videoPlayer ?: return
        _hasRenderedFirstFrame.value = false

        val targetUa = userAgent
            ?: AudioCache.getUserAgentForUrl(url)
            ?: com.nodeplayer.network.innertube.YouTubeClient.IOS.userAgent

        val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(AudioCache.sharedOkHttpClient)
            .setUserAgent(targetUa)

        if (targetUa.contains("Chrome") || url.contains("c=WEB") || url.contains("c=WEB_REMIX")) {
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/"))
        }

        val rangedFactory = com.nodeplayer.music.playback.RangedUpstreamDataSource.Factory(httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(rangedFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(if (!videoId.isNullOrBlank()) "video_$videoId" else url)
            .setCustomCacheKey(if (!videoId.isNullOrBlank()) "video_$videoId" else null)
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        exo.setMediaSource(mediaSource, startPositionMs)
        exo.prepare()
        if (audioController?.isPlaying == true) {
            exo.play()
        }
        startSyncLoop()
    }

    fun pause() {
        videoPlayer?.pause()
    }

    fun resume() {
        videoPlayer?.play()
    }

    fun isPlaying(): Boolean = videoPlayer?.isPlaying == true

    fun stop() {
        syncJob?.cancel()
        unmountGraceJob?.cancel()
        videoPlayer?.stop()
        videoPlayer?.clearMediaItems()
        _hasRenderedFirstFrame.value = false
    }

    fun release() {
        stop()
        videoPlayer?.release()
        videoPlayer = null
        audioController = null
        surfaces.clear()
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                synchronizeClock()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private fun synchronizeClock() {
        val audio = audioController ?: return
        val video = videoPlayer ?: return
        if (!video.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) return
        if (video.playbackState == Player.STATE_IDLE || video.playbackState == Player.STATE_ENDED) return

        val audioPos = audio.currentPosition
        val videoPos = video.currentPosition
        val baseSpeed = audio.playbackParameters.speed
        val now = System.currentTimeMillis()

        if (!audio.isPlaying) {
            if (video.playWhenReady) video.pause()
            if (abs(videoPos - audioPos) > 40L && now - lastSeekTimestamp > 120L) {
                lastSeekTimestamp = now
                video.seekTo(audioPos.coerceAtLeast(0L))
            }
            return
        }

        if (!video.playWhenReady && surfaces.isNotEmpty()) {
            video.play()
        }

        val driftMs = videoPos - audioPos
        if (abs(driftMs) > SEEK_DRIFT_THRESHOLD_MS) {
            if (now - lastSeekTimestamp > SEEK_COOLDOWN_MS) {
                lastSeekTimestamp = now
                video.seekTo(audioPos.coerceAtLeast(0L))
            }
        } else {
            val nudge = VideoAspectMath.calculateRateNudge(driftMs)
            video.playbackParameters = PlaybackParameters(baseSpeed * (1f + nudge))
        }
    }
}
