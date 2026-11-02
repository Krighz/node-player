package com.nodeplayer.music.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UpdateWorker"
        private const val UNIQUE_WORK_NAME = "NodePlayerDailyUpdateCheck"

        fun scheduleOrCancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!UpdatePreferences.isCheckAutomatically(context)) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.d(TAG, "Update worker cancelled (automatic updates disabled)")
                return
            }

            val wifiOnly = UpdatePreferences.isWifiOnly(context)
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled daily update check (wifiOnly=$wifiOnly)")
        }
    }

    override suspend fun doWork(): Result {
        if (!UpdatePreferences.isCheckAutomatically(appContext)) {
            return Result.success()
        }

        // Throttle check: at most once every 12 hours
        val lastChecked = UpdatePreferences.getLastCheckedTimestamp(appContext)
        val elapsed = System.currentTimeMillis() - lastChecked
        if (elapsed < TimeUnit.HOURS.toMillis(12)) {
            Log.d(TAG, "Skipping worker check, checked ${elapsed / 1000 / 60}m ago")
            return Result.success()
        }

        Log.d(TAG, "Executing background update check...")
        when (val result = UpdateChecker.checkForUpdate(appContext)) {
            is UpdateCheckResult.UpdateAvailable -> {
                val info = result.info
                if (!UpdatePreferences.isVersionSkipped(appContext, info.versionName)) {
                    val json = JSONObject().apply {
                        put("versionName", info.versionName)
                        put("releaseTag", info.releaseTag)
                        put("downloadUrl", info.downloadUrl)
                        put("sha256Url", info.sha256Url)
                        put("releaseNotes", info.releaseNotes)
                        put("assetSize", info.assetSize)
                        put("publishedAt", info.publishedAt)
                    }.toString()
                    UpdatePreferences.setCachedUpdate(appContext, json)
                    Log.i(TAG, "Cached new update: ${info.versionName}")
                }
            }
            is UpdateCheckResult.UpToDate -> {
                UpdatePreferences.setCachedUpdate(appContext, null)
                Log.d(TAG, "App is up to date.")
            }
            else -> {
                // Handled quietly
            }
        }

        return Result.success()
    }
}
