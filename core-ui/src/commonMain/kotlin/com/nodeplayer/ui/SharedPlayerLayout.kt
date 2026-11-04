package com.nodeplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.*
import com.nodeplayer.ui.components.LiquidGlassBackground
import com.nodeplayer.ui.components.NodePlayerLogo
import com.nodeplayer.ui.player.BottomPlayerDock
import com.nodeplayer.ui.player.NowPlayingView
import com.nodeplayer.ui.screens.CustomizeUiView
import com.nodeplayer.ui.screens.EqualizerView
import com.nodeplayer.ui.screens.HomeView
import com.nodeplayer.ui.screens.LibraryView
import com.nodeplayer.ui.screens.SettingsView
import com.nodeplayer.ui.theme.GlassTokens
import com.nodeplayer.ui.theme.GlassTokensData
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassTokens
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.liquidGlass

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
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onSelectQueueItem: (Int) -> Unit = {},
    onAudioEffectConfigChange: (AudioEffectConfig) -> Unit,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onToggleExclusiveMode: (Boolean) -> Unit = {},
    onCrossfadeChange: (Float) -> Unit = {},
    onGaplessToggle: (Boolean) -> Unit = {},
    onReplayGainModeChange: (ReplayGainMode) -> Unit = {},
    onLastFmToggle: (Boolean) -> Unit = {},
    currentMusicFolder: String = "",
    onRescanLibrary: () -> Unit = {},
    onSelectMusicFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentNav by remember { mutableStateOf(PlayerNavDestination.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var showNowPlayingOverlay by remember { mutableStateOf(false) }

    val sidebarInteractionSource = remember { MutableInteractionSource() }
    val isSidebarHovered by sidebarInteractionSource.collectIsHoveredAsState()
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarHovered) 230.dp else 78.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
    )

    val accent = Color(themeConfig.fixedAccentColorArgb)
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

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalGlassTokens provides glassTokens,
        LocalGlassThemeConfig provides themeConfig
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // 1. Full Dynamic Liquid Glass Background with all 6 shader animation styles
            LiquidGlassBackground(
                accentColor = accent,
                isOled = themeConfig.isOledBlack,
                isPlaying = isPlaying,
                isPerformanceMode = themeConfig.isPerformanceMode,
                animatedBackdrop = true,
                themeConfig = themeConfig
            )

            // 2. Main Layout (16:9 Expanded Desktop/Landscape vs Portrait Phone)
            if (isExpanded) {
                // 16:9 Expanded Layout: Left Sidebar + Center Screen + Bottom Dock
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Navigation Glass Sidebar (Hover-animated: icon-only collapsed, full-width expanded)
                    Column(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .padding(14.dp)
                            .liquidGlass(cornerRadius = 20.dp)
                            .hoverable(sidebarInteractionSource)
                            .padding(
                                start = if (isSidebarHovered) 16.dp else 8.dp,
                                end = if (isSidebarHovered) 16.dp else 8.dp,
                                top = 16.dp,
                                bottom = 16.dp
                            ),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = if (isSidebarHovered) Alignment.Start else Alignment.CenterHorizontally
                    ) {
                        Column(
                            horizontalAlignment = if (isSidebarHovered) Alignment.Start else Alignment.CenterHorizontally
                        ) {
                            // Logo / Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp, top = 4.dp),
                                horizontalArrangement = if (isSidebarHovered) Arrangement.Start else Arrangement.Center
                            ) {
                                NodePlayerLogo(size = 38.dp, accentColor = accent)
                                if (isSidebarHovered) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Node Player",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "v1.4.0 • Hi-Fi Pure",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f)),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Navigation Items
                            PlayerNavDestination.values().forEach { destination ->
                                val isSelected = currentNav == destination
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                                        .clickable { currentNav = destination }
                                        .padding(horizontal = if (isSidebarHovered) 14.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (isSidebarHovered) Arrangement.Start else Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title,
                                        tint = if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (isSidebarHovered) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = destination.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        // Bottom status in sidebar
                        if (isSidebarHovered) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) accent else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val outputStatusText = if (isPlaying) {
                                    val srcRate = currentTrack?.sampleRate ?: 0
                                    val srcDepth = currentTrack?.bitDepth ?: 0
                                    val isBitPerfect = audioOutputInfo.isTrulyBitPerfect(srcRate, srcDepth, audioEffectConfig.isDspBypassed)
                                    if (isBitPerfect) {
                                        if (isExclusiveMode) "WASAPI Exclusive (Bit-Perfect)" else "Bit-Perfect Output"
                                    } else if (audioEffectConfig.isDspBypassed) {
                                        if (audioOutputInfo.sampleRate > 0) "Direct (${audioOutputInfo.sampleRate / 1000} kHz)" else "Direct Output"
                                    } else {
                                        "32-bit Float DSP Active"
                                    }
                                } else {
                                    "Ready"
                                }
                                Text(
                                    text = outputStatusText,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Central Active Screen Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = if (currentTrack != null) 90.dp else 0.dp)
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
                                onSearchChange = { searchQuery = it }
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
                                trackCount = tracks.size
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout (Phone): Content + Bottom Navigation Bar
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                onSearchChange = { searchQuery = it }
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
                                trackCount = tracks.size
                            )
                        }
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = Color(0xFF0C0E18).copy(alpha = 0.85f),
                        contentColor = accent
                    ) {
                        PlayerNavDestination.values().forEach { destination ->
                            NavigationBarItem(
                                selected = currentNav == destination,
                                onClick = { currentNav = destination },
                                icon = { Icon(destination.icon, contentDescription = destination.title) },
                                label = { Text(destination.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            // 3. Persistent Bottom Mini Player / Dock
            if (currentTrack != null) {
                BottomPlayerDock(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    volume = volume,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onVolumeChange = onVolumeChange,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onExpandNowPlaying = { showNowPlayingOverlay = true }
                )
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
