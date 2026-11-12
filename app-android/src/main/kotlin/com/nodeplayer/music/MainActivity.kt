package com.nodeplayer.music

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.nodeplayer.ui.screens.DetailMediaHeader
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
import androidx.compose.ui.platform.LocalDensity
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
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.music.ui.screens.LibraryScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.theme.NodePlayerTheme
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
import kotlinx.coroutines.withContext
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

        val serviceIntent = Intent(this, com.nodeplayer.music.service.PlaybackService::class.java)
        val requiresImmediatePlaybackService =
            intent?.getBooleanExtra("start_playback", false) == true ||
            (intent?.action != null && intent?.action != Intent.ACTION_MAIN)

        if (requiresImmediatePlaybackService) {
            try {
                startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start PlaybackService", e)
            }
        }

        // Defer non-critical service start and AGSL shader warm-up past the first rendered frame
        window.decorView.post {
            if (!requiresImmediatePlaybackService) {
                try {
                    startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start deferred PlaybackService", e)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                com.kyant.backdrop.RuntimeShaderCache.warmUp()
            }
        }

        // Apply configured or highest supported refresh rate (e.g. 120Hz) off the critical startup path
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val refreshMode = getSharedPreferences("node_player_prefs", android.content.Context.MODE_PRIVATE)
                .getString("display_refresh_rate", "AUTO") ?: "AUTO"
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                applyDisplayRefreshRate(refreshMode)
            }
        }

        checkAndRequestPermissions()

        handleSpotifyCallbackIntent(intent)

        setContent {
            LaunchedEffect(Unit) {
                try {
                    reportFullyDrawn()
                } catch (_: Throwable) {}
            }
            val themeConfig by viewModel.themeConfig.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            val dockScrollController = remember(coroutineScope, themeConfig.dockScrollSensitivity, themeConfig.dockReturnDelayMs) {
                DockScrollController(
                    scope = coroutineScope,
                    sensitivity = themeConfig.dockScrollSensitivity,
                    returnDelayMs = themeConfig.dockReturnDelayMs
                )
            }

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
                var isLyricsExpanded by remember { mutableStateOf(false) }

                val dailyStats by viewModel.dailyListeningStats.collectAsState()
                val sessionSec by viewModel.sessionListeningSeconds.collectAsState()

                val rawPlaylists by viewModel.playlists.collectAsState()
                val youTubeSyncing by viewModel.youTubePlaylistSyncing.collectAsState()
                val youTubeSyncError by viewModel.youTubePlaylistSyncError.collectAsState()
                val domainPlaylists = remember(rawPlaylists) {
                    rawPlaylists.map { it.toPlaylist() }
                }
                var selectedPlaylistForDetail by remember { mutableStateOf<com.nodeplayer.model.Playlist?>(null) }
                var playlistDetailTracks by remember { mutableStateOf<List<com.nodeplayer.model.Track>>(emptyList()) }
                var trackForAddToPlaylist by remember { mutableStateOf<com.nodeplayer.model.Track?>(null) }
                var activeCategory by remember { mutableStateOf<Pair<com.nodeplayer.model.MoodGenre, List<com.nodeplayer.model.Playlist>>?>(null) }
                var loadingCategoryPlaylistId by remember { mutableStateOf<String?>(null) }
                var activePlaylistDetail by remember { mutableStateOf<Pair<com.nodeplayer.ui.screens.DetailMediaHeader, List<com.nodeplayer.model.Track>>?>(null) }

                // Spotify Transfer State
                var showSpotifyDialog by remember { mutableStateOf(false) }
                val isSpotifyLoggedIn by com.nodeplayer.music.auth.SpotifyAccountManager.isLoggedIn.collectAsState()
                val spotifyClientId by com.nodeplayer.music.auth.SpotifyAccountManager.configuredClientId.collectAsState()
                var spotifyPlaylists by remember { mutableStateOf<List<com.nodeplayer.network.spotify.SpotifyPlaylistSummary>>(emptyList()) }
                var isLoadingSpotifyPlaylists by remember { mutableStateOf(false) }

                var reviewingSpotifyPlaylist by remember { mutableStateOf<com.nodeplayer.network.spotify.SpotifyPlaylistSummary?>(null) }
                var spotifyMatchedTracks by remember { mutableStateOf<List<Pair<com.nodeplayer.network.spotify.SpotifyTrack, com.nodeplayer.model.Track>>>(emptyList()) }
                var spotifyUncertainTracks by remember { mutableStateOf<List<Pair<com.nodeplayer.network.spotify.SpotifyTrack, List<com.nodeplayer.model.Track>>>>(emptyList()) }
                var spotifyNotFoundTracks by remember { mutableStateOf<List<com.nodeplayer.network.spotify.SpotifyTrack>>(emptyList()) }
                var isSpotifyMatching by remember { mutableStateOf(false) }
                var spotifyMatchingProgress by remember { mutableStateOf(0f) }
                var spotifyMatchingCurrentTitle by remember { mutableStateOf<String?>(null) }
                var spotifyMatchingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                val isSubRoute = currentRoute in listOf("stats", "recap", "smart_playlists", "duplicate_finder", "widgets", "widget_settings", "playlist_detail", "settings", "equalizer", "optical_diagnostics")
                BackHandler(enabled = isLyricsExpanded || isNowPlayingExpanded || isSubRoute || selectedPlaylistForDetail != null || activePlaylistDetail != null || activeCategory != null) {
                    if (isLyricsExpanded) {
                        // Collapse Lyrics back to NowPlaying (ARCH-03)
                        isLyricsExpanded = false
                    } else if (isNowPlayingExpanded) {
                        isNowPlayingExpanded = false
                    } else if (activePlaylistDetail != null) {
                        activePlaylistDetail = null
                    } else if (activeCategory != null) {
                        activeCategory = null
                    } else if (selectedPlaylistForDetail != null) {
                        selectedPlaylistForDetail = null
                    } else if (isSubRoute) {
                        currentRoute = "home"
                    }
                }

                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val currentPositionState = viewModel.currentPositionMs.collectAsState()
                val currentRmsState = viewModel.currentRms.collectAsState()
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

                LaunchedEffect(selectedPlaylistForDetail?.id) {
                    val pid = selectedPlaylistForDetail?.id
                    if (pid != null) {
                        viewModel.getTracksForPlaylist(pid).collect { tList ->
                            playlistDetailTracks = tList
                        }
                    }
                }

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
                var showYouTubeLoginSheet by remember { mutableStateOf(false) }
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
                        CompositionLocalProvider(
                            LocalControlBackdrop provides remember { emptyBackdrop() }
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
                        if (activePlaylistDetail != null) {
                            val (header, detailTracks) = activePlaylistDetail!!
                            val playOrOpenTrack: (com.nodeplayer.model.Track, List<com.nodeplayer.model.Track>) -> Unit = { track, queue ->
                                if (track.path.startsWith("nodeplayer://youtube/playlist/")) {
                                    coroutineScope.launch {
                                        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
                                                .fetchPlaylistTracks(track.path.removePrefix("nodeplayer://youtube/playlist/"))
                                        }
                                        result.onSuccess { songs ->
                                            activePlaylistDetail = header.copy(id = track.path, title = track.title) to songs
                                        }.onFailure { safeToast("Couldn't open playlist. Please try again.", Toast.LENGTH_SHORT) }
                                    }
                                } else viewModel.playTrack(track, queue)
                            }
                            com.nodeplayer.ui.screens.PlaylistDetailView(
                                header = header,
                                tracks = detailTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                isSavedInLibrary = false,
                                onBack = { activePlaylistDetail = null },
                                onPlayTrack = playOrOpenTrack,
                                onPlayAll = { q -> if (q.isNotEmpty()) playOrOpenTrack(q[0], q) },
                                onShuffleAll = { q ->
                                    if (q.isNotEmpty()) {
                                        val shuffled = q.shuffled()
                                        playOrOpenTrack(shuffled[0], shuffled)
                                    }
                                },
                                onToggleSaveLibrary = { h ->
                                    viewModel.createPlaylist(h.title, detailTracks)
                                    safeToast("Saved ${detailTracks.size} tracks to library", Toast.LENGTH_SHORT)
                                },
                                onTrackOptionsClick = { trackForAddToPlaylist = it }
                            )
                        } else if (activeCategory != null) {
                            val (category, playlists) = activeCategory!!
                            com.nodeplayer.ui.screens.CategoryPlaylistsView(
                                title = category.title,
                                playlists = playlists,
                                onBack = { activeCategory = null },
                                loadingPlaylistId = loadingCategoryPlaylistId,
                                onOpenPlaylist = { playlist ->
                                    val playlistId = playlist.externalId
                                    if (playlistId != null && loadingCategoryPlaylistId == null) {
                                        loadingCategoryPlaylistId = playlistId
                                        coroutineScope.launch {
                                            try {
                                                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    com.nodeplayer.network.innertube.SharedInnerTubeProvider.get().fetchPlaylistTracks(playlistId)
                                                }
                                                if (activeCategory?.first == category) {
                                                    result.onSuccess { songs ->
                                                        activePlaylistDetail = com.nodeplayer.ui.screens.DetailMediaHeader(
                                                            id = playlistId, title = playlist.name,
                                                            subtitle = category.title, artworkUrl = playlist.thumbnailUrl
                                                        ) to songs
                                                    }.onFailure { safeToast("Couldn't open playlist. Please try again.", Toast.LENGTH_SHORT) }
                                                }
                                            } finally { loadingCategoryPlaylistId = null }
                                        }
                                    }
                                }
                            )
                        } else when (currentRoute) {
                            "home", "new", "explore" -> com.nodeplayer.music.ui.screens.HomeScreen(
                                allTracks = allTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                currentRoute = currentRoute,
                                dockScrollController = dockScrollController,
                                onSearchClick = { showSearchSheet = true },
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onOpenSmartPlaylists = { currentRoute = "smart_playlists" },
                                onOpenStats = { currentRoute = "stats" },
                                onOpenDuplicateFinder = { currentRoute = "duplicate_finder" },
                                onOpenNodeConnect = { showPairingDialog = true },
                                onOpenEqualizer = { currentRoute = "equalizer" },
                                onOpenSettings = { currentRoute = "settings" },
                                onOpenOfflineMusic = { com.nodeplayer.common.AppModeState.setOnlineMode(false) },
                                onSignInClick = { showYouTubeLoginSheet = true },
                                onOpenCategory = { category, playlists -> activeCategory = category to playlists },
                                onOpenPlaylistDetail = { title, subtitle, artworkUrl, items ->
                                    activePlaylistDetail = com.nodeplayer.ui.screens.DetailMediaHeader(
                                        id = title,
                                        title = title,
                                        subtitle = subtitle,
                                        artworkUrl = artworkUrl,
                                        description = if (items.isNotEmpty()) "Explore ${items.size} curated tracks from $title." else null
                                    ) to items
                                },
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
                            )
                            "library" -> {
                                LaunchedEffect(Unit) { viewModel.syncYouTubePlaylists() }
                                if (selectedPlaylistForDetail != null) {
                                    val pl = selectedPlaylistForDetail!!
                                    val header = remember(pl.id, pl.name, pl.source, playlistDetailTracks) {
                                        com.nodeplayer.ui.screens.DetailMediaHeader(
                                            id = pl.id.toString(),
                                            title = pl.name,
                                            subtitle = "${pl.source.replaceFirstChar { it.uppercase() }} • ${playlistDetailTracks.size} tracks",
                                            artworkUrl = playlistDetailTracks.firstOrNull()?.path,
                                            description = "Saved in local library."
                                        )
                                    }
                                    com.nodeplayer.ui.screens.PlaylistDetailView(
                                        header = header,
                                        tracks = playlistDetailTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        isSavedInLibrary = true,
                                        onBack = { selectedPlaylistForDetail = null },
                                        onPlayTrack = { tr, q -> viewModel.playTrack(tr, q) },
                                        onPlayAll = { q -> if (q.isNotEmpty()) viewModel.playTrack(q[0], q) },
                                        onShuffleAll = { q ->
                                            if (q.isNotEmpty()) {
                                                val shuffled = q.shuffled()
                                                viewModel.playTrack(shuffled[0], shuffled)
                                            }
                                        },
                                        onTrackOptionsClick = { trackForAddToPlaylist = it }
                                    )
                                } else {
                                    LibraryScreen(
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
                                            viewModel.syncYouTubePlaylists(force = true)
                                        },
                                        onPlayNext = { viewModel.playNext(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        qualityBadgeMode = appSettings.qualityBadgesMode,
                                        audioOutputInfo = audioOutputInfo,
                                        onAnalyzeTrack = { viewModel.analyzeTrack(it) },
                                        analyzingTrackId = analyzingTrackId,
                                        playlists = domainPlaylists,
                                        youTubeSyncing = youTubeSyncing,
                                        youTubeSyncError = youTubeSyncError,
                                        onImportYouTubePlaylists = {
                                            if (com.nodeplayer.music.auth.YouTubeAccountManager.isLoggedIn()) {
                                                viewModel.syncYouTubePlaylists(force = true)
                                            } else showYouTubeLoginSheet = true
                                        },
                                        onOpenPlaylist = { selectedPlaylistForDetail = it },
                                        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                        onImportSpotifyPlaylists = {
                                            com.nodeplayer.music.auth.SpotifyAccountManager.initialize(this@MainActivity)
                                            showSpotifyDialog = true
                                            if (isSpotifyLoggedIn) {
                                                val token = com.nodeplayer.music.auth.SpotifyAccountManager.getValidAccessToken(this@MainActivity)
                                                if (token != null) {
                                                    isLoadingSpotifyPlaylists = true
                                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                        val res = com.nodeplayer.music.auth.SpotifyAccountManager.client.getUserPlaylists(token)
                                                        spotifyPlaylists = res.getOrDefault(emptyList())
                                                        isLoadingSpotifyPlaylists = false
                                                    }
                                                }
                                            }
                                        },
                                        onAddToPlaylist = { trackForAddToPlaylist = it }
                                    )
                                }
                            }
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
                                rmsProvider = { currentRmsState.value },
                                isPerformanceMode = themeConfig.isPerformanceMode,
                                onBack = { currentRoute = "home" },
                            )
                            "more" -> com.nodeplayer.music.ui.screens.MoreScreen(
                                dailyStats = dailyStats,
                                sessionListeningSeconds = sessionSec,
                                onOpenStats = { currentRoute = "stats" },
                                onOpenRecap = { currentRoute = "recap" },
                                onOpenSmartPlaylists = { currentRoute = "smart_playlists" },
                                onOpenDuplicateFinder = { currentRoute = "duplicate_finder" },
                                onOpenDiagnostics = {
                                    NodePlayerApplication.ensureFileLoggingInitialized(this@MainActivity)
                                    currentRoute = "graphics_diagnostics"
                                },
                            )
                            "graphics_diagnostics" -> com.nodeplayer.music.ui.screens.GraphicsDiagnosticsScreen(
                                themeConfig = themeConfig,
                                selfTestResult = selfTestResult,
                                onToggleForceFail = { viewModel.toggleForceSelfTestFail(it) },
                                onReportProblemClick = { showDiagnosticsDialog = true },
                                onBack = { currentRoute = "more" },
                            )
                            "stats" -> com.nodeplayer.music.ui.screens.StatsScreen(
                                dailyStats = dailyStats,
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                                onOpenRecap = { currentRoute = "recap" },
                            )
                            "recap" -> com.nodeplayer.music.ui.screens.YearlyRecapScreen(
                                dailyStats = dailyStats,
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                            )
                            "smart_playlists" -> com.nodeplayer.music.ui.screens.SmartPlaylistsScreen(
                                allTracks = allTracks,
                                onPlayTracks = { queue ->
                                    if (queue.isNotEmpty()) {
                                        viewModel.playTrack(queue[0], queue)
                                    }
                                },
                                onBack = { currentRoute = "more" },
                            )
                            "duplicate_finder" -> com.nodeplayer.music.ui.screens.DuplicateFinderScreen(
                                allTracks = allTracks,
                                onBack = { currentRoute = "more" },
                                onTrackDeleted = { viewModel.refreshLibrary() },
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
                                onBack = { currentRoute = "home" },
                            )
                            "widgets", "widget_settings" -> com.nodeplayer.music.ui.screens.WidgetSettingsScreen(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onBack = { currentRoute = "settings" },
                            )
                            "optical_diagnostics" -> com.nodeplayer.ui.screens.OpticalDiagnosticsView(
                                onBack = { currentRoute = "settings" },
                                themeConfig = themeConfig
                            )
                        }
                    }
                    }

                    // 3. Floating Mini-Player & Bottom Navigation (Convx Liquid Glass Dock)
                    if (currentRoute !in listOf("widget_settings", "widgets", "optical_diagnostics", "settings", "equalizer")) {
                        val activeTrack = currentTrack ?: com.nodeplayer.music.service.PlaybackService.instance?.crossfadeManager?.currentPlayingTrack
                        MainBottomDockOverlay(
                            activeTrack = activeTrack,
                            isNowPlayingExpanded = isNowPlayingExpanded,
                            isPlaying = isPlaying,
                            themeConfig = effectiveThemeConfig,
                            shaderActive = effectiveThemeConfig.isShaderActive(selfTestResult.passed),
                            dockScrollController = dockScrollController,
                            hasActivePlaylistDetail = activePlaylistDetail != null || activeCategory != null,
                            currentRoute = currentRoute,
                            accentColor = accentColor,
                            pageBackdrop = pageBackdrop,
                            positionProvider = { currentPositionState.value },
                            rmsProvider = { currentRmsState.value },
                            onRouteChange = { currentRoute = it },
                            onClearPlaylistDetail = { if (activePlaylistDetail != null) activePlaylistDetail = null else activeCategory = null },
                            onOpenSearch = { showSearchSheet = true },
                            onExpandNowPlaying = { isNowPlayingExpanded = true },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() }
                        )
                    }

                    // 4. Full Now Playing Screen (State 1: Full-bleed edge-to-edge)
                    val activeNowPlayingTrack = currentTrack ?: com.nodeplayer.music.service.PlaybackService.instance?.crossfadeManager?.currentPlayingTrack
                    val playerMotionEnabled = themeConfig.motionLevel != com.nodeplayer.model.MotionLevel.OFF && themeConfig.animationPreset != "OFF"
                    AnimatedVisibility(
                        visible = isNowPlayingExpanded && activeNowPlayingTrack != null,
                        enter = slideInVertically(tween(if (playerMotionEnabled) 320 else 0, easing = FastOutSlowInEasing), initialOffsetY = { it }) + fadeIn(tween(if (playerMotionEnabled) 180 else 0)),
                        exit = slideOutVertically(tween(if (playerMotionEnabled) 280 else 0, easing = FastOutSlowInEasing), targetOffsetY = { it }) + fadeOut(tween(if (playerMotionEnabled) 240 else 0))
                    ) {
                        activeNowPlayingTrack?.let { track ->
                            var showAndroidPlaybackOptions by remember { mutableStateOf(false) }
                            var showAndroidQuickSettings by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxSize()) {
                                com.nodeplayer.ui.player.NowPlayingView(
                                    currentTrack = track,
                                    isPlaying = isPlaying,
                                    positionMs = 0L,
                                    positionProvider = { currentPositionState.value },
                                    durationMs = track.durationMs,
                                    queue = queue,
                                    currentQueueIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                    lrcContent = null,
                                    audioOutputName = audioOutputInfo.outputDeviceName.ifBlank { "This phone" },
                                    sourceLabel = if (currentRoute == "search") "Search" else "Library",
                                    onPlayPause = { viewModel.togglePlayPause() },
                                    onNext = { viewModel.skipNext() },
                                    onPrevious = { viewModel.skipPrevious() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onOpenLyrics = {
                                        isLyricsExpanded = true
                                    },
                                    onOpenNodeConnect = {
                                        showPairingDialog = true
                                    },
                                    onOpenPlaybackOptions = { showAndroidPlaybackOptions = true },
                                    onOpenThreeDotsMenu = { showAndroidQuickSettings = true },
                                    onClose = { isNowPlayingExpanded = false }
                                )

                                com.nodeplayer.ui.player.PlaybackOptionsSheet(
                                    visible = showAndroidPlaybackOptions,
                                    queue = queue,
                                    currentQueueIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                    isShuffle = isShuffleEnabled,
                                    repeatMode = 0,
                                    infiniteAutoplay = false,
                                    accentColor = accentColor,
                                    onToggleShuffle = { viewModel.toggleShuffle() },
                                    onCycleRepeat = { /* cycle repeat */ },
                                    onToggleInfiniteAutoplay = { /* toggle autoplay */ },
                                    onSelectQueueItem = { idx ->
                                        if (idx in queue.indices) viewModel.playTrack(queue[idx], queue)
                                    },
                                    onRemoveQueueItem = { idx -> viewModel.removeFromQueue(idx) },
                                    onDismiss = { showAndroidPlaybackOptions = false }
                                )

                                com.nodeplayer.ui.player.QuickSettingsSheet(
                                    visible = showAndroidQuickSettings,
                                    volume = 1.0f,
                                    outputDeviceName = audioOutputInfo.outputDeviceName.ifBlank { "This phone" },
                                    audioFormatDescription = audioOutputInfo.format,
                                    sleepTimerRemainingSec = sleepTimerRemainingSec,
                                    accentColor = accentColor,
                                    onVolumeChange = { /* volume */ },
                                    onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                    onOpenEqualizer = {
                                        isNowPlayingExpanded = false
                                        currentRoute = "equalizer"
                                    },
                                    onDismiss = { showAndroidQuickSettings = false }
                                )

                                // State 2: Full-Screen Synchronized Lyrics View
                                AnimatedVisibility(
                                    visible = isLyricsExpanded,
                                    enter = fadeIn(spring(stiffness = 380f)),
                                    exit = fadeOut(spring(stiffness = 380f))
                                ) {
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    var trackLyrics by remember(track.id) { mutableStateOf<com.nodeplayer.model.Lyrics?>(null) }
                                    var isLyricsLoading by remember(track.id) { mutableStateOf(false) }

                                    LaunchedEffect(track.id) {
                                        isLyricsLoading = true
                                        trackLyrics = com.nodeplayer.music.lyrics.LyricsManager.getLyrics(
                                            context = context,
                                            track = track,
                                            forceRefresh = false
                                        )
                                        isLyricsLoading = false
                                    }

                                    com.nodeplayer.ui.lyrics.FullScreenLyricsView(
                                        track = track,
                                        lyrics = trackLyrics,
                                        isPlaying = isPlaying,
                                        currentPositionMs = 0L,
                                        positionProvider = { currentPositionState.value },
                                        durationMs = track.durationMs,
                                        isLoading = isLyricsLoading,
                                        audioOutputName = audioOutputInfo.outputDeviceName.ifBlank { "This phone" },
                                        onPlayPause = { viewModel.togglePlayPause() },
                                        onNext = { viewModel.skipNext() },
                                        onPrevious = { viewModel.skipPrevious() },
                                        onSeekTo = { viewModel.seekTo(it) },
                                        onClose = { isLyricsExpanded = false },
                                        onOpenMenu = { showAndroidQuickSettings = true },
                                        onShare = {
                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Now Playing: ${track.title}")
                                                val shareText = buildString {
                                                    append("Listening to \"${track.title}\" by ${track.artist}")
                                                    val plain = trackLyrics?.plainText
                                                    if (!plain.isNullOrBlank()) {
                                                        append("\n\n")
                                                        append(plain.take(500))
                                                    }
                                                }
                                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                            }
                                            startActivity(android.content.Intent.createChooser(shareIntent, "Share Track & Lyrics"))
                                        },
                                        onOpenNodeConnect = { showPairingDialog = true },
                                        onOpenPlaybackOptions = { showAndroidPlaybackOptions = true }
                                    )
                                }
                            }
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

                    if (trackForAddToPlaylist != null) {
                        com.nodeplayer.ui.playlist.AddToPlaylistSheet(
                            track = trackForAddToPlaylist!!,
                            playlists = domainPlaylists,
                            onAddToPlaylist = { pid, tr ->
                                viewModel.addTrackToPlaylist(pid, tr)
                            },
                            onCreatePlaylist = { name, initial ->
                                viewModel.createPlaylist(name, initial)
                            },
                            onDismiss = { trackForAddToPlaylist = null }
                        )
                    }

                    if (showYouTubeLoginSheet) {
                        com.nodeplayer.music.ui.auth.YouTubeLoginSheet(
                            onDismiss = { showYouTubeLoginSheet = false },
                            onLoginSuccess = { showYouTubeLoginSheet = false }
                        )
                    }

                    if (showSpotifyDialog) {
                        com.nodeplayer.ui.spotify.SpotifyImportDialog(
                            isConfigured = !spotifyClientId.isNullOrBlank(),
                            isLoggedIn = isSpotifyLoggedIn,
                            playlists = spotifyPlaylists,
                            isLoadingPlaylists = isLoadingSpotifyPlaylists,
                            onLoginClick = {
                                val authUrl = com.nodeplayer.music.auth.SpotifyAccountManager.createAuthorizationUrl()
                                if (authUrl != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                        startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            onSaveClientId = { id ->
                                com.nodeplayer.music.auth.SpotifyAccountManager.setCustomClientId(this@MainActivity, id)
                            },
                            onSelectPlaylist = { pl ->
                                showSpotifyDialog = false
                                reviewingSpotifyPlaylist = pl
                                val token = com.nodeplayer.music.auth.SpotifyAccountManager.getValidAccessToken(this@MainActivity)
                                if (token != null) {
                                    isSpotifyMatching = true
                                    spotifyMatchingProgress = 0f
                                    spotifyMatchingJob?.cancel()
                                    spotifyMatchingJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val tracks = com.nodeplayer.music.auth.SpotifyAccountManager.client.getPlaylistTracks(token, pl.id).getOrDefault(emptyList())
                                            val ytClient = com.nodeplayer.music.auth.YouTubeAccountManager.innerTubeClient ?: com.nodeplayer.network.innertube.InnerTubeClient()
                                            val matcher = com.nodeplayer.network.spotify.SpotifyTrackMatcher(ytClient)

                                            val matched = mutableListOf<Pair<com.nodeplayer.network.spotify.SpotifyTrack, com.nodeplayer.model.Track>>()
                                            val uncertain = mutableListOf<Pair<com.nodeplayer.network.spotify.SpotifyTrack, List<com.nodeplayer.model.Track>>>()
                                            val notFound = mutableListOf<com.nodeplayer.network.spotify.SpotifyTrack>()

                                            tracks.forEachIndexed { idx, spotTrack ->
                                                spotifyMatchingCurrentTitle = spotTrack.title
                                                spotifyMatchingProgress = (idx + 1).toFloat() / tracks.size.toFloat()
                                                when (val res = matcher.matchTrack(spotTrack)) {
                                                    is com.nodeplayer.network.spotify.MatchResult.HighConfidence -> matched.add(spotTrack to res.candidate)
                                                    is com.nodeplayer.network.spotify.MatchResult.Uncertain -> uncertain.add(spotTrack to res.topCandidates)
                                                    is com.nodeplayer.network.spotify.MatchResult.NotFound -> notFound.add(spotTrack)
                                                }
                                            }

                                            spotifyMatchedTracks = matched
                                            spotifyUncertainTracks = uncertain
                                            spotifyNotFoundTracks = notFound
                                        } catch (t: Throwable) {
                                            com.nodeplayer.common.logging.NodeLog.e("SpotifyMatch", "Matching failed", t)
                                        } finally {
                                            isSpotifyMatching = false
                                            spotifyMatchingCurrentTitle = null
                                        }
                                    }
                                }
                            },
                            onDismiss = { showSpotifyDialog = false }
                        )
                    }

                    if (reviewingSpotifyPlaylist != null) {
                        com.nodeplayer.music.ui.screens.SpotifyTransferReviewScreen(
                            playlist = reviewingSpotifyPlaylist!!,
                            matchedTracks = spotifyMatchedTracks,
                            uncertainTracks = spotifyUncertainTracks,
                            notFoundTracks = spotifyNotFoundTracks,
                            isMatchingActive = isSpotifyMatching,
                            progressFraction = spotifyMatchingProgress,
                            currentMatchingTitle = spotifyMatchingCurrentTitle,
                            onCancelMatching = {
                                spotifyMatchingJob?.cancel()
                                isSpotifyMatching = false
                                spotifyMatchingCurrentTitle = null
                            },
                            onResolveUncertain = { spot, chosen ->
                                spotifyUncertainTracks = spotifyUncertainTracks.filter { it.first.id != spot.id }
                            },
                            onManualSearchForTrack = { _ -> },
                            onFinishTransfer = { resolvedTracks ->
                                val plName = reviewingSpotifyPlaylist?.name ?: "Spotify Playlist"
                                viewModel.createPlaylist(plName, resolvedTracks)
                                reviewingSpotifyPlaylist = null
                                safeToast("Transferred ${resolvedTracks.size} tracks into playlist '$plName'", Toast.LENGTH_SHORT)
                            },
                            onBack = {
                                spotifyMatchingJob?.cancel()
                                reviewingSpotifyPlaylist = null
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
                        com.nodeplayer.ui.screens.GlassSearchView(
                            onDismiss = { showSearchSheet = false },
                            onPlayTrack = { track, queue ->
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
                                    viewModel.playTrack(track, queue)
                                    isNowPlayingExpanded = true
                                }
                                showSearchSheet = false
                            },
                            onAlbumClick = { albumTrack: com.nodeplayer.model.Track ->
                                val cleanAlbumId = if (albumTrack.album.isNotBlank() && (albumTrack.album.startsWith("OLAK") || albumTrack.album.startsWith("MPRE"))) {
                                    albumTrack.album
                                } else {
                                    "album_${albumTrack.album.lowercase().replace(" ", "_")}"
                                }
                                val header = DetailMediaHeader(
                                    id = cleanAlbumId,
                                    title = albumTrack.album.ifBlank { albumTrack.title },
                                    subtitle = albumTrack.artist,
                                    artworkUrl = albumTrack.path,
                                    isAlbum = true
                                )
                                activePlaylistDetail = header to listOf<com.nodeplayer.model.Track>(albumTrack)
                                showSearchSheet = false
                                if (cleanAlbumId.startsWith("OLAK") || cleanAlbumId.startsWith("MPRE")) {
                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val result = com.nodeplayer.network.innertube.InnerTubeClient().use { client ->
                                            client.fetchPlaylistTracks(cleanAlbumId)
                                        }
                                        val loadedTracks = result.getOrDefault(emptyList())
                                        if (loadedTracks.isNotEmpty()) {
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                activePlaylistDetail = header to loadedTracks
                                            }
                                        }
                                    }
                                }
                            },
                            onPlaylistClick = { playlistTrack: com.nodeplayer.model.Track ->
                                val cleanPlaylistId = if (playlistTrack.album.isNotBlank() && (playlistTrack.album.startsWith("PL") || playlistTrack.album.startsWith("VL") || playlistTrack.album.startsWith("RD"))) {
                                    playlistTrack.album
                                } else {
                                    playlistTrack.path.substringAfter("nodeplayer://youtube/playlist/", playlistTrack.album)
                                }
                                val header = DetailMediaHeader(
                                    id = cleanPlaylistId,
                                    title = playlistTrack.title,
                                    subtitle = playlistTrack.artist.ifBlank { "YouTube Music" },
                                    artworkUrl = if (playlistTrack.path.startsWith("http")) playlistTrack.path else null,
                                    description = "YouTube Music Playlist",
                                    isAlbum = false
                                )
                                activePlaylistDetail = header to emptyList()
                                showSearchSheet = false
                                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val result = com.nodeplayer.network.innertube.InnerTubeClient().use { client ->
                                        client.fetchPlaylistTracks(cleanPlaylistId)
                                    }
                                    val loadedTracks = result.getOrDefault(emptyList())
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        activePlaylistDetail = header to loadedTracks
                                    }
                                }
                            },
                            backdrop = pageBackdrop
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
                                NodePlayerApplication.ensureFileLoggingInitialized(this@MainActivity)
                                val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("NodeLog", log))
                                safeToast("Debug log copied to clipboard (${log.length} chars)", Toast.LENGTH_SHORT)
                            },
                            onExportLogClicked = {
                                NodePlayerApplication.ensureFileLoggingInitialized(this@MainActivity)
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

    fun applyDisplayRefreshRate(mode: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                val modes = disp?.supportedModes ?: return
                val targetMode = when (mode.uppercase()) {
                    "60HZ" -> modes.filter { it.refreshRate in 58f..62f }.maxByOrNull { it.refreshRate }
                    "120HZ" -> modes.filter { it.refreshRate in 118f..122f }.maxByOrNull { it.refreshRate } ?: modes.maxByOrNull { it.refreshRate }
                    "HIGHEST" -> modes.maxByOrNull { it.refreshRate }
                    else -> modes.maxByOrNull { it.refreshRate } // AUTO defaults to highest available
                }
                if (targetMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = targetMode.modeId
                    window.attributes = params
                    Log.i("MainActivity", "Applied display refresh rate mode $mode: ${targetMode.modeId} (${targetMode.refreshRate} Hz)")
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Unable to set display refresh rate: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSpotifyCallbackIntent(intent)
    }

    private fun handleSpotifyCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nodeplayer" && uri.host == "spotify-callback") {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                com.nodeplayer.music.auth.SpotifyAccountManager.handleAuthorizationResponse(this@MainActivity, uri)
                    .onSuccess {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Spotify account connected!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { e ->
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Spotify login failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }
}

@Composable
private fun BoxScope.MainBottomDockOverlay(
    activeTrack: com.nodeplayer.model.Track?,
    isNowPlayingExpanded: Boolean,
    isPlaying: Boolean,
    themeConfig: com.nodeplayer.model.GlassThemeConfig,
    shaderActive: Boolean,
    dockScrollController: com.nodeplayer.ui.components.DockScrollController,
    hasActivePlaylistDetail: Boolean,
    currentRoute: String,
    accentColor: Color,
    pageBackdrop: com.kyant.backdrop.Backdrop,
    positionProvider: () -> Long,
    rmsProvider: () -> Float,
    onRouteChange: (String) -> Unit,
    onClearPlaylistDetail: () -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val motionEnabled = themeConfig.motionLevel != com.nodeplayer.model.MotionLevel.OFF && themeConfig.animationPreset != "OFF"
    val expansion = animateFloatAsState(
        targetValue = if (isNowPlayingExpanded) 1f else 0f,
        animationSpec = tween(if (motionEnabled) 280 else 0, easing = FastOutSlowInEasing), label = "dockPlayerTransition"
    )
    val dockScrollFractionState = dockScrollController.offsetFraction.collectAsState()
    val isCollapsedDock by remember(themeConfig.dockAutoHideEnabled, activeTrack, hasActivePlaylistDetail) {
        derivedStateOf {
            (themeConfig.dockAutoHideEnabled && dockScrollFractionState.value > 0.3f && activeTrack != null) ||
                (hasActivePlaylistDetail && activeTrack != null)
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .graphicsLayer {
                alpha = 1f - expansion.value
                translationY = 32.dp.toPx() * expansion.value
                scaleX = 1f - 0.04f * expansion.value
                scaleY = scaleX
            }
    ) {
        val srvDur = com.nodeplayer.music.service.PlaybackService.instance?.player?.duration ?: 0L
        val effectiveDuration = if (srvDur > 0L) srvDur else (activeTrack?.durationMs ?: 0L)
        val duration = effectiveDuration.coerceAtLeast(1L)
        val progressProvider = remember(positionProvider, duration) {
            { (positionProvider().toFloat() / duration.toFloat()).coerceIn(0f, 1f) }
        }

        if (isCollapsedDock && activeTrack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable {
                            if (hasActivePlaylistDetail) {
                                onClearPlaylistDetail()
                            } else {
                                onRouteChange("home")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasActivePlaylistDetail) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Home,
                        contentDescription = if (hasActivePlaylistDetail) "Back" else "Home",
                        tint = if (currentRoute == "home" && !hasActivePlaylistDetail) accentColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                ConvxMiniPlayer(
                    currentTrack = activeTrack,
                    isPlaying = isPlaying,
                    progressFraction = 0f,
                    progressProvider = progressProvider,
                    onPlayPauseClick = onPlayPause,
                    onSkipNextClick = onSkipNext,
                    onSkipPreviousClick = onSkipPrevious,
                    onClick = onExpandNowPlaying,
                    gesturesEnabled = false,
                    rmsLevel = 0f,
                    rmsProvider = rmsProvider,
                    isPerformanceMode = themeConfig.isPerformanceMode,
                    shaderEnabled = shaderActive,
                    chromaticFringe = themeConfig.chromaticFringeValue(),
                    backdrop = pageBackdrop,
                    isCollapsed = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable(onClick = onOpenSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else if (!hasActivePlaylistDetail) {
            if (activeTrack != null) {
                ConvxMiniPlayer(
                    currentTrack = activeTrack,
                    isPlaying = isPlaying,
                    progressFraction = 0f,
                    progressProvider = progressProvider,
                    onPlayPauseClick = onPlayPause,
                    onSkipNextClick = onSkipNext,
                    onSkipPreviousClick = onSkipPrevious,
                    onClick = onExpandNowPlaying,
                    gesturesEnabled = false,
                    rmsLevel = 0f,
                    rmsProvider = rmsProvider,
                    isPerformanceMode = themeConfig.isPerformanceMode,
                    shaderEnabled = shaderActive,
                    chromaticFringe = themeConfig.chromaticFringeValue(),
                    backdrop = pageBackdrop,
                    isCollapsed = false
                )
            }

            ConvxLiquidDock(
                selectedRoute = currentRoute,
                onRouteChange = onRouteChange,
                onSearchClick = onOpenSearch,
                isPerformanceMode = themeConfig.isPerformanceMode,
                shaderEnabled = shaderActive,
                chromaticFringe = themeConfig.chromaticFringeValue(),
                searchButtonVisible = themeConfig.showDockSearchButton,
                lensStrength = themeConfig.lensRefractionStrength,
                rimSpecular = themeConfig.lensRimSpecular,
                backdrop = pageBackdrop
            )
        }
    }
}
