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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nodeplayer.dsp.DspChain
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import com.nodeplayer.music.audio.DspAudioProcessor

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

    companion object {
        const val CHANNEL_ID = "node_player_playback_channel"
        var instance: PlaybackService? = null
            private set
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
            },
            onMediaSessionPlayerSwapped = { newPlayer ->
                mediaSession?.setPlayer(newPlayer)
            }
        )

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, crossfadeManager.activePlayer)
            .setSessionActivity(pendingIntent)
            .build()

        SystemWideEqService.onStateChanged = { running ->
            crossfadeManager.onSystemEqStateChanged(running)
        }
        if (SystemWideEqService.isRunning) {
            crossfadeManager.onSystemEqStateChanged(true)
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

    override fun onDestroy() {
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
