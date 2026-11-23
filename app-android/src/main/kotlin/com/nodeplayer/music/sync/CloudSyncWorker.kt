package com.nodeplayer.music.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nodeplayer.common.logging.NodeLog

/**
 * Background WorkManager worker for scheduled periodic cloud sync.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NodeLog.i(TAG, "Scheduled cloud sync worker triggered")

        val syncResult = CloudSyncManager.performSync(applicationContext, isManualTrigger = false)

        return if (syncResult.isSuccess) {
            NodeLog.i(TAG, "Scheduled cloud sync completed successfully")
            Result.success()
        } else {
            val error = syncResult.exceptionOrNull()
            NodeLog.w(TAG, "Scheduled cloud sync failed: ${error?.message}")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "NodePlayerCloudSyncWork"
        private const val TAG = "CloudSyncWorker"
    }
}
