package com.nodeplayer.music.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nodeplayer.library.storage.StorageCategoryRegistry
import com.nodeplayer.model.storage.StorageUsageStats
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.ui.downloader.DownloadQueueSheet
import com.nodeplayer.ui.storage.StorageManagerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android container screen for unified Storage & Downloads management.
 */
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE) }
    val queueManager = remember { NodePlayerApplication.instance.downloadQueueManager }

    var usageStats by remember { mutableStateOf<List<StorageUsageStats>>(emptyList()) }
    var showQueueSheet by remember { mutableStateOf(false) }

    var concurrentLimit by remember {
        mutableIntStateOf(prefs.getInt("download_concurrent_limit", 2))
    }
    var wifiOnly by remember {
        mutableStateOf(prefs.getBoolean("download_wifi_only", true))
    }
    var qualityPreset by remember {
        mutableStateOf(prefs.getString("download_default_quality", "ASK") ?: "ASK")
    }
    var preferredFormat by remember {
        mutableStateOf(prefs.getString("download_preferred_format", "AUTO") ?: "AUTO")
    }

    fun refreshUsage() {
        coroutineScope.launch {
            val stats = withContext(Dispatchers.IO) {
                StorageCategoryRegistry.calculateTotalUsage()
            }
            usageStats = stats
        }
    }

    LaunchedEffect(Unit) {
        refreshUsage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .statusBarsPadding()
    ) {
        StorageManagerView(
            usageStats = usageStats,
            onClearCategory = { catId ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        StorageCategoryRegistry.clearCategory(catId)
                    }
                    refreshUsage()
                }
            },
            concurrentLimit = concurrentLimit,
            onConcurrentLimitChanged = { newLimit ->
                concurrentLimit = newLimit
                prefs.edit().putInt("download_concurrent_limit", newLimit).apply()
                queueManager.updateConcurrentLimit(newLimit)
            },
            wifiOnly = wifiOnly,
            onWifiOnlyChanged = { enabled ->
                wifiOnly = enabled
                prefs.edit().putBoolean("download_wifi_only", enabled).apply()
                queueManager.updateWifiOnly(enabled)
            },
            qualityPreset = qualityPreset,
            onQualityPresetChanged = { newPreset ->
                qualityPreset = newPreset
                prefs.edit().putString("download_default_quality", newPreset).apply()
            },
            preferredFormat = preferredFormat,
            onPreferredFormatChanged = { newFmt ->
                preferredFormat = newFmt
                prefs.edit().putString("download_preferred_format", newFmt).apply()
            },
            onOpenDownloadQueue = { showQueueSheet = true },
            onBack = onBack
        )

        if (showQueueSheet) {
            val tasks by queueManager.tasks.collectAsState()
            DownloadQueueSheet(
                tasks = tasks,
                onPauseTask = { queueManager.pause(it) },
                onResumeTask = { queueManager.resume(it) },
                onCancelTask = { queueManager.cancel(it) },
                onRetryTask = { queueManager.retry(it) },
                onPauseAll = {
                    tasks.forEach { if (!it.isTerminal) queueManager.pause(it.id) }
                },
                onResumeAll = {
                    tasks.forEach { if (it.state.name.startsWith("PAUSED")) queueManager.resume(it.id) }
                },
                onClearCompleted = { queueManager.clearCompleted() },
                onDismiss = { showQueueSheet = false }
            )
        }
    }
}
