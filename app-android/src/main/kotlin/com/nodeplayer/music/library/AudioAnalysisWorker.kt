package com.nodeplayer.music.library

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AudioAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AudioAnalysisWorker"

        fun schedule(context: Context, chargingOnly: Boolean = false) {
            AudioAnalysisManager.schedule(context, chargingOnly)
        }

        fun cancel(context: Context) {
            AudioAnalysisManager.cancel(context)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val context = applicationContext
        val db = NodePlayerApplication.instance.database
        val dao = db.playerDao()

        val sharedPrefs = context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("audio_analysis_enabled", true)
        if (!isEnabled) {
            AudioAnalysisManager.setRunning(false)
            return@withContext Result.success()
        }

        val speed = sharedPrefs.getString("analysis_speed", "Slow") ?: "Slow"
        val chargingOnly = sharedPrefs.getBoolean("analysis_charging_only", false)

        if (chargingOnly) {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            if (!isCharging) {
                Log.d(TAG, "Audio analysis deferred: device not charging")
                return@withContext Result.retry()
            }
        }

        val tracksToAnalyze = dao.getTracksNeedingAnalysis()
        if (tracksToAnalyze.isEmpty()) {
            AudioAnalysisManager.setRunning(false)
            return@withContext Result.success()
        }

        AudioAnalysisManager.setRunning(true)
        val total = tracksToAnalyze.size

        try {
            for ((index, trackEntity) in tracksToAnalyze.withIndex()) {
                if (isStopped) {
                    AudioAnalysisManager.cancelNotification(context)
                    AudioAnalysisManager.setRunning(false)
                    return@withContext Result.retry()
                }

                // Check paused state
                while (AudioAnalysisManager.isPaused.value && !isStopped) {
                    delay(500)
                }

                // Auto-pause / throttle while music is actively playing
                while (PlaybackService.isCurrentlyPlaying && !isStopped) {
                    delay(1000)
                }

                // Auto-pause while user is heavily scrolling
                while (AudioAnalysisManager.isUserScrolling.get() && !isStopped) {
                    delay(500)
                }

                val current = index + 1
                AudioAnalysisManager.setProgress(current, total, trackEntity.title)
                AudioAnalysisManager.updateNotification(context, current, total, trackEntity.title, paused = false)

                // Pure analysis in background: primitive buffer, no boxing, handles all errors
                val track = trackEntity.toTrack()
                AudioAnalysisManager.analyzeSingleTrackDirect(track, dao)

                // Trickle delay between songs so execution stays gentle on CPU and battery
                val delayMs = when (speed) {
                    "Fast" -> 50L
                    "Normal" -> 500L
                    else -> 1500L // Slow default
                }
                delay(delayMs)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Worker execution interrupted: ${t.message}", t)
        } finally {
            AudioAnalysisManager.cancelNotification(context)
            AudioAnalysisManager.setRunning(false)
        }

        Result.success()
    }
}
