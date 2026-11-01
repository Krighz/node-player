package com.nodeplayer.music.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import coil.load
import coil.transform.RoundedCornersTransformation
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import com.nodeplayer.music.service.PlaybackService
import com.nodeplayer.music.widget.artworkUri

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        const val CHANNEL_ID = "node_player_overlay_channel"
        const val NOTIFICATION_ID = 4040
        const val ACTION_STOP_OVERLAY = "com.nodeplayer.music.action.STOP_OVERLAY"

        var isRunning = false
            private set
        var instance: FloatingOverlayService? = null
            private set

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        setupOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateOverlayContent()
        return START_STICKY
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 240
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_floating_player, null)
        overlayView = view

        val dragArea = view.findViewById<View>(R.id.overlay_drag_area) ?: view
        setupDragListener(dragArea, params!!)
        setupControls(view)

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            stopSelf()
            return
        }

        updateOverlayContent()
    }

    private fun setupDragListener(view: View, layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    overlayView?.let { windowManager?.updateViewLayout(it, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Open app on click
                        val appIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(appIntent)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupControls(view: View) {
        val btnPlayPause = view.findViewById<ImageView>(R.id.overlay_btn_play_pause)
        val btnClose = view.findViewById<ImageView>(R.id.overlay_btn_close)

        btnPlayPause?.setOnClickListener {
            val service = PlaybackService.instance
            if (service != null) {
                service.crossfadeManager.togglePlayPause()
                updateOverlayContent()
                WidgetUpdateManager.updateAllWidgets(this)
            }
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    fun updateOverlayContent() {
        val view = overlayView ?: return
        val service = PlaybackService.instance
        val track = service?.crossfadeManager?.currentPlayingTrack
        val isPlaying = service?.player?.isPlaying == true

        val tvTitle = view.findViewById<TextView>(R.id.overlay_title)
        val tvArtist = view.findViewById<TextView>(R.id.overlay_artist)
        val ivArt = view.findViewById<ImageView>(R.id.overlay_artwork)
        val btnPlayPause = view.findViewById<ImageView>(R.id.overlay_btn_play_pause)

        tvTitle?.text = track?.title ?: "Node Player"
        tvArtist?.text = track?.artist ?: "Touch to play"

        if (track?.artworkUri != null) {
            ivArt?.load(track.artworkUri) {
                transformations(RoundedCornersTransformation(16f))
            }
        } else {
            ivArt?.setImageResource(R.drawable.ic_music_note)
        }

        btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Node Player Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live draggable floating mini player"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            99,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Liquid Glass Mini Player")
            .setContentText("Tap close to dismiss floating overlay")
            .setSmallIcon(R.drawable.ic_music_note)
            .addAction(R.drawable.ic_pause, "Close Overlay", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }
}
