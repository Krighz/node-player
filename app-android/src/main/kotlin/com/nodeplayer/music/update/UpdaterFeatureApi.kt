package com.nodeplayer.music.update

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Public feature contract for app update checking, downloading and installation.
 */
interface UpdaterFeatureApi {
    val availableUpdate: StateFlow<AppUpdateInfo?>
    val downloadStatus: StateFlow<DownloadStatus>
    val isChecking: StateFlow<Boolean>

    fun setAvailableUpdate(info: AppUpdateInfo?)
    fun clearCachedUpdate(context: Context)
    fun setDownloadStatus(status: DownloadStatus)
    fun checkForUpdates(context: Context, isManual: Boolean = false)
    fun startDownload(context: Context, updateInfo: AppUpdateInfo)
    fun installUpdate(context: Context, apkFile: File)
}
