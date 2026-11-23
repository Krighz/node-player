package com.nodeplayer.music.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioOutputInfo
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.OutputDeviceType
import com.nodeplayer.music.diagnostics.ShaderSelfTestResult
import com.nodeplayer.music.service.SystemWideEqService
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidIconButton
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
import kotlin.math.roundToInt

@Composable
fun HardwareSettingsScreen(
    outputInfo: AudioOutputInfo,
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    systemEqEnabled: Boolean,
    systemEqSessions: List<SystemWideEqService.SessionInfo>,
    onToggleSystemEq: (Boolean) -> Unit,
    currentDeviceType: OutputDeviceType,
    autoDeviceProfilesEnabled: Boolean,
    onToggleAutoDeviceProfiles: (Boolean) -> Unit,
    resumeOnHeadsetConnect: Boolean,
    onToggleResumeOnHeadsetConnect: (Boolean) -> Unit,
    resamplerRateChoice: Int,
    onResamplerRateChange: (Int) -> Unit,
    selfTestResult: ShaderSelfTestResult,
    onApplySafeGraphicsMode: () -> Unit,
    onNavigateToOpticalDiagnostics: () -> Unit,
    onPairNewDevice: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val view = LocalView.current
    val sharedPrefs = remember { context.getSharedPreferences("node_player_prefs", Context.MODE_PRIVATE) }

    var refreshRatePref by remember {
        mutableStateOf(sharedPrefs.getString("display_refresh_rate", "AUTO") ?: "AUTO")
    }
    var sleepShake by remember {
        mutableStateOf(sharedPrefs.getBoolean("sleep_timer_shake_extend", true))
    }
    var shakeSensitivity by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("shake_sensitivity", 1.0f))
    }
    var shakeExtendMinutes by remember {
        mutableIntStateOf(sharedPrefs.getInt("shake_extend_minutes", 5))
    }
    var analysisSpeed by remember {
        mutableStateOf(sharedPrefs.getString("analysis_speed", "Slow") ?: "Slow")
    }
    var analysisChargingOnly by remember {
        mutableStateOf(sharedPrefs.getBoolean("analysis_charging_only", false))
    }
    var nodeConnectOn by remember {
        mutableStateOf(sharedPrefs.getBoolean("node_connect_enabled", true))
    }
    var usbDacDirect by remember {
        mutableStateOf(sharedPrefs.getBoolean("usb_dac_direct_enabled", outputInfo.isBitPerfect))
    }

    val refreshOptions = listOf("AUTO" to "Auto", "60Hz" to "60 Hz", "120Hz" to "120 Hz", "HIGHEST" to "Highest")
    val refreshSliderIdx = refreshOptions.indexOfFirst { it.first.equals(refreshRatePref, ignoreCase = true) }.coerceAtLeast(0)

    val rateOptions = listOf(0 to "Auto", 44100 to "44.1k", 48000 to "48k", 96000 to "96k", 192000 to "192k")
    val rateSliderIdx = rateOptions.indexOfFirst { it.first == resamplerRateChoice }.coerceAtLeast(0)

    val shaderOptions = listOf("AUTO" to "Auto", "FULL" to "Full AGSL", "SIMPLE" to "Simple", "BLUR_ONLY" to "Blur Only", "OFF" to "Off")
    val shaderSliderIdx = shaderOptions.indexOfFirst { it.first == themeConfig.shaderMode }.coerceAtLeast(0)

    val speedOptions = listOf("Slow", "Normal", "Fast")
    val speedSliderIdx = speedOptions.indexOf(analysisSpeed).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                LiquidIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Back",
                    lighterStyle = true,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hardware & Engine",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "DAC, GPU shaders, display Hz, sensors & LAN",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }
            }

            LiquidButton(
                onClick = {
                    onResamplerRateChange(0)
                    onToggleSystemEq(false)
                    onToggleAutoDeviceProfiles(true)
                    onToggleResumeOnHeadsetConnect(true)
                    refreshRatePref = "AUTO"
                    sharedPrefs.edit().putString("display_refresh_rate", "AUTO").apply()
                    (context as? com.nodeplayer.music.MainActivity)?.applyDisplayRefreshRate("AUTO")
                    onThemeConfigChange(
                        themeConfig.copy(
                            shaderMode = "AUTO",
                            isPerformanceMode = false,
                            antiBandingDithering = true,
                            hapticsEnabled = true
                        )
                    )
                    Toast.makeText(context, "Hardware settings reset to defaults", Toast.LENGTH_SHORT).show()
                },
                lighterStyle = true,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text("Reset", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. AUDIO DAC & OUTPUT HARDWARE
        HardwareSectionHeader(title = "AUDIO DAC & OUTPUT HARDWARE", icon = Icons.Rounded.GraphicEq, accent = accent)
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Active Hardware Endpoint Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(14.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(1.dp, accent.copy(alpha = 0.30f), ContinuousRoundedRectangle(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (currentDeviceType) {
                                OutputDeviceType.USB -> Icons.Rounded.Usb
                                OutputDeviceType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                                OutputDeviceType.WIRED -> Icons.Rounded.Headphones
                                OutputDeviceType.SPEAKER -> Icons.Rounded.Speaker
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = outputInfo.outputDeviceName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Profile: ${currentDeviceType.displayName} • ${outputInfo.sampleRateDisplay} • 32-bit Float",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bit-Perfect Direct USB DAC
                HardwareToggleRow(
                    title = "Bit-Perfect Direct USB DAC",
                    subtitle = if (outputInfo.outputDeviceName.contains("USB", ignoreCase = true)) {
                        "Bypasses Android AudioFlinger mixer for direct USB hardware clock"
                    } else {
                        "Connect a USB-C / OTG DAC to unlock direct hardware routing"
                    },
                    checked = usbDacDirect || outputInfo.isBitPerfect,
                    enabled = outputInfo.outputDeviceName.contains("USB", ignoreCase = true),
                    onCheckedChange = {
                        usbDacDirect = it
                        sharedPrefs.edit().putBoolean("usb_dac_direct_enabled", it).apply()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Hardware Resampler Output Rate (LiquidChip + LiquidSlider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Resampler Target", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Sets platform AudioTrack sink sample rate", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Text(
                        text = rateOptions[rateSliderIdx].second,
                        style = MaterialTheme.typography.labelLarge.copy(color = accent, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LiquidSlider(
                    value = rateSliderIdx.toFloat(),
                    onValueChange = { idxFloat ->
                        val idx = idxFloat.roundToInt().coerceIn(0, rateOptions.lastIndex)
                        onResamplerRateChange(rateOptions[idx].first)
                    },
                    valueRange = 0f..rateOptions.lastIndex.toFloat(),
                    steps = rateOptions.size - 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rateOptions.forEach { (rate, label) ->
                        LiquidChip(
                            selected = resamplerRateChoice == rate,
                            onClick = { onResamplerRateChange(rate) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Auto-Switch Per-Device Profiles
                HardwareToggleRow(
                    title = "Auto-Switch Per-Device Profiles",
                    subtitle = "Saves and restores separate EQ/DSP chains per hardware endpoint",
                    checked = autoDeviceProfilesEnabled,
                    onCheckedChange = onToggleAutoDeviceProfiles
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Resume on Headset Connect
                HardwareToggleRow(
                    title = "Resume on Headset / BT Connect",
                    subtitle = "Automatically resumes playback when wired or Bluetooth audio connects",
                    checked = resumeOnHeadsetConnect,
                    onCheckedChange = onToggleResumeOnHeadsetConnect
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // System-Wide Hardware EQ
                HardwareToggleRow(
                    title = "System-Wide Hardware EQ (All Apps)",
                    subtitle = "Applies Android DynamicsProcessing across all broadcasting media apps",
                    checked = systemEqEnabled,
                    onCheckedChange = onToggleSystemEq
                )

                AnimatedVisibility(
                    visible = systemEqEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(ContinuousRoundedRectangle(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (systemEqSessions.isNotEmpty()) "${systemEqSessions.size} Active Audio Session(s)" else "Listening for external audio sessions",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (systemEqSessions.isNotEmpty()) "ACTIVE" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (systemEqSessions.isNotEmpty()) Color(0xFF10B981) else accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        systemEqSessions.forEach { session ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${session.packageName} (Session #${session.sessionId})",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. DISPLAY & GPU SHADER HARDWARE
        HardwareSectionHeader(title = "DISPLAY & GPU SHADER HARDWARE", icon = Icons.Rounded.DisplaySettings, accent = accent)
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Display Refresh Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display Refresh Rate", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Controls hardware display mode (60 Hz / 120 Hz / Highest)", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Text(
                        text = refreshOptions[refreshSliderIdx].second,
                        style = MaterialTheme.typography.labelLarge.copy(color = accent, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LiquidSlider(
                    value = refreshSliderIdx.toFloat(),
                    onValueChange = { idxFloat ->
                        val idx = idxFloat.roundToInt().coerceIn(0, refreshOptions.lastIndex)
                        val mode = refreshOptions[idx].first
                        refreshRatePref = mode
                        sharedPrefs.edit().putString("display_refresh_rate", mode).apply()
                        (context as? com.nodeplayer.music.MainActivity)?.applyDisplayRefreshRate(mode)
                    },
                    valueRange = 0f..refreshOptions.lastIndex.toFloat(),
                    steps = refreshOptions.size - 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    refreshOptions.forEach { (mode, label) ->
                        LiquidChip(
                            selected = refreshRatePref.equals(mode, ignoreCase = true),
                            onClick = {
                                refreshRatePref = mode
                                sharedPrefs.edit().putString("display_refresh_rate", mode).apply()
                                (context as? com.nodeplayer.music.MainActivity)?.applyDisplayRefreshRate(mode)
                            },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // GPU Shader Pipeline Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AGSL GPU Shader Pipeline", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Controls real-time lens refraction and RenderEffect blur", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Text(
                        text = shaderOptions[shaderSliderIdx].second,
                        style = MaterialTheme.typography.labelLarge.copy(color = accent, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LiquidSlider(
                    value = shaderSliderIdx.toFloat(),
                    onValueChange = { idxFloat ->
                        val idx = idxFloat.roundToInt().coerceIn(0, shaderOptions.lastIndex)
                        onThemeConfigChange(themeConfig.copy(shaderMode = shaderOptions[idx].first))
                    },
                    valueRange = 0f..shaderOptions.lastIndex.toFloat(),
                    steps = shaderOptions.size - 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shaderOptions.forEach { (mode, label) ->
                        LiquidChip(
                            selected = themeConfig.shaderMode == mode,
                            onClick = { onThemeConfigChange(themeConfig.copy(shaderMode = mode)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Hardware Performance Mode
                HardwareToggleRow(
                    title = "GPU Performance Mode",
                    subtitle = "Disables heavy shader passes to maximize frame rate and battery life",
                    checked = themeConfig.isPerformanceMode,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(isPerformanceMode = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Anti-Banding Display Dithering
                HardwareToggleRow(
                    title = "Display Anti-Banding Dithering",
                    subtitle = "Prevents gradient color banding on 8-bit OLED panels",
                    checked = themeConfig.antiBandingDithering,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(antiBandingDithering = it)) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Liquid Action Buttons for GPU Self-Test & Safe Graphics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidButton(
                        onClick = onNavigateToOpticalDiagnostics,
                        modifier = Modifier.weight(1f),
                        surfaceColor = accent.copy(alpha = 0.26f)
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Optical Test", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    LiquidButton(
                        onClick = {
                            onApplySafeGraphicsMode()
                            Toast.makeText(context, "Safe Graphics Mode applied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        lighterStyle = true
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Text("Safe Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. SENSORS & TACTILE HAPTICS
        HardwareSectionHeader(title = "SENSORS & TACTILE HAPTICS", icon = Icons.Rounded.Vibration, accent = accent)
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                HardwareToggleRow(
                    title = "Hardware Haptic Feedback",
                    subtitle = "Tactile motor ticks on sliders, transport buttons, and dock snaps",
                    checked = themeConfig.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        onThemeConfigChange(themeConfig.copy(hapticsEnabled = enabled))
                        if (enabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                HardwareToggleRow(
                    title = "Accelerometer Shake-to-Extend",
                    subtitle = "Shake device to extend active Sleep Timer without unlocking screen",
                    checked = sleepShake,
                    onCheckedChange = {
                        sleepShake = it
                        sharedPrefs.edit().putBoolean("sleep_timer_shake_extend", it).apply()
                    }
                )

                AnimatedVisibility(
                    visible = sleepShake,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Shake Trigger Sensitivity", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f)))
                            Text("%.1fx".format(shakeSensitivity), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        LiquidSlider(
                            value = shakeSensitivity,
                            onValueChange = {
                                shakeSensitivity = it
                                sharedPrefs.edit().putFloat("shake_sensitivity", it).apply()
                            },
                            valueRange = 0.5f..2.0f
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Extension Duration", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f)))
                            Text("+${shakeExtendMinutes} min", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        LiquidSlider(
                            value = shakeExtendMinutes.toFloat(),
                            onValueChange = {
                                val mins = it.roundToInt().coerceIn(5, 30)
                                shakeExtendMinutes = mins
                                sharedPrefs.edit().putInt("shake_extend_minutes", mins).apply()
                            },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. CPU, THERMAL & BATTERY HARDWARE
        HardwareSectionHeader(title = "CPU, THERMAL & BATTERY HARDWARE", icon = Icons.Rounded.Memory, accent = accent)
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FFT Analysis CPU Throttle", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Controls background worker thread pacing for spectral & LUFS scans", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)))
                    }
                    Text(analysisSpeed, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LiquidSlider(
                    value = speedSliderIdx.toFloat(),
                    onValueChange = { idxFloat ->
                        val idx = idxFloat.roundToInt().coerceIn(0, speedOptions.lastIndex)
                        val spd = speedOptions[idx]
                        analysisSpeed = spd
                        sharedPrefs.edit().putString("analysis_speed", spd).apply()
                    },
                    valueRange = 0f..speedOptions.lastIndex.toFloat(),
                    steps = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedOptions.forEach { spd ->
                        LiquidChip(
                            selected = analysisSpeed == spd,
                            onClick = {
                                analysisSpeed = spd
                                sharedPrefs.edit().putString("analysis_speed", spd).apply()
                            },
                            label = if (spd == "Slow") "Slow (Cool)" else spd,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                HardwareToggleRow(
                    title = "Scan Only While Charging",
                    subtitle = "Pauses background FFT & DR scans until external power is connected",
                    checked = analysisChargingOnly,
                    onCheckedChange = {
                        analysisChargingOnly = it
                        sharedPrefs.edit().putBoolean("analysis_charging_only", it).apply()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                Text(
                    text = "ColorOS / realme Background Audio Lock",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                )
                Text(
                    text = "Set Battery Usage to 'Unrestricted' and enable 'Allow Background Activity' so OS thermal/sleep policies never interrupt playback.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f), lineHeight = 16.sp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Settings > Battery manually", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        surfaceColor = accent.copy(alpha = 0.25f)
                    ) {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Battery", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    LiquidButton(
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open App Info manually", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        lighterStyle = true
                    ) {
                        Icon(Icons.Rounded.SettingsApplications, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("App Info", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. LOCAL NETWORK HARDWARE (NODE CONNECT)
        HardwareSectionHeader(title = "LOCAL NETWORK HARDWARE (NODE CONNECT)", icon = Icons.Rounded.CastConnected, accent = accent)
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                HardwareToggleRow(
                    title = "Enable LAN mTLS & Multicast Discovery",
                    subtitle = "Port 54820 • Device: ${Build.MODEL}",
                    checked = nodeConnectOn,
                    onCheckedChange = { isChecked ->
                        nodeConnectOn = isChecked
                        sharedPrefs.edit().putBoolean("node_connect_enabled", isChecked).apply()
                        com.nodeplayer.music.service.PlaybackService.instance?.setNodeConnectEnabled(isChecked)
                    }
                )

                AnimatedVisibility(
                    visible = nodeConnectOn,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val hub = com.nodeplayer.music.service.PlaybackService.instance?.nodeConnectHub
                    val ncSettingsStore = remember {
                        val prefs = context.getSharedPreferences("node_connect_settings", Context.MODE_PRIVATE)
                        com.nodeplayer.network.service.NodeConnectSettingsStore(
                            com.nodeplayer.music.data.AndroidKeyValueStore(prefs)
                        )
                    }
                    var ncConfig by remember { mutableStateOf(ncSettingsStore.config.value) }

                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Spacer(modifier = Modifier.height(10.dp))

                        HardwareToggleRow(
                            title = "Direct PCM Audio Cast",
                            subtitle = "Stream lossless uncompressed audio over local Wi-Fi",
                            checked = ncConfig.audioCastEnabled,
                            onCheckedChange = {
                                val updated = ncConfig.copy(audioCastEnabled = it)
                                ncConfig = updated
                                ncSettingsStore.saveConfig(updated)
                                hub?.config = updated
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HardwareToggleRow(
                            title = "Mute Local Speakers While Casting",
                            subtitle = "Route audio exclusively to the receiving hardware endpoint",
                            checked = ncConfig.muteLocalOutputDuringCast,
                            onCheckedChange = {
                                val updated = ncConfig.copy(muteLocalOutputDuringCast = it)
                                ncConfig = updated
                                ncSettingsStore.saveConfig(updated)
                                hub?.config = updated
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HardwareToggleRow(
                            title = "LAN Remote Control & Handoff",
                            subtitle = "Allow play/pause/skip and instant queue handoff between PC & phone",
                            checked = ncConfig.remoteControlEnabled && ncConfig.handoffEnabled,
                            onCheckedChange = {
                                val updated = ncConfig.copy(remoteControlEnabled = it, handoffEnabled = it)
                                ncConfig = updated
                                ncSettingsStore.saveConfig(updated)
                                hub?.config = updated
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LiquidButton(
                            onClick = onPairNewDevice,
                            modifier = Modifier.fillMaxWidth(),
                            surfaceColor = accent.copy(alpha = 0.30f)
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Pair Hardware Device (PIN / QR)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(180.dp))
    }
}

@Composable
private fun HardwareSectionHeader(
    title: String,
    icon: ImageVector,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        )
    }
}

@Composable
private fun HardwareToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.50f)
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.5.sp
                )
            )
        }
        LiquidToggle(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
