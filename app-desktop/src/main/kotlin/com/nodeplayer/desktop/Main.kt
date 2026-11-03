package com.nodeplayer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.nodeplayer.desktop.audio.WindowsPlaybackEngine
import com.nodeplayer.desktop.data.DesktopSettingsStore
import com.nodeplayer.desktop.data.DesktopTrackDatabase
import com.nodeplayer.desktop.data.WindowsFileScanner
import com.nodeplayer.model.*
import com.nodeplayer.ui.SharedPlayerLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() = application {
    val windowState = remember { WindowState(size = DpSize(1240.dp, 720.dp)) }

    val engine = remember { WindowsPlaybackEngine() }
    val database = remember { DesktopTrackDatabase() }
    val settingsStore = remember { DesktopSettingsStore() }
    val scanner = remember { WindowsFileScanner() }
    val coroutineScope = rememberCoroutineScope()

    var trackList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var folders by remember { mutableStateOf<List<MusicFolder>>(emptyList()) }

    var audioEffectConfig by remember { mutableStateOf(AudioEffectConfig()) }
    var themeConfig by remember {
        mutableStateOf(
            GlassThemeConfig(
                fixedAccentColorArgb = settingsStore.getLong("fixedAccentColorArgb", 0xFF8B5CF6L),
                backgroundAnimationStyle = settingsStore.getString("backgroundAnimationStyle", "LIQUID_BLOBS"),
                backgroundAnimationSpeed = settingsStore.getFloat("backgroundAnimationSpeed", 1.0f),
                backgroundAnimationIntensity = settingsStore.getFloat("backgroundAnimationIntensity", 0.8f),
                isOledBlack = settingsStore.getBoolean("isOledBlack", false),
                isPerformanceMode = settingsStore.getBoolean("isPerformanceMode", false)
            )
        )
    }

    val currentTrack by engine.currentTrack.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val positionMs by engine.currentPositionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val volume by engine.volume.collectAsState()
    val isShuffle by engine.isShuffle.collectAsState()
    val repeatMode by engine.repeatMode.collectAsState()
    val queue by engine.queue.collectAsState()
    val currentQueueIndex by engine.currentQueueIndex.collectAsState()
    val currentLrc by engine.currentLrc.collectAsState()
    val isExclusiveMode by engine.isExclusiveMode.collectAsState()

    fun refreshLibrary() {
        coroutineScope.launch(Dispatchers.IO) {
            val loaded = database.getAllTracks()
            val loadedAlbums = database.extractAlbums(loaded)
            val loadedArtists = database.extractArtists(loaded)
            val loadedFolders = database.extractFolders(loaded)
            withContext(Dispatchers.Main) {
                trackList = loaded
                albums = loadedAlbums
                artists = loadedArtists
                folders = loadedFolders
            }
        }
    }

    // Load initial library on startup
    LaunchedEffect(Unit) {
        refreshLibrary()
    }

    fun pickAndScanFolder() {
        SwingUtilities.invokeLater {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) {}

            val chooser = JFileChooser()
            chooser.dialogTitle = "Select Music Directory"
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.isAcceptAllFileFilterUsed = false

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedDir = chooser.selectedFile
                if (selectedDir != null && selectedDir.exists()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val tracks = mutableListOf<Track>()
                        scanner.scanPath(selectedDir.absolutePath) { meta ->
                            tracks.add(scanner.toTrack(meta))
                        }
                        database.insertOrUpdateTracks(tracks)
                        refreshLibrary()
                    }
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            engine.release()
            exitApplication()
        },
        state = windowState,
        title = "Node Player"
    ) {
        window.minimumSize = Dimension(960, 600)

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(themeConfig.fixedAccentColorArgb),
                background = Color(0xFF090C15),
                surface = Color(0xFF131728)
            )
        ) {
            SharedPlayerLayout(
                isExpanded = true,
                tracks = trackList,
                albums = albums,
                artists = artists,
                folders = folders,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                volume = volume,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                queue = queue,
                currentQueueIndex = currentQueueIndex,
                lrcContent = currentLrc,
                audioEffectConfig = audioEffectConfig,
                themeConfig = themeConfig,
                audioOutputInfo = AudioOutputInfo(
                    outputDeviceName = engine.audioOutput.currentDeviceSpec?.name ?: "WASAPI Shared Output",
                    sampleRate = engine.audioOutput.currentSampleRate,
                    bitDepth = 32,
                    channels = engine.audioOutput.currentChannels,
                    format = "WASAPI 32-bit Float"
                ),
                isExclusiveMode = isExclusiveMode,
                onPlayTrack = { track, list ->
                    engine.setQueue(list, list.indexOf(track))
                },
                onPlayPause = { engine.playPause() },
                onNext = { engine.next() },
                onPrevious = { engine.previous() },
                onSeek = { engine.seekTo(it) },
                onVolumeChange = { engine.setVolume(it) },
                onToggleShuffle = { engine.toggleShuffle() },
                onCycleRepeat = { engine.cycleRepeatMode() },
                onSelectQueueItem = { engine.playIndex(it) },
                onAudioEffectConfigChange = { cfg ->
                    audioEffectConfig = cfg
                    engine.applyEffectConfig(cfg)
                },
                onThemeConfigChange = { cfg ->
                    themeConfig = cfg
                    settingsStore.putLong("fixedAccentColorArgb", cfg.fixedAccentColorArgb)
                    settingsStore.putString("backgroundAnimationStyle", cfg.backgroundAnimationStyle)
                    settingsStore.putFloat("backgroundAnimationSpeed", cfg.backgroundAnimationSpeed)
                    settingsStore.putFloat("backgroundAnimationIntensity", cfg.backgroundAnimationIntensity)
                    settingsStore.putBoolean("isOledBlack", cfg.isOledBlack)
                    settingsStore.putBoolean("isPerformanceMode", cfg.isPerformanceMode)
                },
                onToggleExclusiveMode = { engine.setExclusiveMode(it) },
                onRescanLibrary = { refreshLibrary() },
                onSelectMusicFolder = { pickAndScanFolder() }
            )
        }
    }
}
