package com.nodeplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.nodeplayer.model.*
import com.nodeplayer.network.library.OnlineAlbum
import com.nodeplayer.network.library.OnlineLibraryManager
import com.nodeplayer.network.library.OnlinePlaylist
import com.nodeplayer.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nodeplayer.ui.player.BottomPlayerDeck
import com.nodeplayer.ui.player.BottomPlayerDock
import com.nodeplayer.ui.player.NowPlayingView
import com.nodeplayer.ui.screens.*
import com.nodeplayer.ui.theme.GlassTokensData
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalControlBackdrop
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.LocalGlassTokens
import com.nodeplayer.ui.theme.LocalMotionConfig

val LocalBottomBarPadding = compositionLocalOf { 80.dp }

enum class PlayerNavDestination(val title: String, val icon: ImageVector) {
    HOME("For You", Icons.Rounded.Home),
    NEW("New", Icons.Rounded.Explore),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    EQUALIZER("Equalizer", Icons.Rounded.GraphicEq),
    CUSTOMIZE("Customize UI", Icons.Rounded.Palette),
    SETTINGS("Settings", Icons.Rounded.Settings),
    OPTICAL_TEST("Optical Test", Icons.Rounded.Visibility)
}

@Composable
fun SharedPlayerLayout(
    isExpanded: Boolean,
    tracks: List<Track>,
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    folders: List<MusicFolder> = emptyList(),
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float = 1.0f,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    isFavorite: Boolean = false,
    queue: List<Track> = emptyList(),
    currentQueueIndex: Int = 0,
    lrcContent: String? = null,
    audioEffectConfig: AudioEffectConfig,
    themeConfig: GlassThemeConfig,
    audioOutputInfo: AudioOutputInfo = AudioOutputInfo(),
    isExclusiveMode: Boolean = false,
    crossfadeSeconds: Float = 0.0f,
    gaplessEnabled: Boolean = true,
    replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    lastFmEnabled: Boolean = false,
    nodeConnectEnabled: Boolean = true,
    connectedDeviceCount: Int = 0,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onSelectQueueItem: (Int) -> Unit = {},
    onAudioEffectConfigChange: (AudioEffectConfig) -> Unit,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onToggleExclusiveMode: (Boolean) -> Unit = {},
    onCrossfadeChange: (Float) -> Unit = {},
    onGaplessToggle: (Boolean) -> Unit = {},
    onReplayGainModeChange: (ReplayGainMode) -> Unit = {},
    onLastFmToggle: (Boolean) -> Unit = {},
    onToggleNodeConnect: (Boolean) -> Unit = {},
    onPairNewDevice: () -> Unit = {},
    onCastToPeer: (DiscoveredPeerInfo) -> Unit = {},
    onOpenSyncCenter: () -> Unit = {},
    currentMusicFolder: String = "",
    onRescanLibrary: () -> Unit = {},
    onSelectMusicFolder: () -> Unit = {},
    remoteTracks: List<Track> = emptyList(),
    onSearchQueryChanged: ((String) -> Unit)? = null,
    sleepTimerRemainingSec: Long? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
    onKeepScreenOn: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentNav by remember { mutableStateOf(PlayerNavDestination.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var playerPresentationState by remember { mutableStateOf<com.nodeplayer.model.PlayerPresentationState>(com.nodeplayer.model.PlayerPresentationState.Closed) }
    var activeContextMenuTrack by remember { mutableStateOf<Track?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(volume) }

    val searchFocusRequester = remember { FocusRequester() }

    val baseAccent = Color(themeConfig.fixedAccentColorArgb)
    var dynamicArtAccent by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(currentTrack?.id, currentTrack?.path) {
        val t = currentTrack
        if (t != null) {
            dynamicArtAccent = PlatformArtworkProvider.extractVibrantColor(t)
        } else {
            dynamicArtAccent = null
        }
    }

    val targetAccent = dynamicArtAccent ?: baseAccent
    val accent by androidx.compose.animation.animateColorAsState(
        targetValue = targetAccent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 650)
    )
    val glassTokens = remember(themeConfig) {
        GlassTokensData(
            opacity = themeConfig.glassOpacity,
            blurStrength = themeConfig.blurStrength,
            grainStrength = themeConfig.glassTexture,
            rimStrength = themeConfig.edgeHighlightStrength,
            refractionStrength = themeConfig.lensRefractionStrength,
            isFullyTransparent = themeConfig.isFullyTransparent,
            keepTextReadable = themeConfig.keepTextReadable,
            isPerformanceMode = themeConfig.isPerformanceMode
        )
    }

    val dynamicBottomPadding = if (currentTrack != null) 136.dp else 80.dp
    val pageBackdrop = rememberLayerBackdrop()

    val coroutineScope = rememberCoroutineScope()
    val dockScrollController = remember(coroutineScope) {
        DockScrollController(
            scope = coroutineScope,
            sensitivity = themeConfig.dockScrollSensitivity,
            returnDelayMs = themeConfig.dockReturnDelayMs,
            enabled = themeConfig.dockAutoHideEnabled
        )
    }

    LaunchedEffect(themeConfig.dockScrollSensitivity, themeConfig.dockReturnDelayMs, themeConfig.dockAutoHideEnabled) {
        dockScrollController.sensitivity = themeConfig.dockScrollSensitivity
        dockScrollController.returnDelayMs = themeConfig.dockReturnDelayMs
        dockScrollController.enabled = themeConfig.dockAutoHideEnabled
    }

    var showSearchBar by remember { mutableStateOf(false) }
    var searchBarQuery by remember { mutableStateOf("") }
    var activeDetailHeader by remember { mutableStateOf<DetailMediaHeader?>(null) }
    var activeDetailTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val onlineLibraryManager = remember { OnlineLibraryManager() }

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalGlassTokens provides glassTokens,
        LocalGlassThemeConfig provides themeConfig,
        LocalMotionConfig provides themeConfig,
        LocalGlassControlsConfig provides themeConfig.controlsConfig,
        LocalBottomBarPadding provides dynamicBottomPadding,
        LocalControlBackdrop provides pageBackdrop
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when {
                            // Ctrl + F: Focus search capsule
                            keyEvent.isCtrlPressed && keyEvent.key == Key.F -> {
                                if (currentNav != PlayerNavDestination.HOME && currentNav != PlayerNavDestination.LIBRARY) {
                                    currentNav = PlayerNavDestination.HOME
                                }
                                searchFocusRequester.requestFocus()
                                true
                            }
                            // Ctrl + 1/2/3: Navigate tabs
                            keyEvent.isCtrlPressed && keyEvent.key == Key.One -> {
                                currentNav = PlayerNavDestination.HOME
                                true
                            }
                            keyEvent.isCtrlPressed && keyEvent.key == Key.Two -> {
                                currentNav = PlayerNavDestination.LIBRARY
                                true
                            }
                            keyEvent.isCtrlPressed && keyEvent.key == Key.Three -> {
                                currentNav = PlayerNavDestination.SETTINGS
                                true
                            }
                            // Space: Play / Pause
                            keyEvent.key == Key.Spacebar -> {
                                onPlayPause()
                                true
                            }
                            // Seek Left / Right ±5s
                            !keyEvent.isShiftPressed && keyEvent.key == Key.DirectionRight -> {
                                onSeek((positionMs + 5000L).coerceAtMost(durationMs))
                                true
                            }
                            !keyEvent.isShiftPressed && keyEvent.key == Key.DirectionLeft -> {
                                onSeek((positionMs - 5000L).coerceAtLeast(0L))
                                true
                            }
                            // Shift + Left / Right: Prev / Next track
                            keyEvent.isShiftPressed && keyEvent.key == Key.DirectionRight -> {
                                onNext()
                                true
                            }
                            keyEvent.isShiftPressed && keyEvent.key == Key.DirectionLeft -> {
                                onPrevious()
                                true
                            }
                            // Up / Down: Volume ±5%
                            keyEvent.key == Key.DirectionUp -> {
                                onVolumeChange((volume + 0.05f).coerceAtMost(1.0f))
                                true
                            }
                            keyEvent.key == Key.DirectionDown -> {
                                onVolumeChange((volume - 0.05f).coerceAtLeast(0.0f))
                                true
                            }
                            // M: Mute toggle
                            keyEvent.key == Key.M -> {
                                if (isMuted) {
                                    isMuted = false
                                    onVolumeChange(if (previousVolume > 0f) previousVolume else 0.5f)
                                } else {
                                    isMuted = true
                                    previousVolume = volume
                                    onVolumeChange(0.0f)
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            // 1. Full Dynamic Liquid Glass Background for expanded desktop mode
            if (isExpanded) {
                if (playerPresentationState !is com.nodeplayer.model.PlayerPresentationState.Closed) {
                    com.nodeplayer.ui.player.AlbumArtGradientBackdrop(
                        track = currentTrack,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LiquidGlassBackground(
                        accentColor = accent,
                        isOled = themeConfig.isOledBlack,
                        isPlaying = isPlaying,
                        isPerformanceMode = themeConfig.isPerformanceMode,
                        animatedBackdrop = themeConfig.animatedBackdrop && isPlaying,
                        themeConfig = themeConfig
                    )
                }
            }

            // 2. Main Layout (Expanded Desktop / Landscape vs Portrait Phone)
            if (isExpanded) {
                // 16:9 Expanded Layout: Top Header + (Left Optical Dock + Screen Area) + Bottom Floating Waveform Deck
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar: Search Capsule (Center) + Device Connect Pill (Right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Empty placeholder on left to balance spacing with the optical dock
                        Box(modifier = Modifier.width(96.dp))

                        // Center: Search Capsule
                        SearchCapsule(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                onSearchQueryChanged?.invoke(it)
                                if (it.isNotBlank() && currentNav != PlayerNavDestination.LIBRARY) {
                                    currentNav = PlayerNavDestination.LIBRARY
                                }
                            },
                            focusRequester = searchFocusRequester,
                            placeholder = "Search artists, tracks, or playlists..."
                        )

                        // Right: Device Connect Pill with Glow Indicator
                        DeviceConnectPill(
                            status = if (nodeConnectEnabled && connectedDeviceCount > 0) DeviceConnectStatus.CONNECTED
                            else if (nodeConnectEnabled) DeviceConnectStatus.DISCOVERING
                            else DeviceConnectStatus.DISCONNECTED,
                            connectedDeviceCount = connectedDeviceCount,
                            allowCastingToThisDevice = nodeConnectEnabled,
                            outputDeviceName = audioOutputInfo.outputDeviceName,
                            onToggleAllowCasting = onToggleNodeConnect,
                            onPairNewDevice = onPairNewDevice,
                            onCastToDevice = onCastToPeer,
                            onOpenSyncCenter = {
                                currentNav = PlayerNavDestination.SETTINGS
                                onOpenSyncCenter()
                            }
                        )
                    }

                    // Content Body: Left Optical Dock + Active Center View
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = if (currentTrack != null) 88.dp else 12.dp)
                    ) {
                        // Left: Optical Rail Dock with Spring Convex Bubble & Mode Switcher
                        Box(
                            modifier = Modifier
                                .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OpticalRailDock(
                                    currentNav = currentNav,
                                    onNavSelect = { destination ->
                                        currentNav = destination
                                    },
                                    backdrop = pageBackdrop,
                                    themeConfig = themeConfig
                                )

                                DesktopModeRailSwitcher()
                            }
                        }

                        // Center: Active Screen Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 20.dp)
                        ) {
                            when (currentNav) {
                                PlayerNavDestination.HOME,
                                PlayerNavDestination.NEW -> HomeView(
                                    tracks = tracks,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    onPlayTrack = onPlayTrack,
                                    onPlayPause = onPlayPause,
                                    onQuickShuffle = {
                                        if (tracks.isNotEmpty()) {
                                            val shuffled = tracks.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    onRescanClick = onRescanLibrary,
                                    onSelectMusicFolder = onSelectMusicFolder,
                                    onNavigateToLibrary = { currentNav = PlayerNavDestination.LIBRARY },
                                    onNavigateToEqualizer = { currentNav = PlayerNavDestination.EQUALIZER },
                                    onOpenPlaylistDetail = { title, subtitle, artworkUrl, items ->
                                        activeDetailHeader = DetailMediaHeader(
                                            id = "home_pl_${title.hashCode()}",
                                            title = title,
                                            subtitle = subtitle,
                                            artworkUrl = artworkUrl,
                                            isAlbum = false
                                        )
                                        activeDetailTracks = items
                                    }
                                )
                                PlayerNavDestination.LIBRARY -> LibraryView(
                                    tracks = tracks,
                                    albums = albums,
                                    artists = artists,
                                    folders = folders,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    onTrackClick = onPlayTrack,
                                    onRescanClick = onRescanLibrary,
                                    searchQuery = searchQuery,
                                    onSearchChange = {
                                        searchQuery = it
                                        onSearchQueryChanged?.invoke(it)
                                    },
                                    remoteTracks = remoteTracks,
                                onlineLibraryManager = onlineLibraryManager,
                                onOpenPlaylistDetail = { pl ->
                                    activeDetailHeader = DetailMediaHeader(
                                        id = pl.id,
                                        title = pl.title,
                                        subtitle = pl.author.ifBlank { "Playlist" },
                                        artworkUrl = pl.artworkUrl,
                                        isAlbum = false
                                    )
                                    activeDetailTracks = tracks.filter { it.album.equals(pl.title, ignoreCase = true) }
                                        .ifEmpty { onlineLibraryManager.state.value.songs.filter { it.album.equals(pl.title, ignoreCase = true) } }
                                        .ifEmpty { onlineLibraryManager.state.value.songs }
                                },
                                onOpenAlbumDetail = { album ->
                                    activeDetailHeader = DetailMediaHeader(
                                        id = album.id,
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkUrl = album.artworkUrl,
                                        year = album.year,
                                        isAlbum = true
                                    )
                                    activeDetailTracks = onlineLibraryManager.state.value.songs.filter {
                                        it.album.equals(album.title, ignoreCase = true)
                                    }
                                },
                                    onNavigateToHome = { currentNav = PlayerNavDestination.HOME },
                                    onNavigateToSearch = { showSearchBar = true },
                                    backdrop = pageBackdrop
                                )
                                PlayerNavDestination.EQUALIZER -> EqualizerView(
                                    config = audioEffectConfig,
                                    onConfigChange = onAudioEffectConfigChange,
                                    currentTrack = currentTrack,
                                    currentDeviceName = audioOutputInfo.outputDeviceName
                                )
                                PlayerNavDestination.CUSTOMIZE -> CustomizeUiView(
                                    themeConfig = themeConfig,
                                    onThemeConfigChange = onThemeConfigChange
                                )
                                PlayerNavDestination.SETTINGS -> SettingsView(
                                    audioOutputInfo = audioOutputInfo,
                                    isExclusiveMode = isExclusiveMode,
                                    onToggleExclusiveMode = onToggleExclusiveMode,
                                    crossfadeSeconds = crossfadeSeconds,
                                    onCrossfadeChange = onCrossfadeChange,
                                    gaplessEnabled = gaplessEnabled,
                                    onGaplessToggle = onGaplessToggle,
                                    replayGainMode = replayGainMode,
                                    onReplayGainModeChange = onReplayGainModeChange,
                                    lastFmEnabled = lastFmEnabled,
                                    onLastFmToggle = onLastFmToggle,
                                    currentMusicFolder = currentMusicFolder,
                                    onSelectMusicFolder = onSelectMusicFolder,
                                    onRescanLibrary = onRescanLibrary,
                                    onOpenCustomizeUi = { currentNav = PlayerNavDestination.CUSTOMIZE },
                                    onOpenEqualizer = { currentNav = PlayerNavDestination.EQUALIZER },
                                    onOpenSyncCenter = onOpenSyncCenter,
                                    trackCount = tracks.size,
                                    nodeConnectEnabled = nodeConnectEnabled,
                                    onToggleNodeConnect = onToggleNodeConnect,
                                    themeConfig = themeConfig,
                                    onThemeConfigChange = onThemeConfigChange,
                                    onOpenOpticalTest = { currentNav = PlayerNavDestination.OPTICAL_TEST },
                                    isExpanded = true
                                )
                                PlayerNavDestination.OPTICAL_TEST -> OpticalDiagnosticsView(
                                    onBack = { currentNav = PlayerNavDestination.SETTINGS },
                                    themeConfig = themeConfig
                                )
                            }
                        }
                    }
                }

                // Pinned Bottom Floating Waveform Player Deck (Expanded mode)
                if (currentTrack != null) {
                    BottomPlayerDeck(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        volume = volume,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        isFavorite = isFavorite,
                        audioOutputInfo = audioOutputInfo,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onVolumeChange = onVolumeChange,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onToggleFavorite = onToggleFavorite,
                        onOpenSignalPath = { currentNav = PlayerNavDestination.EQUALIZER },
                        onOpenOutputDevicePicker = { currentNav = PlayerNavDestination.SETTINGS },
                        onCastClick = { /* open cast sheet */ },
                        onExpandNowPlaying = { playerPresentationState = com.nodeplayer.model.PlayerPresentationState.NowPlaying },
                        sleepTimerRemainingSec = sleepTimerRemainingSec,
                        onSetSleepTimer = onSetSleepTimer,
                        backdrop = pageBackdrop,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            } else {
                // Portrait Layout (Phone): Inverted Box hierarchy per D-01 & DOCK-01
                Box(modifier = Modifier.fillMaxSize()) {
                    // Page Content & Background layer recorded into pageBackdrop
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(pageBackdrop)
                    ) {
                        CompositionLocalProvider(
                            LocalControlBackdrop provides remember { emptyBackdrop() }
                        ) {
                        // Background gradient/blobs or Full Bleed Album Art Gradient
                        if (playerPresentationState !is com.nodeplayer.model.PlayerPresentationState.Closed) {
                            com.nodeplayer.ui.player.AlbumArtGradientBackdrop(
                                track = currentTrack,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LiquidGlassBackground(
                                accentColor = accent,
                                isOled = themeConfig.isOledBlack,
                                isPlaying = isPlaying,
                                isPerformanceMode = themeConfig.isPerformanceMode,
                                animatedBackdrop = themeConfig.animatedBackdrop && isPlaying,
                                themeConfig = themeConfig
                            )
                        }

                        // Active Destination View (full-bleed, scrolling underneath floating chrome)
                        when (currentNav) {
                            PlayerNavDestination.HOME,
                            PlayerNavDestination.NEW -> HomeView(
                                tracks = tracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onPlayTrack = onPlayTrack,
                                onPlayPause = onPlayPause,
                                onQuickShuffle = {
                                    if (tracks.isNotEmpty()) {
                                        val shuffled = tracks.shuffled()
                                        onPlayTrack(shuffled.first(), shuffled)
                                    }
                                },
                                onRescanClick = onRescanLibrary,
                                onSelectMusicFolder = onSelectMusicFolder,
                                onNavigateToLibrary = { currentNav = PlayerNavDestination.LIBRARY },
                                onNavigateToEqualizer = { currentNav = PlayerNavDestination.EQUALIZER },
                                onNavigateToSettings = { currentNav = PlayerNavDestination.SETTINGS },
                                dockScrollController = dockScrollController,
                                showDevicePill = true,
                                nodeConnectEnabled = nodeConnectEnabled,
                                connectedDeviceCount = connectedDeviceCount,
                                onOpenSyncCenter = {
                                    currentNav = PlayerNavDestination.SETTINGS
                                    onOpenSyncCenter()
                                },
                                onPairNewDevice = onPairNewDevice,
                                onCastToPeer = onCastToPeer,
                                onToggleNodeConnect = onToggleNodeConnect,
                                onOpenPlaylistDetail = { title, subtitle, artworkUrl, items ->
                                    activeDetailHeader = DetailMediaHeader(
                                        id = "home_pl_${title.hashCode()}",
                                        title = title,
                                        subtitle = subtitle,
                                        artworkUrl = artworkUrl,
                                        isAlbum = false
                                    )
                                    activeDetailTracks = items
                                }
                            )
                            PlayerNavDestination.LIBRARY -> LibraryView(
                                tracks = tracks,
                                albums = albums,
                                artists = artists,
                                folders = folders,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackClick = onPlayTrack,
                                onRescanClick = onRescanLibrary,
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                remoteTracks = remoteTracks,
                                onlineLibraryManager = onlineLibraryManager,
                                onOpenPlaylistDetail = { pl ->
                                    activeDetailHeader = DetailMediaHeader(
                                        id = pl.id,
                                        title = pl.title,
                                        subtitle = pl.author.ifBlank { "Playlist" },
                                        artworkUrl = pl.artworkUrl,
                                        isAlbum = false
                                    )
                                    activeDetailTracks = tracks.filter { it.album.equals(pl.title, ignoreCase = true) }
                                        .ifEmpty { onlineLibraryManager.state.value.songs.filter { it.album.equals(pl.title, ignoreCase = true) } }
                                        .ifEmpty { onlineLibraryManager.state.value.songs }
                                },
                                onOpenAlbumDetail = { album ->
                                    activeDetailHeader = DetailMediaHeader(
                                        id = album.id,
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkUrl = album.artworkUrl,
                                        year = album.year,
                                        isAlbum = true
                                    )
                                    activeDetailTracks = onlineLibraryManager.state.value.songs.filter {
                                        it.album.equals(album.title, ignoreCase = true)
                                    }
                                },
                                onNavigateToHome = { currentNav = PlayerNavDestination.HOME },
                                onNavigateToSearch = { showSearchBar = true },
                                dockScrollController = dockScrollController,
                                backdrop = pageBackdrop
                            )
                            PlayerNavDestination.EQUALIZER -> EqualizerView(
                                config = audioEffectConfig,
                                onConfigChange = onAudioEffectConfigChange,
                                currentTrack = currentTrack,
                                currentDeviceName = audioOutputInfo.outputDeviceName
                            )
                            PlayerNavDestination.CUSTOMIZE -> CustomizeUiView(
                                themeConfig = themeConfig,
                                onThemeConfigChange = onThemeConfigChange
                            )
                            PlayerNavDestination.SETTINGS -> SettingsView(
                                audioOutputInfo = audioOutputInfo,
                                isExclusiveMode = isExclusiveMode,
                                onToggleExclusiveMode = onToggleExclusiveMode,
                                crossfadeSeconds = crossfadeSeconds,
                                onCrossfadeChange = onCrossfadeChange,
                                gaplessEnabled = gaplessEnabled,
                                onGaplessToggle = onGaplessToggle,
                                replayGainMode = replayGainMode,
                                onReplayGainModeChange = onReplayGainModeChange,
                                lastFmEnabled = lastFmEnabled,
                                onLastFmToggle = onLastFmToggle,
                                currentMusicFolder = currentMusicFolder,
                                onSelectMusicFolder = onSelectMusicFolder,
                                onRescanLibrary = onRescanLibrary,
                                onOpenCustomizeUi = { currentNav = PlayerNavDestination.CUSTOMIZE },
                                onOpenEqualizer = { currentNav = PlayerNavDestination.EQUALIZER },
                                onOpenSyncCenter = onOpenSyncCenter,
                                trackCount = tracks.size,
                                nodeConnectEnabled = nodeConnectEnabled,
                                onToggleNodeConnect = onToggleNodeConnect,
                                themeConfig = themeConfig,
                                onThemeConfigChange = onThemeConfigChange,
                                onOpenOpticalTest = { currentNav = PlayerNavDestination.OPTICAL_TEST },
                                isExpanded = false
                            )
                            PlayerNavDestination.OPTICAL_TEST -> OpticalDiagnosticsView(
                                onBack = { currentNav = PlayerNavDestination.SETTINGS },
                                themeConfig = themeConfig
                            )
                        }
                    }
                    }

                    // Floating Chrome Sibling Overlays above page layer in z-order (hidden when Now Playing or Full Screen Lyrics is active)
                    if (playerPresentationState is com.nodeplayer.model.PlayerPresentationState.Closed) {
                    val isCollapsed by dockScrollController.isCollapsed.collectAsState()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AnimatedContent(
                            targetState = isCollapsed && currentTrack != null,
                            transitionSpec = {
                                fadeIn(spring(stiffness = 380f, dampingRatio = 0.82f)) togetherWith
                                    fadeOut(spring(stiffness = 380f, dampingRatio = 0.82f))
                            },
                            label = "DockCollapseTransition"
                        ) { collapsed ->
                            if (collapsed && currentTrack != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentTab = LIQUID_DOCK_TABS.find { it.destination == currentNav } ?: LIQUID_DOCK_TABS.first()
                                    LiquidButton(
                                        onClick = { dockScrollController.expand() },
                                        backdrop = pageBackdrop,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            imageVector = currentTab.icon,
                                            contentDescription = currentTab.title,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    ConvxMiniPlayer(
                                        currentTrack = currentTrack,
                                        isPlaying = isPlaying,
                                        progressFraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                                        onPlayPauseClick = onPlayPause,
                                        onSkipNextClick = onNext,
                                        onSkipPreviousClick = onPrevious,
                                        onClick = { dockScrollController.expand() },
                                        backdrop = pageBackdrop,
                                        isCollapsed = true,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    LiquidButton(
                                        onClick = { showSearchBar = true },
                                        backdrop = pageBackdrop,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Search",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (currentTrack != null) {
                                        ConvxMiniPlayer(
                                            currentTrack = currentTrack,
                                            isPlaying = isPlaying,
                                            progressFraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                                            onPlayPauseClick = onPlayPause,
                                            onSkipNextClick = onNext,
                                            onSkipPreviousClick = onPrevious,
                                            onClick = { playerPresentationState = com.nodeplayer.model.PlayerPresentationState.NowPlaying },
                                            backdrop = pageBackdrop,
                                            isCollapsed = false,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }

                                    LiquidDock(
                                        currentNav = currentNav,
                                        onNavSelect = { currentNav = it },
                                        backdrop = pageBackdrop,
                                        onSearchClick = { showSearchBar = true }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            // Full-Bleed Liquid-Glass Search Overlay Screen (SRCH-01, SRCH-02, SRCH-03)
            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { -it / 4 },
                exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { -it / 4 }
            ) {
                GlassSearchView(
                    initialQuery = searchBarQuery,
                    onDismiss = { showSearchBar = false },
                    onPlayTrack = { track, searchQueue ->
                        onPlayTrack(track, searchQueue)
                    },
                    onAlbumClick = { track ->
                        activeDetailHeader = DetailMediaHeader(
                            id = "album_${track.album.lowercase().replace(" ", "_")}",
                            title = track.album.ifBlank { track.title },
                            subtitle = track.artist,
                            artworkUrl = track.path,
                            isAlbum = true,
                            isLossless = track.isLossless,
                            isHiRes = track.isHiRes
                        )
                        activeDetailTracks = listOf(track)
                        showSearchBar = false
                    },
                    onPlaylistClick = { track ->
                        val cleanId = if (track.album.isNotBlank()) track.album else track.path.substringAfter("nodeplayer://youtube/playlist/", "")
                        activeDetailHeader = DetailMediaHeader(
                            id = cleanId,
                            title = track.title,
                            subtitle = track.artist,
                            artworkUrl = if (track.path.startsWith("http")) track.path else null,
                            description = "YouTube Music Playlist",
                            isAlbum = false
                        )
                        activeDetailTracks = emptyList()
                        showSearchBar = false
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = com.nodeplayer.network.innertube.InnerTubeClient().use { client ->
                                client.fetchPlaylistTracks(cleanId)
                            }
                            val loaded = result.getOrDefault(emptyList())
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                activeDetailTracks = loaded
                            }
                        }
                    },
                    backdrop = pageBackdrop
                )
            }

            // Apple Music-Style Playlist & Album Detail Page Overlay (PLAY-01, PLAY-02, PLAY-03)
            AnimatedVisibility(
                visible = activeDetailHeader != null,
                enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { it },
                exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { it }
            ) {
                activeDetailHeader?.let { header ->
                    val isSaved = if (header.isAlbum) {
                        onlineLibraryManager.state.value.albums.any { it.title.equals(header.title, ignoreCase = true) || it.id == header.id }
                    } else {
                        onlineLibraryManager.isPlaylistSaved(header.id)
                    }
                    PlaylistDetailView(
                        header = header,
                        tracks = activeDetailTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        isSavedInLibrary = isSaved,
                        onBack = { activeDetailHeader = null },
                        onPlayTrack = { tr, q -> onPlayTrack(tr, q) },
                        onPlayAll = { q ->
                            if (q.isNotEmpty()) onPlayTrack(q.first(), q)
                        },
                        onShuffleAll = { q ->
                            if (q.isNotEmpty()) {
                                val shuffled = q.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        onToggleSaveLibrary = { h ->
                            if (h.isAlbum) {
                                if (isSaved) {
                                    activeDetailTracks.forEach { onlineLibraryManager.removeTrack(it.id) }
                                } else {
                                    activeDetailTracks.forEach { onlineLibraryManager.saveTrack(it) }
                                }
                            } else {
                                if (isSaved) {
                                    onlineLibraryManager.removePlaylist(h.id)
                                } else {
                                    onlineLibraryManager.addPlaylist(
                                        OnlinePlaylist(
                                            id = h.id,
                                            title = h.title,
                                            author = h.subtitle,
                                            trackCount = activeDetailTracks.size,
                                            artworkUrl = h.artworkUrl
                                        )
                                    )
                                }
                            }
                        },
                        onTrackOptionsClick = { track ->
                            activeContextMenuTrack = track
                        },
                        backdrop = pageBackdrop
                    )
                }
            }

            // 4. Expanded Now Playing Fullscreen Overlay (Lyrics, Queue, Artwork)
            AnimatedVisibility(
                visible = playerPresentationState is com.nodeplayer.model.PlayerPresentationState.NowPlaying,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                var showPlaybackOptions by remember { mutableStateOf(false) }
                var showQuickSettings by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    NowPlayingView(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        positionProvider = { positionMs },
                        durationMs = durationMs,
                        queue = queue,
                        currentQueueIndex = currentQueueIndex,
                        lrcContent = lrcContent,
                        audioOutputName = audioOutputInfo.outputDeviceName.ifBlank { "This phone" },
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onOpenLyrics = {
                            playerPresentationState = com.nodeplayer.model.PlayerPresentationState.Lyrics(controlsVisible = true)
                        },
                        onOpenNodeConnect = { onPairNewDevice() },
                        onOpenPlaybackOptions = { showPlaybackOptions = true },
                        onOpenThreeDotsMenu = { showQuickSettings = true },
                        onClose = { playerPresentationState = com.nodeplayer.model.PlayerPresentationState.Closed }
                    )

                    // Playback Options Sheet
                    com.nodeplayer.ui.player.PlaybackOptionsSheet(
                        visible = showPlaybackOptions,
                        queue = queue,
                        currentQueueIndex = currentQueueIndex,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        infiniteAutoplay = false,
                        accentColor = dynamicArtAccent ?: baseAccent,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onToggleInfiniteAutoplay = { /* toggle infinite autoplay */ },
                        onSelectQueueItem = onSelectQueueItem,
                        onRemoveQueueItem = { /* remove item from queue */ },
                        onDismiss = { showPlaybackOptions = false }
                    )

                    // Quick Settings Sheet
                    com.nodeplayer.ui.player.QuickSettingsSheet(
                        visible = showQuickSettings,
                        volume = volume,
                        outputDeviceName = audioOutputInfo.outputDeviceName.ifBlank { "This phone" },
                        audioFormatDescription = audioOutputInfo.format,
                        sleepTimerRemainingSec = sleepTimerRemainingSec,
                        accentColor = dynamicArtAccent ?: baseAccent,
                        onVolumeChange = onVolumeChange,
                        onSetSleepTimer = onSetSleepTimer,
                        onOpenEqualizer = {
                            playerPresentationState = com.nodeplayer.model.PlayerPresentationState.Closed
                            currentNav = PlayerNavDestination.EQUALIZER
                        },
                        onDismiss = { showQuickSettings = false }
                    )
                }
            }

            // 5. Full-Screen Synced Lyrics Immersive Overlay (LYR-01, LYR-02, LYR-03, LYR-04, LYR-05)
            AnimatedVisibility(
                visible = playerPresentationState is com.nodeplayer.model.PlayerPresentationState.Lyrics,
                enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { it },
                exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { it }
            ) {
                val parsedLyrics = remember(lrcContent, currentTrack?.id) {
                    if (!lrcContent.isNullOrBlank()) {
                        com.nodeplayer.library.LrcParser.parse(lrcContent, currentTrack?.id ?: 0L)
                    } else {
                        com.nodeplayer.model.Lyrics(trackId = currentTrack?.id ?: 0L)
                    }
                }

                com.nodeplayer.ui.lyrics.FullScreenLyricsView(
                    track = currentTrack,
                    lyrics = parsedLyrics,
                    isPlaying = isPlaying,
                    currentPositionMs = positionMs,
                    positionProvider = { positionMs },
                    durationMs = durationMs,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeekTo = onSeek,
                    onClose = {
                        // Back hierarchy: from Lyrics collapses to NowPlaying
                        playerPresentationState = com.nodeplayer.model.PlayerPresentationState.NowPlaying
                    },
                    onKeepScreenOn = onKeepScreenOn
                )
            }

            // 6. Universal Glass Context Menu Bottom Sheet (MENU-01, MENU-03)
            activeContextMenuTrack?.let { targetTrack ->
                val isSaved = onlineLibraryManager.isSaved(targetTrack.id)
                com.nodeplayer.ui.components.GlassContextMenuSheet(
                    track = targetTrack,
                    visible = activeContextMenuTrack != null,
                    isSavedInLibrary = isSaved,
                    onDismiss = { activeContextMenuTrack = null },
                    onActionClick = { actionId, tr ->
                        when (actionId) {
                            com.nodeplayer.model.TrackActionId.PLAY_NEXT -> {
                                if (onPlayNext != null) {
                                    onPlayNext(tr)
                                } else {
                                    val currentQ = queue.toMutableList()
                                    val insertIdx = (currentQueueIndex + 1).coerceAtMost(currentQ.size)
                                    currentQ.add(insertIdx, tr)
                                    if (!isPlaying || currentTrack == null) {
                                        onPlayTrack(tr, currentQ)
                                    }
                                }
                            }
                            com.nodeplayer.model.TrackActionId.ADD_TO_QUEUE -> {
                                if (onAddToQueue != null) {
                                    onAddToQueue(tr)
                                } else {
                                    val currentQ = queue.toMutableList()
                                    currentQ.add(tr)
                                    if (!isPlaying || currentTrack == null) {
                                        onPlayTrack(tr, currentQ)
                                    }
                                }
                            }
                            com.nodeplayer.model.TrackActionId.ADD_TO_PLAYLIST -> {
                                onlineLibraryManager.saveTrack(tr)
                            }
                            com.nodeplayer.model.TrackActionId.LIKE_SAVE -> {
                                if (isSaved) {
                                    onlineLibraryManager.removeTrack(tr.id)
                                } else {
                                    onlineLibraryManager.saveTrack(tr)
                                }
                            }
                            com.nodeplayer.model.TrackActionId.START_RADIO -> {
                                onPlayTrack(tr, listOf(tr) + queue.shuffled().take(15))
                            }
                            com.nodeplayer.model.TrackActionId.GO_TO_ARTIST -> {
                                currentNav = PlayerNavDestination.LIBRARY
                            }
                            com.nodeplayer.model.TrackActionId.GO_TO_ALBUM -> {
                                activeDetailHeader = DetailMediaHeader(
                                    id = "album_${tr.album.lowercase().replace(" ", "_")}",
                                    title = tr.album.ifBlank { tr.title },
                                    subtitle = tr.artist,
                                    artworkUrl = tr.path,
                                    isAlbum = true,
                                    isLossless = tr.isLossless,
                                    isHiRes = tr.isHiRes
                                )
                                activeDetailTracks = listOf(tr)
                            }
                            com.nodeplayer.model.TrackActionId.VIEW_LYRICS -> {
                                playerPresentationState = com.nodeplayer.model.PlayerPresentationState.Lyrics(controlsVisible = true)
                            }
                            com.nodeplayer.model.TrackActionId.SHARE -> {
                                // Platform share or toast
                            }
                            com.nodeplayer.model.TrackActionId.SONG_INFO -> {
                                // Info sheet
                            }
                            com.nodeplayer.model.TrackActionId.NOT_INTERESTED -> {
                                onlineLibraryManager.removeTrack(tr.id)
                            }
                        }
                    },
                    backdrop = pageBackdrop
                )
            }
        }
    }
}
