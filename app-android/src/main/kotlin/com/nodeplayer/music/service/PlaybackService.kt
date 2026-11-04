package com.nodeplayer.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import com.nodeplayer.network.cast.ActiveCastSession
import com.nodeplayer.network.cast.JitterBuffer
import com.nodeplayer.network.discovery.DiscoveredNodePeer
import com.nodeplayer.network.protocol.ControlStateEventMessage
import com.nodeplayer.network.service.LocalPlayerController
import com.nodeplayer.network.service.NodeConnectHub
import android.net.wifi.WifiManager
import com.nodeplayer.common.logging.NodeLog
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import com.nodeplayer.music.audio.DspAudioProcessor
import com.nodeplayer.music.widget.FloatingOverlayService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var crossfadeManager: com.nodeplayer.music.audio.CrossfadePlayerManager
    lateinit var sleepTimerManager: SleepTimerManager
        private set
    lateinit var nodeConnectHub: NodeConnectHub
        private set
    private var incomingCastJob: Job? = null
    private var incomingAudioTrack: AudioTrack? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("node_player_discovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            NodeLog.i("nodeconnect/discovery", "Acquired Android WifiManager.MulticastLock")
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/discovery", "Failed to acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                NodeLog.i("nodeconnect/discovery", "Released Android WifiManager.MulticastLock")
            }
        } catch (e: Exception) {
            // Ignore
        }
        multicastLock = null
    }
    val statsTracker = com.nodeplayer.library.PlaybackStatsTracker()
    private val scrobbledTrackIds = mutableSetOf<Long>()

    val player: ExoPlayer
        get() = crossfadeManager.activePlayer

    val dspChain: DspChain
        get() = crossfadeManager.activeDspChain

    val dspAudioProcessor: DspAudioProcessor
        get() = crossfadeManager.activeDspProcessor

    var onTrackTransitionCallback: ((com.nodeplayer.model.Track) -> Unit)? = null

    private var wasPlayingBeforeHeadsetUnplug = false
    private var headsetReceiver: BroadcastReceiver? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lyricUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isCurrentlyPlaying = isPlaying
            statsTracker.onPlayStateChanged(isPlaying)
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            scheduleNextLyricUpdate()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                statsTracker.onPlayStateChanged(false)
            }
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            scheduleNextLyricUpdate()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                statsTracker.onSeek()
                if (::nodeConnectHub.isInitialized) {
                    serviceScope.launch(Dispatchers.IO) {
                        nodeConnectHub.castService.flushSenderStream(newPosition.positionMs)
                    }
                }
            }
        }
    }

    fun playIncomingCastStream(session: ActiveCastSession, jitter: JitterBuffer) {
        incomingCastJob?.cancel()
        try {
            incomingAudioTrack?.stop()
            incomingAudioTrack?.release()
        } catch (_: Exception) {}

        incomingCastJob = serviceScope.launch(Dispatchers.IO) {
            val minBufSize = AudioTrack.getMinBufferSize(
                session.sampleRate,
                if (session.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                if (session.bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_FLOAT
            )
            val bufferSize = minBufSize.coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(if (session.bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(session.sampleRate)
                        .setChannelMask(if (session.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            incomingAudioTrack = track
            track.play()

            val buf = ByteArray(4096)
            try {
                while (isActive) {
                    val read = jitter.read(buf, 0, buf.size)
                    if (read > 0) {
                        track.write(buf, 0, read)
                    } else {
                        delay(5)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun startAudioCasting(targetPeer: DiscoveredNodePeer) {
        serviceScope.launch(Dispatchers.IO) {
            val conn = nodeConnectHub.connectToPeer(targetPeer) ?: return@launch
            val track = crossfadeManager.currentPlayingTrack ?: return@launch
            nodeConnectHub.castService.startCastAsSender(conn, track, crossfadeManager.activePlayer.currentPosition)

            crossfadeManager.dspAudioProcessorA.preDspPcmTap = { bytes, _, _, _ ->
                serviceScope.launch(Dispatchers.IO) {
                    nodeConnectHub.castService.sendPcmAudioChunk(bytes, crossfadeManager.activePlayer.currentPosition)
                }
            }
            crossfadeManager.dspAudioProcessorB.preDspPcmTap = { bytes, _, _, _ ->
                serviceScope.launch(Dispatchers.IO) {
                    nodeConnectHub.castService.sendPcmAudioChunk(bytes, crossfadeManager.activePlayer.currentPosition)
                }
            }
        }
    }

    fun stopAudioCasting() {
        serviceScope.launch(Dispatchers.IO) {
            nodeConnectHub.castService.stopCastAsSender()
            crossfadeManager.dspAudioProcessorA.preDspPcmTap = null
            crossfadeManager.dspAudioProcessorB.preDspPcmTap = null
        }
    }

    companion object {
        @Volatile var isCurrentlyPlaying: Boolean = false
        const val CHANNEL_ID = "node_player_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.nodeplayer.music.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.nodeplayer.music.action.NEXT"
        const val ACTION_PREV = "com.nodeplayer.music.action.PREV"
        const val ACTION_SEEK_TO = "com.nodeplayer.music.action.SEEK_TO"
        const val ACTION_SEEK_BACK_10 = "com.nodeplayer.music.action.SEEK_BACK_10"
        const val ACTION_SEEK_FORWARD_30 = "com.nodeplayer.music.action.SEEK_FORWARD_30"
        const val ACTION_START_OVERLAY = "com.nodeplayer.music.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.nodeplayer.music.action.STOP_OVERLAY"
        const val ACTION_SLEEP_START = "com.nodeplayer.music.action.SLEEP_START"
        const val ACTION_SLEEP_EXTEND_5 = "com.nodeplayer.music.action.SLEEP_EXTEND_5"
        const val ACTION_SLEEP_EXTEND_15 = "com.nodeplayer.music.action.SLEEP_EXTEND_15"
        const val ACTION_SLEEP_CANCEL = "com.nodeplayer.music.action.SLEEP_CANCEL"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"
        const val EXTRA_SLEEP_FINISH_TRACK = "extra_sleep_finish_track"
        const val EXTRA_SLEEP_FADE_SECONDS = "extra_sleep_fade_seconds"

        var onAudioTrackConfigChangedCallback: ((sampleRate: Int, encoding: String, channels: Int) -> Unit)? = null
        const val EXTRA_SLEEP_STOP_TRACKS = "extra_sleep_stop_tracks"
        const val EXTRA_SLEEP_SHAKE_EXTEND = "extra_sleep_shake_extend"
        const val EXTRA_SEEK_FRACTION = "extra_seek_fraction"
        const val EXTRA_SEEK_POSITION_MS = "extra_seek_position_ms"

        var instance: PlaybackService? = null
            private set
        var onServiceCreated: ((PlaybackService) -> Unit)? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action in listOf(
                ACTION_PLAY_PAUSE,
                ACTION_NEXT,
                ACTION_PREV,
                ACTION_SEEK_TO,
                ACTION_SEEK_BACK_10,
                ACTION_SEEK_FORWARD_30
            )
        ) {
            startInitialForegroundIfNeeded()
        }

        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (::crossfadeManager.isInitialized) {
                    if (crossfadeManager.currentQueue.isEmpty()) {
                        serviceScope.launch(Dispatchers.IO) {
                            val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                            val tracks = db?.playerDao()?.getAllTracksList()?.map { it.toTrack() } ?: emptyList()
                            if (tracks.isNotEmpty()) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                                    val lastTrackId = prefs.getLong("last_track_id", -1L)
                                    val lastPos = prefs.getLong("last_pos", 0L)
                                    val idx = tracks.indexOfFirst { it.id == lastTrackId }.coerceAtLeast(0)
                                    crossfadeManager.playQueue(tracks, idx, lastPos)
                                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
                                }
                            } else {
                                dismissForegroundAndStopIfNeeded()
                            }
                        }
                    } else {
                        crossfadeManager.togglePlayPause()
                        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    }
                }
            }
            ACTION_NEXT -> {
                if (::crossfadeManager.isInitialized) {
                    if (crossfadeManager.currentQueue.isEmpty()) {
                        serviceScope.launch(Dispatchers.IO) {
                            val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                            val tracks = db?.playerDao()?.getAllTracksList()?.map { it.toTrack() } ?: emptyList()
                            if (tracks.isNotEmpty()) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    crossfadeManager.playQueue(tracks, 0, 0L)
                                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
                                }
                            } else {
                                dismissForegroundAndStopIfNeeded()
                            }
                        }
                    } else {
                        crossfadeManager.skipNext()
                        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    }
                }
            }
            ACTION_PREV -> {
                if (::crossfadeManager.isInitialized) {
                    if (crossfadeManager.currentQueue.isEmpty()) {
                        serviceScope.launch(Dispatchers.IO) {
                            val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                            val tracks = db?.playerDao()?.getAllTracksList()?.map { it.toTrack() } ?: emptyList()
                            if (tracks.isNotEmpty()) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    crossfadeManager.playQueue(tracks, 0, 0L)
                                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
                                }
                            } else {
                                dismissForegroundAndStopIfNeeded()
                            }
                        }
                    } else {
                        crossfadeManager.skipPrevious()
                        com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    }
                }
            }
            ACTION_SEEK_TO -> {
                if (::crossfadeManager.isInitialized) {
                    val duration = player.duration.let { if (it > 0) it else crossfadeManager.currentPlayingTrack?.durationMs ?: 0L }
                    if (intent.hasExtra(EXTRA_SEEK_FRACTION) && duration > 0) {
                        val fraction = intent.getFloatExtra(EXTRA_SEEK_FRACTION, 0f)
                        val targetMs = (fraction * duration).toLong().coerceIn(0, duration)
                        player.seekTo(targetMs)
                    } else if (intent.hasExtra(EXTRA_SEEK_POSITION_MS)) {
                        val targetMs = intent.getLongExtra(EXTRA_SEEK_POSITION_MS, 0L)
                        player.seekTo(targetMs)
                    }
                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    scheduleNextLyricUpdate()
                }
            }
            ACTION_SEEK_BACK_10 -> {
                if (::crossfadeManager.isInitialized) {
                    val pos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                    player.seekTo(pos)
                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    scheduleNextLyricUpdate()
                }
            }
            ACTION_SEEK_FORWARD_30 -> {
                if (::crossfadeManager.isInitialized) {
                    val dur = player.duration.let { if (it > 0) it else crossfadeManager.currentPlayingTrack?.durationMs ?: 0L }
                    val pos = (player.currentPosition + 30000L).coerceAtMost(dur)
                    player.seekTo(pos)
                    com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                    scheduleNextLyricUpdate()
                }
            }
            ACTION_START_OVERLAY -> {
                FloatingOverlayService.start(this)
            }
            ACTION_STOP_OVERLAY -> {
                FloatingOverlayService.stop(this)
            }
            ACTION_SLEEP_START -> {
                if (::sleepTimerManager.isInitialized) {
                    val mins = intent.getIntExtra(EXTRA_SLEEP_MINUTES, 30)
                    val finish = intent.getBooleanExtra(EXTRA_SLEEP_FINISH_TRACK, true)
                    val fadeSec = intent.getIntExtra(EXTRA_SLEEP_FADE_SECONDS, 10)
                    val stopTracks = intent.getIntExtra(EXTRA_SLEEP_STOP_TRACKS, 0)
                    val shake = intent.getBooleanExtra(EXTRA_SLEEP_SHAKE_EXTEND, true)
                    sleepTimerManager.startTimer(mins, finish, fadeSec, stopTracks, shake)
                }
            }
            ACTION_SLEEP_EXTEND_5 -> {
                if (::sleepTimerManager.isInitialized) {
                    sleepTimerManager.extend(5)
                }
            }
            ACTION_SLEEP_EXTEND_15 -> {
                if (::sleepTimerManager.isInitialized) {
                    sleepTimerManager.extend(15)
                }
            }
            ACTION_SLEEP_CANCEL -> {
                if (::sleepTimerManager.isInitialized) {
                    sleepTimerManager.cancel()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        onServiceCreated?.invoke(this)
        createNotificationChannel()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .build()
        )

        crossfadeManager = com.nodeplayer.music.audio.CrossfadePlayerManager(
            context = this,
            onTrackTransition = { track ->
                onTrackTransitionCallback?.invoke(track)
                sleepTimerManager.onTrackChanged()
                scrobbledTrackIds.remove(track.id)
                statsTracker.onTrackStarted(track.id)
                serviceScope.launch(Dispatchers.IO) {
                    val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                    val sk = prefs.getString("lastfm_session_key", "") ?: ""
                    val enabled = prefs.getBoolean("lastfm_scrobble_enabled", false)
                    if (enabled && sk.isNotBlank()) {
                        com.nodeplayer.music.library.LastFmClient.updateNowPlaying(track, sk)
                    }
                }
                com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                scheduleNextLyricUpdate()
            },
            onMediaSessionPlayerSwapped = { newPlayer ->
                mediaSession?.setPlayer(newPlayer)
                newPlayer.addListener(playerListener)
            }
        )
        crossfadeManager.onAudioTrackConfigChanged = { sr, enc, ch ->
            onAudioTrackConfigChangedCallback?.invoke(sr, enc, ch)
        }
        crossfadeManager.activePlayer.addListener(playerListener)

        sleepTimerManager = SleepTimerManager(
            context = this,
            playerProvider = { if (::crossfadeManager.isInitialized) crossfadeManager.activePlayer else null },
            onTimerExpired = {
                com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            }
        )

        statsTracker.onPlayCountThresholdReached = { trackId ->
            serviceScope.launch(Dispatchers.IO) {
                val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                db?.playerDao()?.recordTrackPlayed(trackId, System.currentTimeMillis())
            }
        }

        statsTracker.onListeningTimeAccumulated = { dateStr, seconds ->
            serviceScope.launch(Dispatchers.IO) {
                val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                db?.playerDao()?.addListeningTime(dateStr, seconds, 0)
            }
        }

        // Periodic stats ticker & scrobble eligibility checker (every 3s)
        serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(3000L)
                if (::crossfadeManager.isInitialized && crossfadeManager.activePlayer.isPlaying) {
                    statsTracker.tick()
                    val track = crossfadeManager.currentPlayingTrack
                    if (track != null && !scrobbledTrackIds.contains(track.id)) {
                        val listenedMs = statsTracker.currentTrackListenedMsValue
                        if (com.nodeplayer.library.LastFmSigner.isEligibleForScrobble(track.durationMs / 1000L, listenedMs / 1000L)) {
                            scrobbledTrackIds.add(track.id)
                            val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                            val sk = prefs.getString("lastfm_session_key", "") ?: ""
                            val enabled = prefs.getBoolean("lastfm_scrobble_enabled", false)
                            if (enabled && sk.isNotBlank()) {
                                kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    com.nodeplayer.music.library.LastFmClient.scrobble(
                                        track = track,
                                        timestampSec = System.currentTimeMillis() / 1000L,
                                        sessionKey = sk,
                                        context = this@PlaybackService
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, crossfadeManager.activePlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    when (playerCommand) {
                        Player.COMMAND_PLAY_PAUSE -> {
                            val p = crossfadeManager.activePlayer
                            if (!p.isPlaying && (p.playbackState == Player.STATE_ENDED || (p.duration > 0 && p.currentPosition >= p.duration))) {
                                p.seekTo(0L)
                            }
                        }
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            crossfadeManager.skipNext()
                            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
                            return SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            crossfadeManager.skipPrevious()
                            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()

        SystemWideEqService.onStateChanged = { running ->
            crossfadeManager.onSystemEqStateChanged(running)
        }
        if (SystemWideEqService.isRunning) {
            crossfadeManager.onSystemEqStateChanged(true)
        }

        serviceScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
            val shuffle = prefs.getBoolean("shuffle_enabled", false)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                crossfadeManager.setShuffle(shuffle)
            }
            try {
                val db = (application as? com.nodeplayer.music.NodePlayerApplication)?.database
                val tracks = db?.playerDao()?.getAllTracksList()?.map { it.toTrack() } ?: emptyList()
                if (tracks.isNotEmpty()) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        val lastTrackId = prefs.getLong("last_track_id", -1L)
                        val targetIdx = tracks.indexOfFirst { it.id == lastTrackId }.coerceAtLeast(0)
                        crossfadeManager.setQueueIfEmpty(tracks, targetIdx)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Error preloading tracks", e)
            }
        }

        registerHeadsetReceiver()
        val localController = object : LocalPlayerController {
            override fun play() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.play() }
            }
            override fun pause() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.pause() }
            }
            override fun playPause() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.togglePlayPause() }
            }
            override fun seekTo(positionMs: Long) {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.seekTo(positionMs) }
            }
            override fun skipToNext() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.skipNext() }
            }
            override fun skipToPrevious() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.skipPrevious() }
            }
            override fun setVolume(volume: Float) {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.activePlayer.volume = volume }
            }
            override fun toggleShuffle() {
                serviceScope.launch(Dispatchers.Main) { crossfadeManager.setShuffle(!crossfadeManager.isShuffleEnabled) }
            }
            override fun cycleRepeat() {
                serviceScope.launch(Dispatchers.Main) {
                    val nextMode = (crossfadeManager.activePlayer.repeatMode + 1) % 3
                    crossfadeManager.activePlayer.repeatMode = nextMode
                }
            }
        }

        val keysDir = File(filesDir, "nodeplayer/keys").apply { mkdirs() }
        val regFile = File(filesDir, "nodeplayer/registry/paired_devices.txt").apply { parentFile?.mkdirs() }

        nodeConnectHub = NodeConnectHub(
            keyStorage = com.nodeplayer.network.crypto.SecureKeyStorage(keysDir),
            registry = com.nodeplayer.network.pairing.DeviceRegistry(regFile),
            localController = localController,
            onIncomingCastStream = { session, jitter ->
                playIncomingCastStream(session, jitter)
            },
            onIncomingHandoff = { handoff ->
                serviceScope.launch(Dispatchers.Main) {
                    val tracks = crossfadeManager.currentQueue
                    val matchedIdx = tracks.indexOfFirst { it.title.equals(handoff.trackTitle, ignoreCase = true) }
                    if (matchedIdx >= 0) {
                        crossfadeManager.playQueue(tracks, matchedIdx, handoff.positionMs)
                    }
                }
                true
            }
        )

        val connectPrefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        val nodeConnectEnabled = connectPrefs.getBoolean("node_connect_enabled", true)
        if (nodeConnectEnabled) {
            acquireMulticastLock()
            nodeConnectHub.start(
                deviceName = Build.MODEL,
                platform = "Android"
            )
        }
    }

    fun setNodeConnectEnabled(enabled: Boolean) {
        val connectPrefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        connectPrefs.edit().putBoolean("node_connect_enabled", enabled).apply()
        if (enabled) {
            if (::nodeConnectHub.isInitialized) {
                acquireMulticastLock()
                nodeConnectHub.start(
                    deviceName = Build.MODEL,
                    platform = "Android"
                )
            }
        } else {
            if (::nodeConnectHub.isInitialized) {
                nodeConnectHub.stop()
                releaseMulticastLock()
            }
        }
    }

    private fun registerHeadsetReceiver() {
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                val resumeEnabled = prefs.getBoolean("resume_on_headset_connect", true)

                when (action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        wasPlayingBeforeHeadsetUnplug = player.isPlaying
                    }
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 1) { // Plugged in
                            if (resumeEnabled && wasPlayingBeforeHeadsetUnplug) {
                                wasPlayingBeforeHeadsetUnplug = false
                                player.play()
                            }
                        } else if (state == 0) { // Unplugged
                            wasPlayingBeforeHeadsetUnplug = player.isPlaying
                        }
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (resumeEnabled && wasPlayingBeforeHeadsetUnplug) {
                            wasPlayingBeforeHeadsetUnplug = false
                            player.play()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        registerReceiver(headsetReceiver, filter)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun setSessionPlayer(newPlayer: ExoPlayer) {
        mediaSession?.setPlayer(newPlayer)
    }



    private fun startInitialForegroundIfNeeded() {
        try {
            createNotificationChannel()
            val initialNotification = buildInitialNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Failed to start initial foreground notification", e)
        }
    }

    private fun buildInitialNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Playing audio")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun dismissForegroundAndStopIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleNextLyricUpdate() {
        lyricUpdateJob?.cancel()
        if (!::crossfadeManager.isInitialized) return
        val track = crossfadeManager.currentPlayingTrack ?: return
        if (!player.isPlaying) return

        val lrcFile = File(track.path.substringBeforeLast('.') + ".lrc")
        if (!lrcFile.exists()) return

        val lyrics = com.nodeplayer.library.LrcParser.parse(lrcFile.readText())
        if (lyrics.lines.isEmpty()) return

        val currentPos = player.currentPosition
        val nextLine = lyrics.lines.firstOrNull { it.timestampMs > currentPos } ?: return
        val delayMs = (nextLine.timestampMs - currentPos).coerceAtLeast(20L)

        lyricUpdateJob = serviceScope.launch {
            delay(delayMs)
            val actualPos = player.currentPosition
            val lagMs = actualPos - nextLine.timestampMs
            android.util.Log.d("WidgetLyrics", "Lyric line advanced: '${nextLine.text}' (scheduled: ${nextLine.timestampMs}ms, actual: ${actualPos}ms, lag: ${lagMs}ms)")
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            scheduleNextLyricUpdate()
        }
    }

    override fun onDestroy() {
        if (::nodeConnectHub.isInitialized) {
            nodeConnectHub.stop()
            releaseMulticastLock()
        }
        incomingCastJob?.cancel()
        try {
            incomingAudioTrack?.stop()
            incomingAudioTrack?.release()
        } catch (_: Exception) {}
        if (::sleepTimerManager.isInitialized) {
            sleepTimerManager.release()
        }
        lyricUpdateJob?.cancel()
        serviceScope.cancel()
        try { player.removeListener(playerListener) } catch (_: Exception) {}
        headsetReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            headsetReceiver = null
        }
        SystemWideEqService.onStateChanged = null
        instance = null
        onTrackTransitionCallback = null
        onAudioTrackConfigChangedCallback = null
        if (::crossfadeManager.isInitialized) {
            crossfadeManager.release()
        }
        isCurrentlyPlaying = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
