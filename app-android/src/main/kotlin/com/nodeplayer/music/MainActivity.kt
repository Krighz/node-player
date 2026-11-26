package com.nodeplayer.music

import android.Manifest
import android.content.Context
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nodeplayer.music.ui.PlayerViewModel
import com.nodeplayer.music.diagnostics.DiagnosticsDumpManager
import com.nodeplayer.music.ui.components.DiagnosticsDumpDialog
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nodeplayer.music.service.PlaybackService
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.components.ConvxLiquidDock
import com.nodeplayer.ui.components.ConvxMiniPlayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.nodeplayer.ui.components.DockScrollController
import com.nodeplayer.ui.components.rememberDockScrollConnection
import com.nodeplayer.music.diagnostics.FrameMetricsTracker
import java.io.File
import com.nodeplayer.music.ui.components.LiquidGlassBackground
import com.nodeplayer.music.ui.screens.EqualizerScreen
import com.nodeplayer.ui.theme.LocalControlBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.music.ui.screens.LibraryScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.screens.DownloadsScreen
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
import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import com.nodeplayer.ui.components.NodeConnectPairingDialog
import androidx.compose.ui.graphics.asAndroidBitmap
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
        com.nodeplayer.music.ui.video.MusicVideoEngine.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

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

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val refreshMode = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
                .getString("display_refresh_rate", "AUTO") ?: "AUTO"
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                applyDisplayRefreshRate(refreshMode)
            }
        }

        checkAndRequestPermissions()
        handleSpotifyCallbackIntent(intent)
        handleGoogleDriveCallbackIntent(intent)

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
            val trackExtendedInfo by viewModel.trackExtendedInfo.collectAsState()
            val effectiveThemeConfig = remember(themeConfig, appSettings.glassControlsConfig) {
                themeConfig.copy(
                    glassTexture = 0.0f,
                    noiseGrainAlpha = 0.0f,
                    controlsConfig = appSettings.glassControlsConfig
                )
            }

            NodePlayerTheme(
                themeConfig = effectiveThemeConfig,
                artworkAccentColor = accentColor
            ) {
                val prefs = remember { getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE) }
                var showOnboarding by remember {
                    mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
                }

                val intentRoute = intent?.getStringExtra("route")
                var currentRoute by remember { mutableStateOf(intentRoute ?: "home") }
                var isNowPlayingExpanded by remember { mutableStateOf(false) }
                var isLyricsExpanded by remember { mutableStateOf(false) }
                val lyricsSettingsState by viewModel.lyricsSettings.collectAsState()

                val shouldHideStatusBar = appSettings.hideStatusBar || (isLyricsExpanded && lyricsSettingsState.hideStatusBar)
                LaunchedEffect(shouldHideStatusBar) {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    if (shouldHideStatusBar) {
                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        insetsController.systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    }
                }

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
                var activePlaylistDetail by remember { mutableStateOf<Pair<DetailMediaHeader, List<com.nodeplayer.model.Track>>?>(null) }
                val categoryShelvesCache = remember { mutableMapOf<String, List<com.nodeplayer.model.Playlist>>() }
                val playlistTracksCache = remember { mutableMapOf<String, List<com.nodeplayer.model.Track>>() }
                var isCategoryLoading by remember { mutableStateOf(false) }
                var isPlaylistDetailLoading by remember { mutableStateOf(false) }

                var showDownloadQueueSheet by remember { mutableStateOf(false) }
                var trackForFormatPicker by remember { mutableStateOf<com.nodeplayer.model.Track?>(null) }
                var pickerOptions by remember { mutableStateOf<List<com.nodeplayer.model.storage.AudioStreamOption>>(emptyList()) }
                var isResolvingPickerOptions by remember { mutableStateOf(false) }

                fun requestTrackDownload(track: com.nodeplayer.model.Track) {
                    val qualityPref = prefs.getString("download_default_quality", "ASK") ?: "ASK"
                    if (qualityPref == "ASK") {
                        trackForFormatPicker = track
                        pickerOptions = emptyList()
                        isResolvingPickerOptions = true
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val videoId = if (track.path.startsWith("online://")) track.path.removePrefix("online://") else track.id.toString()
                            val resolver = com.nodeplayer.network.downloader.StreamFormatResolver()
                            val res = resolver.resolveAvailableAudioOptions(videoId)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isResolvingPickerOptions = false
                                pickerOptions = res.getOrDefault(emptyList())
                            }
                        }
                    } else {
                        viewModel.enqueueDownload(track)
                        Toast.makeText(this@MainActivity, "Added to download queue", Toast.LENGTH_SHORT).show()
                    }
                }

                fun navigateToCategory(category: com.nodeplayer.model.MoodGenre, preloaded: List<com.nodeplayer.model.Playlist> = emptyList()) {
                    val cacheKey = "${category.browseId}:${category.params ?: ""}"
                    if (preloaded.isNotEmpty()) {
                        categoryShelvesCache[cacheKey] = preloaded
                        activeCategory = category to preloaded
                        return
                    }
                    val cached = categoryShelvesCache[cacheKey]
                    activeCategory = category to (cached ?: emptyList())
                    if (cached == null) {
                        isCategoryLoading = true
                        coroutineScope.launch {
                            try {
                                val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
                                        .browseCategoryShelves(category.browseId, category.params)
                                }
                                val fetched = res.getOrDefault(emptyList())
                                categoryShelvesCache[cacheKey] = fetched
                                if (activeCategory?.first?.browseId == category.browseId) {
                                    activeCategory = category to fetched
                                }
                            } catch (_: Exception) {
                            } finally {
                                isCategoryLoading = false
                            }
                        }
                    }
                }

                fun navigateToPlaylistDetail(
                    id: String,
                    title: String,
                    subtitle: String,
                    artworkUrl: String?,
                    initialTracks: List<com.nodeplayer.model.Track> = emptyList()
                ) {
                    val cached = playlistTracksCache[id] ?: initialTracks
                    val header = DetailMediaHeader(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        artworkUrl = artworkUrl,
                        description = if (cached.isNotEmpty()) "Explore ${cached.size} curated tracks from $title." else null
                    )
                    activePlaylistDetail = header to cached
                    if (cached.isEmpty()) {
                        isPlaylistDetailLoading = true
                        coroutineScope.launch {
                            try {
                                val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.nodeplayer.network.innertube.SharedInnerTubeProvider.get().fetchPlaylistTracks(id)
                                }
                                res.onSuccess { fetched ->
                                    playlistTracksCache[id] = fetched
                                    if (activePlaylistDetail?.first?.id == id) {
                                        activePlaylistDetail = header.copy(
                                            description = if (fetched.isNotEmpty()) "Explore ${fetched.size} curated tracks from $title." else null
                                        ) to fetched
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                isPlaylistDetailLoading = false
                            }
                        }
                    }
                }

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
                var spotifyMatchingProgress by remember { mutableFloatStateOf(0f) }
                var spotifyMatchingCurrentTitle by remember { mutableStateOf<String?>(null) }
                var spotifyMatchingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                var showPlaylistImportDialog by remember { mutableStateOf(false) }
                var importedPlaylistFileText by remember { mutableStateOf<String?>(null) }
                val openPlaylistDocument = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val text = com.nodeplayer.library.playlist.UniversalPlaylistParser.decodeBytesWithFallback(bytes)
                                if (text.isNotBlank()) {
                                    com.nodeplayer.common.logging.NodeLog.d("NP_IMPORT", "File opened successfully, length=${text.length}")
                                    importedPlaylistFileText = text
                                    showPlaylistImportDialog = true
                                } else {
                                    android.widget.Toast.makeText(this@MainActivity, "Selected file is empty", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            com.nodeplayer.common.logging.NodeLog.e("NP_IMPORT", "Failed to read chosen file", e)
                            android.widget.Toast.makeText(this@MainActivity, "Could not read file: ${e.localizedMessage ?: "Unknown error"}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val isSubRoute = currentRoute in listOf(
                    "stats", "recap", "smart_playlists", "duplicate_finder", "widgets", "widget_settings",
                    "playlist_detail", "settings", "equalizer", "optical_diagnostics", "storage", "storage_settings",
                    "audio_converter", "downloads", "artist_detail"
                )
                BackHandler(enabled = isLyricsExpanded || isNowPlayingExpanded || isSubRoute || selectedPlaylistForDetail != null || activePlaylistDetail != null || activeCategory != null) {
                    if (isLyricsExpanded) {
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
                        if (currentRoute == "downloads") {
                            currentRoute = "library"
                        } else if (currentRoute in listOf("storage", "storage_settings", "widgets", "widget_settings", "optical_diagnostics", "audio_converter")) {
                            currentRoute = "settings"
                        } else {
                            currentRoute = "home"
                        }
                    }
                }

                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
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
                val repeatMode by viewModel.repeatMode.collectAsState()
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
                val videoPlayerState by viewModel.videoPlayerState.collectAsState()

                LaunchedEffect(selectedPlaylistForDetail?.id) {
                    val pid = selectedPlaylistForDetail?.id
                    if (pid != null) {
                        viewModel.getTracksForPlaylist(pid).collect { tList ->
                            if (tList.isNotEmpty() || selectedPlaylistForDetail?.externalId.isNullOrBlank()) {
                                playlistDetailTracks = tList
                            }
                        }
                    }
                }

                LaunchedEffect(selectedPlaylistForDetail?.id, playlistDetailTracks.size) {
                    val pl = selectedPlaylistForDetail
                    if (pl != null && playlistDetailTracks.isEmpty() && !pl.externalId.isNullOrBlank()) {
                        val extId = pl.externalId!!
                        val cached = playlistTracksCache[extId]
                        if (cached != null) {
                            playlistDetailTracks = cached
                        } else {
                            val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.nodeplayer.network.innertube.SharedInnerTubeProvider.get().fetchPlaylistTracks(extId)
                            }
                            res.onSuccess { fetched ->
                                playlistTracksCache[extId] = fetched
                                playlistDetailTracks = fetched
                            }
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
                var searchInitialQuery by remember { mutableStateOf("") }
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
                    hub?.onAllowPrompt = { name, _, callback ->
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

                val audioManager = remember { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                var systemVolume by remember {
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    mutableFloatStateOf(current.toFloat() / max.toFloat())
                }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                systemVolume = current.toFloat() / max.toFloat()
                            }
                        }
                    }
                    val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                    registerReceiver(receiver, filter)
                    onDispose {
                        try {
                            unregisterReceiver(receiver)
                        } catch (_: Exception) {}
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
                val routeMotionEnabled = !effectiveThemeConfig.reduceMotion &&
                    !effectiveThemeConfig.isPerformanceMode &&
                    effectiveThemeConfig.animationPreset != "OFF"

                CompositionLocalProvider(
                    LocalControlBackdrop provides pageBackdrop
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(pageBackdrop)
                            .then(
                                if (effectiveThemeConfig.dockAutoHideEnabled) {
                                    Modifier.nestedScroll(
                                        rememberDockScrollConnection(
                                            controller = dockScrollController,
                                            enabled = true
                                        )
                                    )
                                } else Modifier
                            )
                    ) {
                        val gyroTiltState = com.nodeplayer.music.ui.rememberAndroidGyroscopeTiltState(
                            enabled = effectiveThemeConfig.gyroscopeParallaxEnabled,
                            tiltStrength = effectiveThemeConfig.artworkTiltStrength
                        )
                        val nodeHaptics = com.nodeplayer.ui.haptics.rememberNodeHaptics(effectiveThemeConfig.hapticsEnabled)

                        val handlePlayTrack: (com.nodeplayer.model.Track, List<com.nodeplayer.model.Track>) -> Unit = { track, q ->
                            nodeHaptics.click()
                            viewModel.playTrack(track, q)
                        }
                        val handleAddToQueue: (com.nodeplayer.model.Track) -> Unit = { track ->
                            nodeHaptics.impact()
                            viewModel.addToQueue(track)
                            safeToast("Added \"${track.title}\" to Up Next", Toast.LENGTH_SHORT)
                        }
                        val handlePlayNext: (com.nodeplayer.model.Track) -> Unit = { track ->
                            nodeHaptics.impact()
                            viewModel.playNext(track)
                            safeToast("Playing \"${track.title}\" next", Toast.LENGTH_SHORT)
                        }

                        CompositionLocalProvider(
                            LocalControlBackdrop provides remember { emptyBackdrop() },
                            com.nodeplayer.ui.theme.LocalGyroscopeTilt provides gyroTiltState
                        ) {
                        LiquidGlassBackground(
                            accentColor = accentColor,
                            isOled = isOled,
                            isLight = !isDark && !isOled,
                            isPlaying = isPlaying,
                            isPerformanceMode = effectiveThemeConfig.isPerformanceMode,
                            animatedBackdrop = effectiveThemeConfig.animatedBackdrop,
                            glassStrength = effectiveThemeConfig.glassStrength,
                            themeConfig = effectiveThemeConfig
                        )

                        val mainOverlay = when {
                            activePlaylistDetail != null -> "playlist_detail"
                            activeCategory != null -> "category"
                            else -> "route"
                        }
                        AnimatedContent(
                            targetState = mainOverlay,
                            transitionSpec = {
                                if (!routeMotionEnabled) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else if (targetState == "playlist_detail" || targetState == "category") {
                                    (scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.90f) + fadeIn(tween(220))) togetherWith
                                        (scaleOut(spring(dampingRatio = 0.82f, stiffness = 380f), targetScale = 0.94f) + fadeOut(tween(180)))
                                } else {
                                    (scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.94f) + fadeIn(tween(200))) togetherWith
                                        (scaleOut(spring(dampingRatio = 0.82f, stiffness = 380f), targetScale = 0.90f) + fadeOut(tween(180)))
                                }
                            },
                            label = "MainOverlayTransition"
                        ) { overlay ->
                            when (overlay) {
                                "playlist_detail" -> {
                                    val (header, detailTracks) = activePlaylistDetail ?: return@AnimatedContent
                                    val playOrOpenTrack: (com.nodeplayer.model.Track, List<com.nodeplayer.model.Track>) -> Unit = { track, q ->
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
                                        } else viewModel.playTrack(track, q)
                                    }
                                    com.nodeplayer.ui.screens.PlaylistDetailView(
                                        header = header,
                                        tracks = detailTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        isSavedInLibrary = false,
                                        isLoading = isPlaylistDetailLoading && detailTracks.isEmpty(),
                                        onBack = { activePlaylistDetail = null },
                                        onPlayTrack = handlePlayTrack,
                                        onPlayAll = { q -> if (q.isNotEmpty()) handlePlayTrack(q[0], q) },
                                        onShuffleAll = { q ->
                                            if (q.isNotEmpty()) {
                                                val shuffled = q.shuffled()
                                                handlePlayTrack(shuffled[0], shuffled)
                                            }
                                        },
                                        onToggleSaveLibrary = { h ->
                                            viewModel.createPlaylist(h.title, detailTracks)
                                            safeToast("Saved ${detailTracks.size} tracks to library", Toast.LENGTH_SHORT)
                                        },
                                        onTrackOptionsClick = { trackForAddToPlaylist = it }
                                    )
                                }
                                "category" -> {
                                    val (category, playlists) = activeCategory ?: return@AnimatedContent
                                    com.nodeplayer.ui.screens.CategoryPlaylistsView(
                                        title = category.title,
                                        playlists = playlists,
                                        onBack = { activeCategory = null },
                                        loadingPlaylistId = loadingCategoryPlaylistId,
                                        isLoading = isCategoryLoading,
                                        onOpenPlaylist = { playlist ->
                                            val playlistId = playlist.externalId ?: playlist.id.toString()
                                            navigateToPlaylistDetail(
                                                id = playlistId,
                                                title = playlist.name,
                                                subtitle = category.title,
                                                artworkUrl = playlist.thumbnailUrl
                                            )
                                        }
                                    )
                                }
                                else -> {
                                    AnimatedContent(
                                        targetState = currentRoute,
                                        transitionSpec = {
                                            if (!routeMotionEnabled) {
                                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                            } else if (effectiveThemeConfig.screenTransitionStyle == "FADE") {
                                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                                            } else {
                                                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 400f)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                                    (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 400f)) { -it / 4 } + fadeOut(tween(180)))
                                            }
                                        },
                                        label = "MainRouteTransition"
                                    ) { route ->
                                when (route) {
                                    "home", "new", "explore" -> com.nodeplayer.music.ui.screens.HomeScreen(
                                        allTracks = allTracks,
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        currentRoute = route,
                                        dockScrollController = dockScrollController,
                                        onSearchClick = { showSearchSheet = true },
                                        onTrackClick = handlePlayTrack,
                                        onPlayPauseClick = {
                                            nodeHaptics.click()
                                            viewModel.togglePlayPause()
                                        },
                                        onOpenSmartPlaylists = { currentRoute = "smart_playlists" },
                                        onOpenStats = { currentRoute = "stats" },
                                        onOpenDuplicateFinder = { currentRoute = "duplicate_finder" },
                                        onOpenNodeConnect = { showPairingDialog = true },
                                        onOpenEqualizer = { currentRoute = "equalizer" },
                                        onOpenSettings = { currentRoute = "settings" },
                                        onOpenOfflineMusic = { com.nodeplayer.common.AppModeState.setOnlineMode(false) },
                                        onSignInClick = { showYouTubeLoginSheet = true },
                                        onOpenCategory = { category, playlists -> navigateToCategory(category, playlists) },
                                        onCategoryClick = { category -> navigateToCategory(category) },
                                        onNavigateToExplore = { currentRoute = "explore" },
                                        onOpenPlaylistDetail = { id, title, subtitle, artworkUrl, items ->
                                            navigateToPlaylistDetail(id, title, subtitle, artworkUrl, items)
                                        },
                                        nodeConnectStatus = if (connectedPeers.isNotEmpty()) com.nodeplayer.ui.components.DeviceConnectStatus.CONNECTED
                                            else if (discoveredPeers.isNotEmpty() || activeSession != null) com.nodeplayer.ui.components.DeviceConnectStatus.DISCOVERING
                                            else com.nodeplayer.ui.components.DeviceConnectStatus.DISCONNECTED,
                                        connectedDeviceCount = connectedPeers.size,
                                        onStartUpdate = { updateInfo ->
                                            UpdateDownloadManager.startDownload(this@MainActivity, updateInfo)
                                        },
                                        onCancelUpdate = {
                                            UpdateDownloadManager.cancelDownload(this@MainActivity)
                                        },
                                        onInstallUpdate = {
                                            val readyApk = (downloadStatus as? DownloadStatus.ReadyToInstall)?.apkFile
                                                ?: UpdateDownloadManager.getDownloadedApk(this@MainActivity, UpdateManager.availableUpdate.value?.versionName ?: "")
                                            if (readyApk != null) {
                                                performInstall(readyApk)
                                            } else {
                                                safeToast("Download not ready yet", Toast.LENGTH_SHORT)
                                            }
                                        }
                                    )
                                    "library" -> {
                                        LaunchedEffect(Unit) { viewModel.syncYouTubePlaylists() }
                                        if (selectedPlaylistForDetail != null) {
                                            val pl = selectedPlaylistForDetail!!
                                            val header = remember(pl.id, pl.name, pl.source, playlistDetailTracks, pl.thumbnailUrl) {
                                                DetailMediaHeader(
                                                    id = pl.id.toString(),
                                                    title = pl.name,
                                                    subtitle = "${pl.source.replaceFirstChar { it.uppercase() }} • ${if (playlistDetailTracks.isNotEmpty()) playlistDetailTracks.size.toString() + " tracks" else (if (pl.trackCount > 0) pl.trackCount.toString() + " tracks" else "Playlist")}",
                                                    artworkUrl = playlistDetailTracks.firstOrNull()?.path ?: pl.thumbnailUrl,
                                                    description = "Saved in local library."
                                                )
                                            }
                                            com.nodeplayer.ui.screens.PlaylistDetailView(
                                                header = header,
                                                tracks = playlistDetailTracks,
                                                currentTrack = currentTrack,
                                                isPlaying = isPlaying,
                                                isSavedInLibrary = true,
                                                isLoading = playlistDetailTracks.isEmpty() && !pl.externalId.isNullOrBlank(),
                                                onBack = { selectedPlaylistForDetail = null },
                                                onPlayTrack = handlePlayTrack,
                                                onPlayAll = { q -> if (q.isNotEmpty()) handlePlayTrack(q[0], q) },
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
                                                onTrackClick = handlePlayTrack,
                                                onRescanClick = {
                                                    checkAndRequestPermissions()
                                                    viewModel.refreshLibrary()
                                                    viewModel.syncYouTubePlaylists(force = true)
                                                },
                                                onPlayNext = handlePlayNext,
                                                onAddToQueue = handleAddToQueue,
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
                                                onOpenPlaylist = { pl ->
                                                    selectedPlaylistForDetail = pl
                                                    if (pl.externalId != null) {
                                                        val cached = playlistTracksCache[pl.externalId!!]
                                                        if (cached != null) {
                                                            playlistDetailTracks = cached
                                                        }
                                                    }
                                                },
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
                                                onImportPlaylistFile = {
                                                    showPlaylistImportDialog = true
                                                },
                                                onAddToPlaylist = { trackForAddToPlaylist = it },
                                                dailyStats = dailyStats,
                                                onOpenStats = { currentRoute = "stats" },
                                                onOpenSettings = { currentRoute = "settings" },
                                                onOpenDownloadQueue = { showDownloadQueueSheet = true },
                                                onOpenDownloads = { currentRoute = "downloads" },
                                                onDownloadTrack = {
                                                    viewModel.enqueueDownload(it)
                                                    android.widget.Toast.makeText(this@MainActivity, "Added to download queue: ${it.title}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
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
                                        rmsProvider = { viewModel.currentRms.value },
                                        isPerformanceMode = effectiveThemeConfig.isPerformanceMode,
                                        onBack = { currentRoute = "home" }
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
                                        }
                                    )
                                    "graphics_diagnostics" -> com.nodeplayer.music.ui.screens.GraphicsDiagnosticsScreen(
                                        themeConfig = effectiveThemeConfig,
                                        selfTestResult = selfTestResult,
                                        onToggleForceFail = { viewModel.toggleForceSelfTestFail(it) },
                                        onReportProblemClick = { showDiagnosticsDialog = true },
                                        onBack = { currentRoute = "more" }
                                    )
                                    "stats" -> com.nodeplayer.music.ui.screens.StatsScreen(
                                        dailyStats = dailyStats,
                                        allTracks = allTracks,
                                        onBack = { currentRoute = "more" },
                                        onOpenRecap = { currentRoute = "recap" }
                                    )
                                    "recap" -> com.nodeplayer.music.ui.screens.YearlyRecapScreen(
                                        dailyStats = dailyStats,
                                        allTracks = allTracks,
                                        onBack = { currentRoute = "more" }
                                    )
                                    "smart_playlists" -> com.nodeplayer.music.ui.screens.SmartPlaylistsScreen(
                                        allTracks = allTracks,
                                        onPlayTracks = { q ->
                                            if (q.isNotEmpty()) {
                                                viewModel.playTrack(q[0], q)
                                            }
                                        },
                                        onBack = { currentRoute = "more" }
                                    )
                                    "duplicate_finder" -> com.nodeplayer.music.ui.screens.DuplicateFinderScreen(
                                        allTracks = allTracks,
                                        onBack = { currentRoute = "more" },
                                        onTrackDeleted = { viewModel.refreshLibrary() }
                                    )
                                    "artist_detail" -> {
                                        val artistData by viewModel.artistPageData.collectAsState()
                                        val isArtistLoading by viewModel.isArtistPageLoading.collectAsState()
                                        com.nodeplayer.ui.screens.ArtistDetailView(
                                            artistData = artistData,
                                            isLoading = isArtistLoading,
                                            onBack = { currentRoute = "home" },
                                            onPlayTrack = { trk -> viewModel.playTrack(trk) },
                                            onShuffleArtist = {
                                                artistData?.topSongs?.let { songs ->
                                                    if (songs.isNotEmpty()) {
                                                        val shuffled = songs.shuffled()
                                                        viewModel.playTrack(shuffled.first(), shuffled)
                                                    }
                                                }
                                            },
                                            onToggleFollow = {
                                                artistData?.let { art ->
                                                    viewModel.toggleFollowArtist(art.browseId, art.artistName, art.heroImageUrl)
                                                }
                                            },
                                            onSelectRelease = { rel ->
                                                navigateToPlaylistDetail(
                                                    id = rel.id,
                                                    title = rel.title,
                                                    subtitle = "${artistData?.artistName ?: "Artist"} • ${rel.type} ${rel.year}".trim(),
                                                    artworkUrl = rel.artworkUrl
                                                )
                                            },
                                            onSelectPlaylist = { pl ->
                                                navigateToPlaylistDetail(
                                                    id = pl.playlistId,
                                                    title = pl.title,
                                                    subtitle = pl.author ?: (artistData?.artistName ?: "YouTube Music"),
                                                    artworkUrl = pl.thumbnailUrl
                                                )
                                            },
                                            onSelectVideo = { vid ->
                                                viewModel.playTrack(
                                                    com.nodeplayer.model.Track(
                                                        id = kotlin.math.abs(vid.videoId.hashCode().toLong()),
                                                        title = vid.title,
                                                        artist = artistData?.artistName ?: "YouTube Music",
                                                        album = "Music Video",
                                                        durationMs = 0L,
                                                        path = "nodeplayer://youtube/${vid.videoId}",
                                                        formatDescription = "YouTube Audio"
                                                    )
                                                )
                                            },
                                            onSelectSimilarArtist = { sim ->
                                                viewModel.loadArtistPage(sim.browseId)
                                            },
                                            onOptionsClick = { trk ->
                                                trackForAddToPlaylist = trk
                                            }
                                        )
                                    }
                                    "settings" -> SettingsScreen(
                                        outputInfo = audioOutputInfo,
                                        themeConfig = effectiveThemeConfig,
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
                                        allTracks = allTracks,
                                        effectConfig = effectConfig,
                                        selfTestResult = selfTestResult,
                                        selectedVideoQuality = videoPlayerState.selectedQuality,
                                        onVideoQualityChange = { viewModel.setPreferredVideoQuality(it) },
                                        onOpenAudioConverter = { currentRoute = "audio_converter" },
                                        onOpenStorageManager = { currentRoute = "storage" },
                                        onBack = { currentRoute = "home" }
                                    )
                                    "widgets", "widget_settings" -> com.nodeplayer.music.ui.screens.WidgetSettingsScreen(
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        onBack = { currentRoute = "settings" }
                                    )
                                    "storage", "storage_settings" -> com.nodeplayer.music.ui.screens.StorageSettingsScreen(
                                        onBack = { currentRoute = "settings" }
                                    )
                                    "optical_diagnostics" -> com.nodeplayer.ui.screens.OpticalDiagnosticsView(
                                        onBack = { currentRoute = "settings" },
                                        themeConfig = effectiveThemeConfig
                                    )
                                    "audio_converter" -> {
                                        val androidTranscoder = remember { com.nodeplayer.music.converter.AndroidFFmpegTranscoder() }
                                        val androidConverterManager = remember {
                                            com.nodeplayer.library.converter.TranscodeQueueManager(
                                                transcoder = androidTranscoder
                                            )
                                        }
                                        var selectedAndroidFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                                        val defaultConvertedDir = remember {
                                            val musicDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: filesDir
                                            java.io.File(musicDir, "Converted").also { it.mkdirs() }
                                        }
                                        var destinationAndroidFolder by remember { mutableStateOf<java.io.File?>(defaultConvertedDir) }
                                        val flacs = remember(allTracks) {
                                            allTracks.map { java.io.File(it.path) }.filter { it.extension.equals("flac", ignoreCase = true) && it.exists() }
                                        }

                                        val pickFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                                        ) { uri ->
                                            if (uri != null) {
                                                try {
                                                    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                                                    if (docId.startsWith("primary:")) {
                                                        val rel = docId.substringAfter("primary:")
                                                        val f = java.io.File(android.os.Environment.getExternalStorageDirectory(), rel)
                                                        if (f.exists() || f.mkdirs()) {
                                                            destinationAndroidFolder = f
                                                        }
                                                    } else {
                                                        destinationAndroidFolder = defaultConvertedDir
                                                    }
                                                } catch (_: Exception) {
                                                    destinationAndroidFolder = defaultConvertedDir
                                                }
                                            }
                                        }

                                        val pickMultipleDocumentsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                            contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
                                        ) { uris ->
                                            if (uris.isNotEmpty()) {
                                                val resolved = uris.mapNotNull { uri ->
                                                    try {
                                                        if (uri.scheme == "file") {
                                                            java.io.File(uri.path ?: "")
                                                        } else {
                                                            val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                                                if (cursor.moveToFirst()) cursor.getString(0) else null
                                                            }
                                                            if (name != null) {
                                                                allTracks.find { java.io.File(it.path).name.equals(name, ignoreCase = true) }?.let { java.io.File(it.path) }
                                                                    ?: run {
                                                                        val tempFile = java.io.File(cacheDir, "converter_in_$name")
                                                                        contentResolver.openInputStream(uri)?.use { input ->
                                                                            tempFile.outputStream().use { output -> input.copyTo(output) }
                                                                        }
                                                                        tempFile
                                                                    }
                                                            } else null
                                                        }
                                                    } catch (_: Exception) { null }
                                                }.filter { it.exists() }
                                                selectedAndroidFiles = (selectedAndroidFiles + resolved).distinctBy { it.absolutePath }
                                            }
                                        }

                                        com.nodeplayer.ui.converter.AudioConverterView(
                                            queueManager = androidConverterManager,
                                            selectedFiles = selectedAndroidFiles,
                                            candidateFiles = flacs,
                                            onSelectedFilesChange = { selectedAndroidFiles = it },
                                            onPickIndividualFiles = {
                                                pickMultipleDocumentsLauncher.launch(arrayOf("audio/*", "application/octet-stream", "*/*"))
                                            },
                                            onPickFiles = {
                                                selectedAndroidFiles = flacs
                                            },
                                            destinationFolder = destinationAndroidFolder,
                                            onPickDestinationFolder = {
                                                pickFolderLauncher.launch(null)
                                            },
                                            onBack = { currentRoute = "settings" }
                                        )
                                    }
                                    "downloads" -> DownloadsScreen(
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        onBack = { currentRoute = "library" },
                                        onTrackClick = handlePlayTrack,
                                        onPlayNext = handlePlayNext,
                                        onAddToQueue = handleAddToQueue,
                                        onAddToPlaylist = { trackForAddToPlaylist = it },
                                        onOpenDownloadQueue = { showDownloadQueueSheet = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

                    if (currentRoute !in listOf("widget_settings", "widgets", "optical_diagnostics", "settings", "equalizer", "audio_converter", "downloads", "storage", "storage_settings")) {
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
                            positionProvider = { viewModel.currentPositionMs.value },
                            rmsProvider = { viewModel.currentRms.value },
                            onRouteChange = { currentRoute = it },
                            onClearPlaylistDetail = { if (activePlaylistDetail != null) activePlaylistDetail = null else activeCategory = null },
                            onOpenSearch = { showSearchSheet = true },
                            onExpandNowPlaying = { isNowPlayingExpanded = true },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() }
                        )
                    }

                    val activeNowPlayingTrack = currentTrack ?: com.nodeplayer.music.service.PlaybackService.instance?.crossfadeManager?.currentPlayingTrack
                    val playerMotionEnabled = effectiveThemeConfig.motionLevel != com.nodeplayer.model.MotionLevel.OFF && effectiveThemeConfig.animationPreset != "OFF"

                    val sheetDragOffsetY = remember { Animatable(0f) }
                    val screenHeightPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
                    val composeScope = rememberCoroutineScope()
                    var dragAnimJob by remember { mutableStateOf<Job?>(null) }
                    var isDismissingSheet by remember { mutableStateOf(false) }

                    // Background parallel thread lyrics pre-caching
                    val activityContext = androidx.compose.ui.platform.LocalContext.current
                    val currentLyricsSettings by viewModel.lyricsSettings.collectAsState()
                    var cachedLyrics by remember(activeNowPlayingTrack?.id) { mutableStateOf<com.nodeplayer.model.Lyrics?>(null) }
                    var isLyricsPreloaded by remember(activeNowPlayingTrack?.id) { mutableStateOf(false) }

                    LaunchedEffect(activeNowPlayingTrack?.id, currentLyricsSettings.preferredProvider) {
                        val trk = activeNowPlayingTrack ?: return@LaunchedEffect
                        isLyricsPreloaded = false
                        withContext(Dispatchers.IO) {
                            val result = com.nodeplayer.music.lyrics.LyricsManager.getLyrics(
                                context = activityContext,
                                track = trk,
                                forceRefresh = currentLyricsSettings.preferredProvider != com.nodeplayer.model.LyricsProviderChoice.AUTO,
                                provider = currentLyricsSettings.preferredProvider
                            )
                            withContext(Dispatchers.Main) {
                                cachedLyrics = result
                                isLyricsPreloaded = true
                            }
                        }
                    }

                    // Auto-fullscreen when rotating to landscape in video mode
                    val currentOrientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
                    val isLandscape = currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                    LaunchedEffect(isLandscape, videoPlayerState.isVideoActive) {
                        if (videoPlayerState.isVideoActive) {
                            viewModel.setCinemaExpanded(isLandscape)
                        }
                    }

                    LaunchedEffect(isNowPlayingExpanded) {
                        if (isNowPlayingExpanded) {
                            sheetDragOffsetY.snapTo(0f)
                            isDismissingSheet = false
                        }
                    }

                    AnimatedVisibility(
                        visible = isNowPlayingExpanded && activeNowPlayingTrack != null,
                        enter = slideInVertically(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            initialOffsetY = { it }
                        ) + fadeIn(tween(if (playerMotionEnabled) 180 else 0)),
                        exit = if (sheetDragOffsetY.value > screenHeightPx * 0.5f) {
                            fadeOut(tween(if (playerMotionEnabled) 120 else 0))
                        } else {
                            slideOutVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                targetOffsetY = { it }
                            ) + fadeOut(tween(if (playerMotionEnabled) 200 else 0))
                        }
                    ) {
                        activeNowPlayingTrack?.let { track ->
                            var showAndroidPlaybackOptions by remember { mutableStateOf(false) }
                            var showAndroidQuickSettings by remember { mutableStateOf(false) }
                            var activeNowPlayingPalette by remember(track.id) {
                                mutableStateOf(com.nodeplayer.ui.theme.PaletteExtractor.getCachedPalette(track))
                            }

                            val androidLyricsFontMap = remember {
                                mapOf(
                                    com.nodeplayer.model.LyricsFontChoice.INTER to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.inter)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.OUTFIT to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.outfit)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.JETBRAINS_MONO to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.jetbrains_mono)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.PAPYRUS to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.papyrus)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.MINECRAFT to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.minecraft)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.PATRICK_HAND to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.patrick_hand)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.COMFORTAA to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.comfortaa)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.CAVEAT to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.caveat)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.LOBSTER to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.lobster)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.BEBAS_NEUE to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.bebas_neue)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.ORBITRON to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.orbitron)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.PLAYFAIR to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.playfair_display)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.DANCING_SCRIPT to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.dancing_script)
                                    ),
                                    com.nodeplayer.model.LyricsFontChoice.CINZEL to androidx.compose.ui.text.font.FontFamily(
                                        androidx.compose.ui.text.font.Font(R.font.cinzel)
                                    )
                                )
                            }

                            val androidLyricsFontResolver: (com.nodeplayer.model.LyricsFontChoice) -> androidx.compose.ui.text.font.FontFamily = remember(androidLyricsFontMap) {
                                { choice -> androidLyricsFontMap[choice] ?: (androidLyricsFontMap[com.nodeplayer.model.LyricsFontChoice.INTER] ?: androidx.compose.ui.text.font.FontFamily.SansSerif) }
                            }

                            val activeLyricsFont = androidLyricsFontResolver(currentLyricsSettings.fontChoice)

                            val isLocalVideo = (track.path.startsWith("/") || track.path.startsWith("file:") || track.path.startsWith("content:")) &&
                                (track.mimeType.contains("video", ignoreCase = true) ||
                                 track.formatDescription.contains("video", ignoreCase = true) ||
                                 track.genre?.contains("video", ignoreCase = true) == true ||
                                 track.path.contains("video", ignoreCase = true))

                            val isVideoAvailable = isLocalVideo ||
                                track.mimeType.contains("video", ignoreCase = true) ||
                                track.formatDescription.contains("video", ignoreCase = true) ||
                                track.genre?.contains("video", ignoreCase = true) == true ||
                                track.path.contains("video", ignoreCase = true) ||
                                track.path.startsWith("nodeplayer://youtube/") ||
                                track.path.startsWith("http") ||
                                videoPlayerState.isAvailable

                            val dragOffset = sheetDragOffsetY.value
                            val dragProgress = (dragOffset / screenHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val smoothCurve = ((1f - dragProgress) * (1f - dragProgress)).coerceIn(0f, 1f)
                            val backdropBlurRadius = (36f * smoothCurve).dp
                            val backdropScrimAlpha = 0.75f * smoothCurve
                            val sheetScale = 1f - (dragProgress * 0.08f)
                            val sheetCornerRadius = (dragProgress * 32f).dp

                            CompositionLocalProvider(
                                com.nodeplayer.ui.lyrics.LocalLyricsFontFamily provides activeLyricsFont,
                                com.nodeplayer.ui.lyrics.LocalLyricsFontResolver provides androidLyricsFontResolver
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Deep glass blur that dissolves smoothly along a curve as dragged down
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = smoothCurve
                                            }
                                            .background(Color.Black.copy(alpha = backdropScrimAlpha))
                                            .blur(backdropBlurRadius)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationY = dragOffset
                                                scaleX = sheetScale
                                                scaleY = sheetScale
                                                alpha = if (isDismissingSheet) smoothCurve else 1f
                                                clip = dragProgress > 0.005f
                                                shape = RoundedCornerShape(sheetCornerRadius)
                                            }
                                    ) {
                                    com.nodeplayer.ui.player.NowPlayingView(
                                        currentTrack = track,
                                        isPlaying = isPlaying,
                                        positionMs = 0L,
                                        positionProvider = { viewModel.currentPositionMs.value },
                                        durationMs = track.durationMs,
                                        queue = queue,
                                        currentQueueIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                        lrcContent = cachedLyrics?.plainText,
                                        audioOutputName = audioOutputInfo.outputDeviceName.ifBlank { "This Device" },
                                        sourceLabel = if (currentRoute == "search") "Search" else "Library",
                                        palette = activeNowPlayingPalette,
                                        onPaletteChanged = { activeNowPlayingPalette = it },
                                        volume = systemVolume,
                                        onVolumeChange = { newVol ->
                                            systemVolume = newVol
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val target = (newVol * max).roundToInt().coerceIn(0, max)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                        },
                                        videoContent = { mod ->
                                            val livePlayer = com.nodeplayer.music.service.PlaybackService.instance?.player
                                            com.nodeplayer.music.ui.video.VideoSurface(
                                                modifier = mod,
                                                priority = com.nodeplayer.model.video.SurfacePriority.NOW_PLAYING_CARD,
                                                player = if (isLocalVideo) livePlayer else null,
                                                onFirstFrameRendered = { viewModel.onVideoFirstFrameRendered() }
                                            )
                                        },
                                        isVideoActive = videoPlayerState.isVideoActive || isLocalVideo,
                                        isVideoAvailable = isVideoAvailable,
                                        hasRenderedFirstFrame = videoPlayerState.hasRenderedFirstFrame,
                                        currentHeight = videoPlayerState.currentHeight,
                                        availableHeights = videoPlayerState.availableHeights,
                                        videoAspect = videoPlayerState.aspect,
                                        onToggleVideo = { enable -> viewModel.toggleVideoMode(enable, track) },
                                        onSelectQuality = { h ->
                                            viewModel.switchVideoQuality(com.nodeplayer.model.video.VideoQuality.fromHeight(h))
                                        },
                                        onOpenFullscreenCinema = { viewModel.setCinemaExpanded(true) },
                                        onPlayPause = { viewModel.togglePlayPause() },
                                        onNext = { viewModel.skipNext() },
                                        onPrevious = { viewModel.skipPrevious() },
                                        onSeek = { viewModel.seekTo(it) },
                                        onOpenLyrics = { isLyricsExpanded = true },
                                        onOpenNodeConnect = { showPairingDialog = true },
                                        onOpenPlaybackOptions = { showAndroidPlaybackOptions = true },
                                        onOpenThreeDotsMenu = { showAndroidQuickSettings = true },
                                        artZoomScale = effectiveThemeConfig.artZoomScale,
                                        artVerticalOffsetDp = effectiveThemeConfig.artVerticalOffsetDp,
                                        extendedInfo = trackExtendedInfo,
                                        onPlayTrack = { newTrack -> viewModel.playTrack(newTrack, listOf(newTrack)) },
                                        onArtistClick = { artistName, browseId ->
                                            isNowPlayingExpanded = false
                                            if (!browseId.isNullOrBlank()) {
                                                viewModel.loadArtistPage(browseId)
                                                currentRoute = "artist_detail"
                                            } else {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val client = com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
                                                    val resolvedId = client.searchArtistBrowseId(artistName)
                                                    withContext(Dispatchers.Main) {
                                                        if (resolvedId != null) {
                                                            viewModel.loadArtistPage(resolvedId)
                                                            currentRoute = "artist_detail"
                                                        } else {
                                                            searchInitialQuery = artistName
                                                            showSearchSheet = true
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onTrackOptionsClick = { trackForAddToPlaylist = it },
                                        onDragDelta = { delta ->
                                            if (!isDismissingSheet) {
                                                dragAnimJob?.cancel()
                                                dragAnimJob = composeScope.launch {
                                                    try {
                                                        val next = (sheetDragOffsetY.value + delta).coerceAtLeast(0f)
                                                        sheetDragOffsetY.snapTo(next)
                                                    } catch (_: CancellationException) {}
                                                }
                                            }
                                        },
                                        onDragEnd = { velocityY ->
                                            if (!isDismissingSheet) {
                                                dragAnimJob?.cancel()
                                                dragAnimJob = composeScope.launch {
                                                    try {
                                                        val currentOffset = sheetDragOffsetY.value
                                                        val dismissThreshold = screenHeightPx * 0.40f
                                                        if (currentOffset > dismissThreshold || velocityY > 1000f) {
                                                            isDismissingSheet = true
                                                            sheetDragOffsetY.animateTo(
                                                                targetValue = screenHeightPx,
                                                                animationSpec = spring(stiffness = 320f, dampingRatio = 0.85f)
                                                            )
                                                            isNowPlayingExpanded = false
                                                            isDismissingSheet = false
                                                        } else {
                                                            sheetDragOffsetY.animateTo(
                                                                targetValue = 0f,
                                                                animationSpec = spring(stiffness = 350f, dampingRatio = 0.82f)
                                                            )
                                                        }
                                                    } catch (_: CancellationException) {
                                                    } catch (e: Exception) {
                                                        com.nodeplayer.common.logging.NodeLog.e("NowPlaying", "Error during drag release", e)
                                                    }
                                                }
                                            }
                                        },
                                        onClose = {
                                            if (!isDismissingSheet) {
                                                dragAnimJob?.cancel()
                                                dragAnimJob = composeScope.launch {
                                                    try {
                                                        isDismissingSheet = true
                                                        sheetDragOffsetY.animateTo(
                                                            targetValue = screenHeightPx,
                                                            animationSpec = spring(stiffness = 320f, dampingRatio = 0.85f)
                                                        )
                                                        isNowPlayingExpanded = false
                                                        isDismissingSheet = false
                                                    } catch (_: CancellationException) {}
                                                }
                                            }
                                        }
                                    )
                                }

                                    if (videoPlayerState.isCinemaExpanded) {
                                        val livePlayer = com.nodeplayer.music.service.PlaybackService.instance?.player
                                        val cinemaPos by viewModel.currentPositionMs.collectAsState()
                                        com.nodeplayer.ui.player.FullscreenCinemaPlayer(
                                            currentTrack = track,
                                            isPlaying = isPlaying,
                                            positionMs = cinemaPos,
                                            durationMs = track.durationMs,
                                            videoContent = { mod ->
                                                val livePlayer = com.nodeplayer.music.service.PlaybackService.instance?.player
                                                com.nodeplayer.music.ui.video.VideoSurface(
                                                    modifier = mod,
                                                    fit = true,
                                                    priority = com.nodeplayer.model.video.SurfacePriority.FULLSCREEN_CINEMA,
                                                    player = if (isLocalVideo) livePlayer else null
                                                )
                                            },
                                            currentHeight = videoPlayerState.currentHeight,
                                            availableHeights = videoPlayerState.availableHeights,
                                            onClose = { viewModel.setCinemaExpanded(false) },
                                            onPlayPause = { viewModel.togglePlayPause() },
                                            onSeek = { viewModel.seekTo(it) },
                                            onSelectQuality = { h ->
                                                viewModel.switchVideoQuality(com.nodeplayer.model.video.VideoQuality.fromHeight(h))
                                            },
                                            onBrightnessDelta = { delta: Float ->
                                                val lp = window.attributes
                                                val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                                lp.screenBrightness = (cur + delta).coerceIn(0.01f, 1.0f)
                                                window.attributes = lp
                                            },
                                            onVolumeDelta = { delta: Float ->
                                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                val change = (delta * max.toFloat()).roundToInt()
                                                val target = (cur + (if (change == 0) (if (delta > 0f) 1 else -1) else change)).coerceIn(0, max)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                                systemVolume = target.toFloat() / max.toFloat()
                                            }
                                        )
                                    }

                                    com.nodeplayer.ui.player.PlaybackOptionsSheet(
                                        visible = showAndroidPlaybackOptions,
                                        queue = queue,
                                        currentQueueIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                        isShuffle = isShuffleEnabled,
                                        repeatMode = repeatMode,
                                        infiniteAutoplay = false,
                                        accentColor = accentColor,
                                        onToggleShuffle = { viewModel.toggleShuffle() },
                                        onCycleRepeat = { viewModel.cycleRepeatMode() },
                                        onToggleInfiniteAutoplay = { },
                                        onSelectQueueItem = { idx ->
                                            if (idx in queue.indices) viewModel.playTrack(queue[idx], queue)
                                        },
                                        onRemoveQueueItem = { idx -> viewModel.removeFromQueue(idx) },
                                        onDismiss = { showAndroidPlaybackOptions = false }
                                    )

                                    var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
                                    com.nodeplayer.ui.player.QuickSettingsSheet(
                                        visible = showAndroidQuickSettings,
                                        volume = 1.0f,
                                        outputDeviceName = audioOutputInfo.outputDeviceName.ifBlank { "This Device" },
                                        audioFormatDescription = audioOutputInfo.format,
                                        sleepTimerRemainingSec = sleepTimerRemainingSec,
                                        accentColor = accentColor,
                                        onVolumeChange = { },
                                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                        onOpenEqualizer = {
                                            isNowPlayingExpanded = false
                                            currentRoute = "equalizer"
                                        },
                                        isVideoActive = videoPlayerState.isVideoActive,
                                        isVideoPlaying = isPlaying,
                                        onToggleVideo = { viewModel.toggleVideoMode(null, track) },
                                        onDownloadTrack = {
                                            viewModel.enqueueDownload(track)
                                            android.widget.Toast.makeText(this@MainActivity, "Added to download queue: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToPlaylist = {
                                            showAndroidQuickSettings = false
                                            trackForAddToPlaylist = track
                                        },
                                        playbackSpeed = currentPlaybackSpeed,
                                        onPlaybackSpeedChange = { spd ->
                                            currentPlaybackSpeed = spd
                                            com.nodeplayer.music.service.PlaybackService.instance?.player?.setPlaybackSpeed(spd)
                                        },
                                        onDismiss = { showAndroidQuickSettings = false }
                                    )

                                    AnimatedVisibility(
                                        visible = isLyricsExpanded,
                                        enter = fadeIn(spring(stiffness = 380f)),
                                        exit = fadeOut(spring(stiffness = 380f))
                                    ) {
                                        val effectiveLyrics = cachedLyrics
                                        val effectiveLyricsLoading = !isLyricsPreloaded && effectiveLyrics == null
                                        val effectiveLyricsSettings by viewModel.lyricsSettings.collectAsState()

                                        com.nodeplayer.ui.lyrics.FullScreenLyricsView(
                                            track = track,
                                            lyrics = effectiveLyrics,
                                            lyricsSettings = effectiveLyricsSettings,
                                            onUpdateLyricsSettings = { viewModel.updateLyricsSettings(it) },
                                            isPlaying = isPlaying,
                                            currentPositionMs = 0L,
                                            positionProvider = { viewModel.currentPositionMs.value },
                                            durationMs = track.durationMs,
                                            isLoading = effectiveLyricsLoading,
                                            palette = activeNowPlayingPalette,
                                            audioOutputName = audioOutputInfo.outputDeviceName.ifBlank { "This Device" },
                                            isShuffle = isShuffleEnabled,
                                            onToggleShuffle = { viewModel.toggleShuffle() },
                                            repeatMode = repeatMode,
                                            onToggleRepeat = { viewModel.cycleRepeatMode() },
                                            onPlayPause = { viewModel.togglePlayPause() },
                                            onNext = { viewModel.skipNext() },
                                            onPrevious = { viewModel.skipPrevious() },
                                            onSeekTo = { viewModel.seekTo(it) },
                                            onClose = { isLyricsExpanded = false },
                                            onOpenMenu = { showAndroidQuickSettings = true },
                                            onShare = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Now Playing: ${track.title}")
                                                    val shareText = buildString {
                                                        append("Listening to \"${track.title}\" by ${track.artist}")
                                                        val plain = effectiveLyrics?.plainText
                                                        if (!plain.isNullOrBlank()) {
                                                            append("\n\n")
                                                            append(plain.take(500))
                                                        }
                                                    }
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                startActivity(Intent.createChooser(shareIntent, "Share Track & Lyrics"))
                                            },
                                            onOpenNodeConnect = { showPairingDialog = true },
                                            onOpenPlaybackOptions = { showAndroidPlaybackOptions = true },
                                            onShareCard = { lines, cardTrack, cardPalette ->
                                                val t = cardTrack ?: track
                                                lifecycleScope.launch {
                                                    val art = t?.let { com.nodeplayer.ui.components.PlatformArtworkProvider.loadArtwork(it)?.asAndroidBitmap() }
                                                    com.nodeplayer.music.lyrics.LyricCardRenderer.shareCard(
                                                        context = this@MainActivity,
                                                        track = t,
                                                        lines = lines,
                                                        palette = cardPalette,
                                                        artwork = art
                                                    )
                                                }
                                            },
                                            onSaveCard = { lines, cardTrack, cardPalette ->
                                                val t = cardTrack ?: track
                                                lifecycleScope.launch {
                                                    val art = t?.let { com.nodeplayer.ui.components.PlatformArtworkProvider.loadArtwork(it)?.asAndroidBitmap() }
                                                    com.nodeplayer.music.lyrics.LyricCardRenderer.saveCardToGallery(
                                                        context = this@MainActivity,
                                                        track = t,
                                                        lines = lines,
                                                        palette = cardPalette,
                                                        artwork = art
                                                    )
                                                }
                                            }
                                        )
                                    }
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
                            onAddToPlaylist = { pid, tr -> viewModel.addTrackToPlaylist(pid, tr) },
                            onCreatePlaylist = { name, initial -> viewModel.createPlaylist(name, initial) },
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
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
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

                    if (showPlaylistImportDialog) {
                        com.nodeplayer.ui.playlist.PlaylistImportSheet(
                            localCatalog = allTracks,
                            searchProvider = com.nodeplayer.network.innertube.InnerTubeTrackSearchProvider(
                                com.nodeplayer.network.innertube.SharedInnerTubeProvider.get()
                            ),
                            onSavePlaylist = { name, acceptedTracks, alsoCreateYouTube ->
                                viewModel.createPlaylistWithTracks(name, acceptedTracks, alsoCreateYouTube)
                            },
                            onOpenPlaylist = { playlistId ->
                                val pl = domainPlaylists.find { it.id == playlistId }
                                if (pl != null) {
                                    selectedPlaylistForDetail = pl
                                }
                            },
                            onDismiss = {
                                showPlaylistImportDialog = false
                                importedPlaylistFileText = null
                            },
                            initialText = importedPlaylistFileText,
                            onOpenFilePicker = {
                                openPlaylistDocument.launch(arrayOf("*/*"))
                            }
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
                            onResolveUncertain = { spot, resolvedTrack ->
                                spotifyUncertainTracks = spotifyUncertainTracks.filter { it.first.id != spot.id }
                                spotifyMatchedTracks = spotifyMatchedTracks + (spot to resolvedTrack)
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
                            currentShaderMode = effectiveThemeConfig.shaderMode,
                            currentScreenProvider = { currentRoute },
                            onDismiss = { showDiagnosticsDialog = false }
                        )
                    }

                    if (showDownloadQueueSheet) {
                        val dlTasks by viewModel.downloadTasks.collectAsState()
                        val qm = viewModel.downloadQueueManager
                        com.nodeplayer.ui.downloader.DownloadQueueSheet(
                            tasks = dlTasks,
                            onPauseTask = { qm.pause(it) },
                            onResumeTask = { qm.resume(it) },
                            onCancelTask = { qm.cancel(it) },
                            onRetryTask = { qm.retry(it) },
                            onPauseAll = { dlTasks.forEach { t -> if (!t.isTerminal) qm.pause(t.id) } },
                            onResumeAll = { dlTasks.forEach { t -> if (t.state.name.startsWith("PAUSED")) qm.resume(t.id) } },
                            onClearCompleted = { qm.clearCompleted() },
                            onDismiss = { showDownloadQueueSheet = false }
                        )
                    }

                    if (trackForFormatPicker != null) {
                        com.nodeplayer.ui.downloader.DownloadFormatPickerSheet(
                            track = trackForFormatPicker!!,
                            availableOptions = pickerOptions,
                            isLoading = isResolvingPickerOptions,
                            onOptionSelected = { option ->
                                val targetTrack = trackForFormatPicker
                                if (targetTrack != null) {
                                    viewModel.enqueueDownload(targetTrack, option)
                                }
                                trackForFormatPicker = null
                                android.widget.Toast.makeText(this@MainActivity, "Added to download queue", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { trackForFormatPicker = null }
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
                            initialQuery = searchInitialQuery,
                            onDismiss = {
                                showSearchSheet = false
                                searchInitialQuery = ""
                            },
                            onPlayTrack = { track, q ->
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
                                            safeToast("Playing '${track.title}' from ${conn.peerDeviceName}...", Toast.LENGTH_SHORT)
                                        }
                                    }
                                } else {
                                    viewModel.playTrack(track, q)
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
                                activePlaylistDetail = header to listOf(albumTrack)
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
                            val prefs = getSharedPreferences("node_connect_settings", Context.MODE_PRIVATE)
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
                            } catch (_: Exception) {
                                "127.0.0.1"
                            }
                        }
                        NodeConnectPairingDialog(
                            onDismissRequest = { showPairingDialog = false },
                            activeSession = activeSession,
                            localIp = localIp,
                            localDeviceId = hub?.activeDeviceId ?: Build.MODEL,
                            localDeviceName = Build.MODEL,
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
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("NodeLog", log))
                                safeToast("Debug log copied to clipboard (${log.length} chars)", Toast.LENGTH_SHORT)
                            },
                            onExportLogClicked = {
                                NodePlayerApplication.ensureFileLoggingInitialized(this@MainActivity)
                                val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                                val out = File(filesDir, "logs/node_connect_export.txt")
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
                                val currentTrackNow = srv?.crossfadeManager?.currentPlayingTrack
                                if (conn != null && srv != null) {
                                    if (hub?.castService?.isCasting == true) {
                                        srv.stopAudioCasting()
                                        safeToast("Audio Cast stopped", Toast.LENGTH_SHORT)
                                    } else if (currentTrackNow != null) {
                                        try {
                                            srv.startAudioCasting(conn, currentTrackNow)
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
                                    val currentTrackNow = srv?.crossfadeManager?.currentPlayingTrack
                                    if (conn != null && currentTrackNow != null) {
                                        try {
                                            val pos = srv.player.currentPosition
                                            val playingNow = srv.player.isPlaying
                                            hub?.handoffService?.initiateHandoff(conn, currentTrackNow, pos, playingNow)
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
                            title = { Text("Allow Connection?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White) },
                            text = {
                                Text("Device '${prompt.first}' is requesting to connect to Node Player.", color = Color.White.copy(alpha = 0.8f))
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
                                        Text("Deny", color = Color(0xFFEF4444))
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

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isVideoActive = viewModel.videoPlayerState.value.isVideoActive
            val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
            val pipEnabled = prefs.getBoolean("video_pip", true)
            if (isVideoActive && pipEnabled) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to enter Picture-in-Picture: ${e.message}")
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FrameMetricsTracker.start(this)
        val player = PlaybackService.instance?.player
        if (player != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                .build()
        }
    }

    override fun onStop() {
        super.onStop()
        FrameMetricsTracker.stop(this)
        val prefs = getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE)
        val dataSaver = prefs.getBoolean("video_background_audio_only", false)
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (dataSaver && !inPip) {
            val player = PlaybackService.instance?.player
            if (player != null) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
        }
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
                    else -> modes.maxByOrNull { it.refreshRate }
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
        handleGoogleDriveCallbackIntent(intent)
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

    private fun handleGoogleDriveCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val isGdriveCallback = (uri.scheme == "nodeplayer" && (uri.host == "gdrive-callback" || uri.path?.contains("gdrive-callback") == true)) ||
                (uri.scheme == "com.nodeplayer.music" && (uri.host == "oauth2redirect" || uri.path?.contains("oauth2redirect") == true)) ||
                (uri.scheme?.startsWith("com.googleusercontent.apps") == true)
        if (isGdriveCallback) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                com.nodeplayer.music.auth.GoogleAuthManager.handleAuthorizationResponse(this@MainActivity, uri)
                    .onSuccess {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Connected to Google Drive!", Toast.LENGTH_SHORT).show()
                        }
                        // Initial sync upon successful connection
                        com.nodeplayer.music.sync.CloudSyncManager.performSync(this@MainActivity, isManualTrigger = true)
                    }
                    .onFailure { e ->
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Google Drive connection failed: ${e.message}", Toast.LENGTH_LONG).show()
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
    dockScrollController: DockScrollController,
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
    val isScrollCollapsed by dockScrollController.isCollapsed.collectAsState()
    val isCollapsedDock by remember(themeConfig.dockAutoHideEnabled, activeTrack, hasActivePlaylistDetail) {
        derivedStateOf {
            (themeConfig.dockAutoHideEnabled && isScrollCollapsed && activeTrack != null) ||
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
                        .size(54.dp)
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
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )

                Box(
                    modifier = Modifier
                        .size(54.dp)
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

            val dockOffsetPx by animateFloatAsState(
                targetValue = with(LocalDensity.current) {
                    if (themeConfig.dockAutoHideEnabled && isScrollCollapsed) 80.dp.toPx() else 0f
                },
                animationSpec = spring(stiffness = 380f, dampingRatio = 0.82f),
                label = "dockScrollOffset"
            )
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
                backdrop = pageBackdrop,
                modifier = Modifier.graphicsLayer {
                    translationY = dockOffsetPx
                }
            )
        }
    }
}

