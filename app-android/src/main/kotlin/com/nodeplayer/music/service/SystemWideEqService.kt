package com.nodeplayer.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.EqBandConfig
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * System-Wide Audio Effects Service.
 *
 * Implements:
 * - Dynamic session interception via AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
 * - Hardware/Platform DSP DynamicsProcessing (API 28+: 10-band Pre-EQ + Soft Limiter + Preamp)
 * - Automatic session release on ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
 * - Persistent notification with quick toggle action and active session telemetry
 * - Synchronization with in-app EQ curve and presets
 * - Clean bypass of internal biquad EQ when active to prevent double-EQ processing
 */
class SystemWideEqService : Service() {

    data class SessionRecord(
        val sessionId: Int,
        val packageName: String?,
        val effect: DynamicsProcessing,
        val attachedTimeMs: Long = System.currentTimeMillis()
    )

    data class SessionInfo(
        val sessionId: Int,
        val packageName: String,
        val attachedTimeMs: Long
    )

    private val activeSessions = ConcurrentHashMap<Int, SessionRecord>()
    private var internalReceiver: BroadcastReceiver? = null

    companion object {
        const val TAG = "SystemWideEqService"
        const val CHANNEL_ID = "node_player_system_eq_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.nodeplayer.music.action.START_SYSTEM_EQ"
        const val ACTION_STOP = "com.nodeplayer.music.action.STOP_SYSTEM_EQ"
        const val ACTION_UPDATE_CONFIG = "com.nodeplayer.music.action.UPDATE_EQ_CONFIG"

        @Volatile
        var isRunning: Boolean = false
            private set

        var instance: SystemWideEqService? = null
            private set

        private val _activeSessionsState = MutableStateFlow<List<SessionInfo>>(emptyList())
        val activeSessionsState: StateFlow<List<SessionInfo>> = _activeSessionsState.asStateFlow()

        var currentConfig: AudioEffectConfig = AudioEffectConfig()
            private set

        var onStateChanged: ((Boolean) -> Unit)? = null

        fun start(context: Context, initialConfig: AudioEffectConfig? = null) {
            if (initialConfig != null) {
                currentConfig = initialConfig
            }
            val intent = Intent(context, SystemWideEqService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SystemWideEqService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateConfig(config: AudioEffectConfig) {
            currentConfig = config
            instance?.updateAllEffects(config)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        Log.i(TAG, "SystemWideEqService created and initialized")

        createNotificationChannel()
        registerSessionReceiver()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        onStateChanged?.invoke(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received STOP action -> stopping SystemWideEqService")
                stopSelf()
                return START_NOT_STICKY
            }
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                val pkgName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Unknown"
                if (sessionId > 0) {
                    attachSession(sessionId, pkgName)
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                if (sessionId > 0) {
                    releaseSession(sessionId)
                }
            }
            ACTION_UPDATE_CONFIG -> {
                updateAllEffects(currentConfig)
            }
        }

        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSessionReceiver() {
        internalReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val action = intent.action ?: return
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Unknown App"

                Log.d(TAG, "Broadcast received: $action | session=$sessionId | package=$packageName")

                if (sessionId <= 0) return

                when (action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                        attachSession(sessionId, packageName)
                    }
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                        releaseSession(sessionId)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            priority = 1000
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(internalReceiver, filter)
        }
        Log.i(TAG, "Registered dynamic BroadcastReceiver for audio session events")
    }

    fun attachSession(sessionId: Int, packageName: String?) {
        if (sessionId <= 0) return
        if (activeSessions.containsKey(sessionId)) {
            Log.d(TAG, "Session $sessionId ($packageName) already attached")
            return
        }

        try {
            val dpConfig = buildDynamicsConfig(currentConfig)
            val effect = DynamicsProcessing(100, sessionId, dpConfig)
            effect.enabled = true

            val record = SessionRecord(
                sessionId = sessionId,
                packageName = packageName,
                effect = effect
            )
            activeSessions[sessionId] = record

            Log.i(TAG, "Successfully attached DynamicsProcessing to session $sessionId (package: $packageName, active: ${activeSessions.size})")
            updateSessionState()
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach DynamicsProcessing to session $sessionId ($packageName)", e)
        }
    }

    fun releaseSession(sessionId: Int) {
        val record = activeSessions.remove(sessionId)
        if (record != null) {
            try {
                record.effect.enabled = false
                record.effect.release()
                Log.i(TAG, "Released DynamicsProcessing for session $sessionId (remaining: ${activeSessions.size})")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing effect for session $sessionId: ${e.message}")
            }
            updateSessionState()
            updateNotification()
        }
    }

    private fun updateSessionState() {
        _activeSessionsState.value = activeSessions.values.map {
            SessionInfo(
                sessionId = it.sessionId,
                packageName = it.packageName ?: "Unknown",
                attachedTimeMs = it.attachedTimeMs
            )
        }
    }

    fun updateAllEffects(config: AudioEffectConfig) {
        for (record in activeSessions.values) {
            try {
                applyConfigToEffect(record.effect, config)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update effect parameters for session ${record.sessionId}", e)
            }
        }
        updateNotification()
    }

    private fun applyConfigToEffect(effect: DynamicsProcessing, config: AudioEffectConfig) {
        // Preamp gain
        effect.setInputGainAllChannelsTo(config.preampGainDb)

        // Pre-EQ 10 bands
        val preEq = effect.getPreEqByChannelIndex(0)
        preEq.isEnabled = config.eqEnabled
        for (i in 0 until 10) {
            val bandCfg = config.bands.getOrNull(i) ?: EqBandConfig(i, 1000f, 0f)
            val gain = if (config.eqEnabled) bandCfg.gainDb else 0.0f
            preEq.setBand(i, DynamicsProcessing.EqBand(true, bandCfg.centerFreq, gain))
        }
        effect.setPreEqAllChannelsTo(preEq)

        // Limiter
        val limiter = effect.getLimiterByChannelIndex(0)
        limiter.isEnabled = config.limiterEnabled
        limiter.threshold = config.limiterThresholdDb
        effect.setLimiterAllChannelsTo(limiter)
    }

    private fun buildDynamicsConfig(config: AudioEffectConfig): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // Stereo 2 channels
            true, // Pre-EQ enabled
            10, // 10 bands
            false, // MBC off
            0,
            false, // Post-EQ off
            0,
            true // Limiter on
        )

        // Input Gain (preamp)
        builder.setInputGainAllChannelsTo(config.preampGainDb)

        // 10-band Pre-EQ
        val preEq = DynamicsProcessing.Eq(true, config.eqEnabled, 10)
        for (i in 0 until 10) {
            val band = config.bands.getOrNull(i) ?: EqBandConfig(i, 1000f, 0f)
            val gain = if (config.eqEnabled) band.gainDb else 0.0f
            preEq.setBand(i, DynamicsProcessing.EqBand(true, band.centerFreq, gain))
        }
        builder.setPreEqAllChannelsTo(preEq)

        // Soft limiter (-0.1 dBFS soft knee, 1ms attack, 60ms release)
        val limiter = DynamicsProcessing.Limiter(
            true, // inUse
            config.limiterEnabled, // enabled
            0, // linkGroup
            1.0f, // attackTime ms
            60.0f, // releaseTime ms
            10.0f, // ratio
            config.limiterThresholdDb, // threshold
            0.0f // postGain
        )
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System-Wide Equalizer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of global DynamicsProcessing audio sessions"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SystemWideEqService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCount = activeSessions.size
        val presetText = currentConfig.presetName
        val contentText = if (sessionCount > 0) {
            "Active on $sessionCount session(s) • Preset: $presetText"
        } else {
            "Standby: Waiting for media apps • Preset: $presetText"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System-Wide Equalizer Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_eq_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_eq_notification, "Turn Off", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        Log.i(TAG, "SystemWideEqService destroying: releasing all ${activeSessions.size} effects")
        isRunning = false
        instance = null

        // Unregister receiver
        internalReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            internalReceiver = null
        }

        // Release all DynamicsProcessing instances
        for ((sessionId, record) in activeSessions) {
            try {
                record.effect.enabled = false
                record.effect.release()
                Log.d(TAG, "Cleanly released session $sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing session $sessionId: ${e.message}")
            }
        }
        activeSessions.clear()
        updateSessionState()

        onStateChanged?.invoke(false)
        super.onDestroy()
    }
}
