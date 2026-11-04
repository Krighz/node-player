package com.nodeplayer.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nodeplayer.desktop.audio.WindowsPlaybackEngine
import com.nodeplayer.desktop.data.DesktopSettingsStore
import com.nodeplayer.desktop.data.DesktopTrackDatabase
import com.nodeplayer.desktop.data.DesktopTrackRepositoryAdapter
import com.nodeplayer.desktop.data.WindowsFileScanner
import com.nodeplayer.model.*
import com.nodeplayer.network.protocol.ControlStateEventMessage
import com.nodeplayer.network.service.ConnectState
import com.nodeplayer.network.service.LocalPlayerController
import com.nodeplayer.network.service.NodeConnectHub
import com.nodeplayer.ui.SharedPlayerLayout
import com.nodeplayer.ui.components.NodeConnectPairingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 750.dp)
    val coroutineScope = rememberCoroutineScope()

    remember {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home", ".")
        val logDir = File(appData, "NodePlayer/logs").apply { mkdirs() }
        com.nodeplayer.common.logging.NodeLog.initFileLogging(File(logDir, "node_connect.log"))
        com.nodeplayer.common.logging.NodeLog.addSink { level, tag, msg, tr ->
            println("[${level.name}] [$tag] $msg")
            tr?.printStackTrace()
        }
    }

    val settingsStore = remember { DesktopSettingsStore() }
    val database = remember { DesktopTrackDatabase() }
    val scanner = remember { WindowsFileScanner() }
    val engine = remember { WindowsPlaybackEngine() }

    val trackRepo = remember { DesktopTrackRepositoryAdapter(database) }
    val localController = remember {
        object : LocalPlayerController {
            override fun play() { if (!engine.isPlaying.value) engine.playPause() }
            override fun pause() { if (engine.isPlaying.value) engine.playPause() }
            override fun playPause() { engine.playPause() }
            override fun seekTo(positionMs: Long) { engine.seekTo(positionMs) }
            override fun skipToNext() { engine.next() }
            override fun skipToPrevious() { engine.previous() }
            override fun setVolume(volume: Float) { engine.setVolume(volume) }
            override fun toggleShuffle() { engine.toggleShuffle() }
            override fun cycleRepeat() { engine.cycleRepeatMode() }
        }
    }

    var trackList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var folders by remember { mutableStateOf<List<MusicFolder>>(emptyList()) }

    var audioEffectConfig by remember { mutableStateOf(settingsStore.loadEffectConfig()) }
    var themeConfig by remember { mutableStateOf(settingsStore.loadThemeConfig().copy(isOledBlack = false)) }
    var currentMusicFolder by remember { mutableStateOf(settingsStore.getLastMusicFolder()) }

    val hub = remember {
        NodeConnectHub(
            trackRepository = trackRepo,
            localController = localController,
            onIncomingCastStream = { session, jitter ->
                engine.playIncomingCastStream(jitter, session)
            },
            onIncomingHandoff = { handoff ->
                val matched = trackList.firstOrNull { it.title.equals(handoff.trackTitle, ignoreCase = true) }
                if (matched != null) {
                    engine.playTrack(matched)
                    engine.seekTo(handoff.positionMs)
                }
                true
            }
        )
    }

    LaunchedEffect(hub) {
        engine.preDspPcmTap = { pcmBytes, ptsMs ->
            coroutineScope.launch {
                hub.castService.sendPcmAudioChunk(pcmBytes, ptsMs)
            }
        }
        engine.onSeekFlushCallback = { ptsMs ->
            coroutineScope.launch {
                hub.castService.flushSenderStream(ptsMs)
            }
        }
    }

    val connectState by hub.connectionState.collectAsState()
    val connectedDeviceCount by hub.connectedDeviceCount.collectAsState()
    val isNodeConnectOn = connectState != ConnectState.OFF

    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingProgress by remember { mutableStateOf(false) }
    var pairingStatusMsg by remember { mutableStateOf<String?>(null) }
    val activeSession by hub.activePairingSession.collectAsState()
    val discoveredPeers by hub.discoveredPeers.collectAsState()

    val localIpAddress = remember {
        try {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress ?: java.net.InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "127.0.0.1"
        }
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

    // Apply saved audio DSP config, load library, and start Node Connect on startup
    LaunchedEffect(Unit) {
        engine.applyEffectConfig(audioEffectConfig)
        refreshLibrary()
        hub.start(
            deviceName = "Node Player PC (${System.getProperty("user.name", "Windows")})",
            platform = "Windows"
        )
    }

    // Broadcast playback state to connected controllers
    LaunchedEffect(currentTrack, isPlaying, positionMs) {
        val t = currentTrack
        if (t != null && isNodeConnectOn && connectedDeviceCount > 0) {
            hub.controlService.broadcastLocalState(
                ControlStateEventMessage(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    volume = volume,
                    trackTitle = t.title,
                    artist = t.artist,
                    album = t.album,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    formatBadge = t.formatBadgeText
                )
            )
        }
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

            if (currentMusicFolder.isNotBlank()) {
                val currentDir = File(currentMusicFolder)
                if (currentDir.exists()) {
                    chooser.currentDirectory = currentDir
                }
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedDir = chooser.selectedFile
                if (selectedDir != null && selectedDir.exists()) {
                    val path = selectedDir.absolutePath
                    currentMusicFolder = path
                    settingsStore.setLastMusicFolder(path)

                    coroutineScope.launch(Dispatchers.IO) {
                        val tracks = mutableListOf<Track>()
                        scanner.scanPath(path) { meta ->
                            tracks.add(scanner.toTrack(meta))
                        }
                        database.insertOrUpdateTracks(tracks)
                        refreshLibrary()
                    }
                }
            }
        }
    }

    fun rescanCurrentFolder() {
        if (currentMusicFolder.isNotBlank()) {
            val dir = File(currentMusicFolder)
            if (dir.exists()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val tracks = mutableListOf<Track>()
                    scanner.scanPath(dir.absolutePath) { meta ->
                        tracks.add(scanner.toTrack(meta))
                    }
                    database.insertOrUpdateTracks(tracks)
                    refreshLibrary()
                }
                return
            }
        }
        pickAndScanFolder()
    }

    Window(
        onCloseRequest = {
            hub.stop()
            engine.release()
            exitApplication()
        },
        state = windowState,
        title = "Node Player",
        icon = painterResource("icon.png")
    ) {
        window.minimumSize = Dimension(960, 600)

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(themeConfig.fixedAccentColorArgb),
                background = Color(0xFF070912),
                surface = Color(0xFF111424)
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
                nodeConnectEnabled = isNodeConnectOn,
                connectedDeviceCount = connectedDeviceCount,
                onToggleNodeConnect = { enabled ->
                    if (enabled) {
                        hub.start(
                            deviceName = "Node Player PC (${System.getProperty("user.name", "Windows")})",
                            platform = "Windows"
                        )
                    } else {
                        hub.stop()
                    }
                },
                currentMusicFolder = currentMusicFolder,
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
                    settingsStore.saveEffectConfig(cfg)
                },
                onThemeConfigChange = { cfg ->
                    themeConfig = cfg
                    settingsStore.saveThemeConfig(cfg)
                },
                onToggleExclusiveMode = { engine.setExclusiveMode(it) },
                onPairNewDevice = {
                    if (hub.activePairingSession.value == null) {
                        hub.startPairingSession()
                    }
                    pairingStatusMsg = null
                    showPairingDialog = true
                },
                onCastToPeer = { peer ->
                    coroutineScope.launch {
                        val t = currentTrack
                        if (t != null) {
                            val conn = hub.activeConnections.firstOrNull { it.peerDeviceId == peer.id }
                            if (conn != null) {
                                hub.castService.startCastAsSender(conn, t, positionMs)
                            }
                        }
                    }
                },
                onOpenSyncCenter = {
                    coroutineScope.launch {
                        for (conn in hub.activeConnections) {
                            hub.syncService.pushSyncPackage(conn)
                        }
                    }
                },
                onRescanLibrary = { rescanCurrentFolder() },
                onSelectMusicFolder = { pickAndScanFolder() }
            )

            var pendingAllowPrompt by remember { mutableStateOf<Pair<String, (Boolean, Boolean) -> Unit>?>(null) }
            LaunchedEffect(hub) {
                hub.onAllowPrompt = { name, id, callback ->
                    pendingAllowPrompt = name to callback
                }
            }

            if (pendingAllowPrompt != null) {
                val prompt = pendingAllowPrompt!!
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        prompt.second(false, false)
                        pendingAllowPrompt = null
                    },
                    title = { androidx.compose.material3.Text("Allow Connection?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White) },
                    text = {
                        androidx.compose.material3.Text("Device '${prompt.first}' is requesting to connect to Node Player on this PC.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(onClick = {
                            prompt.second(true, true)
                            pendingAllowPrompt = null
                        }) {
                            androidx.compose.material3.Text("Always Allow")
                        }
                    },
                    dismissButton = {
                        Row {
                            androidx.compose.material3.TextButton(onClick = {
                                prompt.second(true, false)
                                pendingAllowPrompt = null
                            }) {
                                androidx.compose.material3.Text("Allow Once")
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                prompt.second(false, false)
                                pendingAllowPrompt = null
                            }) {
                                androidx.compose.material3.Text("Deny", color = androidx.compose.ui.graphics.Color(0xFFEF4444))
                            }
                        }
                    }
                )
            }

            if (showPairingDialog) {
                NodeConnectPairingDialog(
                    onDismissRequest = { showPairingDialog = false },
                    activeSession = activeSession,
                    localIp = localIpAddress,
                    localDeviceId = hub.activeDeviceId ?: "pc-host",
                    localDeviceName = "Node Player PC (${System.getProperty("user.name", "Windows")})",
                    localPort = hub.activePort,
                    localCandidateIps = hub.getLocalCandidateIps().ifEmpty { listOf(localIpAddress) },
                    discoveredPeers = discoveredPeers,
                    onStartSession = { hub.startPairingSession() },
                    onCancelSession = { hub.cancelPairingSession() },
                    onPairWithHost = { host, port, pin ->
                        coroutineScope.launch {
                            pairingProgress = true
                            pairingStatusMsg = "Connecting to $host:$port..."
                            val res = hub.pairWithHost(host, port, pin)
                            pairingProgress = false
                            if (res.isSuccess) {
                                pairingStatusMsg = "Successfully paired with ${res.getOrNull()}!"
                                delay(1200)
                                showPairingDialog = false
                            } else {
                                pairingStatusMsg = "Pairing failed: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    onConnectToPeerDirectly = { peer ->
                        coroutineScope.launch {
                            pairingProgress = true
                            pairingStatusMsg = "Connecting to ${peer.deviceName}..."
                            val conn = hub.connectToPeer(peer)
                            pairingProgress = false
                            if (conn != null) {
                                pairingStatusMsg = "Connected to ${peer.deviceName}!"
                                delay(1000)
                                showPairingDialog = false
                            } else {
                                pairingStatusMsg = "Connection failed to ${peer.deviceName}"
                            }
                        }
                    },
                    onConnectToAddress = { host, port ->
                        coroutineScope.launch {
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
                            val conn = hub.connectToPeer(peer)
                            pairingProgress = false
                            if (conn != null) {
                                pairingStatusMsg = "Connected to $host:$port!"
                                delay(1000)
                                showPairingDialog = false
                            } else {
                                pairingStatusMsg = "Failed to connect to $host:$port"
                            }
                        }
                    },
                    onScanNetworkClicked = { hub.triggerSubnetScan() },
                    onProbeGatewayClicked = { hub.triggerGatewayProbe() },
                    onTestPingClicked = { host, port, cb ->
                        coroutineScope.launch {
                            val res = hub.testConnectionTo(host, port)
                            cb(res)
                        }
                    },
                    onCopyLogClicked = {
                        val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(log), null)
                    },
                    onExportLogClicked = {
                        val log = com.nodeplayer.common.logging.NodeLog.exportFormattedLog()
                        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home", ".")
                        val out = File(appData, "NodePlayer/logs/node_connect_export.txt")
                        out.parentFile?.mkdirs()
                        out.writeText(log)
                    },
                    isPairingInProgress = pairingProgress,
                    pairingStatusMessage = pairingStatusMsg
                )
            }
        }
    }
}
