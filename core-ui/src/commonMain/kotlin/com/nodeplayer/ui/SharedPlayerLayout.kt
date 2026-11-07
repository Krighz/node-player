package com.nodeplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nodeplayer.model.*
import com.nodeplayer.ui.components.*
import com.nodeplayer.ui.player.BottomPlayerDeck
import com.nodeplayer.ui.player.BottomPlayerDock
import com.nodeplayer.ui.player.NowPlayingView
import com.nodeplayer.ui.screens.*
import com.nodeplayer.ui.theme.GlassTokensData
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassControlsConfig
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.LocalGlassTokens

val LocalBottomBarPadding = compositionLocalOf { 80.dp }

enum class PlayerNavDestination(val title: String, val icon: ImageVector) {
    HOME("For You", Icons.Rounded.Home),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    EQUALIZER("Equalizer", Icons.Rounded.GraphicEq),
    CUSTOMIZE("Customize UI", Icons.Rounded.Palette),
    SETTINGS("Settings", Icons.Rounded.Settings)
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
    modifier: Modifier = Modifier
) {
    var currentNav by remember { mutableStateOf(PlayerNavDestination.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var showNowPlayingOverlay by remember { mutableStateOf(false) }
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

    val offsetFraction by dockScrollController.offsetFraction.collectAsState()
    val animatedOffsetFraction by animateFloatAsState(
        targetValue = if (themeConfig.dockAutoHideEnabled) offsetFraction else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockOffsetFraction"
    )
    val miniPlayerOffsetFraction = if (themeConfig.dockAutoHideMiniPlayer) animatedOffsetFraction else 0f
    val dockOffsetFraction = animatedOffsetFraction

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalGlassTokens provides glassTokens,
        LocalGlassThemeConfig provides themeConfig,
        LocalGlassControlsConfig provides themeConfig.controlsConfig,
        LocalBottomBarPadding provides dynamicBottomPadding
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
                LiquidGlassBackground(
                    accentColor = accent,
                    isOled = themeConfig.isOledBlack,
                    isPlaying = isPlaying,
                    isPerformanceMode = themeConfig.isPerformanceMode,
                    animatedBackdrop = themeConfig.animatedBackdrop && isPlaying,
                    themeConfig = themeConfig
                )
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
                        // Left: Optical Rail Dock with Spring Convex Bubble
                        Box(
                            modifier = Modifier
                                .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            OpticalRailDock(
                                currentNav = currentNav,
                                onNavSelect = { destination ->
                                    currentNav = destination
                                },
                                backdrop = pageBackdrop,
                                themeConfig = themeConfig
                            )
                        }

                        // Center: Active Screen Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 20.dp)
                        ) {
                            when (currentNav) {
                                PlayerNavDestination.HOME -> HomeView(
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
                                    onNavigateToEqualizer = { currentNav = PlayerNavDestination.EQUALIZER }
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
                                    remoteTracks = remoteTracks
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
                                    isExpanded = true
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
                        onExpandNowPlaying = { showNowPlayingOverlay = true },
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
                        // Background gradient/blobs
                        LiquidGlassBackground(
                            accentColor = accent,
                            isOled = themeConfig.isOledBlack,
                            isPlaying = isPlaying,
                            isPerformanceMode = themeConfig.isPerformanceMode,
                            animatedBackdrop = themeConfig.animatedBackdrop && isPlaying,
                            themeConfig = themeConfig
                        )

                        // Active Destination View (full-bleed, scrolling underneath floating chrome)
                        when (currentNav) {
                            PlayerNavDestination.HOME -> HomeView(
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
                                onToggleNodeConnect = onToggleNodeConnect
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
                                dockScrollController = dockScrollController
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
                                isExpanded = false
                            )
                        }
                    }

                    // Floating Chrome Sibling Overlays above page layer in z-order
                    val density = LocalDensity.current
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
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
                                onClick = { showNowPlayingOverlay = true },
                                backdrop = pageBackdrop,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .graphicsLayer {
                                        translationY = with(density) { (120.dp * miniPlayerOffsetFraction).toPx() }
                                        alpha = (1f - miniPlayerOffsetFraction).coerceIn(0f, 1f)
                                    }
                            )
                        }

                        LiquidDock(
                            currentNav = currentNav,
                            onNavSelect = { currentNav = it },
                            backdrop = pageBackdrop,
                            onSearchClick = {
                                currentNav = PlayerNavDestination.LIBRARY
                            },
                            modifier = Modifier.graphicsLayer {
                                translationY = with(density) { (120.dp * dockOffsetFraction).toPx() }
                                alpha = (1f - dockOffsetFraction).coerceIn(0f, 1f)
                            }
                        )
                    }

                    // Bottom-edge tap target to restore dock when hidden per D-03
                    if (dockOffsetFraction > 0.5f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    dockScrollController.onBottomEdgeTap()
                                }
                        )
                    }
                }
            }

            // 4. Expanded Now Playing Fullscreen Overlay (Lyrics, Queue, Artwork)
            AnimatedVisibility(
                visible = showNowPlayingOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NowPlayingView(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    queue = queue,
                    currentQueueIndex = currentQueueIndex,
                    lrcContent = lrcContent,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onSelectQueueItem = onSelectQueueItem,
                    onClose = { showNowPlayingOverlay = false }
                )
            }
        }
    }
}
