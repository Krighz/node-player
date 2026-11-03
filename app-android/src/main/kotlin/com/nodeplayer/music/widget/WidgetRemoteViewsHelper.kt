package com.nodeplayer.music.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.nodeplayer.model.WidgetSettings
import com.nodeplayer.music.R
import com.nodeplayer.music.service.PlaybackService

object WidgetRemoteViewsHelper {

    fun applyElementVisibilities(
        views: RemoteViews,
        settings: WidgetSettings,
        isCard: Boolean = false,
        isLyrics: Boolean = false,
        isSlim: Boolean = false,
        isSquare: Boolean = false
    ) {
        views.setViewVisibility(R.id.widget_artwork, if (settings.showArt) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_title, if (settings.showTitleArtist) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_artist, if (settings.showTitleArtist) View.VISIBLE else View.GONE)

        if (isCard) {
            views.setViewVisibility(R.id.widget_glyph, if (settings.showWaveform) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_progress_row, if (settings.showSeekbarTimes) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_controls_row, if (settings.showTransport) View.VISIBLE else View.GONE)
        }
        if (isLyrics) {
            views.setViewVisibility(R.id.widget_lyrics_container, if (settings.showLyrics) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_controls_row, if (settings.showTransport) View.VISIBLE else View.GONE)
        }
        if (isSlim) {
            views.setViewVisibility(R.id.widget_progress, if (settings.showSeekbarTimes) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_controls, if (settings.showTransport) View.VISIBLE else View.GONE)
        }
        if (isSquare) {
            views.setViewVisibility(R.id.widget_btn_play_pause, if (settings.showTransport) View.VISIBLE else View.GONE)
        }
    }

    fun bindControls(context: Context, views: RemoteViews, isPlaying: Boolean) {
        // Play/Pause
        views.setImageViewResource(
            R.id.widget_btn_play_pause,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        val playPauseIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = PlaybackService.ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

        // Previous
        val prevIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = PlaybackService.ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

        // Next
        val nextIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = PlaybackService.ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

        // Rewind -10s
        val rewindIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = PlaybackService.ACTION_SEEK_BACK_10
        }
        val rewindPendingIntent = PendingIntent.getBroadcast(
            context, 4, rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_rewind, rewindPendingIntent)

        // Forward +30s
        val forwardIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = PlaybackService.ACTION_SEEK_FORWARD_30
        }
        val forwardPendingIntent = PendingIntent.getBroadcast(
            context, 5, forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_forward, forwardPendingIntent)
    }

    fun bindProgressAndChronometer(
        views: RemoteViews,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        // Chronometer counts elapsed time automatically without wake locks or polling!
        val baseTime = SystemClock.elapsedRealtime() - positionMs
        views.setChronometer(R.id.widget_elapsed_time, baseTime, null, isPlaying)

        // Total time
        views.setTextViewText(R.id.widget_total_time, formatTime(durationMs))

        // Progress Bar
        val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
        views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
    }

    fun bindTapToSeekZones(context: Context, views: RemoteViews) {
        val zoneIds = intArrayOf(
            R.id.zone_0, R.id.zone_1, R.id.zone_2, R.id.zone_3, R.id.zone_4,
            R.id.zone_5, R.id.zone_6, R.id.zone_7, R.id.zone_8, R.id.zone_9,
            R.id.zone_10, R.id.zone_11, R.id.zone_12, R.id.zone_13, R.id.zone_14,
            R.id.zone_15, R.id.zone_16, R.id.zone_17, R.id.zone_18, R.id.zone_19
        )

        for (i in zoneIds.indices) {
            val fraction = (i + 0.5f) / 20.0f
            val seekIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SEEK_TO
                putExtra(PlaybackService.EXTRA_SEEK_FRACTION, fraction)
            }
            val seekPendingIntent = PendingIntent.getService(
                context,
                200 + i,
                seekIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(zoneIds[i], seekPendingIntent)
        }
    }

    fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
