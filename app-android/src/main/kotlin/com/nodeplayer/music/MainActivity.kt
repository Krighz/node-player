package com.nodeplayer.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nodeplayer.music.ui.PlayerViewModel
import com.nodeplayer.music.diagnostics.DiagnosticsDumpManager
import com.nodeplayer.music.ui.components.DiagnosticsDumpDialog
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.components.ConvxLiquidDock
import com.nodeplayer.ui.components.ConvxMiniPlayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.nodeplayer.ui.debug.GlassDebug
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.music.ui.components.GlobalSearchSheet
import com.nodeplayer.music.diagnostics.FrameMetricsTracker
import java.io.File
import com.nodeplayer.music.ui.components.LiquidGlassBackground
import com.nodeplayer.music.ui.player.NowPlayingSheet
import com.nodeplayer.music.ui.screens.EqualizerScreen
import com.nodeplayer.ui.theme.LocalControlBackdrop
import dev.chrisbanes.haze.haze
import com.nodeplayer.music.ui.screens.LibraryScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.theme.NodePlayerTheme
import dev.chrisbanes.haze.HazeState
import com.nodeplayer.music.update.UpdateManager
import com.nodeplayer.music.update.UpdateOptInDialog
import com.nodeplayer.music.update.UpdateChecker
import com.nodeplayer.music.update.UpdatePreferences
import com.nodeplayer.music.update.UpdateInstaller
import com.nodeplayer.music.update.UpdateDownloadManager
import com.nodeplayer.music.update.DownloadStatus
import com.nodeplayer.music.update.InstallUnknownAppsDialog
import com.nodeplayer.music.update.PlaybackStopConfirmDialog
import com.nodeplayer.music.update.WhatsNewSheet
import android.widget.Toast
import com.nodeplayer.ui.components.NodeConnectPairingDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.refreshLibrary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        try {
            val serviceIntent = Intent(this, com.nodeplayer.music.service.PlaybackService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to eagerly start PlaybackService", e)
        }

        checkAndRequestPermissions()

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()
            val dockHazeState = remember { HazeState() }
            val coroutineScope = rememberCoroutineScope()
            val dockScrollController = remember(coroutineScope, themeConfig.dockScrollSensitivity, themeConfig.dockReturnDelayMs) {
                DockScrollController(
                    scope = coroutineScope,
                    sensitivity = themeConfig.dockScrollSensitivity,
                    returnDelayMs = themeConfig.dockReturnDelayMs
                )
            }
            val dockScrollFraction by dockScrollController.offsetFraction.collectAsState()

            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = when (themeConfig.themeMode) {
                "LIGHT" -> false
                "DARK", "OLED" -> true
                else -> systemInDark
            }
            val isOled = themeConfig.isOledBlack || themeConfig.themeMode == "OLED"

            val appSettings by viewModel.appSettings.collectAsState()
            val effectiveThemeConfig = remember(themeConfig, appSettings.glassControlsConfig) {
                themeConfig.copy(controlsConfig = appSettings.glassControlsConfig)
            }

            NodePlayerTheme(
                themeConfig = effectiveThemeConfig,
                artworkAccentColor = accentColor
            ) {
                val prefs = remember { getSharedPreferences("node_player_prefs", android.content.Context.MODE_PRIVATE) }
                var showOnboarding by remember {
                    mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
                }

                val intentRoute = intent?.getStringExtra("route")
                var currentRoute by remember { mutableStateOf(intentRoute ?: "home") }
                var isNowPlayingExpanded by remember { mutableStateOf(false) }

                val dailyStats by viewModel.dailyListeningStats.collectAsState()
                val sessionSec by viewModel.sessionListeningSeconds.collectAsState()

                val isSubRoute = currentRoute in listOf("stats", "recap", "smart_playlists", "duplicate_finder", "widgets", "widget_settings")
                BackHandler(enabled = isNowPlayingExpanded || isSubRoute) {
                    if (isNowPlayingExpanded) {
                        isNowPlayingExpanded = false
                    } else if (isSubRoute) {
                        currentRoute = "home"
                    }
                }

                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val currentPositionMs by viewModel.currentPositionMs.collectAsState()
                val currentRms by viewModel.currentRms.collectAsState()
                val audioOutputInfo by viewModel.audioOutputInfo.collectAsState()
                val effectConfig by viewModel.effectConfig.collectAsState()
                val systemEqEnabled by viewModel.systemEqEnabled.collectAsState()
                val systemEqSessions by viewModel.systemEqSessions.collectAsState()

                val allTracks by viewModel.allTracks.collectAsState()
                val albums by viewModel.albums.collectAsState()
                val artists by viewModel.artists.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val allScannedFolders by viewModel.allScannedFolders.collectAsState()
                val excludedFolders by viewModel.excludedFolders.collectAsState()
                val excludedTracks by viewModel.excludedTracks.collectAsState()
                val minDurationSeconds by viewModel.minDurationSeconds.collectAsState()
                val crossfadeConfig by viewModel.crossfadeConfig.collectAsState()
                val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
                val queue by viewModel.queue.collectAsState()
                val sleepTimerRemainingSec by viewModel.sleepTimerRemainingSeconds.collectAsState()

                val currentDeviceType by viewModel.currentDeviceType.collectAsState()
                val convolverCpuPercent by viewModel.convolverCpuPercent.collectAsState()
                val activePresetLabel by viewModel.activePresetLabel.collectAsState()
                val resumeOnHeadsetConnect by viewModel.resumeOnHeadsetConnect.collectAsState()
                val resamplerRateChoice by viewModel.resamplerRateChoice.collectAsState()
                val analyzingTrackId by viewModel.analyzingTrackId.collectAsState()
                val autoDeviceProfilesEnabled by viewModel.autoDeviceProfilesEnabled.collectAsState()
                val selfTestResult by viewModel.shaderSelfTestResult.collectAsState()

                var showDiagnosticsDialog by remember { mutableStateOf(false) }
                var pendingAutoReportFile by remember { mutableStateOf<File?>(null) }
                val showUpdateOptIn by UpdateManager.showOptInDialog.collectAsState()

                var showWhatsNewSheet by remember { mutableStateOf(false) }
                var whatsNewVersion by remember { mutableStateOf("") }
                var whatsNewText by remember { mutableStateOf("") }

                var showUnknownAppsDialog by remember { mutableStateOf(false) }
                var showPlaybackStopConfirm by remember { mutableStateOf(false) }
                var pendingApkToInstall by remember { mutableStateOf<File?>(null) }
                val downloadStatus by UpdateManager.downloadStatus.collectAsState()
                var showSearchSheet by remember { mutableStateOf(false) }
                var showPairingDialog by remember { mutableStateOf(false) }
                var pairingProgress by remember { mutableStateOf(false) }
                var pairingStatusMsg by remember { mutableStateOf<String?>(null) }
                var showQrScanner by remember { mutableStateOf(false) }
                var pendingAllowPrompt by remember { mutableStateOf<Pair<String, (Boolean, Boolean) -> Unit>?>(null) }
                val hub = com.nodeplayer.music.service.PlaybackService.instance?.nodeConnectHub
                val activeSession by (hub?.activePairingSession ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
                val discoveredPeers by (hub?.discoveredPeers ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
                val connectedPeers by (hub?.connectedPeers ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

                LaunchedEffect(hub) {
                    hub?.onAllowPrompt = { name, id, callback ->
                        pendingAllowPrompt = name to callback
                    }
                }

                LaunchedEffect(Unit) {
                    UpdateManager.init(this@MainActivity)
                    if (DiagnosticsDumpManager.hasPendingAutoReport(this@MainActivity)) {
                        pendingAutoReportFile = DiagnosticsDumpManager.getPendingAutoReportFile(this@MainActivity)
                    }

                    val currentVersion = UpdateChecker.getInstalledVersionName(this@MainActivity)
                    val lastSeen = UpdatePreferences.getLastSeenVersionName(this@MainActivity)
                    if (lastSeen == null) {
                        UpdatePreferences.setLastSeenVersionName(this@MainActivity, currentVersion)
                    } else if (lastSeen != currentVersion) {
                        whatsNewVersion = currentVersion
                        whatsNewText = UpdateManager.getReleaseChangelog(this@MainActivity, currentVersion)
                        showWhatsNewSheet = true
                    }
                }

                val safeToast: (String, Int) -> Unit = { msg, len ->
                    runOnUiThread { Toast.makeText(this@MainActivity, msg, len).show() }
                }

                val performInstall: (File) -> Unit = { apk ->
                    when (val valResult = UpdateInstaller.validateApkSecurity(this@MainActivity, apk)) {
                        is UpdateInstaller.PreInstallValidationResult.Invalid -> {
                            safeToast(valResult.reason, Toast.LENGTH_LONG)
                            UpdateManager.setDownloadStatus(DownloadStatus.Error(valResult.reason))
                        }
                        is UpdateInstaller.PreInstallValidationResult.Valid -> {
                            if (!UpdateInstaller.canInstallPackages(this@MainActivity)) {
                                pendingApkToInstall = apk
                                showUnknownAppsDialog = true
                            } else if (isPlaying) {
                                pendingApkToInstall = apk
                                showPlaybackStopConfirm = true
                            } else {
                                val installResult = UpdateInstaller.installApk(this@MainActivity, apk)
                                if (installResult.isFailure) {
                                    safeToast("Install error: ${installResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG)
                                }
                            }
                        }
                    }
                }

                val pageBackdrop = rememberLayerBackdrop()

                CompositionLocalProvider(
                    LocalControlBackdrop provides pageBackdrop
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    // Blur Source Container: Encloses both background AND scrolling screen content recorded into pageBackdrop
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(pageBackdrop)
                            .haze(dockHazeState)
                            .then(
                                if (themeConfig.dockAutoHideEnabled) {
                                    Modifier.nestedScroll(
                                        rememberDockScrollConnection(
                                            controller = dockScrollController,
                                            enabled = true
                                        )
                                    )
                                } else Modifier
                            )
                    ) {
                        // 1. Dribbble-inspired Liquid Glass Organic Chrome & Obsidian Background
                        LiquidGlassBackground(
                            accentColor = accentColor,
                            isOled = isOled,
                            isLight = !isDark && !isOled,
                            isPlaying = isPlaying,
                            isPerformanceMode = themeConfig.isPerformanceMode,
                            animatedBackdrop = themeConfig.animatedBackdrop,
                            glassStrength = themeConfig.glassStrength,
                            themeConfig = themeConfig
                        )

                        // 2. Foreground Interactive UI (Cards use clean translucent glass; no recursive blur)
                        when (currentRoute) {
                            "home" -> com.nodeplayer.music.ui.screens.HomeScreen(
                                allTracks = allTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onOpenSmartPlaylists = { currentRoute = "smart_playlists" },
                                onOpenStats = { currentRoute = "stats" },
                                onOpenDuplicateFinder = { currentRoute = "duplicate_finder" },
                                onOpenNodeConnect = { showPairingDialog = true },
                                nodeConnectStatus = if (connectedPeers.isNotEmpty()) com.nodeplayer.ui.components.DeviceConnectStatus.CONNECTED
                                    else if (discoveredPeers.isNotEmpty() || activeSession != null) com.nodeplayer.ui.components.DeviceConnectStatus.DISCOVERING
                                    else com.nodeplayer.ui.components.DeviceConnectStatus.DISCONNECTED,
                                connectedDeviceCount = connectedPeers.size,
                                onInstallUpdate = {
                                    val readyApk = (downloadStatus as? DownloadStatus.ReadyToInstall)?.apkFile
                                        ?: UpdateDownloadManager.getDownloadedApk(this@MainActivity, UpdateManager.availableUpdate.value?.versionName ?: "")
                                    if (readyApk != null) {
                                        performInstall(readyApk)
                                    } else {
                                        safeToast("Download not ready yet", Toast.LENGTH_SHORT)
                                    }
                                },
                                hazeState = null
                            )
                            "library" -> LibraryScreen(
                                tracks = allTracks,
                                albums = albums,
                                artists = artists,
                                folders = folders,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onRescanClick = {
                                    checkAndRequestPermissions()
                                    viewModel.refreshLibrary()
                                },
                                onPlayNext = { viewModel.playNext(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                qualityBadgeMode = appSettings.qualityBadgesMode,
                                hazeState = null,
                                audioOutputInfo = audioOutputInfo,
                                onAnalyzeTrack = { viewModel.analyzeTrack(it) },
                                analyzingTrackId = analyzingTrackId
                            )
                            "equalizer" -> EqualizerScreen(
                                config = effectConfig,
                                onConfigChange = { viewModel.updateEffectConfig(it) },
                                systemEqActive = systemEqEnabled,
                                systemSessionCount = systemEqSessions.size,
                                currentTrack = currentTrack,
                                activePresetLabel = activePresetLabel,
                                convolverCpuPercent = convolverCpuPercent,
                                currentDeviceType = currentDeviceType,
                                onToggleAbCompare = { viewModel.toggleAbCompare() },
                                onImportAutoEq = { text, name -> viewModel.importAutoEq(text, name) },
                                onExportProfile = { viewModel.exportCurrentProfile() },
                                onImportProfile = { viewModel.importProfileJson(it) },
                                onSaveTrackPreset = { currentTrack?.let { viewModel.saveAsTrackPreset(it) } },
                                onSaveAlbumPreset = { currentTrack?.let { viewModel.saveAsAlbumPreset(it) } },
                                onClearPreset = { currentTrack?.let { viewModel.clearTrackOrAlbumPreset(it) } },
                                onLoadConvolverPreset = { viewModel.setConvolverPreset(it) },
                                rmsLevel = currentRms,
                                isPerformanceMode = themeConfig.isPerformanceMode,
                                hazeState = null
                            )
                            "more" -> com.nodeplayer.music.ui.screens.MoreScreen(
                                dailyStats = dailyStats,
                                sessionListeningSeconds = sessionSec,
                                onOpenStats = { currentRoute = "stats" },
                                onOpenRecap = { currentRoute = "recap" },
                                onOpenSmartPlaylists = { currentRoute = "smart_playlists" },
                                onOpenDuplicateFinder = { currentRoute = "duplicate_finder" },
                                onOpenDiagnostics = { currentRoute = "graphics_diagnostics" },
                                hazeState = null
                            )
                            "graphics_diagnostics" -> com.nodeplayer.music.ui.screens.GraphicsDiagnosticsScreen(
                                themeConfig = themeConfig,
                                selfTestResult = selfTestResult,
                                onToggleForceFail = { viewModel.toggleForceSelfTestFail(it) },
                                onReportProblemClick = { showDiagnosticsDialog = true },
                                onBack = { currentRoute = "more" },
                                hazeState = null
                            )
                            "stats" -> com.nodeplayer.music.ui.screens.StatsScreen(
                                dailyStats = dailyStats,
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                                onOpenRecap = { currentRoute = "recap" },
                                hazeState = null
                            )
                            "recap" -> com.nodeplayer.music.ui.screens.YearlyRecapScreen(
                                dailyStats = dailyStats,
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                                hazeState = null
                            )
                            "smart_playlists" -> com.nodeplayer.music.ui.screens.SmartPlaylistsScreen(
                                allTracks = allTracks,
                                onPlayTracks = { queue ->
                                    if (queue.isNotEmpty()) {
                                        viewModel.playTrack(queue[0], queue)
                                    }
                                },
                                onBack = { currentRoute = "more" },
                                hazeState = null
                            )
                            "duplicate_finder" -> com.nodeplayer.music.ui.screens.DuplicateFinderScreen(
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                                onTrackDeleted = { viewModel.refreshLibrary() },
                                hazeState = null
                            )
                            "settings" -> SettingsScreen(
                                outputInfo = audioOutputInfo,
                                themeConfig = themeConfig,
                                crossfadeConfig = crossfadeConfig,
                                onCrossfadeConfigChange = { viewModel.updateCrossfadeConfig(it) },
                                systemEqEnabled = systemEqEnabled,
                                systemEqSessions = systemEqSessions,
                                onToggleSystemEq = { viewModel.setSystemEqEnabled(it) },
                                currentDeviceType = currentDeviceType,
                                autoDeviceProfilesEnabled = autoDeviceProfilesEnabled,
                                onToggleAutoDeviceProfiles = { viewModel.setAutoDeviceProfilesEnabled(it) },
                                resumeOnHeadsetConnect = resumeOnHeadsetConnect,
                                onToggleResumeOnHeadsetConnect = { viewModel.setResumeOnHeadsetConnect(it) },
                                resamplerRateChoice = resamplerRateChoice,
                                onResamplerRateChange = { viewModel.setResamplerRateChoice(it) },
                                allFolders = if (allScannedFolders.isNotEmpty()) allScannedFolders else folders,
                                excludedFolders = excludedFolders,
                                excludedTracks = excludedTracks,
                                minDurationSeconds = minDurationSeconds,
                                onMinDurationChange = { viewModel.setMinDurationSeconds(it) },
                                onToggleExcludeFolder = { viewModel.toggleFolderExclusion(it) },
                                onAddCustomFolder = { viewModel.addCustomFolder(it) },
                                onRestoreTrack = { viewModel.restoreExcludedTrack(it) },
                                onThemeConfigChange = { viewModel.updateThemeConfig(it) },
                                onApplySafeGraphicsMode = { viewModel.applySafeGraphicsMode() },
                                onReportProblem = { showDiagnosticsDialog = true },
                                onNavigateToOpticalDiagnostics = { currentRoute = "optical_diagnostics" },
                                onPairNewDevice = { showPairingDialog = true },
                                onRescanLibrary = { viewModel.refreshLibrary() },
                                hazeState = null
                            )
                            "widgets", "widget_settings" -> com.nodeplayer.music.ui.screens.WidgetSettingsScreen(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onBack = { currentRoute = "settings" },
                                hazeState = null
                            )
                            "optical_diagnostics" -> com.nodeplayer.ui.screens.OpticalDiagnosticsView(
                                onBack = { currentRoute = "settings" },
                                themeConfig = themeConfig
                            )
                        }
                    }

                    // 3. Floating Mini-Player & Bottom Navigation (Convx Liquid Glass Dock with Auto-Hide)
                    val dockAutoHide = themeConfig.dockAutoHideEnabled
                    val miniPlayerAutoHide = themeConfig.dockAutoHideMiniPlayer
                    val dockOffsetFraction = if (dockAutoHide) dockScrollFraction else 0f
                    val miniPlayerOffsetFraction = if (dockAutoHide && miniPlayerAutoHide) dockScrollFraction else 0f

                    if (currentRoute !in listOf("widget_settings", "widgets", "optical_diagnostics")) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = dockOffsetFraction * 110f.dp.toPx()
                                    alpha = 1f - (dockOffsetFraction * 0.85f).coerceIn(0f, 1f)
                                }
                                .navigationBarsPadding()
                        ) {
                            if (currentTrack != null) {
                                val duration = currentTrack!!.durationMs.coerceAtLeast(1L)
                                val progress = (currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                                val isShaderActive = themeConfig.isShaderActive(selfTestResult.passed)

                                ConvxMiniPlayer(
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    progressFraction = progress,
                                    onPlayPauseClick = { viewModel.togglePlayPause() },
                                    onSkipNextClick = { viewModel.skipNext() },
                                    onSkipPreviousClick = { viewModel.skipPrevious() },
                                    onClick = { isNowPlayingExpanded = true },
                                    gesturesEnabled = appSettings.miniPlayerGesturesEnabled,
                                    rmsLevel = currentRms,
                                    isPerformanceMode = themeConfig.isPerformanceMode,
                                    shaderEnabled = isShaderActive,
                                    chromaticFringe = themeConfig.chromaticFringeValue(),
                                    backdrop = pageBackdrop,
                                    modifier = if (dockAutoHide && !miniPlayerAutoHide) {
                                        Modifier.graphicsLayer {
                                            // Counter-translate to keep mini player pinned when dock slides down
                                            translationY = -dockOffsetFraction * 72f.dp.toPx()
                                        }
                                    } else Modifier
                                )
                            }

                            val isShaderActive = themeConfig.isShaderActive(selfTestResult.passed)

                            GlassDebug.logThrottled(
                                "GlassPath",
                                "API=${android.os.Build.VERSION.SDK_INT}, shaderMode=${themeConfig.shaderMode}, selfTestPassed=${selfTestResult.passed}, " +
                                "isShaderActive=$isShaderActive, perfMode=${themeConfig.isPerformanceMode}, dockLens=${themeConfig.dockGlassLensEnabled}"
                            )

                            ConvxLiquidDock(
                                selectedRoute = currentRoute,
                                onRouteChange = { currentRoute = it },
                                onSearchClick = { showSearchSheet = true },
                                isPerformanceMode = themeConfig.isPerformanceMode,
                                shaderEnabled = isShaderActive,
                                chromaticFringe = themeConfig.chromaticFringeValue(),
                                searchButtonVisible = themeConfig.showDockSearchButton,
                                lensStrength = themeConfig.lensRefractionStrength,
                                rimSpecular = themeConfig.lensRimSpecular,
                                backdrop = pageBackdrop
                            )
                        }
                    }

                    // 4. Full Now Playing Sheet (Smooth slide animation)
                    AnimatedVisibility(
                        visible = isNowPlayingExpanded && currentTrack != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        currentTrack?.let { track ->
                            NowPlayingSheet(
                                track = track,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPositionMs,
                                audioOutputInfo = audioOutputInfo,
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onSkipNextClick = { viewModel.skipNext() },
                                onSkipPreviousClick = { viewModel.skipPrevious() },
                                onSeekTo = { viewModel.seekTo(it) },
                                onDismiss = { isNowPlayingExpanded = false },
                                onOpenEq = {
                                    isNowPlayingExpanded = false
                                    currentRoute = "equalizer"
                                },
                                isShuffle = isShuffleEnabled,
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                rmsLevel = currentRms,
                                isPerformanceMode = themeConfig.isPerformanceMode,
                                queue = queue,
                                onPlayTrack = { viewModel.playTrack(it, queue) },
                                onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                                sleepTimerRemainingSec = sleepTimerRemainingSec,
                                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                onOpenSignalPath = {
                                    // Open signal path dialog / inspector
                                },
                                themeConfig = themeConfig,
                                hazeState = dockHazeState
                            )
                        }
                    }

                    if (showOnboarding) {
                        com.nodeplayer.music.ui.components.FirstRunOnboardingDialog(
                            accentColor = accentColor,
                            onRequestPermissions = { checkAndRequestPermissions() },
                            onStartScan = { viewModel.refreshLibrary() },
                            onDismiss = {
                                prefs.edit().putBoolean("onboarding_completed", true).apply()
                                showOnboarding = false
                            }
                        )
                    }

                    if (showDiagnosticsDialog) {
                        DiagnosticsDumpDialog(
                            currentShaderMode = themeConfig.shaderMode,
                            currentScreenProvider = { currentRoute },
                            onDismiss = { showDiagnosticsDialog = false }
                        )
                    }

                    if (pendingAutoReportFile != null) {
                        AlertDialog(
                            onDismissRequest = {
                                DiagnosticsDumpManager.clearAutoReport(this@MainActivity)
                                pendingAutoReportFile = null
                            },
                            title = { Text("Something Looked Wrong") },
                            text = { Text("Node Player detected a shader or graphics issue earlier. Would you like to share a diagnostic report to help us fix it?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val file = pendingAutoReportFile!!
                                        val intent = DiagnosticsDumpManager.createShareIntent(this@MainActivity, file)
                                        startActivity(Intent.createChooser(intent, "Share Diagnostics Dump"))
                                        DiagnosticsDumpManager.clearAutoReport(this@MainActivity)
                                        pendingAutoReportFile = null
                                    }
                                ) {
                                    Text("Send Report")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        DiagnosticsDumpManager.clearAutoReport(this@MainActivity)
                                        pendingAutoReportFile = null
                                    }
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        )
                    }

                    if (showUpdateOptIn) {
                        UpdateOptInDialog { agreed ->
                            UpdateManager.onOptInChoice(this@MainActivity, agreed)
                        }
                    }

                    if (showUnknownAppsDialog) {
                        InstallUnknownAppsDialog(
                            onOpenSettings = {
                                showUnknownAppsDialog = false
                                startActivity(UpdateInstaller.getManageUnknownAppSourcesIntent(this@MainActivity))
                            },
                            onDismiss = { showUnknownAppsDialog = false }
                        )
                    }

                    if (showPlaybackStopConfirm) {
                        PlaybackStopConfirmDialog(
                            onConfirm = {
                                showPlaybackStopConfirm = false
                                if (isPlaying) {
                                    viewModel.togglePlayPause()
                                }
                                pendingApkToInstall?.let { apk ->
                                    val installResult = UpdateInstaller.installApk(this@MainActivity, apk)
                                    if (installResult.isFailure) {
                                        safeToast("Install error: ${installResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG)
                                    }
                                }
                            },
                            onDismiss = { showPlaybackStopConfirm = false }
                        )
                    }

                    if (showWhatsNewSheet) {
                        WhatsNewSheet(
                            versionName = whatsNewVersion,
                            changelogText = whatsNewText,
                            onDismiss = {
                                showWhatsNewSheet = false
                                UpdatePreferences.setLastSeenVersionName(this@MainActivity, whatsNewVersion)
                            }
                        )
                    }

                    if (showSearchSheet) {
                        GlobalSearchSheet(
                            tracks = allTracks,
                            onTrackClick = { track ->
                                if (track.path.startsWith("nodeconnect://")) {
                                    val conn = hub?.activeConnections?.firstOrNull()
                                    if (conn != null) {
                                        lifecycleScope.launch {
                                            hub.controlService.sendRemoteCommand(
                                                connection = conn,
                                                action = com.nodeplayer.network.protocol.ControlAction.PLAY_TRACK,
                                                trackId = track.id,
                                                trackTitle = track.title
                                            )
                                            safeToast("Playing '${track.title}' from ${conn.peerDeviceName}...", android.widget.Toast.LENGTH_SHORT)
                                        }
                                    }
                                } else {
                                    viewModel.playTrack(track, allTracks)
                                }
                                showSearchSheet = false
                            },
                            onDismiss = { showSearchSheet = false }
                        )
                    }
                    if (showPairingDialog) {
                        val ncSettingsStore = remember {
                            val prefs = getSharedPreferences("node_connect_settings", android.content.Context.MODE_PRIVATE)
                            com.nodeplayer.network.service.NodeConnectSettingsStore(com.nodeplayer.music.data.AndroidKeyValueStore(prefs))
                        }
                        var ncConfig by remember { mutableStateOf(ncSettingsStore.config.value) }
                        LaunchedEffect(ncConfig) {
                            hub?.config = ncConfig
                        }

                        val localIp = remember {
                            try {
                                java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                                    .flatMap { it.inetAddresses.asSequence() }
                                    .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                                    ?.hostAddress ?: "127.0.0.1"
                            } catch (e: Exception) {
                                "127.0.0.1"
                            }
                        }
                        NodeConnectPairingDialog(
                            onDismissRequest = { showPairingDialog = false },
                            activeSession = activeSession,
                            localIp = localIp,
                            localDeviceId = hub?.activeDeviceId ?: android.os.Build.MODEL,
                            localDeviceName = android.os.Build.MODEL,
                            localPort = hub?.activePort ?: 54820,
                            localCandidateIps = hub?.getLocalCandidateIps() ?: listOf(localIp),
                            discoveredPeers = discoveredPeers,
                            config = ncConfig,
                            onConfigChange = { updated ->
                                ncConfig = updated
                                ncSettingsStore.saveConfig(updated)
                                hub?.config = updated
                            },
                            onStartSession = { hub?.startPairingSession() },
                            onCancelSession = { hub?.cancelPairingSession() },
                            onPairWithHost = { host, port, pin ->
                                lifecycleScope.launch {
                                    pairingProgress = true
                                    pairingStatusMsg = "Connecting to $host:$port..."
                                    val res = hub?.pairWithHost(host, port, pin)
                                    pairingProgress = false
                                    if (res != null && res.isSuccess) {
                                        pairingStatusMsg = "Successfully paired with ${res.getOrNull()}!"
                                        delay(1200)
                                        pairingStatusMsg = null
                                    } else {
                                        pairingStatusMsg = "Pairing failed: ${res?.exceptionOrNull()?.message ?: "Host unreachable"}"
                                    }
                                }
                            },
                            onConnectToPeerDirectly = { peer ->
                                lifecycleScope.launch {
                                    pairingProgress = true
                                    pairingStatusMsg = "Connecting to ${peer.deviceName}..."
                                    val conn = hub?.connectToPeer(peer)
                                    pairingProgress = false
                                    if (conn != null) {
                                        safeToast("Connected to ${peer.deviceName}!", Toast.LENGTH_SHORT)
                                        pairingStatusMsg = null
                                    } else {
                                        pairingStatusMsg = "Connection failed to ${peer.deviceName}"
                                    }
                                }
                            },
                            onConnectToAddress = { host, port ->
                                lifecycleScope.launch {
                                    pairingProgress = true
                                    pairingStatusMsg = "Connecting to $host:$port..."
                                    val peer = com.nodeplayer.network.discovery.DiscoveredNodePeer(
                                        deviceId = "manual_${host.replace('.', '_')}",
                                        deviceName = "Node Peer ($host)",
                                        platform = "Manual",
                                        hostAddress = host,
                                        port = port,
                                        certificateFingerprintSha256 = "",
                                        candidateAddresses = listOf(host),
                                        discoveryMethod = "Manual IP"
                                    )
                                    val conn = hub?.connectToPeer(peer)
                                    pairingProgress = false
                                    if (conn != null) {
                                        safeToast("Connected to $host:$port!", Toast.LENGTH_SHORT)
                                        pairingStatusMsg = null
                                    } else {
                                        pairingStatusMsg = "Failed to connect to $host:$port"
                                    }
                                }
                            },
                            onScanQrClicked = { showQrScanner = true },
                            onScanNetworkClicked = { hub?.triggerSubnetScan() },
                            onProbeGatewayClicked = { hub?.triggerGatewayProbe() },
                            onTestPingClicked = { host, port, cb ->
                                lifecycleScope.launch {
                                    val res = hub?.testConnectionTo(host, port)
                                    if (res != null) cb(res)
                                }
                            },
                            onCopyLogClicked = {
                                val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("NodeLog", log))
                                safeToast("Debug log copied to clipboard (${log.length} chars)", Toast.LENGTH_SHORT)
                            },
                            onExportLogClicked = {
                                val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                                val out = java.io.File(filesDir, "logs/node_connect_export.txt")
                                out.parentFile?.mkdirs()
                                out.writeText(log)
                                safeToast("Log exported to ${out.name}", Toast.LENGTH_SHORT)
                            },
                            isPairingInProgress = pairingProgress,
                            onStartUsbLink = {
                                lifecycleScope.launch {
                                    pairingProgress = true
                                    pairingStatusMsg = "Connecting over USB ADB link (127.0.0.1:54826)..."
                                    val peer = com.nodeplayer.network.discovery.DiscoveredNodePeer(
                                        deviceId = "usb_adb_pc",
                                        deviceName = "PC via USB ADB",
                                        platform = "Windows via USB",
                                        hostAddress = "127.0.0.1",
                                        port = 54826,
                                        certificateFingerprintSha256 = "",
                                        candidateAddresses = listOf("127.0.0.1"),
                                        discoveryMethod = "USB Debugging"
                                    )
                                    val conn = hub?.connectToPeer(peer)
                                    pairingProgress = false
                                    if (conn != null) {
                                        safeToast("Connected to PC over USB link!", Toast.LENGTH_SHORT)
                                        pairingStatusMsg = null
                                    } else {
                                        pairingStatusMsg = "USB Link failed. Ensure 'adb reverse tcp:54826 tcp:54820' is active."
                                    }
                                }
                            },
                            pairingStatusMessage = pairingStatusMsg,
                            connectedPeers = connectedPeers,
                            onSendRemotePlayPause = { deviceId ->
                                lifecycleScope.launch { hub?.sendRemotePlayPause(deviceId) }
                            },
                            onSendRemotePrevious = { deviceId ->
                                lifecycleScope.launch { hub?.sendRemotePrevious(deviceId) }
                            },
                            onSendRemoteNext = { deviceId ->
                                lifecycleScope.launch { hub?.sendRemoteNext(deviceId) }
                            },
                            onDisconnectPeer = { deviceId ->
                                hub?.disconnectDevice(deviceId)
                            },
                            onTriggerAudioCast = { deviceId ->
                                val conn = hub?.activeConnections?.firstOrNull { it.peerDeviceId == deviceId }
                                val srv = com.nodeplayer.music.service.PlaybackService.instance
                                val currentTrack = srv?.crossfadeManager?.currentPlayingTrack
                                if (conn != null && srv != null) {
                                    if (hub?.castService?.isCasting == true) {
                                        srv.stopAudioCasting()
                                        safeToast("Audio Cast stopped", Toast.LENGTH_SHORT)
                                    } else if (currentTrack != null) {
                                        try {
                                            srv.startAudioCasting(conn, currentTrack)
                                            safeToast("Audio Cast started to ${conn.peerDeviceName}!", Toast.LENGTH_SHORT)
                                        } catch (e: Exception) {
                                            safeToast("Audio Cast failed: ${e.message}", Toast.LENGTH_SHORT)
                                        }
                                    } else {
                                        safeToast("No track currently playing to cast", Toast.LENGTH_SHORT)
                                    }
                                } else {
                                    safeToast("Device not connected or playback service inactive", Toast.LENGTH_SHORT)
                                }
                            },
                            onTriggerHandoff = { deviceId ->
                                lifecycleScope.launch {
                                    val conn = hub?.activeConnections?.firstOrNull { it.peerDeviceId == deviceId }
                                    val srv = com.nodeplayer.music.service.PlaybackService.instance
                                    val currentTrack = srv?.crossfadeManager?.currentPlayingTrack
                                    if (conn != null && currentTrack != null) {
                                        try {
                                            val pos = srv.player.currentPosition
                                            val isPlaying = srv.player.isPlaying
                                            hub?.handoffService?.initiateHandoff(conn, currentTrack, pos, isPlaying)
                                            srv.player.pause()
                                            safeToast("Handoff transferred to ${conn.peerDeviceName}!", Toast.LENGTH_SHORT)
                                        } catch (e: Exception) {
                                            safeToast("Handoff failed: ${e.message}", Toast.LENGTH_SHORT)
                                        }
                                    } else {
                                        safeToast("No active track to handoff", Toast.LENGTH_SHORT)
                                    }
                                }
                            }
                        )
                    }

                    if (showQrScanner) {
                        com.nodeplayer.music.ui.components.QrScannerDialog(
                            onDismissRequest = { showQrScanner = false },
                            onQrCodeScanned = { qrText ->
                                showQrScanner = false
                                lifecycleScope.launch {
                                    try {
                                        val content = qrText.removePrefix("nodeconnect://")
                                        val addressesPart = content.substringBefore("?")
                                        val hostPart = addressesPart.substringBefore(":")
                                        val portPart = addressesPart.substringAfter(":").toIntOrNull() ?: 54820
                                        val candidateList = hostPart.split(",").filter { it.isNotBlank() }
                                        val firstIp = candidateList.firstOrNull() ?: "127.0.0.1"

                                        val params = content.substringAfter("?", "").split("&").associate {
                                            val kv = it.split("=")
                                            (kv.getOrNull(0) ?: "") to (kv.getOrNull(1) ?: "")
                                        }
                                        val devId = params["dev"] ?: "qr_host"
                                        val devName = java.net.URLDecoder.decode(params["name"] ?: "Node Peer", "UTF-8")

                                        safeToast("Connecting to $devName...", Toast.LENGTH_SHORT)

                                        val peer = com.nodeplayer.network.discovery.DiscoveredNodePeer(
                                            deviceId = devId,
                                            deviceName = devName,
                                            platform = "Desktop/QR",
                                            hostAddress = firstIp,
                                            port = portPart,
                                            certificateFingerprintSha256 = "",
                                            candidateAddresses = candidateList,
                                            discoveryMethod = "QR Code"
                                        )
                                        val conn = hub?.connectToPeer(peer)
                                        if (conn != null) {
                                            safeToast("Connected to $devName!", Toast.LENGTH_SHORT)
                                            pairingStatusMsg = null
                                        } else {
                                            safeToast("Connection to $devName failed", Toast.LENGTH_LONG)
                                        }
                                    } catch (e: Exception) {
                                        safeToast("QR parse error: ${e.message}", Toast.LENGTH_LONG)
                                    }
                                }
                            }
                        )
                    }

                    if (pendingAllowPrompt != null) {
                        val prompt = pendingAllowPrompt!!
                        AlertDialog(
                            onDismissRequest = {
                                prompt.second(false, false)
                                pendingAllowPrompt = null
                            },
                            title = { Text("Allow Connection?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White) },
                            text = {
                                Text("Device '${prompt.first}' is requesting to connect to Node Player.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                            },
                            confirmButton = {
                                Button(onClick = {
                                    prompt.second(true, true)
                                    pendingAllowPrompt = null
                                }) {
                                    Text("Always Allow")
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = {
                                        prompt.second(true, false)
                                        pendingAllowPrompt = null
                                    }) {
                                        Text("Allow Once")
                                    }
                                    TextButton(onClick = {
                                        prompt.second(false, false)
                                        pendingAllowPrompt = null
                                    }) {
                                        Text("Deny", color = androidx.compose.ui.graphics.Color(0xFFEF4444))
                                    }
                                }
                            }
                        )
                    }

                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FrameMetricsTracker.start(this)
    }

    override fun onStop() {
        super.onStop()
        FrameMetricsTracker.stop(this)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.refreshLibrary()
        }
    }
}
