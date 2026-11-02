package com.nodeplayer.music.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val TAG = "UpdateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    fun setAvailableUpdate(info: AppUpdateInfo?) {
        _availableUpdate.value = info
    }

    fun clearCachedUpdate(context: Context) {
        UpdatePreferences.setCachedUpdate(context, null)
        _availableUpdate.value = null
    }

    val downloadStatus: StateFlow<DownloadStatus> = UpdateDownloadManager.downloadStatus

    fun setDownloadStatus(status: DownloadStatus) {
        UpdateDownloadManager.setDownloadStatus(status)
    }

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _showOptInDialog = MutableStateFlow(false)
    val showOptInDialog: StateFlow<Boolean> = _showOptInDialog.asStateFlow()

    private var dismissedForSession = false

    fun init(context: Context) {
        if (!UpdatePreferences.hasPromptedOptIn(context)) {
            _showOptInDialog.value = true
        } else {
            UpdateWorker.scheduleOrCancel(context)
            restoreCachedUpdate(context)
            checkOnForeground(context)
        }
    }

    fun onOptInChoice(context: Context, agreed: Boolean) {
        UpdatePreferences.setOptInChoice(context, agreed)
        _showOptInDialog.value = false
        UpdateWorker.scheduleOrCancel(context)
        if (agreed) {
            triggerCheck(context, isManual = false)
        }
    }

    fun checkOnForeground(context: Context) {
        if (dismissedForSession) return
        if (!UpdatePreferences.isCheckAutomatically(context)) return

        val lastChecked = UpdatePreferences.getLastCheckedTimestamp(context)
        val elapsed = System.currentTimeMillis() - lastChecked
        if (elapsed < TimeUnit.HOURS.toMillis(12)) {
            restoreCachedUpdate(context)
            return
        }

        triggerCheck(context, isManual = false)
    }

    fun restoreCachedUpdate(context: Context) {
        if (dismissedForSession) return
        val cachedJson = UpdatePreferences.getCachedUpdate(context) ?: return
        try {
            val json = JSONObject(cachedJson)
            val versionName = json.optString("versionName", "")
            if (versionName.isNotBlank() && !UpdatePreferences.isVersionSkipped(context, versionName)) {
                val currentVersion = UpdateChecker.getInstalledVersionName(context)
                if (SemVer.isNewer(versionName, currentVersion)) {
                    _availableUpdate.value = AppUpdateInfo(
                        versionName = versionName,
                        releaseTag = json.optString("releaseTag", "v$versionName"),
                        downloadUrl = json.optString("downloadUrl", ""),
                        sha256Url = json.optString("sha256Url", ""),
                        releaseNotes = json.optString("releaseNotes", ""),
                        assetSize = json.optLong("assetSize", 0L),
                        publishedAt = json.optString("publishedAt", "")
                    )
                } else {
                    UpdatePreferences.setCachedUpdate(context, null)
                }
            }
        } catch (_: Exception) {}
    }

    fun triggerCheck(
        context: Context,
        isManual: Boolean,
        onResult: ((UpdateCheckResult) -> Unit)? = null
    ) {
        if (_isChecking.value) return
        _isChecking.value = true

        scope.launch {
            try {
                val result = UpdateChecker.checkForUpdate(context, ignoreNetworkRestrictions = isManual)
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        val info = result.info
                        val json = JSONObject().apply {
                            put("versionName", info.versionName)
                            put("releaseTag", info.releaseTag)
                            put("downloadUrl", info.downloadUrl)
                            put("sha256Url", info.sha256Url)
                            put("releaseNotes", info.releaseNotes)
                            put("assetSize", info.assetSize)
                            put("publishedAt", info.publishedAt)
                        }.toString()
                        UpdatePreferences.setCachedUpdate(context, json)

                        if (!UpdatePreferences.isVersionSkipped(context, info.versionName) && !dismissedForSession) {
                            _availableUpdate.value = info
                        }
                    }
                    is UpdateCheckResult.UpToDate -> {
                        UpdatePreferences.setCachedUpdate(context, null)
                        _availableUpdate.value = null
                    }
                    else -> {
                        // Keep cached update if offline or rate limited
                    }
                }
                onResult?.invoke(result)
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun skipVersion(context: Context, version: String) {
        UpdatePreferences.skipVersion(context, version)
        _availableUpdate.value = null
    }

    fun dismissForSession() {
        dismissedForSession = true
        _availableUpdate.value = null
    }

    fun unskipVersion(context: Context, version: String) {
        UpdatePreferences.unskipVersion(context, version)
        restoreCachedUpdate(context)
    }

    fun startDownload(context: Context, info: AppUpdateInfo) {
        UpdateDownloadManager.startDownload(context, info)
    }

    fun cancelDownload(context: Context) {
        UpdateDownloadManager.cancelDownload(context)
    }

    fun clearAllSkipped(context: Context) {
        UpdatePreferences.clearSkippedVersions(context)
        restoreCachedUpdate(context)
    }

    fun getReleaseChangelog(context: Context, version: String): String {
        try {
            val cached = UpdatePreferences.getCachedUpdate(context)
            if (cached != null) {
                val json = JSONObject(cached)
                val notes = json.optString("releaseNotes", "")
                if (notes.isNotBlank()) return notes
            }
        } catch (_: Exception) {}
        return "• In-app update system with background checks, verify & install\n" +
                "• Send feedback via GitHub Discussions and issue bug reporter\n" +
                "• Support Node Player with direct UPI payments\n" +
                "• Hardened AGSL shaders, offscreen self-test & glass appearance controls\n" +
                "• Bug fixes & performance optimizations"
    }
}
