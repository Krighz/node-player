package com.nodeplayer.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nodeplayer.music.ui.PlayerViewModel
import com.nodeplayer.music.ui.components.FrostedBottomBar
import com.nodeplayer.music.ui.components.FrostedMiniPlayer
import com.nodeplayer.music.ui.components.LiquidGlassBackground
import com.nodeplayer.music.ui.player.NowPlayingSheet
import com.nodeplayer.music.ui.screens.EqualizerScreen
import dev.chrisbanes.haze.haze
import com.nodeplayer.music.ui.screens.LibraryScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.theme.NodePlayerTheme
import dev.chrisbanes.haze.HazeState

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

        checkAndRequestPermissions()

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()
            val dockHazeState = remember { HazeState() }

            NodePlayerTheme(
                accentColor = if (themeConfig.useDynamicAccent) accentColor else Color(themeConfig.fixedAccentColorArgb),
                isOled = themeConfig.isOledBlack
            ) {
                var currentRoute by remember { mutableStateOf("library") }
                var isNowPlayingExpanded by remember { mutableStateOf(false) }

                BackHandler(enabled = isNowPlayingExpanded) {
                    isNowPlayingExpanded = false
                }

                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val currentPositionMs by viewModel.currentPositionMs.collectAsState()
                val currentRms by viewModel.currentRms.collectAsState()
                val audioOutputInfo by viewModel.audioOutputInfo.collectAsState()
                val effectConfig by viewModel.effectConfig.collectAsState()

                val allTracks by viewModel.allTracks.collectAsState()
                val albums by viewModel.albums.collectAsState()
                val artists by viewModel.artists.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val excludedFolders by viewModel.excludedFolders.collectAsState()
                val excludedTracks by viewModel.excludedTracks.collectAsState()
                val minDurationSeconds by viewModel.minDurationSeconds.collectAsState()

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Blur Source Container: Encloses both background AND scrolling screen content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(dockHazeState)
                    ) {
                        // 1. Dribbble-inspired Liquid Glass Organic Chrome & Obsidian Background
                        LiquidGlassBackground(
                            accentColor = accentColor,
                            isOled = themeConfig.isOledBlack,
                            isPlaying = isPlaying
                        )

                        // 2. Foreground Interactive UI (Cards use clean translucent glass; no recursive blur)
                        when (currentRoute) {
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
                                hazeState = null
                            )
                            "equalizer" -> EqualizerScreen(
                                config = effectConfig,
                                onConfigChange = { viewModel.updateEffectConfig(it) },
                                hazeState = null
                            )
                            "settings" -> SettingsScreen(
                                outputInfo = audioOutputInfo,
                                themeConfig = themeConfig,
                                allFolders = folders,
                                excludedFolders = excludedFolders,
                                excludedTracks = excludedTracks,
                                minDurationSeconds = minDurationSeconds,
                                onMinDurationChange = { viewModel.setMinDurationSeconds(it) },
                                onToggleExcludeFolder = { viewModel.toggleFolderExclusion(it) },
                                onAddCustomFolder = { viewModel.addCustomFolder(it) },
                                onRestoreTrack = { viewModel.restoreExcludedTrack(it) },
                                onThemeConfigChange = { viewModel.updateThemeConfig(it) },
                                onRescanLibrary = { viewModel.refreshLibrary() },
                                hazeState = null
                            )
                        }
                    }

                    // 3. Floating Mini-Player & Bottom Navigation (Frosted glass dock sampling dockHazeState)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        if (currentTrack != null) {
                            val duration = currentTrack!!.durationMs.coerceAtLeast(1L)
                            val progress = (currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                            FrostedMiniPlayer(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                progressFraction = progress,
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onSkipNextClick = { viewModel.skipNext() },
                                onClick = { isNowPlayingExpanded = true },
                                hazeState = dockHazeState,
                                rmsLevel = currentRms
                            )
                        }

                        FrostedBottomBar(
                            selectedRoute = currentRoute,
                            onRouteChange = { currentRoute = it },
                            hazeState = dockHazeState
                        )
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
                                rmsLevel = currentRms
                            )
                        }
                    }
                }
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
}
