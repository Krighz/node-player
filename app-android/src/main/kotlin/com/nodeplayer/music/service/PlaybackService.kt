package com.nodeplayer.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var crossfadeManager: com.nodeplayer.music.audio.CrossfadePlayerManager

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
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            scheduleNextLyricUpdate()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this@PlaybackService)
            scheduleNextLyricUpdate()
        }
    }

    companion object {
        const val CHANNEL_ID = "node_player_playback_channel"
        const val ACTION_PLAY_PAUSE = "com.nodeplayer.music.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.nodeplayer.music.action.NEXT"
        const val ACTION_PREV = "com.nodeplayer.music.action.PREV"
        const val ACTION_SEEK_TO = "com.nodeplayer.music.action.SEEK_TO"
        const val ACTION_SEEK_BACK_10 = "com.nodeplayer.music.action.SEEK_BACK_10"
        const val ACTION_SEEK_FORWARD_30 = "com.nodeplayer.music.action.SEEK_FORWARD_30"
        const val ACTION_START_OVERLAY = "com.nodeplayer.music.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.nodeplayer.music.action.STOP_OVERLAY"
        const val EXTRA_SEEK_FRACTION = "extra_seek_fraction"
        const val EXTRA_SEEK_POSITION_MS = "extra_seek_position_ms"

        var instance: PlaybackService? = null
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
                com.nodeplayer.music.widget.WidgetUpdateManager.updateAllWidgets(this)
                scheduleNextLyricUpdate()
            },
            onMediaSessionPlayerSwapped = { newPlayer ->
                mediaSession?.setPlayer(newPlayer)
                newPlayer.addListener(playerListener)
            }
        )
        crossfadeManager.activePlayer.addListener(playerListener)

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

        val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        val shuffle = prefs.getBoolean("shuffle_enabled", false)
        crossfadeManager.setShuffle(shuffle)

        serviceScope.launch(Dispatchers.IO) {
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
        if (::crossfadeManager.isInitialized) {
            crossfadeManager.release()
        }
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
