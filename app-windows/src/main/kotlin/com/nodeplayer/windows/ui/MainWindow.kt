package com.nodeplayer.windows.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.Track
import com.nodeplayer.windows.audio.RepeatMode
import com.nodeplayer.windows.audio.WindowsPlaybackEngine
import com.nodeplayer.windows.data.DesktopSettingsStore
import com.nodeplayer.windows.data.DesktopTrackDatabase
import com.nodeplayer.windows.data.WindowsFileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

enum class ScreenTab {
    LIBRARY, EQUALIZER, SETTINGS
}

@Composable
fun MainWindow(
    engine: WindowsPlaybackEngine,
    database: DesktopTrackDatabase,
    settingsStore: DesktopSettingsStore
) {
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(ScreenTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var trackList by remember { mutableStateOf(emptyList<Track>()) }
    var isScanning by remember { mutableStateOf(false) }

    val currentTrack by engine.currentTrack.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val positionMs by engine.currentPositionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val volume by engine.volume.collectAsState()
    val isShuffle by engine.isShuffle.collectAsState()
    val repeatMode by engine.repeatMode.collectAsState()

    // Load library initially
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            trackList = database.getAllTracks()
        }
    }

    val filteredTracks = remember(trackList, searchQuery) {
        if (searchQuery.isBlank()) trackList
        else trackList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    fun openFolderPicker() {
        coroutineScope.launch {
            val dialog = FileDialog(null as Frame?, "Select Music Folder", FileDialog.LOAD)
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null) {
                val selectedPath = if (file != null) File(dir, file).absolutePath else dir
                isScanning = true
                withContext(Dispatchers.IO) {
                    val scanner = WindowsFileScanner()
                    val tracks = mutableListOf<Track>()
                    scanner.scanPath(selectedPath) { meta ->
                        tracks.add(scanner.toTrack(meta))
                    }
                    database.insertOrUpdateTracks(tracks)
                    trackList = database.getAllTracks()
                }
                isScanning = false
            }
        }
    }

    WindowsGlassTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF020617)
                        )
                    )
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. Left Glass Sidebar
                NavigationSidebar(
                    currentTab = currentTab,
                    onSelectTab = { currentTab = it },
                    onScanFolder = { openFolderPicker() },
                    isScanning = isScanning,
                    trackCount = trackList.size
                )

                // 2. Central View
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 20.dp, top = 20.dp, bottom = 100.dp)
                ) {
                    when (currentTab) {
                        ScreenTab.LIBRARY -> LibraryView(
                            tracks = filteredTracks,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onPlayTrack = { track ->
                                engine.setQueue(filteredTracks, filteredTracks.indexOf(track))
                            },
                            onScanFolder = { openFolderPicker() },
                            isScanning = isScanning
                        )
                        ScreenTab.EQUALIZER -> EqualizerView(engine = engine)
                        ScreenTab.SETTINGS -> SettingsView(
                            settingsStore = settingsStore,
                            trackCount = trackList.size
                        )
                    }
                }
            }

            // 3. Persistent Bottom Mini Player / Now Playing Glass Dock
            BottomPlayerBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                volume = volume,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                onPlayPause = { engine.playPause() },
                onNext = { engine.next() },
                onPrevious = { engine.previous() },
                onSeek = { engine.seekTo(it) },
                onVolumeChange = { engine.setVolume(it) },
                onToggleShuffle = { engine.toggleShuffle() },
                onCycleRepeat = { engine.cycleRepeatMode() }
            )
        }
    }
}

