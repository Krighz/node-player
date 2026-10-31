package com.nodeplayer.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nodeplayer.music.ui.PlayerViewModel
import com.nodeplayer.music.ui.components.FrostedBottomBar
import com.nodeplayer.music.ui.components.FrostedMiniPlayer
import com.nodeplayer.music.ui.player.NowPlayingSheet
import com.nodeplayer.music.ui.screens.EqualizerScreen
import com.nodeplayer.music.ui.screens.LibraryScreen
import com.nodeplayer.music.ui.screens.SettingsScreen
import com.nodeplayer.music.ui.theme.NodePlayerTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

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
            val hazeState = remember { HazeState() }

            NodePlayerTheme(
                accentColor = if (themeConfig.useDynamicAccent) accentColor else Color(themeConfig.fixedAccentColorArgb),
                isOled = themeConfig.isOledBlack
            ) {
                var currentRoute by remember { mutableStateOf("library") }
                var isNowPlayingExpanded by remember { mutableStateOf(false) }

                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val currentPositionMs by viewModel.currentPositionMs.collectAsState()
                val audioOutputInfo by viewModel.audioOutputInfo.collectAsState()
                val effectConfig by viewModel.effectConfig.collectAsState()

                val allTracks by viewModel.allTracks.collectAsState()
                val albums by viewModel.albums.collectAsState()
                val artists by viewModel.artists.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Main Screen Area with Haze blur provider
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(hazeState)
                    ) {
                        when (currentRoute) {
                            "library" -> LibraryScreen(
                                tracks = allTracks,
                                albums = albums,
                                artists = artists,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onRescanClick = {
                                    checkAndRequestPermissions()
                                    viewModel.refreshLibrary()
                                },
                                hazeState = hazeState
                            )
                            "equalizer" -> EqualizerScreen(
                                config = effectConfig,
                                onConfigChange = { viewModel.updateEffectConfig(it) },
                                hazeState = hazeState
                            )
                            "settings" -> SettingsScreen(
                                outputInfo = audioOutputInfo,
                                themeConfig = themeConfig,
                                onThemeConfigChange = { viewModel.updateThemeConfig(it) },
                                onRescanLibrary = { viewModel.refreshLibrary() },
                                hazeState = hazeState
                            )
                        }
                    }

                    // Floating Mini-Player and Frosted Bottom Bar Container
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
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
                                hazeState = hazeState
                            )
                        }

                        FrostedBottomBar(
                            selectedRoute = currentRoute,
                            onRouteChange = { currentRoute = it },
                            hazeState = hazeState
                        )
                    }

                    // Full Now Playing Sheet (Smooth slide animation)
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
                                }
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
