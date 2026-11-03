package com.nodeplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.nodeplayer.ui.player.BottomPlayerDock
import com.nodeplayer.ui.player.NowPlayingView
import com.nodeplayer.ui.screens.CustomizeUiView
import com.nodeplayer.ui.screens.EqualizerView
import com.nodeplayer.ui.screens.LibraryView
import com.nodeplayer.ui.screens.SettingsView
import com.nodeplayer.ui.theme.GlassTokens
import com.nodeplayer.ui.theme.GlassTokensData
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassTokens
import com.nodeplayer.ui.theme.LocalGlassThemeConfig
import com.nodeplayer.ui.theme.liquidGlass

enum class PlayerNavDestination(val title: String, val icon: ImageVector) {
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
    var currentNav by remember { mutableStateOf(PlayerNavDestination.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var showNowPlayingOverlay by remember { mutableStateOf(false) }

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
                    // Left Navigation Glass Sidebar
                    Column(
                        modifier = Modifier
                            .width(230.dp)
                            .fillMaxHeight()
                            .padding(16.dp)
                            .liquidGlass(cornerRadius = 20.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Logo / Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(accent, Color(0xFF06B6D4)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Node Player",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                    Text(
                                        text = "v1.4.0 • Hi-Fi Pure",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }

                            // Navigation Items
                            PlayerNavDestination.values().forEach { destination ->
                                val isSelected = currentNav == destination
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                                        .clickable { currentNav = destination }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title,
                                        tint = if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = destination.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
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
