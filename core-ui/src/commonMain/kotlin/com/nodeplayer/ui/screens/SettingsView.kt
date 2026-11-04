package com.nodeplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.ReplayGainMode
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.LocalGlassThemeConfig

@Composable
fun SettingsView(
    audioOutputInfo: AudioOutputInfo = AudioOutputInfo(),
    isExclusiveMode: Boolean = false,
    onToggleExclusiveMode: (Boolean) -> Unit = {},
    crossfadeSeconds: Float = 0.0f,
    onCrossfadeChange: (Float) -> Unit = {},
    gaplessEnabled: Boolean = true,
    onGaplessToggle: (Boolean) -> Unit = {},
    replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    onReplayGainModeChange: (ReplayGainMode) -> Unit = {},
    lastFmEnabled: Boolean = false,
    onLastFmToggle: (Boolean) -> Unit = {},
    currentMusicFolder: String = "",
    onSelectMusicFolder: () -> Unit = {},
    onRescanLibrary: () -> Unit = {},
    onOpenCustomizeUi: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSyncCenter: () -> Unit = {},
    trackCount: Int = 0,
    nodeConnectEnabled: Boolean = true,
    onToggleNodeConnect: (Boolean) -> Unit = {},
    themeConfig: GlassThemeConfig = LocalGlassThemeConfig.current,
    onThemeConfigChange: (GlassThemeConfig) -> Unit = {},
    isExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }
    var showHotkeysDialog by remember { mutableStateOf(false) }

    fun matchesFilter(vararg terms: String): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim().lowercase()
        return terms.any { it.lowercase().contains(q) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "Audio routing, DSP, glass appearance, Node Connect, and hotkeys",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
            }

            // Quick Hotkeys Pill Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2230).copy(alpha = 0.8f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .clickable { showHotkeysDialog = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hotkeys", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Search Bar in Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(Color(0xFF141722).copy(alpha = 0.78f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(21.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    Text("Search settings (DAC, WASAPI, EQ, Shaders, Connect)...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchQuery.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2-Column Grid (if expanded) or 1-Column Stack
        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (matchesFilter("audio", "dac", "wasapi", "exclusive", "output", "bit-perfect", "pipeline")) {
                        AudioRoutingCard(
                            audioOutputInfo = audioOutputInfo,
                            isExclusiveMode = isExclusiveMode,
                            onToggleExclusiveMode = onToggleExclusiveMode,
                            accent = accent
                        )
                    }

                    if (matchesFilter("dsp", "equalizer", "eq", "crossfade", "gapless", "replaygain", "transitions")) {
                        DspAndTransitionsCard(
                            crossfadeSeconds = crossfadeSeconds,
                            onCrossfadeChange = onCrossfadeChange,
                            gaplessEnabled = gaplessEnabled,
                            onGaplessToggle = onGaplessToggle,
                            replayGainMode = replayGainMode,
                            onReplayGainModeChange = onReplayGainModeChange,
                            onOpenEqualizer = onOpenEqualizer,
                            accent = accent
                        )
                    }

                    if (matchesFilter("library", "music", "folder", "scan", "rescan", "tracks")) {
                        MusicFoldersCard(
                            currentMusicFolder = currentMusicFolder,
                            trackCount = trackCount,
                            onSelectMusicFolder = onSelectMusicFolder,
                            onRescanLibrary = onRescanLibrary,
                            accent = accent
                        )
                    }
                }

                // Right Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (matchesFilter("connect", "node connect", "cast", "network", "remote", "devices", "sync")) {
                        NodeConnectCard(
                            nodeConnectEnabled = nodeConnectEnabled,
                            onToggleNodeConnect = onToggleNodeConnect,
                            onOpenSyncCenter = onOpenSyncCenter,
                            accent = accent
                        )
                    }

                    if (matchesFilter("appearance", "glass", "oled", "shader", "customize", "theme", "performance")) {
                        AppearanceAndGlassCard(
                            themeConfig = themeConfig,
                            onThemeConfigChange = onThemeConfigChange,
                            onOpenCustomizeUi = onOpenCustomizeUi,
                            accent = accent
                        )
                    }

                    if (matchesFilter("last.fm", "scrobble", "profile", "about", "version", "offline", "security")) {
                        AboutAndScrobblerCard(
                            lastFmEnabled = lastFmEnabled,
                            onLastFmToggle = onLastFmToggle,
                            accent = accent
                        )
                    }
                }
            }
        } else {
            // Phone single-column stack
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (matchesFilter("audio", "dac", "wasapi", "exclusive", "output")) {
                    AudioRoutingCard(audioOutputInfo, isExclusiveMode, onToggleExclusiveMode, accent)
                }
                if (matchesFilter("dsp", "equalizer", "eq", "crossfade", "gapless")) {
                    DspAndTransitionsCard(crossfadeSeconds, onCrossfadeChange, gaplessEnabled, onGaplessToggle, replayGainMode, onReplayGainModeChange, onOpenEqualizer, accent)
                }
                if (matchesFilter("connect", "node connect", "cast", "sync")) {
                    NodeConnectCard(nodeConnectEnabled, onToggleNodeConnect, onOpenSyncCenter, accent)
                }
                if (matchesFilter("library", "folder", "rescan")) {
                    MusicFoldersCard(currentMusicFolder, trackCount, onSelectMusicFolder, onRescanLibrary, accent)
                }
                if (matchesFilter("appearance", "glass", "oled")) {
                    AppearanceAndGlassCard(themeConfig, onThemeConfigChange, onOpenCustomizeUi, accent)
                }
                if (matchesFilter("last.fm", "about", "version")) {
                    AboutAndScrobblerCard(lastFmEnabled, onLastFmToggle, accent)
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Hotkeys Dialog
    if (showHotkeysDialog) {
        AlertDialog(
            onDismissRequest = { showHotkeysDialog = false },
            confirmButton = {
                TextButton(onClick = { showHotkeysDialog = false }) {
                    Text("Close", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keyboard & Mouse Shortcuts", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotkeyRow("Space", "Play / Pause toggle")
                    HotkeyRow("Right / Left Arrow", "Seek ±5 seconds")
                    HotkeyRow("Shift + Right / Left", "Next / Previous track")
                    HotkeyRow("Up / Down Arrow", "Volume ±5%")
                    HotkeyRow("Ctrl + F", "Focus Search Capsule")
                    HotkeyRow("Ctrl + 1 / 2 / 3", "Navigate Home / Library / Settings")
                    HotkeyRow("M", "Mute / Unmute audio")
                    HotkeyRow("Mouse Wheel on Bar", "Scrub playback position smoothly")
                }
            },
            containerColor = Color(0xFF141722),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun HotkeyRow(key: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(key, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

// 1. Audio Routing / DAC Card
@Composable
private fun AudioRoutingCard(
    audioOutputInfo: AudioOutputInfo,
    isExclusiveMode: Boolean,
    onToggleExclusiveMode: (Boolean) -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speaker, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Routing / DAC", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                GlassBadge(
                    text = if (isExclusiveMode) "WASAPI EXCLUSIVE" else "WASAPI SHARED",
                    color = if (isExclusiveMode) Color(0xFFF59E0B) else accent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Active Device: ${audioOutputInfo.outputDeviceName}", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
            Text("Sink Rate: ${if (audioOutputInfo.sampleRate > 0) audioOutputInfo.sampleRate else 48000} Hz • 32-bit Float Pipeline", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("WASAPI Exclusive Mode (Bit-Perfect)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Bypasses Windows mixer for bit-exact audiophile output", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Switch(
                    checked = isExclusiveMode,
                    onCheckedChange = onToggleExclusiveMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }
        }
    }
}

// 2. DSP & Transitions Card
@Composable
private fun DspAndTransitionsCard(
    crossfadeSeconds: Float,
    onCrossfadeChange: (Float) -> Unit,
    gaplessEnabled: Boolean,
    onGaplessToggle: (Boolean) -> Unit,
    replayGainMode: ReplayGainMode,
    onReplayGainModeChange: (ReplayGainMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DSP & Equalizer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = onOpenEqualizer,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f), contentColor = accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Open EQ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade Duration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("%.1fs".format(crossfadeSeconds), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Slider(
                value = crossfadeSeconds,
                onValueChange = onCrossfadeChange,
                valueRange = 0f..12f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gapless Playback", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Zero silence between continuous tracks", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Switch(
                    checked = gaplessEnabled,
                    onCheckedChange = onGaplessToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ReplayGain Normalization", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("R128 loudness matching", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(replayGainMode.name, color = accent, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ReplayGainMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    onReplayGainModeChange(mode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 3. Music Folders & Library Card
@Composable
private fun MusicFoldersCard(
    currentMusicFolder: String,
    trackCount: Int,
    onSelectMusicFolder: () -> Unit,
    onRescanLibrary: () -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Music Library", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                GlassBadge(text = "$trackCount TRACKS", color = accent)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (currentMusicFolder.isNotBlank()) "Path: $currentMusicFolder" else "Default music folder in use",
                fontSize = 12.sp,
                color = if (currentMusicFolder.isNotBlank()) accent else Color.White.copy(alpha = 0.6f),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSelectMusicFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRescanLibrary,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 4. Node Connect & Sync Center Card
@Composable
private fun NodeConnectCard(
    nodeConnectEnabled: Boolean,
    onToggleNodeConnect: (Boolean) -> Unit,
    onOpenSyncCenter: () -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cast, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Node Connect", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Switch(
                    checked = nodeConnectEnabled,
                    onCheckedChange = onToggleNodeConnect,
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (nodeConnectEnabled) "Local network active (mTLS EC-256 + mDNS discovery)" else "Disabled (Zero network sockets open)",
                fontSize = 11.5.sp,
                color = if (nodeConnectEnabled) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenSyncCenter,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2333)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Sync Center (HLC 3-Snapshot Undo)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// 5. Appearance & Glass Card
@Composable
private fun AppearanceAndGlassCard(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onOpenCustomizeUi: () -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Appearance & Glass", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = onOpenCustomizeUi,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f), contentColor = accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Customizer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OLED Pure Black", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Zero luminance backdrop for OLED screens", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Switch(
                    checked = themeConfig.isOledBlack,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(isOledBlack = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Performance / Low-Power Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Disables dynamic glass shaders to save battery", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Switch(
                    checked = themeConfig.isPerformanceMode,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(isPerformanceMode = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }
        }
    }
}

// 6. Scrobbler & About Card
@Composable
private fun AboutAndScrobblerCard(
    lastFmEnabled: Boolean,
    onLastFmToggle: (Boolean) -> Unit,
    accent: Color
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Last.fm Scrobbler", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Scrobble listened tracks (opt-in)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Switch(
                    checked = lastFmEnabled,
                    onCheckedChange = onLastFmToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))

            Text("Node Player v1.6.0", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
            Spacer(modifier = Modifier.height(4.dp))
            Text("No network use unless you opt in. Local peer-to-peer audio streaming & sync.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            Text("Windows MSI/EXE is Authenticode-unsigned (SmartScreen prompt on first run). Android APK signed with release key.", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}
