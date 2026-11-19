package com.nodeplayer.music.mixtape

import android.content.Context
import androidx.work.*
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.NodePlayerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SmartMixtapeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as NodePlayerApplication
        val mixtapeManager = app.smartMixtapeManager
        
        NodeLog.i(TAG, "Executing scheduled Smart Offline Mixtape synchronization...")

        val usableSpace = app.filesDir.usableSpace
        if (usableSpace < 250L * 1024L * 1024L) {
            NodeLog.w(TAG, "Device storage below 250MB threshold. Aborting sync.")
            return@withContext Result.failure()
        }

        try {
            mixtapeManager.refreshMemoryIndex()
            Result.success()
        } catch (e: Exception) {
            NodeLog.e(TAG, "Mixtape sync encountered failure: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SmartMixtapeWorker"
        private const val UNIQUE_WORK_NAME = "NodePlayerSmartMixtapeSync"

        fun schedule(context: Context, requireWifi: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SmartMixtapeWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun syncNow(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<SmartMixtapeWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${UNIQUE_WORK_NAME}_OneTime",
                ExistingWorkPolicy.KEEP,
                oneTimeRequest
            )
        }
    }
}
