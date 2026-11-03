package com.nodeplayer.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import com.nodeplayer.library.LrcParser
import com.nodeplayer.model.Lyrics
import com.nodeplayer.model.Track
import com.nodeplayer.model.WidgetAccentMode
import com.nodeplayer.model.WidgetSettings
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.R
import com.nodeplayer.music.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val Track.artworkUri: Uri
    get() = Uri.parse("content://media/external/audio/albumart/$albumId")

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

object WidgetUpdateManager : WidgetFeatureApi {

    private const val TAG = "WidgetUpdateManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun saveLastKnownTrack(context: Context, track: Track) {
        try {
            val prefs = context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_track_id", track.id)
                .putString("last_track_title", track.title)
                .putString("last_track_artist", track.artist)
                .putString("last_track_album", track.album)
                .putLong("last_track_album_id", track.albumId)
                .putString("last_track_path", track.path)
                .putLong("last_track_duration", track.durationMs)
                .apply()
        } catch (_: Exception) {}
    }

    override fun getLastKnownTrack(context: Context): Track? {
        return try {
            val prefs = context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
            val id = prefs.getLong("last_track_id", -1L)
            if (id != -1L) {
                Track(
                    id = id,
                    title = prefs.getString("last_track_title", "Node Player") ?: "Node Player",
                    artist = prefs.getString("last_track_artist", "") ?: "",
                    album = prefs.getString("last_track_album", "") ?: "",
                    albumId = prefs.getLong("last_track_album_id", 0L),
                    durationMs = prefs.getLong("last_track_duration", 0L),
                    path = prefs.getString("last_track_path", "") ?: ""
                )
            } else null
        } catch (_: Exception) { null }
    }

    override fun updateAllWidgets(context: Context) {
        scope.launch {
            try {
                // 1. Read ExoPlayer state on Main Thread
                val (activeTrack, isPlaying, currentPositionMs, durationMs) = withContext(Dispatchers.Main) {
                    val service = PlaybackService.instance
                    val track = service?.crossfadeManager?.currentPlayingTrack
                    val playing = service?.player?.isPlaying == true
                    val pos = service?.player?.currentPosition ?: 0L
                    val playerDur = service?.player?.duration ?: 0L
                    val trackDur = track?.durationMs ?: 0L
                    val dur = if (playerDur > 0L) playerDur else (if (trackDur > 0L) trackDur else 1L)
                    Tuple4(track, playing, pos, dur)
                }

                if (activeTrack != null) {
                    saveLastKnownTrack(context, activeTrack)
                }

                val currentTrack = activeTrack ?: getLastKnownTrack(context) ?: withContext(Dispatchers.IO) {
                    try {
                        val db = (context.applicationContext as? NodePlayerApplication)?.database
                        db?.playerDao()?.getAllTracksList()?.firstOrNull()?.toTrack()
                    } catch (_: Exception) { null }
                }

                val settings = WidgetPreferences.getSettings(context)

                // Load artwork thumbnail for Palette and Widget ImageView
                val artworkBitmap = loadArtwork(context, currentTrack)
                val colors = resolveColors(settings, artworkBitmap)

                val appWidgetManager = AppWidgetManager.getInstance(context)

                // Update Variant 1: Card 4x2
                updateCard4x2(context, appWidgetManager, currentTrack, isPlaying, currentPositionMs, durationMs, settings, colors, artworkBitmap)

                // Update Variant 2: Lyrics Card 4x3
                updateLyrics4x3(context, appWidgetManager, currentTrack, isPlaying, currentPositionMs, durationMs, settings, colors, artworkBitmap)

                // Update Variant 3: Slim 4x1
                updateSlim4x1(context, appWidgetManager, currentTrack, isPlaying, currentPositionMs, durationMs, settings, colors, artworkBitmap)

                // Update Variant 4: Square 2x2
                updateSquare2x2(context, appWidgetManager, currentTrack, isPlaying, currentPositionMs, durationMs, settings, colors, artworkBitmap)

                // Legacy alias widget
                updateLegacyWidget(context, appWidgetManager, currentTrack, isPlaying, currentPositionMs, durationMs, settings, colors, artworkBitmap)

                // Update live floating overlay if active
                if (FloatingOverlayService.isRunning) {
                    withContext(Dispatchers.Main) {
                        FloatingOverlayService.instance?.updateOverlayContent()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating widgets", e)
            }
        }
    }

    private fun resolveColors(settings: WidgetSettings, artwork: Bitmap?): Triple<FloatArray, FloatArray, FloatArray> {
        return when (settings.refractionStyle) {
            "LIQUID_OBSIDIAN" -> Triple(
                floatArrayOf(0.95f, 0.98f, 1.0f),  // Crystal pearl highlight
                floatArrayOf(0.20f, 0.85f, 1.0f),  // Cyan specular rim
                floatArrayOf(0.04f, 0.05f, 0.07f)   // Deep liquid obsidian
            )
            "FROST_PEARL" -> Triple(
                floatArrayOf(1.0f, 1.0f, 1.0f),
                floatArrayOf(0.85f, 0.92f, 1.0f),
                floatArrayOf(0.12f, 0.14f, 0.18f)
            )
            "PRISMATIC_CLEAR" -> Triple(
                floatArrayOf(1.0f, 0.60f, 0.20f),
                floatArrayOf(0.10f, 0.90f, 1.0f),
                floatArrayOf(0.03f, 0.03f, 0.05f)
            )
            "DYNAMIC_ART" -> WidgetShaderRenderer.extractColors(artwork)
            else -> when (settings.accentMode) {
                WidgetAccentMode.DYNAMIC_PALETTE -> WidgetShaderRenderer.extractColors(artwork)
                WidgetAccentMode.WARM_AMBER_COOL_CYAN -> Triple(
                    floatArrayOf(1.0f, 0.65f, 0.12f),
                    floatArrayOf(0.12f, 0.78f, 0.98f),
                    floatArrayOf(0.06f, 0.07f, 0.09f)
                )
                WidgetAccentMode.ELECTRIC_VIOLET -> Triple(
                    floatArrayOf(0.80f, 0.15f, 1.0f),
                    floatArrayOf(0.0f, 0.90f, 1.0f),
                    floatArrayOf(0.05f, 0.05f, 0.08f)
                )
                WidgetAccentMode.MONOCHROME_GLASS -> Triple(
                    floatArrayOf(0.95f, 0.98f, 1.0f),
                    floatArrayOf(0.45f, 0.50f, 0.55f),
                    floatArrayOf(0.03f, 0.03f, 0.04f)
                )
            }
        }
    }

    private fun updateCard4x2(
        context: Context,
        appWidgetManager: AppWidgetManager,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        settings: WidgetSettings,
        colors: Triple<FloatArray, FloatArray, FloatArray>,
        artwork: Bitmap?
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetCard4x2Provider::class.java))
        if (ids.isEmpty()) return

        val scale = if (settings.highDpi) 2 else 1
        val widthPx = 540 * scale
        val heightPx = 280 * scale
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f * scale,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode,
            edgeHighlightStrength = settings.edgeHighlightStrength,
            refractionStyle = settings.refractionStyle
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_card_4x2)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            WidgetRemoteViewsHelper.bindControls(context, views, isPlaying)
            WidgetRemoteViewsHelper.bindProgressAndChronometer(views, isPlaying, positionMs, durationMs)
            WidgetRemoteViewsHelper.bindTapToSeekZones(context, views)
            WidgetRemoteViewsHelper.applyElementVisibilities(views, settings, isCard = true)

            // Open Scrub Sheet
            val scrubIntent = Intent(context, ScrubSheetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val scrubPendingIntent = PendingIntent.getActivity(
                context,
                100,
                scrubIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_scrub, scrubPendingIntent)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updateLyrics4x3(
        context: Context,
        appWidgetManager: AppWidgetManager,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        settings: WidgetSettings,
        colors: Triple<FloatArray, FloatArray, FloatArray>,
        artwork: Bitmap?
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetLyrics4x3Provider::class.java))
        if (ids.isEmpty()) return

        val scale = if (settings.highDpi) 2 else 1
        val widthPx = 540 * scale
        val heightPx = 380 * scale
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f * scale,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode,
            edgeHighlightStrength = settings.edgeHighlightStrength,
            refractionStyle = settings.refractionStyle
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        val lyrics = loadLyrics(track)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics_4x3)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            WidgetRemoteViewsHelper.bindControls(context, views, isPlaying)
            WidgetRemoteViewsHelper.applyElementVisibilities(views, settings, isLyrics = true)

            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_lyrics_empty, View.GONE)
                views.setViewVisibility(R.id.widget_lyric_prev, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lyric_current, View.VISIBLE)
                views.setViewVisibility(R.id.widget_lyric_next, View.VISIBLE)

                // Find active line matching positionMs
                val activeIdx = lyrics.lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
                val prevLine = if (activeIdx > 0) lyrics.lines[activeIdx - 1].text else ""
                val currentLine = lyrics.lines[activeIdx].text
                val nextLine = if (activeIdx + 1 < lyrics.lines.size) lyrics.lines[activeIdx + 1].text else ""

                views.setTextViewText(R.id.widget_lyric_prev, prevLine)
                views.setTextViewText(R.id.widget_lyric_current, currentLine)
                views.setTextViewText(R.id.widget_lyric_next, nextLine)
            } else {
                views.setViewVisibility(R.id.widget_lyric_prev, View.GONE)
                views.setViewVisibility(R.id.widget_lyric_current, View.GONE)
                views.setViewVisibility(R.id.widget_lyric_next, View.GONE)
                views.setViewVisibility(R.id.widget_lyrics_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_lyrics_empty, "No synced lyrics available")
            }

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updateSlim4x1(
        context: Context,
        appWidgetManager: AppWidgetManager,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        settings: WidgetSettings,
        colors: Triple<FloatArray, FloatArray, FloatArray>,
        artwork: Bitmap?
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetSlim4x1Provider::class.java))
        if (ids.isEmpty()) return

        val scale = if (settings.highDpi) 2 else 1
        val widthPx = 540 * scale
        val heightPx = 140 * scale
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.0f * scale,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode,
            edgeHighlightStrength = settings.edgeHighlightStrength,
            refractionStyle = settings.refractionStyle
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_slim_4x1)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            WidgetRemoteViewsHelper.bindControls(context, views, isPlaying)
            WidgetRemoteViewsHelper.applyElementVisibilities(views, settings, isSlim = true)

            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updateSquare2x2(
        context: Context,
        appWidgetManager: AppWidgetManager,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        settings: WidgetSettings,
        colors: Triple<FloatArray, FloatArray, FloatArray>,
        artwork: Bitmap?
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetSquare2x2Provider::class.java))
        if (ids.isEmpty()) return

        val scale = if (settings.highDpi) 2 else 1
        val widthPx = 280 * scale
        val heightPx = 280 * scale
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f * scale,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode,
            edgeHighlightStrength = settings.edgeHighlightStrength,
            refractionStyle = settings.refractionStyle
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_square_2x2)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            WidgetRemoteViewsHelper.applyElementVisibilities(views, settings, isSquare = true)

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

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updateLegacyWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        settings: WidgetSettings,
        colors: Triple<FloatArray, FloatArray, FloatArray>,
        artwork: Bitmap?
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, NodePlayerWidget::class.java))
        if (ids.isEmpty()) return

        for (id in ids) {
            NodePlayerWidget.updateWidget(context, track, isPlaying)
        }
    }

    private fun bindCommonTrackInfo(
        context: Context,
        views: RemoteViews,
        track: Track?,
        artwork: Bitmap?
    ) {
        if (track != null) {
            views.setTextViewText(R.id.widget_title, track.title)
            views.setTextViewText(R.id.widget_artist, track.artist)
        } else {
            views.setTextViewText(R.id.widget_title, "Node Player")
            views.setTextViewText(R.id.widget_artist, "Touch to play")
        }

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, WidgetArtworkLoader.getRoundedCornerBitmap(artwork, 24f))
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_music_note)
        }

        // Tapping root opens Now Playing / Main Activity
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
    }

    fun loadArtwork(context: Context, track: Track?): Bitmap? = WidgetArtworkLoader.loadArtwork(context, track)

    private fun loadLyrics(track: Track?): Lyrics? {
        if (track == null) return null
        return try {
            val lrcFile = File(track.path.substringBeforeLast(".") + ".lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                LrcParser.parse(lrcFile.readText(), track.id, "local_lrc")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
