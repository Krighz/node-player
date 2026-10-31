package com.nodeplayer.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
