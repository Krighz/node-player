package com.nodeplayer.music.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nodeplayer.music.service.PlaybackService

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val service = PlaybackService.instance
        if (service != null && service.crossfadeManager.currentQueue.isNotEmpty()) {
            when (action) {
                PlaybackService.ACTION_PLAY_PAUSE -> service.crossfadeManager.togglePlayPause()
                PlaybackService.ACTION_NEXT -> service.crossfadeManager.skipNext()
                PlaybackService.ACTION_PREV -> service.crossfadeManager.skipPrevious()
                PlaybackService.ACTION_SEEK_BACK_10 -> {
                    val pos = (service.player.currentPosition - 10000L).coerceAtLeast(0L)
                    service.player.seekTo(pos)
                }
                PlaybackService.ACTION_SEEK_FORWARD_30 -> {
                    val dur = service.player.duration.let { if (it > 0) it else service.crossfadeManager.currentPlayingTrack?.durationMs ?: 0L }
                    val pos = (service.player.currentPosition + 30000L).coerceAtMost(dur)
                    service.player.seekTo(pos)
                }
            }
            WidgetUpdateManager.updateAllWidgets(context)
        } else {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
                intent.extras?.let { putExtras(it) }
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("WidgetActionReceiver", "Failed to start service", e)
            }
        }
    }
}
