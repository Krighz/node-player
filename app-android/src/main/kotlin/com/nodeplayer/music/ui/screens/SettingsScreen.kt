package com.nodeplayer.music.ui.screens

import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.nodeplayer.music.ui.components.glassRefraction
import com.nodeplayer.ui.components.LiquidGlassSlider
import com.nodeplayer.ui.components.LiquidGlassSwitch
import com.nodeplayer.music.ui.theme.glassSurface
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
import com.nodeplayer.music.update.UpdatePreferences
import com.nodeplayer.music.update.UpdateManager
import com.nodeplayer.music.update.UpdateWorker
import com.nodeplayer.music.update.UpdateCheckResult
import com.nodeplayer.music.feedback.FeedbackHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
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
    onApplySafeGraphicsMode: () -> Unit = {},
    onReportProblem: () -> Unit = {},
    onNavigateToOpticalDiagnostics: () -> Unit = {},
    onPairNewDevice: () -> Unit = {},
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sharedPrefs = remember { context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE) }
    var nodeConnectOn by remember { mutableStateOf(sharedPrefs.getBoolean("node_connect_enabled", true)) }
    var gaplessEnabled by remember { mutableStateOf(true) }
    var crossfadeSeconds by remember { mutableFloatStateOf(2.0f) }
    var lrclibOptIn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var fakeHiRes by remember { mutableStateOf(sharedPrefs.getBoolean("fake_hires_enabled", true)) }
    var loudnessScan by remember { mutableStateOf(sharedPrefs.getBoolean("loudness_scan_enabled", true)) }
    var targetLufs by remember { mutableFloatStateOf(sharedPrefs.getFloat("loudness_target_lufs", -14.0f)) }
    var clipGuard by remember { mutableStateOf(sharedPrefs.getBoolean("loudness_clip_guard", true)) }
    var drMeter by remember { mutableStateOf(sharedPrefs.getBoolean("dr_meter_enabled", true)) }
    var genreCrossfade by remember { mutableStateOf(sharedPrefs.getBoolean("genre_crossfade_enabled", true)) }
    var sleepFinishTrack by remember { mutableStateOf(sharedPrefs.getBoolean("sleep_timer_finish_track", true)) }
    var sleepFadeSec by remember { mutableIntStateOf(sharedPrefs.getInt("sleep_timer_fade_seconds", 10)) }
    var sleepShake by remember { mutableStateOf(sharedPrefs.getBoolean("sleep_timer_shake_extend", true)) }
    var badgeMode by remember { mutableStateOf(sharedPrefs.getString("quality_badges_mode", "DETAILED") ?: "DETAILED") }
    var miniGestures by remember { mutableStateOf(sharedPrefs.getBoolean("mini_player_gestures_enabled", true)) }
    var lastFmSessionKey by remember { mutableStateOf(sharedPrefs.getString("lastfm_session_key", "") ?: "") }
    var lastFmUsername by remember { mutableStateOf(sharedPrefs.getString("lastfm_username", "") ?: "") }
    var lastFmScrobbleEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("lastfm_scrobble_enabled", false)) }
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
    var activeSection by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = activeSection != null) {
        activeSection = if (activeSection == "motion_playground") "customize_ui" else null
    }
    var showTopMenu by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(sharedPrefs.getBoolean("show_advanced_settings", false)) }

    var updateCheckAuto by remember { mutableStateOf(UpdatePreferences.isCheckAutomatically(context)) }
    var updateWifiOnly by remember { mutableStateOf(UpdatePreferences.isWifiOnly(context)) }
    var updateLastChecked by remember { mutableLongStateOf(UpdatePreferences.getLastCheckedTimestamp(context)) }
    var skippedVersions by remember { mutableStateOf(UpdatePreferences.getSkippedVersions(context)) }
    val isCheckingUpdates by UpdateManager.isChecking.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: it.toString()
            onAddCustomFolder(path)
        }
    }

    if (activeSection == "motion_playground") {
        AnimationPlaygroundScreen(
            themeConfig = themeConfig,
            onThemeConfigChange = onThemeConfigChange,
            onBack = { activeSection = "customize_ui" },
            
            modifier = modifier
        )
        return
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
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (showAdvancedSettings) "Simplify Settings (Hide Advanced)" else "Show Advanced Settings",
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            showTopMenu = false
                            showAdvancedSettings = !showAdvancedSettings
                            sharedPrefs.edit().putBoolean("show_advanced_settings", showAdvancedSettings).apply()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
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

                data class SettingsHubItem(
            val id: String,
            val title: String,
            val description: String,
            val icon: androidx.compose.ui.graphics.vector.ImageVector
        )

        val hubSections = remember {
            listOf(
                SettingsHubItem("node_connect", "Node Connect", "Audio sharing without files, mTLS pairing, sync", Icons.Rounded.CastConnected),
                SettingsHubItem("sound_eq", "Sound & EQ", "Audio output, 32-bit float, resampler, DSP", Icons.Rounded.Tune),
                SettingsHubItem("playback", "Playback & Gestures", "Gapless, crossfade, headset, gestures, timer", Icons.Rounded.PlayCircle),
                SettingsHubItem("library", "Library & Folders", "Storage paths, duration filter, exclusions", Icons.Rounded.Folder),
                SettingsHubItem("customize_ui", "Customize UI", "Lens dock, themes, bundled fonts, spacing", Icons.Rounded.Palette),
                SettingsHubItem("widgets", "Widgets & Lock Screen", "4 static styles, shimmer, element toggles", Icons.Rounded.Widgets),
                SettingsHubItem("lyrics", "Lyrics & Metadata", "Synced LRC, LRCLIB integration, karaoke", Icons.Rounded.Lyrics),
                SettingsHubItem("lastfm", "Last.fm & Network", "Scrobbling, now playing, custom API keys", Icons.Rounded.CloudQueue),
                SettingsHubItem("stats", "Stats & Audio Analysis", "Audio quality analysis, genuine Hi-Res, DR", Icons.Rounded.BarChart),
                SettingsHubItem("updates", "Updates", "GitHub releases, auto-check, download manager", Icons.Rounded.SystemUpdate),
                SettingsHubItem("backup", "Backup & Data", "Export settings, restore backup, reset data", Icons.Rounded.Save),
                SettingsHubItem("diagnostics", "Diagnostics & Graphics", "Frame metrics, hardware info, shader logs", Icons.Rounded.BugReport),
                SettingsHubItem("about", "Support & About", "Node Player v${com.nodeplayer.music.BuildConfig.VERSION_NAME}, licenses, GitHub", Icons.Rounded.Info)
            )
        }

        val isHubMode = activeSection == null && searchQuery.isBlank()

        val shouldShow: (String) -> Boolean = { section ->
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                val item = hubSections.find { it.id == section }
                (item != null && (item.title.lowercase().contains(q) || item.description.lowercase().contains(q))) ||
                    (section == "sound_eq" && (q.contains("sound") || q.contains("audio") || q.contains("eq") || q.contains("dac") || q.contains("usb") || q.contains("resampl") || q.contains("float"))) ||
                    (section == "playback" && (q.contains("play") || q.contains("crossfade") || q.contains("gapless") || q.contains("headset") || q.contains("gesture") || q.contains("sleep") || q.contains("timer"))) ||
                    (section == "library" && (q.contains("lib") || q.contains("folder") || q.contains("scan") || q.contains("exclud") || q.contains("durat"))) ||
                    (section == "customize_ui" && (q.contains("ui") || q.contains("theme") || q.contains("glass") || q.contains("dock") || q.contains("font") || q.contains("color") || q.contains("round"))) ||
                    (section == "widgets" && (q.contains("widg") || q.contains("lock") || q.contains("overlay"))) ||
                    (section == "lyrics" && (q.contains("lyric") || q.contains("lrc") || q.contains("lrclib"))) ||
                    (section == "lastfm" && (q.contains("last") || q.contains("scrobbl") || q.contains("api"))) ||
                    (section == "stats" && (q.contains("stat") || q.contains("analys") || q.contains("hires") || q.contains("dr") || q.contains("loud"))) ||
                    (section == "updates" && (q.contains("updat") || q.contains("vers") || q.contains("git") || q.contains("check"))) ||
                    (section == "backup" && (q.contains("back") || q.contains("restor") || q.contains("export") || q.contains("import"))) ||
                    (section == "diagnostics" && (q.contains("diag") || q.contains("graph") || q.contains("frame") || q.contains("fps") || q.contains("crash") || q.contains("shader") || q.contains("log"))) ||
                    (section == "about" && (q.contains("about") || q.contains("licen") || q.contains("node") || q.contains("feedb") || q.contains("bug")))
            } else {
                activeSection == section
            }
        }

        fun resetCurrentSection(sec: String?) {
            when (sec) {
                "sound_eq" -> {
                    onCrossfadeConfigChange(CrossfadeConfig())
                    onResamplerRateChange(0)
                    onToggleSystemEq(false)
                }
                "playback" -> {
                    gaplessEnabled = true
                    miniGestures = true
                    onToggleResumeOnHeadsetConnect(true)
                    sharedPrefs.edit()
                        .putBoolean("gapless_enabled", true)
                        .putBoolean("mini_player_gestures_enabled", true)
                        .apply()
                }
                "library" -> {
                    onMinDurationChange(30)
                    onRescanLibrary()
                }
                "customize_ui" -> {
                    onThemeConfigChange(GlassThemeConfig())
                }
                "widgets" -> {
                    WidgetPreferences.saveSettings(context, com.nodeplayer.model.WidgetSettings())
                    WidgetUpdateManager.updateAllWidgets(context)
                }
                "lyrics" -> {
                    lrclibOptIn = false
                    sharedPrefs.edit().putBoolean("lrclib_opt_in", false).apply()
                }
                "lastfm" -> {
                    lastFmScrobbleEnabled = false
                    sharedPrefs.edit().putBoolean("lastfm_scrobble_enabled", false).apply()
                }
                "stats" -> {
                    fakeHiRes = true
                    drMeter = true
                    loudnessScan = true
                    sharedPrefs.edit()
                        .putBoolean("fake_hires_enabled", true)
                        .putBoolean("dr_meter_enabled", true)
                        .putBoolean("loudness_scan_enabled", true)
                        .apply()
                }
                "updates" -> {
                    UpdatePreferences.setCheckAutomatically(context, true)
                    UpdatePreferences.setWifiOnly(context, true)
                    updateCheckAuto = true
                    updateWifiOnly = true
                }
            }
            Toast.makeText(context, "Section reset to defaults", Toast.LENGTH_SHORT).show()
        }

        if (isHubMode) {
            // Quick Mode Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidChip(
                    selected = themeConfig.isPerformanceMode,
                    onClick = {
                        onThemeConfigChange(themeConfig.copy(isPerformanceMode = !themeConfig.isPerformanceMode))
                    },
                    label = { Text("Performance: ${if (themeConfig.isPerformanceMode) "ON" else "OFF"}") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                LiquidChip(
                    selected = themeConfig.shaderMode != "OFF",
                    onClick = {
                        val nextMode = when (themeConfig.shaderMode) {
                            "AUTO" -> "FULL"
                            "FULL" -> "SIMPLE"
                            "SIMPLE" -> "BLUR_ONLY"
                            "BLUR_ONLY" -> "OFF"
                            else -> "AUTO"
                        }
                        onThemeConfigChange(themeConfig.copy(shaderMode = nextMode))
                    },
                    label = { Text("Shader: ${themeConfig.shaderMode}") },
                    leadingIcon = {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                LiquidChip(
                    selected = true,
                    onClick = {
                        val nextTheme = when (themeConfig.themeMode) {
                            "SYSTEM" -> "DARK"
                            "DARK" -> "LIGHT"
                            "LIGHT" -> "OLED"
                            else -> "SYSTEM"
                        }
                        onThemeConfigChange(themeConfig.copy(themeMode = nextTheme, isOledBlack = nextTheme == "OLED"))
                    },
                    label = { Text("Theme: ${themeConfig.themeMode}") },
                    leadingIcon = {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SETTINGS HUB",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 2-Column Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsHubCard("Node Connect", "Audio sharing & mTLS pairing", Icons.Rounded.CastConnected) { activeSection = "node_connect" }
                    SettingsHubCard("Sound & EQ", "Audio output, 32-bit float, resampler, DSP", Icons.Rounded.Tune) { activeSection = "sound_eq" }
                    SettingsHubCard("Playback & Gestures", "Gapless, crossfade, headset, gestures, timer", Icons.Rounded.PlayCircle) { activeSection = "playback" }
                    SettingsHubCard("Library & Folders", "Storage paths, duration filter, exclusions", Icons.Rounded.Folder) { activeSection = "library" }
                    SettingsHubCard("Customize UI", "Lens dock, themes, bundled fonts, spacing", Icons.Rounded.Palette) { activeSection = "customize_ui" }
                    SettingsHubCard("Widgets & Lock Screen", "4 static styles, shimmer, element toggles", Icons.Rounded.Widgets) { activeSection = "widgets" }
                    SettingsHubCard("Lyrics & Metadata", "Synced LRC, LRCLIB integration, karaoke", Icons.Rounded.Lyrics) { activeSection = "lyrics" }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsHubCard("Last.fm & Network", "Scrobbling, now playing, custom API keys", Icons.Rounded.CloudQueue) { activeSection = "lastfm" }
                    SettingsHubCard("Stats & Audio Analysis", "Audio quality analysis, genuine Hi-Res, DR", Icons.Rounded.BarChart) { activeSection = "stats" }
                    SettingsHubCard("Updates", "GitHub releases, auto-check, download manager", Icons.Rounded.SystemUpdate) { activeSection = "updates" }
                    SettingsHubCard("Backup & Data", "Export settings, restore backup, reset data", Icons.Rounded.Save) { activeSection = "backup" }
                    SettingsHubCard("Diagnostics & Graphics", "Frame metrics, hardware info, shader logs", Icons.Rounded.BugReport) { activeSection = "diagnostics" }
                    SettingsHubCard("Support & About", "Node Player v${com.nodeplayer.music.BuildConfig.VERSION_NAME}, licenses, GitHub", Icons.Rounded.Info) { activeSection = "about" }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // Subpage Navigation Header
            if (activeSection != null && searchQuery.isBlank()) {
                val currentTitle = when (activeSection) {
                    "node_connect" -> "Node Connect"
                    "sound_eq" -> "Sound & EQ"
                    "playback" -> "Playback & Gestures"
                    "library" -> "Library & Folders"
                    "customize_ui" -> "Customize UI"
                    "widgets" -> "Widgets & Lock Screen"
                    "lyrics" -> "Lyrics & Metadata"
                    "lastfm" -> "Last.fm & Network"
                    "stats" -> "Stats & Audio Analysis"
                    "updates" -> "Updates"
                    "backup" -> "Backup & Data"
                    "diagnostics" -> "Diagnostics & Graphics"
                    "about" -> "Support & About"
                    else -> "Settings"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { activeSection = null }
                    ) {
                        IconButton(
                            onClick = { activeSection = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    TextButton(onClick = { resetCurrentSection(activeSection) }) {
                        Text("Reset", color = accent, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }



        if (shouldShow("sound_eq")) {
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
                    LiquidToggle(
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
                    LiquidToggle(
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
                    LiquidToggle(
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

                }

        if (shouldShow("playback")) {
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
                    LiquidToggle(
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
                    LiquidSlider(
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
                            LiquidChip(
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
                        LiquidToggle(
                            checked = crossfadeConfig.autoGapless,
                            onCheckedChange = { onCrossfadeConfigChange(crossfadeConfig.copy(autoGapless = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

                }

        if (shouldShow("sound_eq")) {
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
                    LiquidToggle(
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

                }

        if (shouldShow("library")) {
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
                LiquidSlider(
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
                                    LiquidToggle(
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

                }

        if (shouldShow("playback")) {
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
                    LiquidToggle(
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
                LiquidSlider(
                    value = crossfadeSeconds,
                    onValueChange = { crossfadeSeconds = it },
                    valueRange = 0.0f..10.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

                }

        if (shouldShow("customize_ui")) {
            CustomizeUiScreen(
                themeConfig = themeConfig,
                onThemeConfigChange = onThemeConfigChange,
                onApplySafeGraphicsMode = onApplySafeGraphicsMode,
                onReportProblem = onReportProblem,
                onOpenMotionPlayground = { activeSection = "motion_playground" },
                
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (shouldShow("playback")) {
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

                }

        if (shouldShow("widgets")) {
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
                    LiquidToggle(
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
                LiquidGlassSlider(
                    value = widgetSettings.glassOpacity,
                    onValueChange = {
                        widgetSettings = widgetSettings.copy(glassOpacity = it)
                        WidgetPreferences.saveSettings(context, widgetSettings)
                        WidgetUpdateManager.updateAllWidgets(context)
                    },
                    valueRange = 0.40f..0.95f
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
                LiquidSlider(
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

                }

        if (shouldShow("lyrics")) {
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
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Sync & Lyrics (Deferred)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            "Online sync is deferred. Node Player makes 0 internet requests and operates in 100% offline local mode.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    LiquidToggle(
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

                }

        if (shouldShow("lastfm")) {
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
                    LiquidToggle(
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

                }

        if (shouldShow("stats")) {
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
                    LiquidToggle(
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
                        LiquidToggle(
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
                        LiquidToggle(
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
                    LiquidSlider(
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
                        LiquidToggle(
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
                        LiquidToggle(
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
                        LiquidToggle(
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
                    LiquidToggle(
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
                    LiquidToggle(
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
                LiquidSlider(
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
                    LiquidToggle(
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
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Quality Badges Mode
                Text("Quality Badges Display", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OFF", "COMPACT", "DETAILED").forEach { mode ->
                        LiquidChip(
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
                    LiquidToggle(
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

                }

        if (shouldShow("diagnostics")) {
        // Liquid Glass Optical Diagnostics Card
        Text(
            text = "LIQUID GLASS OPTICAL TEST",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liquid Glass Optical Test",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Triple-zone optical pattern, blur, refraction and puck fringing verification",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    FilledTonalButton(
                        onClick = onNavigateToOpticalDiagnostics,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.25f), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

                }

        if (shouldShow("backup")) {
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

                }

        if (shouldShow("updates")) {
        // Updates Section
        Text(
            text = "UPDATES",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Check automatically switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Check for updates automatically",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Periodically queries GitHub Releases in the background",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    LiquidToggle(
                        checked = updateCheckAuto,
                        onCheckedChange = { checked ->
                            updateCheckAuto = checked
                            UpdatePreferences.setCheckAutomatically(context, checked)
                            UpdateWorker.scheduleOrCancel(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Wi-Fi only switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Wi-Fi only",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Restricts background checks and downloads to unmetered Wi-Fi",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    LiquidToggle(
                        checked = updateWifiOnly,
                        onCheckedChange = { checked ->
                            updateWifiOnly = checked
                            UpdatePreferences.setWifiOnly(context, checked)
                            UpdateWorker.scheduleOrCancel(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Last checked and Check Now button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Last checked",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        val lastCheckedStr = if (updateLastChecked > 0L) {
                            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(updateLastChecked))
                        } else {
                            "Never"
                        }
                        Text(
                            text = lastCheckedStr,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }

                    Button(
                        onClick = {
                            UpdateManager.triggerCheck(context, isManual = true) { result ->
                                updateLastChecked = UpdatePreferences.getLastCheckedTimestamp(context)
                                val showSafeToast: (String, Int) -> Unit = { msg, len ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, msg, len).show()
                                    }
                                }
                                when (result) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        showSafeToast("Update available: ${result.info.versionName}", Toast.LENGTH_SHORT)
                                    }
                                    is UpdateCheckResult.UpToDate -> {
                                        showSafeToast("Node Player is up to date (${com.nodeplayer.music.update.UpdateChecker.getInstalledVersionName(context)})", Toast.LENGTH_SHORT)
                                    }
                                    is UpdateCheckResult.RateLimited -> {
                                        showSafeToast(result.message, Toast.LENGTH_LONG)
                                    }
                                    is UpdateCheckResult.NetworkError -> {
                                        showSafeToast("Network error: ${result.message}", Toast.LENGTH_SHORT)
                                    }
                                    is UpdateCheckResult.Error -> {
                                        showSafeToast("Check error: ${result.message}", Toast.LENGTH_SHORT)
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdates,
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Text("Check now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Skipped versions
                if (skippedVersions.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))
                    Text(
                        text = "Skipped Versions",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    skippedVersions.forEach { version ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("v$version", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                            TextButton(
                                onClick = {
                                    UpdateManager.unskipVersion(context, version)
                                    skippedVersions = UpdatePreferences.getSkippedVersions(context)
                                }
                            ) {
                                Text("Unskip", color = accent, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Privacy Disclosure
                Text(
                    text = "Update checks connect directly to GitHub Releases API with User-Agent NodePlayer/<version>. This reveals your IP address and app version to GitHub.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.45f),
                        lineHeight = 14.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

                }

        if (shouldShow("node_connect")) {
            Text(
                text = "NODE CONNECT & LOCAL SYNC",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Node Connect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "mTLS zero-cloud audio streaming, remote control & sync across PC and phone.",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                        LiquidToggle(
                            checked = nodeConnectOn,
                            onCheckedChange = { isChecked ->
                                nodeConnectOn = isChecked
                                sharedPrefs.edit().putBoolean("node_connect_enabled", isChecked).apply()
                                com.nodeplayer.music.service.PlaybackService.instance?.setNodeConnectEnabled(isChecked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent)
                        )
                    }

                    if (nodeConnectOn) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val hub = com.nodeplayer.music.service.PlaybackService.instance?.nodeConnectHub
                        val connectedCount by (hub?.connectedDeviceCount ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
                        val pairedList by (hub?.pairedDevices ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (connectedCount > 0) Color(0xFF00F0FF) else Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (connectedCount > 0) "$connectedCount device(s) connected" else "Listening on local network (mTLS)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Port: 54820 · Device: ${android.os.Build.MODEL}",
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onPairNewDevice,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pair New Device (PIN / QR)", fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NODE CONNECT FEATURE SWITCHES",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val ncSettingsStore = remember {
                            val prefs = context.getSharedPreferences("node_connect_settings", android.content.Context.MODE_PRIVATE)
                            com.nodeplayer.network.service.NodeConnectSettingsStore(com.nodeplayer.music.data.AndroidKeyValueStore(prefs))
                        }
                        var ncConfig by remember { mutableStateOf(ncSettingsStore.config.value) }

                        // 1. Audio Cast
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Audio Cast (Direct Streaming)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("Stream uncompressed live PCM audio directly to connected nodes", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(
                                checked = ncConfig.audioCastEnabled,
                                onCheckedChange = { isChecked ->
                                    val updated = ncConfig.copy(audioCastEnabled = isChecked)
                                    ncConfig = updated
                                    ncSettingsStore.saveConfig(updated)
                                    hub?.config = updated
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent)
                            )
                        }

                        // Mute Local Device While Casting
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Mute Local Device While Casting", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("Silence local phone speakers and output sound only on receiving PC", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(
                                checked = ncConfig.muteLocalOutputDuringCast,
                                onCheckedChange = { isChecked ->
                                    val updated = ncConfig.copy(muteLocalOutputDuringCast = isChecked)
                                    ncConfig = updated
                                    ncSettingsStore.saveConfig(updated)
                                    hub?.config = updated
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent)
                            )
                        }

                        // 2. Remote Control
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Remote Control", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("Allow play, pause, and skip commands across connected devices", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(
                                checked = ncConfig.remoteControlEnabled,
                                onCheckedChange = { isChecked ->
                                    val updated = ncConfig.copy(remoteControlEnabled = isChecked)
                                    ncConfig = updated
                                    ncSettingsStore.saveConfig(updated)
                                    hub?.config = updated
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent)
                            )
                        }

                        // 3. Library Sharing
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Library Sharing & Search", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("Permit cross-device song searching (zero file path exposure)", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(
                                checked = ncConfig.shareLibraryEnabled,
                                onCheckedChange = { isChecked ->
                                    val updated = ncConfig.copy(shareLibraryEnabled = isChecked)
                                    ncConfig = updated
                                    ncSettingsStore.saveConfig(updated)
                                    hub?.config = updated
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent)
                            )
                        }

                        // 4. Playback Handoff
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Playback Handoff", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("Seamlessly shift playback queue and position between devices", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(
                                checked = ncConfig.handoffEnabled,
                                onCheckedChange = { isChecked ->
                                    val updated = ncConfig.copy(handoffEnabled = isChecked)
                                    ncConfig = updated
                                    ncSettingsStore.saveConfig(updated)
                                    hub?.config = updated
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent)
                            )
                        }

                        if (pairedList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "PAIRED TRUSTED DEVICES (${pairedList.size})",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            pairedList.forEach { paired ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (paired.platform == "Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(paired.deviceName, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text("Pinned SHA-256 mTLS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
                                        }
                                    }
                                    TextButton(
                                        onClick = { hub?.revokeDevice(paired.deviceId) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Revoke", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (shouldShow("about")) {
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
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onReportProblem() })
                        }
                    ) {
                        Text(
                            text = "Node Player",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Version ${com.nodeplayer.music.BuildConfig.VERSION_NAME} (Hold for diagnostics)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onReportProblem() })
                        }
                    ) {
                        Text(
                            text = "v${com.nodeplayer.music.BuildConfig.VERSION_NAME}",
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
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open Link",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Send Feedback (GitHub Discussions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { FeedbackHelper.openFeedback(context) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Send Feedback",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                            Text(
                                text = "Join conversations on GitHub Discussions",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open Discussions",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Report a Bug (GitHub Issues with device info pre-filled)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { FeedbackHelper.openBugReport(context) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Report a Bug",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                            Text(
                                text = "Open issue with pre-filled device & OS details",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Report Issue",
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

                // Audio Engine Architecture Specs
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "Audio Architecture Specs",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 32-bit Float low-latency audio processing pipeline\n• Freeverb 4-comb 2-allpass diffusion reverb\n• Truthful real-time bit-perfect / resampled sink inspector\n• 10-band biquad parametric & graphic equalizer\n• Dynamic Room caching & off-main-thread parser",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f), lineHeight = 18.sp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Replay Welcome Tour
                var showTourDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTourDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Replay Welcome Tour",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Replay",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (showTourDialog) {
                    com.nodeplayer.music.ui.components.FirstRunOnboardingDialog(
                        accentColor = accent,
                        onRequestPermissions = { },
                        onStartScan = { },
                        onDismiss = { showTourDialog = false }
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

        Spacer(modifier = Modifier.height(14.dp))

        com.nodeplayer.music.ui.components.SupportCard()

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


@Composable
private fun SettingsHubCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
