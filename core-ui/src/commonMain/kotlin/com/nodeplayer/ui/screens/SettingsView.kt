package com.nodeplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.ReplayGainMode
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassButton
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.theme.LocalAccentColor

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
    trackCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = "Audio engine, DSP routing, music library folders, and playback preferences",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Music Folders & Library Management Card (Prominently featured inside Settings)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Music Folders & Library",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (currentMusicFolder.isNotBlank()) "Folder: $currentMusicFolder" else "No folder selected yet",
                            fontSize = 12.sp,
                            color = if (currentMusicFolder.isNotBlank()) accent else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    GlassBadge(
                        text = "$trackCount TRACKS",
                        color = accent
                    )
                }

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
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Music Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRescanLibrary,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan Library", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Audio Engine & WASAPI Output Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio Output Engine",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Device: ${audioOutputInfo.outputDeviceName}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                    }
                    GlassBadge(
                        text = if (isExclusiveMode) "WASAPI EXCLUSIVE" else "WASAPI SHARED",
                        color = if (isExclusiveMode) Color(0xFFF59E0B) else accent
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WASAPI Exclusive Mode (Bit-Perfect)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Bypasses Windows mixer for bit-exact audiophile output", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = isExclusiveMode,
                        onCheckedChange = onToggleExclusiveMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pipeline Format", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("32-bit Float Internal Processing • Hardware Direct", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Text(
                        text = "${if (audioOutputInfo.sampleRate > 0) audioOutputInfo.sampleRate else 48000} Hz",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Playback Transitions & Signal Conditioning Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Playback & Transitions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dual-Player Crossfade", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("%.1fs".format(crossfadeSeconds), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Slider(
                    value = crossfadeSeconds,
                    onValueChange = onCrossfadeChange,
                    valueRange = 0f..12f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gapless Playback", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Seamless buffer transition without pause between live/concept tracks", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = gaplessEnabled,
                        onCheckedChange = onGaplessToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ReplayGain Normalization", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Equalizes loudness based on R128 track or album tags", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
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

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Equalizer & Customize UI Quick Jump Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                onClick = onOpenEqualizer
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Sound Lab & EQ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("10-Band Biquad, DSP", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            GlassCard(
                modifier = Modifier.weight(1f),
                onClick = onOpenCustomizeUi
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Customize UI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Shaders, Palettes", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Last.fm Scrobbler Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last.fm Scrobbler",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Automatically scrobble listened tracks to Last.fm profile",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = lastFmEnabled,
                    onCheckedChange = onLastFmToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. About & Version Info
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Node Player",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Version 1.4.0 (Unified Compose Multiplatform)",
                    fontSize = 12.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pure audio fidelity with hardware-accelerated liquid glass aesthetics.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
