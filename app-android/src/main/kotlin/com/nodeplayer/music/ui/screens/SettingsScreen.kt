package com.nodeplayer.music.ui.screens

import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.CrossfadeConfig
import com.nodeplayer.model.FadeCurve
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.widget.FloatingOverlayService
import com.nodeplayer.music.widget.WidgetCard4x2Provider
import com.nodeplayer.music.widget.WidgetLyrics4x3Provider
import com.nodeplayer.music.widget.WidgetPreferences
import com.nodeplayer.music.widget.WidgetSlim4x1Provider
import com.nodeplayer.music.widget.WidgetSquare2x2Provider
import com.nodeplayer.music.widget.WidgetUpdateManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import dev.chrisbanes.haze.HazeState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    outputInfo: AudioOutputInfo,
    themeConfig: GlassThemeConfig,
    crossfadeConfig: CrossfadeConfig = CrossfadeConfig(),
    onCrossfadeConfigChange: (CrossfadeConfig) -> Unit = {},
    systemEqEnabled: Boolean = false,
    systemEqSessions: List<com.nodeplayer.music.service.SystemWideEqService.SessionInfo> = emptyList(),
    onToggleSystemEq: (Boolean) -> Unit = {},
    currentDeviceType: com.nodeplayer.model.OutputDeviceType = com.nodeplayer.model.OutputDeviceType.SPEAKER,
    autoDeviceProfilesEnabled: Boolean = true,
    onToggleAutoDeviceProfiles: (Boolean) -> Unit = {},
    resumeOnHeadsetConnect: Boolean = true,
    onToggleResumeOnHeadsetConnect: (Boolean) -> Unit = {},
    resamplerRateChoice: Int = 0,
    onResamplerRateChange: (Int) -> Unit = {},
    allFolders: List<MusicFolder> = emptyList(),
    excludedFolders: Set<String> = emptySet(),
    excludedTracks: List<Track> = emptyList(),
    minDurationSeconds: Int = 30,
    onMinDurationChange: (Int) -> Unit = {},
    onToggleExcludeFolder: (String) -> Unit = {},
    onAddCustomFolder: (String) -> Unit = {},
    onRestoreTrack: (Long) -> Unit = {},
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onRescanLibrary: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var gaplessEnabled by remember { mutableStateOf(true) }
    var crossfadeSeconds by remember { mutableFloatStateOf(2.0f) }
    var lrclibOptIn by remember { mutableStateOf(false) }
    val sharedPrefs = remember { context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    var fakeHiRes by remember { mutableStateOf(sharedPrefs.getBoolean("fake_hires_enabled", true)) }
    var loudnessScan by remember { mutableStateOf(sharedPrefs.getBoolean("loudness_scan_enabled", true)) }
    var targetLufs by remember { mutableFloatStateOf(sharedPrefs.getFloat("loudness_target_lufs", -14.0f)) }
    var clipGuard by remember { mutableStateOf(sharedPrefs.getBoolean("loudness_clip_guard", true)) }
    var drMeter by remember { mutableStateOf(sharedPrefs.getBoolean("dr_meter_enabled", true)) }
    var smartPlaylists by remember { mutableStateOf(sharedPrefs.getBoolean("smart_playlists_enabled", true)) }
    var duplicateFinder by remember { mutableStateOf(sharedPrefs.getBoolean("duplicate_finder_enabled", true)) }
    var genreCrossfade by remember { mutableStateOf(sharedPrefs.getBoolean("genre_crossfade_enabled", true)) }
    var sleepFinishTrack by remember { mutableStateOf(sharedPrefs.getBoolean("sleep_timer_finish_track", true)) }
    var sleepFadeSec by remember { mutableIntStateOf(sharedPrefs.getInt("sleep_timer_fade_seconds", 10)) }
    var sleepShake by remember { mutableStateOf(sharedPrefs.getBoolean("sleep_timer_shake_extend", true)) }
    var badgeMode by remember { mutableStateOf(sharedPrefs.getString("quality_badges_mode", "DETAILED") ?: "DETAILED") }
    var spectrogramEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("spectrogram_enabled", true)) }
    var soundLabEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("sound_lab_enabled", true)) }
    var miniGestures by remember { mutableStateOf(sharedPrefs.getBoolean("mini_player_gestures_enabled", true)) }
    var lastFmSessionKey by remember { mutableStateOf(sharedPrefs.getString("lastfm_session_key", "") ?: "") }
    var lastFmUsername by remember { mutableStateOf(sharedPrefs.getString("lastfm_username", "") ?: "") }
    var lastFmScrobbleEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("lastfm_scrobble_enabled", false)) }
    var lastFmPendingCount by remember { mutableIntStateOf(0) }
    var lastFmAuthToken by remember { mutableStateOf<String?>(null) }
    var customApiKey by remember { mutableStateOf(sharedPrefs.getString("custom_lastfm_api_key", "").orEmpty()) }
    var customApiSecret by remember { mutableStateOf(sharedPrefs.getString("custom_lastfm_api_secret", "").orEmpty()) }
    var showAdvancedLastFm by remember { mutableStateOf(false) }
    var showSecretText by remember { mutableStateOf(false) }
    var lastFmConfigVersion by remember { mutableIntStateOf(0) }

    var audioAnalysisEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("audio_analysis_enabled", true)) }
    var analysisSpeed by remember { mutableStateOf(sharedPrefs.getString("analysis_speed", "Slow") ?: "Slow") }
    var analysisChargingOnly by remember { mutableStateOf(sharedPrefs.getBoolean("analysis_charging_only", false)) }

    val isAnalysisRunning by com.nodeplayer.music.library.AudioAnalysisManager.isRunning.collectAsState()
    val isAnalysisPaused by com.nodeplayer.music.library.AudioAnalysisManager.isPaused.collectAsState()
    val analysisProgress by com.nodeplayer.music.library.AudioAnalysisManager.progress.collectAsState()
    val analysisSongTitle by com.nodeplayer.music.library.AudioAnalysisManager.currentSongTitle.collectAsState()
    var showLastFmConfirmDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var isFoldersExpanded by remember { mutableStateOf(true) }
    var isExcludedTracksExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTopMenu by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(sharedPrefs.getBoolean("show_advanced_settings", false)) }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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

            Box {
                IconButton(onClick = { showTopMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Quick Jump",
                        tint = accent
                    )
                }
                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false },
                    modifier = Modifier.background(Color(0xFF1E2130))
                ) {
                    listOf(
                        "Audio Output" to 0,
                        "UI & Glassmorphism" to 380,
                        "Folders & Engine" to 800,
                        "Playback & Crossfade" to 1200,
                        "Battery & ColorOS Guide" to 1600,
                        "Quality Tools & Analysis" to 1950,
                        "Widgets & Scrobbler" to 2300,
                        "About & System" to 2800
                    ).forEach { (label, scrollTarget) ->
                        DropdownMenuItem(
                            text = { Text(label, color = Color.White) },
                            onClick = {
                                showTopMenu = false
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(scrollTarget)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search settings...", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                focusedBorderColor = accent.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Per-Device Audio Profiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Switch Device Profiles", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Currently: ${currentDeviceType.displayName}",
                            style = MaterialTheme.typography.bodySmall.copy(color = accent)
                        )
                    }
                    Switch(
                        checked = autoDeviceProfilesEnabled,
                        onCheckedChange = onToggleAutoDeviceProfiles,
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Resume on Headset Connect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resume on Headset Connect", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Auto-resumes playback when wired or Bluetooth headset reconnects",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = resumeOnHeadsetConnect,
                        onCheckedChange = onToggleResumeOnHeadsetConnect,
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Resampler Output Rate
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Resampler Output Rate", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("Forces platform audio sink target rate (where supported)", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    Spacer(modifier = Modifier.height(8.dp))
                    val rates = listOf(0 to "Auto", 44100 to "44.1k", 48000 to "48k", 96000 to "96k", 192000 to "192k")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rates.forEach { (rate, label) ->
                            val isSel = resamplerRateChoice == rate
                            Button(
                                onClick = { onResamplerRateChange(rate) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) accent else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Crossfade & Playback Engine Section
        Text(
            text = "CROSSFADE & PLAYBACK",
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
                // Crossfade Main Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dual-Player Crossfade", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Seamless blend using secondary ExoPlayer with isolated DSP filter states",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = crossfadeConfig.enabled,
                        onCheckedChange = { onCrossfadeConfigChange(crossfadeConfig.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (crossfadeConfig.enabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Duration Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Crossfade Duration", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Clamped to half duration on short tracks", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Text(
                            "%.1fs".format(crossfadeConfig.durationSeconds),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accent)
                        )
                    }
                    Slider(
                        value = crossfadeConfig.durationSeconds,
                        onValueChange = { onCrossfadeConfigChange(crossfadeConfig.copy(durationSeconds = (it * 2).roundToInt() / 2f)) },
                        valueRange = 1.0f..12.0f,
                        steps = 21,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Curve Picker
                    Text("Volume Fade Curve", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text(
                        when (crossfadeConfig.curve) {
                            FadeCurve.EQUAL_POWER -> "Equal Power (Constant acoustic power, cos/sin curve)"
                            FadeCurve.LINEAR -> "Linear (Direct linear amplitude ramp)"
                            FadeCurve.LOGARITHMIC -> "Logarithmic (Hermite S-curve, smooth endpoints)"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FadeCurve.values().forEach { curve ->
                            val isSelected = crossfadeConfig.curve == curve
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCrossfadeConfigChange(crossfadeConfig.copy(curve = curve)) },
                                label = { Text(curve.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.25f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Auto Gapless Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Disable for Gapless Albums", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text(
                                "Preserves seamless continuous mix without crossfade for consecutive album tracks or live mixes",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                            )
                        }
                        Switch(
                            checked = crossfadeConfig.autoGapless,
                            onCheckedChange = { onCrossfadeConfigChange(crossfadeConfig.copy(autoGapless = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // System-Wide Equalizer Section
        Text(
            text = "SYSTEM-WIDE AUDIO EFFECTS",
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
                // Main Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System-Wide EQ (All Apps)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Applies 10-band EQ & dynamics limiter to other apps broadcasting AudioEffect sessions",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = systemEqEnabled,
                        onCheckedChange = onToggleSystemEq,
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (systemEqEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Live Sessions Telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Sessions", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text(
                                if (systemEqSessions.isNotEmpty())
                                    "${systemEqSessions.size} app session(s) intercepted"
                                else
                                    "Standby: Waiting for media apps to broadcast session",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                            )
                        }
                        Text(
                            if (systemEqSessions.isNotEmpty()) "INTERCEPTING" else "LISTENING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (systemEqSessions.isNotEmpty()) Color(0xFF10B981) else accent
                            )
                        )
                    }

                    if (systemEqSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        systemEqSessions.forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    session.packageName,
                                    style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Medium),
                                    maxLines = 1
                                )
                                Text(
                                    "Session ${session.sessionId}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Anti-Double-EQ Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "In-app biquad EQ is bypassed automatically to prevent double-EQ processing.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        )
                    }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Excluded Audio & Voice Notes Review Screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExcludedTracksExpanded = !isExcludedTracksExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Excluded Tracks & Voice Notes",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                        )
                        Text(
                            text = "${excludedTracks.size} items excluded • Tap to review & restore",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    IconButton(onClick = { isExcludedTracksExpanded = !isExcludedTracksExpanded }) {
                        Icon(
                            imageVector = if (isExcludedTracksExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }

                if (isExcludedTracksExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (excludedTracks.isEmpty()) {
                            Text(
                                "No tracks are currently excluded.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        } else {
                            for (track in excludedTracks.take(30)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist} • ${track.durationFormatted} • ${track.path.takeLast(35)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.50f)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { onRestoreTrack(track.id) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Restore", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (excludedTracks.size > 30) {
                                Text(
                                    "+ ${excludedTracks.size - 30} more excluded voice notes/files",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f))
                                )
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
                // 1. Theme Mode
                Text("Theme Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Select application appearance and dark/light switching", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light", "OLED" to "OLED")
                    modes.forEach { (mode, label) ->
                        val selected = themeConfig.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeConfigChange(themeConfig.copy(themeMode = mode, isOledBlack = mode == "OLED")) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 2. Glassmorphism Strength
                Text("Liquid Glassmorphism Strength", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Control translucency, refraction highlight and blur intensity", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val strengths = listOf("DRAMATIC" to "Dramatic", "BALANCED" to "Balanced", "SUBTLE" to "Subtle", "OFF" to "Solid")
                    strengths.forEach { (str, label) ->
                        val selected = themeConfig.glassStrength == str
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeConfigChange(themeConfig.copy(glassStrength = str)) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 3. Accent Color Source
                Text("Accent Color Source", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Where UI highlight colors originate from", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sources = listOf("ARTWORK" to "Album Art", "MATERIAL_YOU" to "Material You", "PRESET" to "Custom Preset")
                    sources.forEach { (src, label) ->
                        val selected = themeConfig.accentSource == src
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeConfigChange(themeConfig.copy(accentSource = src, useDynamicAccent = src == "ARTWORK")) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }

                if (themeConfig.accentSource == "PRESET") {
                    Spacer(modifier = Modifier.height(10.dp))
                    val presets = listOf(
                        0xFF6366F1 to "Indigo",
                        0xFF06B6D4 to "Cyan",
                        0xFF10B981 to "Emerald",
                        0xFFF59E0B to "Amber",
                        0xFFF43F5E to "Rose",
                        0xFF8B5CF6 to "Violet"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presets.forEach { (argb, _) ->
                            val isSel = themeConfig.fixedAccentColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(argb), shape = CircleShape)
                                    .border(
                                        width = if (isSel) 3.dp else 1.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onThemeConfigChange(themeConfig.copy(fixedAccentColorArgb = argb))
                                    }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 4. Spacing Density & Corner Roundness
                Text("Spacing Density & Corners", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Customize layout padding and card rounding", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val densities = listOf("COMPACT" to "Compact", "COMFORTABLE" to "Comfortable", "ROOMY" to "Roomy")
                    densities.forEach { (d, label) ->
                        val selected = themeConfig.spacingDensity == d
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeConfigChange(themeConfig.copy(spacingDensity = d)) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val roundness = listOf("SHARP" to "Sharp (8dp)", "ROUNDED" to "Rounded (16dp)", "PILL" to "Pill (24dp)")
                    roundness.forEach { (r, label) ->
                        val selected = themeConfig.cornerRoundness == r
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeConfigChange(themeConfig.copy(cornerRoundness = r, cornerRadiusDp = when(r) { "SHARP" -> 8f; "PILL" -> 24f; else -> 16f })) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // 5. Toggles: Animated Backdrop & Haptics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animated Caustic Backdrop", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Continuous fluid refraction light pulses", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = themeConfig.animatedBackdrop && !themeConfig.isPerformanceMode,
                        enabled = !themeConfig.isPerformanceMode,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(animatedBackdrop = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Gentle tactile clicks on transport & buttons", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = themeConfig.hapticsEnabled,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(hapticsEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Performance Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Disables animations and heavy shaders to maximize GPU battery efficiency", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = themeConfig.isPerformanceMode,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(isPerformanceMode = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reset Button
                OutlinedButton(
                    onClick = { onThemeConfigChange(GlassThemeConfig()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset UI & Glass to Defaults")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dedicated ColorOS & realme Battery Guide
        Text(
            text = "COLOROS & REALME BATTERY GUIDE",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Uninterrupted Playback on realme / ColorOS",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Prevent ColorOS from freezing Node Player during screen-off",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val steps = listOf(
                    "1. Battery Usage" to "Set Node Player battery usage to 'Don't optimize' (or 'Unrestricted' in Android 14).",
                    "2. Allow Background Activity" to "Enable 'Allow background activity' in App Info > Battery.",
                    "3. Auto-Launch & Secondary Launch" to "Enable 'Allow auto-launch' so Bluetooth and media buttons can start playback.",
                    "4. Lock in Recent Apps" to "Open Recent Apps, tap the three dots or swipe down on Node Player and select 'Lock'."
                )

                steps.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Settings > Battery manually", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White)
                    ) {
                        Text("Battery Settings", fontSize = 11.sp, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Settings > Apps manually", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                    ) {
                        Text("App Details", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Home Screen Widgets & Liquid Glass Section
        Text(
            text = "HOME SCREEN WIDGETS & LIQUID GLASS",
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
            var widgetSettings by remember { mutableStateOf(WidgetPreferences.getSettings(context)) }
            var isOverlayRunning by remember { mutableStateOf(FloatingOverlayService.isRunning) }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add Widget to Home Screen",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "One-tap pin to launcher with real AGSL liquid glass background",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pin buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                val provider = ComponentName(context, WidgetCard4x2Provider::class.java)
                                manager.requestPinAppWidget(provider, null, null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Card 4x2", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                val provider = ComponentName(context, WidgetLyrics4x3Provider::class.java)
                                manager.requestPinAppWidget(provider, null, null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lyrics 4x3", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                val provider = ComponentName(context, WidgetSlim4x1Provider::class.java)
                                manager.requestPinAppWidget(provider, null, null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Slim 4x1", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                val provider = ComponentName(context, WidgetSquare2x2Provider::class.java)
                                manager.requestPinAppWidget(provider, null, null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Square 2x2", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Floating Overlay Mini-Player
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Floating Mini-Player", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Draggable floating glass card over other apps.\nNote for realme GT 6T: Allow Background Activity in Battery settings if dismissed.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Switch(
                        checked = isOverlayRunning,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (AndroidSettings.canDrawOverlays(context)) {
                                    FloatingOverlayService.start(context)
                                    isOverlayRunning = true
                                } else {
                                    val overlayIntent = Intent(
                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(overlayIntent)
                                }
                            } else {
                                FloatingOverlayService.stop(context)
                                isOverlayRunning = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Glass Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Widget Glass Opacity", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${(widgetSettings.glassOpacity * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = widgetSettings.glassOpacity,
                    onValueChange = {
                        widgetSettings = widgetSettings.copy(glassOpacity = it)
                        WidgetPreferences.saveSettings(context, widgetSettings)
                        WidgetUpdateManager.updateAllWidgets(context)
                    },
                    valueRange = 0.40f..0.95f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Corner Radius Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Widget Corner Radius", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("${widgetSettings.cornerRadiusDp.toInt()} dp", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = widgetSettings.cornerRadiusDp,
                    onValueChange = {
                        widgetSettings = widgetSettings.copy(cornerRadiusDp = it)
                        WidgetPreferences.saveSettings(context, widgetSettings)
                        WidgetUpdateManager.updateAllWidgets(context)
                    },
                    valueRange = 16f..36f,
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

        Spacer(modifier = Modifier.height(20.dp))

        // 6. Last.fm Scrobbling Section
        Text(
            text = "LAST.FM SCROBBLING",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Last.fm Scrobbler Glass Card
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
                        Text("Enable Last.fm Scrobbling", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Scrobble qualifying tracks (>30s & 50%)", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = lastFmScrobbleEnabled,
                        onCheckedChange = {
                            lastFmScrobbleEnabled = it
                            sharedPrefs.edit().putBoolean("lastfm_scrobble_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Last.fm Debug Status (key yes/no, secret yes/no - show yes/no only, never the values)
                val isKeyConfigured = remember(customApiKey, lastFmConfigVersion) {
                    com.nodeplayer.music.library.LastFmClient.isKeyConfigured(context)
                }
                val isSecretConfigured = remember(customApiSecret, lastFmConfigVersion) {
                    com.nodeplayer.music.library.LastFmClient.isSecretConfigured(context)
                }
                val keyYesNo = if (isKeyConfigured) "yes" else "no"
                val secretYesNo = if (isSecretConfigured) "yes" else "no"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (keyYesNo == "yes" && secretYesNo == "yes") Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (keyYesNo == "yes" && secretYesNo == "yes") Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Last.fm configured: key $keyYesNo, secret $secretYesNo",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Clear message if secret is missing instead of failing
                if (secretYesNo == "no") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Last.fm shared secret is missing",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Add LASTFM_API_SECRET to local.properties or paste it in Advanced Setup below.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (lastFmSessionKey.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connected to Last.fm", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }
                            Text("User: @$lastFmUsername", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Button(
                            onClick = {
                                lastFmSessionKey = ""
                                lastFmUsername = ""
                                sharedPrefs.edit().putString("lastfm_session_key", "").putString("lastfm_username", "").apply()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))
                        ) {
                            Text("Disconnect")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Account Status", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                        Button(
                            onClick = {
                                if (!com.nodeplayer.music.library.LastFmClient.isSecretConfigured(context)) {
                                    showAdvancedLastFm = true
                                    android.widget.Toast.makeText(
                                        context,
                                        "Last.fm shared secret is missing. Add to local.properties or paste below.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    val tokenRes = com.nodeplayer.music.library.LastFmClient.getRequestToken(context)
                                    tokenRes.onSuccess { token ->
                                        lastFmAuthToken = token
                                        val authUrl = com.nodeplayer.music.library.LastFmClient.getAuthUrl(token, context)
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                        context.startActivity(browserIntent)
                                        showLastFmConfirmDialog = true
                                    }.onFailure { err ->
                                        android.widget.Toast.makeText(context, "${err.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text("Connect Last.fm")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Queue debug info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offline Scrobble Queue", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val flushed = com.nodeplayer.music.library.LastFmClient.flushPendingQueue(lastFmSessionKey)
                                android.widget.Toast.makeText(context, "Flushed $flushed scrobbles", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Force Flush", fontSize = 11.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Advanced manual paste fields (fallback for testing; built-in wins; never in git)
                TextButton(
                    onClick = { showAdvancedLastFm = !showAdvancedLastFm },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showAdvancedLastFm) "Hide Advanced Setup ▲" else "Advanced / Manual API Setup ▼",
                        style = MaterialTheme.typography.labelSmall.copy(color = accent, fontWeight = FontWeight.SemiBold)
                    )
                }

                if (showAdvancedLastFm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(12.dp)
                    ) {
                        val builtInKey = com.nodeplayer.music.library.LastFmClient.isBuiltInKeyPresent
                        val builtInSecret = com.nodeplayer.music.library.LastFmClient.isBuiltInSecretPresent

                        Text(
                            text = "Paste credentials directly into the app (stored privately in DataStore/prefs, never in git). Built-in values from local.properties take precedence.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = { customApiKey = it },
                            label = { Text(if (builtInKey) "API Key (using built-in)" else "API Key fallback") },
                            enabled = !builtInKey,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (builtInKey) {
                            Text(
                                text = "✓ Built-in key from local.properties is active (wins over pasted)",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontSize = 10.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customApiSecret,
                            onValueChange = { customApiSecret = it },
                            label = { Text(if (builtInSecret) "Shared Secret (using built-in)" else "Shared Secret fallback") },
                            enabled = !builtInSecret,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showSecretText) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecretText = !showSecretText }) {
                                    Icon(
                                        imageVector = if (showSecretText) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle secret visibility",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (builtInSecret) {
                            Text(
                                text = "✓ Built-in secret from local.properties is active (wins over pasted)",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontSize = 10.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    sharedPrefs.edit()
                                        .putString("custom_lastfm_api_key", customApiKey.trim())
                                        .putString("custom_lastfm_api_secret", customApiSecret.trim())
                                        .commit()
                                    lastFmConfigVersion++
                                    android.widget.Toast.makeText(context, "Credentials saved privately in app", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                            ) {
                                Text("Save Credentials")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7. Audio Quality & Analysis Section
        Text(
            text = "AUDIO QUALITY & ANALYSIS",
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
                // Master Switch: Audio Quality Analysis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio Quality & Analysis", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        Text("Run spectral analysis, DR rating and loudness normalization", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = audioAnalysisEnabled,
                        onCheckedChange = {
                            audioAnalysisEnabled = it
                            sharedPrefs.edit().putBoolean("audio_analysis_enabled", it).apply()
                            if (!it) {
                                com.nodeplayer.music.library.AudioAnalysisWorker.cancel(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (audioAnalysisEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Fake Hi-Res
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fake Hi-Res Detector", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("FFT background spectrum analysis for cutoffs & upsampling", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Switch(
                            checked = fakeHiRes,
                            onCheckedChange = {
                                fakeHiRes = it
                                sharedPrefs.edit().putBoolean("fake_hires_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Loudness Scan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EBU R128 Loudness Scan", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Integrated LUFS measurement & automatic gain matching", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Switch(
                            checked = loudnessScan,
                            onCheckedChange = {
                                loudnessScan = it
                                sharedPrefs.edit().putBoolean("loudness_scan_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Target Level & Clip guard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Loudness", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)))
                        Text("%.1f LUFS".format(targetLufs), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = targetLufs,
                        onValueChange = {
                            targetLufs = it
                            sharedPrefs.edit().putFloat("loudness_target_lufs", it).apply()
                        },
                        valueRange = -23.0f..-9.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Anti-Clipping Guard", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)))
                        Switch(
                            checked = clipGuard,
                            onCheckedChange = {
                                clipGuard = it
                                sharedPrefs.edit().putBoolean("loudness_clip_guard", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // DR Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic Range (DR) Meter", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Standard 3s block dynamic range rating", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Switch(
                            checked = drMeter,
                            onCheckedChange = {
                                drMeter = it
                                sharedPrefs.edit().putBoolean("dr_meter_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Speed Setting
                    Column {
                        Text("Analysis Speed & Throttle", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Controls inter-song delay and background CPU impact", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Slow", "Normal", "Fast").forEach { spd ->
                                val selected = analysisSpeed == spd
                                OutlinedButton(
                                    onClick = {
                                        analysisSpeed = spd
                                        sharedPrefs.edit().putString("analysis_speed", spd).apply()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) accent.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (selected) accent else Color.White.copy(alpha = 0.7f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(if (spd == "Slow") "Slow (Def)" else spd, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Only While Charging Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only While Charging", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Postpone background scan until device is plugged in", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                        }
                        Switch(
                            checked = analysisChargingOnly,
                            onCheckedChange = {
                                analysisChargingOnly = it
                                sharedPrefs.edit().putBoolean("analysis_charging_only", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Live Status & Progress Notification / Controls
                    if (isAnalysisRunning) {
                        val (cur, tot) = analysisProgress ?: (0 to 0)
                        val frac = if (tot > 0) cur.toFloat() / tot else 0f
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAnalysisPaused) "Analysis Paused" else "Analyzing Track $cur / $tot",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (isAnalysisPaused) Color(0xFFF59E0B) else accent)
                                )
                                Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                            }
                            if (!analysisSongTitle.isNullOrBlank()) {
                                Text(
                                    text = analysisSongTitle!!,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)),
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isAnalysisPaused) Color(0xFFF59E0B) else accent,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isAnalysisPaused) {
                                            com.nodeplayer.music.library.AudioAnalysisManager.resume(context)
                                        } else {
                                            com.nodeplayer.music.library.AudioAnalysisManager.pause(context)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f))
                                ) {
                                    Icon(
                                        imageVector = if (isAnalysisPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isAnalysisPaused) "Resume" else "Pause", color = accent, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        com.nodeplayer.music.library.AudioAnalysisWorker.cancel(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                com.nodeplayer.music.library.AudioAnalysisWorker.schedule(context, chargingOnly = analysisChargingOnly)
                                android.widget.Toast.makeText(context, "Background quality analysis started", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f))
                        ) {
                            Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = null, tint = accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze Unscanned Tracks Now", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Reset to default button
                    OutlinedButton(
                        onClick = {
                            fakeHiRes = true
                            loudnessScan = true
                            targetLufs = -14.0f
                            clipGuard = true
                            drMeter = true
                            analysisSpeed = "Slow"
                            analysisChargingOnly = false
                            sharedPrefs.edit()
                                .putBoolean("fake_hires_enabled", true)
                                .putBoolean("loudness_scan_enabled", true)
                                .putFloat("loudness_target_lufs", -14.0f)
                                .putBoolean("loudness_clip_guard", true)
                                .putBoolean("dr_meter_enabled", true)
                                .putString("analysis_speed", "Slow")
                                .putBoolean("analysis_charging_only", false)
                                .apply()
                            android.widget.Toast.makeText(context, "Audio analysis settings reset to defaults", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Audio Analysis to Defaults", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 8. Playback & Sleep Timer Section
        Text(
            text = "PLAYBACK & SLEEP TIMER",
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
                // Genre-Based Crossfade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Genre-Adaptive Crossfade", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Contextual curves & lengths (ambient longer, live zero)", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = genreCrossfade,
                        onCheckedChange = {
                            genreCrossfade = it
                            sharedPrefs.edit().putBoolean("genre_crossfade_enabled", it).apply()
                            com.nodeplayer.music.service.PlaybackService.instance?.crossfadeManager?.genreCrossfadeEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Sleep finish track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sleep Timer: Finish Current Track", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                    Switch(
                        checked = sleepFinishTrack,
                        onCheckedChange = {
                            sleepFinishTrack = it
                            sharedPrefs.edit().putBoolean("sleep_timer_finish_track", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                // Sleep fade out
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fade-Out Length", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)))
                    Text("${sleepFadeSec}s", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = sleepFadeSec.toFloat(),
                    onValueChange = {
                        sleepFadeSec = it.toInt()
                        sharedPrefs.edit().putInt("sleep_timer_fade_seconds", sleepFadeSec).apply()
                    },
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                // Sleep Shake to extend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shake Phone to Extend", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                        Text("Adds +5 minutes when shake is detected", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = sleepShake,
                        onCheckedChange = {
                            sleepShake = it
                            sharedPrefs.edit().putBoolean("sleep_timer_shake_extend", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 9. UI & Gestures
        Text(
            text = "INTERFACE & GESTURES",
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
                // Quality Badges Mode
                Text("Quality Badges Display", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OFF", "COMPACT", "DETAILED").forEach { mode ->
                        FilterChip(
                            selected = badgeMode == mode,
                            onClick = {
                                badgeMode = mode
                                sharedPrefs.edit().putString("quality_badges_mode", mode).apply()
                            },
                            label = { Text(mode) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Mini Player Gestures
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mini Player Gestures", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Swipe left/right next/prev, swipe up open, double-tap play", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Switch(
                        checked = miniGestures,
                        onCheckedChange = {
                            miniGestures = it
                            sharedPrefs.edit().putBoolean("mini_player_gestures_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reset to Defaults Button
        Button(
            onClick = {
                coroutineScope.launch {
                    com.nodeplayer.music.data.AppSettingsRepository.resetToDefaults(context)
                    sharedPrefs.edit().clear().apply()
                    android.widget.Toast.makeText(context, "All settings reset to defaults", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.25f))
        ) {
            Icon(imageVector = Icons.Rounded.Restore, contentDescription = null, tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Settings to Defaults", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Diagnostics & Local Crash Logger Card
        Text(
            text = "DIAGNOSTICS & LOCAL CRASH LOGS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        var showCrashLogDialog by remember { mutableStateOf(false) }
        val hasCrash = remember { com.nodeplayer.music.diagnostics.LocalCrashLogger.hasCrashLog(context) }

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
                        Text(
                            text = "Local Crash Recorder",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = if (hasCrash) "Recorded crash detected on device" else "Zero crashes recorded (System healthy)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (hasCrash) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasCrash) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (hasCrash) "Log Present" else "Healthy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (hasCrash) Color(0xFFF59E0B) else Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Strictly private. Logs are kept on your phone only and never transmitted anywhere.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showCrashLogDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Log", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val log = com.nodeplayer.music.diagnostics.LocalCrashLogger.getCrashLogs(context)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Node Player Crash Log")
                                putExtra(Intent.EXTRA_TEXT, log)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Crash Log"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Log", fontSize = 11.sp)
                    }
                }
            }
        }

        if (showCrashLogDialog) {
            val logText = remember { com.nodeplayer.music.diagnostics.LocalCrashLogger.getCrashLogs(context) }
            AlertDialog(
                onDismissRequest = { showCrashLogDialog = false },
                title = { Text("Local Crash Log") },
                text = {
                    Column {
                        Text(
                            text = "Stored privately in app directory without external network transmission.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = logText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(logText))
                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }
                },
                dismissButton = {
                    Row {
                        if (hasCrash) {
                            TextButton(onClick = {
                                com.nodeplayer.music.diagnostics.LocalCrashLogger.clearCrashLogs(context)
                                Toast.makeText(context, "Crash log cleared", Toast.LENGTH_SHORT).show()
                                showCrashLogDialog = false
                            }) {
                                Text("Clear Log", color = Color(0xFFEF4444))
                            }
                        }
                        TextButton(onClick = { showCrashLogDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 10. Backup, Restore & M3U Section
        Text(
            text = "BACKUP, RESTORE & PLAYLISTS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        var showImportM3uDialog by remember { mutableStateOf(false) }
        var m3uPlaylistName by remember { mutableStateOf("Imported Playlist") }
        var m3uText by remember { mutableStateOf("") }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Library & Settings Backup",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "Export playlists, play counts, favorites, EQ and themes into a single .nodebackup file.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                val db = com.nodeplayer.music.NodePlayerApplication.instance.database
                                val file = com.nodeplayer.music.data.NodeBackupManager.exportNodeBackup(
                                    context = context,
                                    database = db,
                                    effectConfig = com.nodeplayer.model.AudioEffectConfig(),
                                    themeConfig = themeConfig,
                                    allTracks = emptyList()
                                )
                                if (file != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Export Node Backup"))
                                } else {
                                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = { showImportM3uDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import M3U", fontSize = 11.sp)
                    }
                }
            }
        }

        if (showImportM3uDialog) {
            AlertDialog(
                onDismissRequest = { showImportM3uDialog = false },
                title = { Text("Import M3U Playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = m3uPlaylistName,
                            onValueChange = { m3uPlaylistName = it },
                            label = { Text("Playlist Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = m3uText,
                            onValueChange = { m3uText = it },
                            label = { Text("M3U Content / Paths") },
                            placeholder = { Text("#EXTM3U\n/storage/emulated/0/Music/song1.mp3\n...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val db = com.nodeplayer.music.NodePlayerApplication.instance.database
                        coroutineScope.launch {
                            val result = com.nodeplayer.music.data.NodeBackupManager.importM3uPlaylist(
                                m3uContent = m3uText,
                                playlistName = m3uPlaylistName,
                                database = db,
                                allTracks = emptyList()
                            )
                            Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                        }
                        showImportM3uDialog = false
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportM3uDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7. About Section
        Text(
            text = "ABOUT",
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
                    Column {
                        Text(
                            text = "Node Player",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Version 0.1.0",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "v0.1.0",
                            color = accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Source Code Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Krighz/node-player"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Source Code on GitHub",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Open Link",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // License
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "License",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Text(
                        text = "Apache-2.0",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = accent)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Open Source Licenses Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLicensesDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Open Source Licenses",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "View",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(200.dp))
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                Text(
                    text = "Open Source Licenses",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val licenses = listOf(
                        "AndroidX Media3 (ExoPlayer)" to "Apache-2.0 (Google LLC)",
                        "Jetpack Compose & Material 3" to "Apache-2.0 (Google LLC)",
                        "Haze Glassmorphism" to "Apache-2.0 (Chris Banes)",
                        "Coil Image Loading" to "Apache-2.0 (Colin White)",
                        "AndroidX Room Database" to "Apache-2.0 (Google LLC)",
                        "AndroidX Palette" to "Apache-2.0 (Google LLC)",
                        "Kotlin & Kotlinx Coroutines" to "Apache-2.0 (JetBrains s.r.o.)"
                    )
                    licenses.forEach { (lib, lic) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(lib, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text(lic, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF16181F),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showLastFmConfirmDialog && lastFmAuthToken != null) {
        AlertDialog(
            onDismissRequest = { showLastFmConfirmDialog = false },
            title = { Text("Complete Last.fm Authorization", color = Color.White) },
            text = { Text("Have you authorized Node Player in your web browser?", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLastFmConfirmDialog = false
                        coroutineScope.launch {
                            val sessionRes = com.nodeplayer.music.library.LastFmClient.getSession(lastFmAuthToken!!, context)
                            sessionRes.onSuccess { (key, user) ->
                                lastFmSessionKey = key
                                lastFmUsername = user
                                sharedPrefs.edit()
                                    .putString("lastfm_session_key", key)
                                    .putString("lastfm_username", user)
                                    .putBoolean("lastfm_scrobble_enabled", true)
                                    .apply()
                                lastFmScrobbleEnabled = true
                                android.widget.Toast.makeText(context, "Logged in as @$user", android.widget.Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                android.widget.Toast.makeText(context, "Failed to get session: ${err.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Yes, Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLastFmConfirmDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF16181F)
        )
    }
}
