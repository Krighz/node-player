package com.nodeplayer.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import dev.chrisbanes.haze.HazeState
import java.io.File

@Composable
fun SettingsScreen(
    outputInfo: AudioOutputInfo,
    themeConfig: GlassThemeConfig,
    allFolders: List<MusicFolder> = emptyList(),
    excludedFolders: Set<String> = emptySet(),
    minDurationSeconds: Int = 30,
    onMinDurationChange: (Int) -> Unit = {},
    onToggleExcludeFolder: (String) -> Unit = {},
    onAddCustomFolder: (String) -> Unit = {},
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onRescanLibrary: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var gaplessEnabled by remember { mutableStateOf(true) }
    var crossfadeSeconds by remember { mutableFloatStateOf(2.0f) }
    var lrclibOptIn by remember { mutableStateOf(false) }
    var isFoldersExpanded by remember { mutableStateOf(true) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: it.toString()
            onAddCustomFolder(path)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Text(
                text = "Audio output, aesthetics & folder engine",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.65f)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. Audio Output Section
        Text(
            text = "AUDIO OUTPUT",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Device
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Output Device", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(outputInfo.outputDeviceName, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Icon(imageVector = Icons.Rounded.Speaker, contentDescription = null, tint = accent)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Bit-Perfect USB Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bit-Perfect Direct USB DAC", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            if (outputInfo.outputDeviceName.contains("USB")) "Bypasses Android audio flinger on USB DAC"
                            else "Connect a USB DAC to enable bit-perfect mode",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = outputInfo.isBitPerfect,
                        onCheckedChange = { /* Bit perfect toggle */ },
                        enabled = outputInfo.outputDeviceName.contains("USB"),
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Float Output
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("32-bit Float Pipeline", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Active in ExoPlayer DefaultAudioSink", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981)))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Folder Wise Music & Scans Section
        Text(
            text = "LIBRARY & FOLDERS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Rescan Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rescan Music Folders", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Scan phone storage with current filter rules", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Button(
                        onClick = onRescanLibrary,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rescan", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Short Clips / Call Recording Filter Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ignore Short Audio & Voice Notes", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Filters out call recordings and notification chimes (< ${minDurationSeconds}s)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Text(
                        "${minDurationSeconds}s",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accent)
                    )
                }
                Slider(
                    value = minDurationSeconds.toFloat(),
                    onValueChange = { onMinDurationChange(it.toInt()) },
                    valueRange = 0f..60f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Custom Folder Picker via Storage Access Framework
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add Custom Music Folder", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Select SD Card or custom directory to scan", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Detected Folders Whitelist/Blacklist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFoldersExpanded = !isFoldersExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Manage Scanned Folders (${allFolders.size})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Toggle folders to include or exclude from library", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Icon(
                        imageVector = if (isFoldersExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = accent
                    )
                }

                AnimatedVisibility(visible = isFoldersExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (allFolders.isEmpty()) {
                            Text("No folders scanned yet", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f)))
                        } else {
                            allFolders.forEach { folder ->
                                val isExcluded = excludedFolders.contains(folder.path)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleExcludeFolder(folder.path) }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (isExcluded) Color.White.copy(alpha = 0.35f) else accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isExcluded) Color.White.copy(alpha = 0.40f) else Color.White
                                                )
                                            )
                                            Text(
                                                text = "${folder.trackCount} tracks • ${folder.path.takeLast(30)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.50f)
                                                )
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = !isExcluded,
                                        onCheckedChange = { onToggleExcludeFolder(folder.path) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Playback Section
        Text(
            text = "PLAYBACK ENGINE",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Gapless
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Gapless Playback", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Zero-gap transition for live and concept albums", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = gaplessEnabled,
                        onCheckedChange = { gaplessEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Crossfade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Crossfade Duration", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("%.1fs".format(crossfadeSeconds), color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = crossfadeSeconds,
                    onValueChange = { crossfadeSeconds = it },
                    valueRange = 0.0f..10.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Aesthetics & Glassmorphism Section
        Text(
            text = "APPEARANCE & GLASS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // OLED Black
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OLED Pure Black Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Turns liquid background to pitch #000000", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = themeConfig.isOledBlack,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isOledBlack = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Dynamic Artwork Accent
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dynamic Album Accent", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Extracts fluid theme colors via Palette API", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = themeConfig.useDynamicAccent,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(useDynamicAccent = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Blur Strength
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Liquid Glass Blur Radius", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${themeConfig.blurRadiusDp.toInt()} dp", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = themeConfig.blurRadiusDp,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(blurRadiusDp = it)) },
                    valueRange = 8f..40f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Lyrics & Privacy Section
        Text(
            text = "LYRICS & PRIVACY",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Lyrics (LRCLIB)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Strictly opt-in. When off, zero internet requests are made.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = lrclibOptIn,
                        onCheckedChange = { lrclibOptIn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(200.dp))
    }
}