@Composable
private fun NavigationSidebar(
    currentTab: ScreenTab,
    onSelectTab: (ScreenTab) -> Unit,
    onScanFolder: () -> Unit,
    isScanning: Boolean,
    trackCount: Int
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .padding(16.dp)
            .liquidGlass(cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // App Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GlassColors.AccentPurple, GlassColors.AccentCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Node Player",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Windows v1.4.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextMuted
                    )
                }
            }

            // Navigation Items
            SidebarItem(
                title = "Library",
                icon = Icons.Default.LibraryMusic,
                badge = if (trackCount > 0) "$trackCount" else null,
                isSelected = currentTab == ScreenTab.LIBRARY,
                onClick = { onSelectTab(ScreenTab.LIBRARY) }
            )
            SidebarItem(
                title = "Equalizer",
                icon = Icons.Default.GraphicEq,
                isSelected = currentTab == ScreenTab.EQUALIZER,
                onClick = { onSelectTab(ScreenTab.EQUALIZER) }
            )
            SidebarItem(
                title = "Settings",
                icon = Icons.Default.Settings,
                isSelected = currentTab == ScreenTab.SETTINGS,
                onClick = { onSelectTab(ScreenTab.SETTINGS) }
            )
        }

        // Bottom Actions: Folder Scan
        Column {
            Button(
                onClick = onScanFolder,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlassColors.AccentPurple.copy(alpha = 0.8f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "Scanning..." else "Add Music Folder",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "WASAPI 32-bit Float DSP",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.AccentCyan.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) GlassColors.GlassSurfaceActive else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) GlassColors.AccentPurple else GlassColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) GlassColors.TextPrimary else GlassColors.TextSecondary
            ),
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun LibraryView(
    tracks: List<Track>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    onScanFolder: () -> Unit,
    isScanning: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = { Text("Search title, artist, album...", color = GlassColors.TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GlassColors.TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlassColors.AccentPurple,
                    unfocusedBorderColor = GlassColors.GlassBorder,
                    focusedContainerColor = GlassColors.GlassSurface,
                    unfocusedContainerColor = GlassColors.GlassSurface,
                    focusedTextColor = GlassColors.TextPrimary,
                    unfocusedTextColor = GlassColors.TextPrimary
                )
            )
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlass(cornerRadius = 16.dp)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = GlassColors.TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Windows music library is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a folder on your PC or external drive to scan music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onScanFolder,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isScanning) "Scanning..." else "Choose Music Folder")
                    }
                }
            }
        } else {
            // Track Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TITLE",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(2.5f)
                )
                Text(
                    text = "ARTIST",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.8f)
                )
                Text(
                    text = "ALBUM",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.8f)
                )
                Text(
                    text = "QUALITY",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "TIME",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(60.dp)
                )
            }

            Divider(color = GlassColors.GlassBorder.copy(alpha = 0.5f), thickness = 1.dp)

            // Track List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                items(tracks, key = { it.id.toString() + it.path }) { track ->
                    val isCurrent = currentTrack?.path == track.path
                    TrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        onClick = { onPlayTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val rowBg = if (isCurrent) GlassColors.GlassSurfaceActive else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title & Playing icon
        Row(
            modifier = Modifier.weight(2.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = GlassColors.AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) GlassColors.AccentPurple else GlassColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Artist
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Album
        Text(
            text = track.album,
            style = MaterialTheme.typography.bodyMedium.copy(color = GlassColors.TextMuted),
            modifier = Modifier.weight(1.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Quality Badge
        Box(modifier = Modifier.weight(1.2f)) {
            val badgeColor = if (track.isHiRes) GlassColors.HiResGold else GlassColors.AccentCyan
            Text(
                text = track.formatBadgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = badgeColor,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = track.durationFormatted,
            style = MaterialTheme.typography.bodyMedium.copy(color = GlassColors.TextMuted),
            modifier = Modifier.width(60.dp)
        )
    }
}

@Composable
private fun EqualizerView(engine: WindowsPlaybackEngine) {
    val bands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    var gains by remember { mutableStateOf(FloatArray(10) { 0.0f }) }
    var preampDb by remember { mutableStateOf(0.0f) }
    var limiterEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlass(cornerRadius = 20.dp)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "10-Band Parametric Equalizer",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Shared 32-bit floating point Biquad filters",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    gains = FloatArray(10) { 0.0f }
                    preampDb = 0.0f
                    engine.dspChain.reset()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Flat / Reset")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preamp Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Preamp Gain: ${"%.1f".format(preampDb)} dB",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(180.dp)
            )
            Slider(
                value = preampDb,
                onValueChange = {
                    preampDb = it
                    engine.dspChain.preamp.gainDb = it
                },
                valueRange = -12.0f..12.0f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // 10-Band Sliders
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bands.forEachIndexed { index, freq ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        text = "+12",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextMuted
                    )
                    Slider(
                        value = gains[index],
                        onValueChange = { newVal ->
                            val updated = gains.clone()
                            updated[index] = newVal
                            gains = updated
                            engine.dspChain.equalizer.enabled = true
                            val bandConfigs = bands.mapIndexed { i, f ->
                                com.nodeplayer.model.EqBandConfig(i, f.toFloat(), gains[i], 1.414f)
                            }
                            engine.dspChain.equalizer.updateBands(bandConfigs)
                        },
                        valueRange = -12.0f..12.0f,
                        modifier = Modifier.weight(1f).width(40.dp)
                    )
                    Text(
                        text = "-12",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (freq >= 1000) "${freq / 1000}k" else "$freq",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Soft Limiter Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "32-bit Soft Limiter",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Prevents clipping and digital distortion across all sample rates",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = limiterEnabled,
                onCheckedChange = {
                    limiterEnabled = it
                    engine.dspChain.limiter.enabled = it
                }
            )
        }
    }
}

@Composable
private fun SettingsView(
    settingsStore: DesktopSettingsStore,
    trackCount: Int
) {
    var exclusiveMode by remember { mutableStateOf(settingsStore.getBoolean("wasapi_exclusive", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlass(cornerRadius = 20.dp)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Audio Output Mode
        Text(
            text = "AUDIO OUTPUT",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.AccentCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "WASAPI Exclusive Mode (Bit-Perfect)", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Bypasses the Windows system audio mixer for bit-perfect hardware output matching source sample rate",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = exclusiveMode,
                onCheckedChange = {
                    exclusiveMode = it
                    settingsStore.putBoolean("wasapi_exclusive", it)
                }
            )
        }

        Divider(color = GlassColors.GlassBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))

        // Storage & Library
        Text(
            text = "STORAGE & DATABASE",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.AccentCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Library Catalog: %APPDATA%\\NodePlayer\\library.db ($trackCount tracks indexed)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Settings: %APPDATA%\\NodePlayer\\settings.properties",
            style = MaterialTheme.typography.bodyMedium
        )

        Divider(color = GlassColors.GlassBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))

        // About & Version
        Text(
            text = "ABOUT",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.AccentCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Node Player for Windows",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Version: 1.4.0 (Windows x64)",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Architecture: Shared Multiplatform Core + Skia Liquid Glass",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BottomPlayerBar(
    modifier: Modifier = Modifier,
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 0.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Scrubber line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val curSec = positionMs / 1000
            val curMin = curSec / 60
            val curRemSec = curSec % 60
            Text(
                text = "%d:%02d".format(curMin, curRemSec),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(40.dp)
            )

            Slider(
                value = progress,
                onValueChange = { newProg ->
                    onSeek((newProg * durationMs).toLong())
                },
                modifier = Modifier.weight(1f).height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = GlassColors.AccentPurple,
                    activeTrackColor = GlassColors.AccentPurple,
                    inactiveTrackColor = GlassColors.GlassBorder
                )
            )

            val totalSec = durationMs / 1000
            val totalMin = totalSec / 60
            val totalRemSec = totalSec % 60
            Text(
                text = "%d:%02d".format(totalMin, totalRemSec),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(40.dp)
            )
        }

        // Controls & Info Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Current Track Info
            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassColors.GlassSurfaceActive),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = GlassColors.AccentPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = currentTrack?.title ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack?.let { "${it.artist} • ${it.formatBadgeText}" } ?: "Select a song from library",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = GlassColors.TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Center: Playback Controls
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) GlassColors.AccentPurple else GlassColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = GlassColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                FloatingActionButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    containerColor = GlassColors.AccentPurple,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = GlassColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) GlassColors.AccentPurple else GlassColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Right: Volume Slider
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (volume == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = GlassColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(100.dp).height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = GlassColors.TextPrimary,
                        activeTrackColor = GlassColors.AccentPurple,
                        inactiveTrackColor = GlassColors.GlassBorder
                    )
                )
            }
        }
    }
}
