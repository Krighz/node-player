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

object WidgetUpdateManager {

    private const val TAG = "WidgetUpdateManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun saveLastKnownTrack(context: Context, track: Track) {
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

    fun getLastKnownTrack(context: Context): Track? {
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

    fun updateAllWidgets(context: Context) {
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
        return when (settings.accentMode) {
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
                floatArrayOf(0.50f, 0.55f, 0.60f),
                floatArrayOf(0.35f, 0.40f, 0.45f),
                floatArrayOf(0.03f, 0.03f, 0.04f)
            )
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

        val widthPx = 540
        val heightPx = 280
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_card_4x2)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            bindControls(context, views, isPlaying)
            bindProgressAndChronometer(views, isPlaying, positionMs, durationMs)
            bindTapToSeekZones(context, views)
            applyElementVisibilities(views, settings, isCard = true)

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

        val widthPx = 540
        val heightPx = 380
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        val lyrics = loadLyrics(track)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics_4x3)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            bindControls(context, views, isPlaying)
            applyElementVisibilities(views, settings, isSlim = true)

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

        val widthPx = 540
        val heightPx = 140
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.0f,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_slim_4x1)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            bindControls(context, views, isPlaying)
            applyElementVisibilities(views, settings, isSlim = true)

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

        val widthPx = 280
        val heightPx = 280
        val config = WidgetShaderConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = settings.cornerRadiusDp * 2.5f,
            isPlaying = isPlaying,
            phase = if (isPlaying) (SystemClock.elapsedRealtime() % 10000) / 1000f else 0f,
            glassOpacity = settings.glassOpacity,
            warmColor = colors.first,
            coolColor = colors.second,
            baseColor = colors.third,
            isPerformanceMode = settings.isPerformanceMode
        )
        val glassBitmap = WidgetShaderRenderer.renderGlassBackground(config)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_square_2x2)
            views.setImageViewBitmap(R.id.widget_bg, glassBitmap)

            bindCommonTrackInfo(context, views, track, artwork)
            applyElementVisibilities(views, settings, isSquare = true)

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
            views.setImageViewBitmap(R.id.widget_artwork, getRoundedCornerBitmap(artwork, 24f))
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

    private fun applyElementVisibilities(
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

    private fun bindControls(context: Context, views: RemoteViews, isPlaying: Boolean) {
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

    private fun bindProgressAndChronometer(
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

    private fun bindTapToSeekZones(context: Context, views: RemoteViews) {
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

    fun loadArtwork(context: Context, track: Track?): Bitmap? {
        if (track == null) return null

        // 1. Android 10+ MediaStore thumbnail by track ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.id > 0) {
            try {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
                val thumb = context.contentResolver.loadThumbnail(trackUri, Size(512, 512), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        // 2. Embedded picture via MediaMetadataRetriever from file path
        if (track.path.isNotBlank()) {
            val file = File(track.path)
            if (file.exists() && file.canRead()) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val pic = mmr.embeddedPicture
                    if (pic != null) {
                        val thumb = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        if (thumb != null) return thumb
                    }
                } catch (_: Exception) {
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            }
        }

        // 3. MediaStore album art URI
        if (track.albumId > 0) {
            try {
                val uri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val thumb = BitmapFactory.decodeStream(stream)
                    if (thumb != null) return thumb
                }
            } catch (_: Exception) {}
        }

        // 4. Android 10+ MediaStore thumbnail by album ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.albumId > 0) {
            try {
                val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, track.albumId)
                val thumb = context.contentResolver.loadThumbnail(albumUri, Size(512, 512), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        // 5. Procedural elegant fallback artwork (so the widget artwork box is never empty)
        return generateFallbackArtwork(track.title)
    }

    private fun generateFallbackArtwork(seed: String): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val hash = seed.hashCode()
        val c1 = 0xFF000000.toInt() or (hash and 0x00FFFFFF)
        val c2 = 0xFF1E293B.toInt()

        val shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            c1, c2, android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Draw clean music symbol / initial
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 180
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        val letter = seed.take(1).uppercase()
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(if (letter.isNotBlank()) letter else "♪", size / 2f, yPos, paint)

        return bitmap
    }

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

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
