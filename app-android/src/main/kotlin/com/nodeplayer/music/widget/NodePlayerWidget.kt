package com.nodeplayer.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nodeplayer.model.Track
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import com.nodeplayer.music.service.PlaybackService

class NodePlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val service = PlaybackService.instance
        val currentTrack = service?.crossfadeManager?.currentPlayingTrack
        val isPlaying = service?.player?.isPlaying == true

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, currentTrack, isPlaying)
        }
    }

    companion object {
        fun updateWidget(context: Context, track: Track?, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NodePlayerWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, track, isPlaying)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            track: Track?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_node_player)

            if (track != null) {
                views.setTextViewText(R.id.widget_title, track.title)
                views.setTextViewText(R.id.widget_artist, track.artist)
            } else {
                views.setTextViewText(R.id.widget_title, "Node Player")
                views.setTextViewText(R.id.widget_artist, "Touch to play")
            }

            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            // Open App
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Play/Pause Action
            val playPauseIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = PlaybackService.ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Previous Action
            val prevIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = PlaybackService.ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            // Next Action
            val nextIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = PlaybackService.ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
