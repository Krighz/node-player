package com.nodeplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.*
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidDialog
import com.nodeplayer.ui.components.LiquidGlassSlider
import com.nodeplayer.ui.components.LiquidGlassSwitch
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.ParametricEqCurveGraph
import com.nodeplayer.ui.theme.LocalAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerView(
    config: AudioEffectConfig,
    onConfigChange: (AudioEffectConfig) -> Unit,
    activePresetLabel: String? = null,
    currentTrack: Track? = null,
    currentDeviceName: String = "Default Audio Output",
    onToggleAbCompare: () -> Unit = {},
    onImportAutoEq: (String, String) -> Unit = { _, _ -> },
    onSaveTrackPreset: () -> Unit = {},
    onSaveAlbumPreset: () -> Unit = {},
    onClearPreset: () -> Unit = {},
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    var showAutoEqDialog by remember { mutableStateOf(false) }
    var autoEqText by remember { mutableStateOf("") }
    var autoEqName by remember { mutableStateOf("Custom AutoEQ") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 1. Header with Master EQ Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Equalizer & DSP Chain",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "Target: $currentDeviceName • 32-bit Float Precision",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
            }

            LiquidGlassSwitch(
                checked = config.eqEnabled,
                onCheckedChange = { onConfigChange(config.copy(eqEnabled = it)) }
            )
        }

        // 2. Toolbar Actions: A/B Compare, AutoEQ Import, Track Preset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidChip(
                selected = config.isAbComparing,
                onClick = onToggleAbCompare,
                label = if (config.isAbComparing) "A/B Match: %+.1f dB".format(config.abLoudnessOffsetDb) else "A/B Compare",
                icon = if (config.isAbComparing) Icons.Rounded.CompareArrows else Icons.Rounded.SwapHoriz
            )

            LiquidChip(
                selected = false,
                onClick = { showAutoEqDialog = true },
                label = "AutoEQ Import",
                icon = Icons.Rounded.Tune
            )

            if (currentTrack != null) {
                LiquidChip(
                    selected = false,
                    onClick = onSaveTrackPreset,
                    label = "Save Track Preset",
                    icon = Icons.Rounded.BookmarkAdd
                )
                LiquidChip(
                    selected = false,
                    onClick = onSaveAlbumPreset,
                    label = "Save Album Preset",
                    icon = Icons.Rounded.Album
                )
            }
        }

        if (activePresetLabel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activePresetLabel,
                    style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.SemiBold)
                )
                LiquidButton(
                    onClick = onClearPreset,
                    lighterStyle = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Interactive Parametric Frequency Curve Graph
        ParametricEqCurveGraph(
            bands = config.bands,
            onBandsChange = { updated ->
                onConfigChange(config.copy(bands = updated, eqEnabled = true))
            },
            eqEnabled = config.eqEnabled,
            accentColor = accent,
            rmsLevel = rmsLevel,
            showSpectrum = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Presets Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioEffectConfig.PRESETS.keys.forEach { preset ->
                val isSelected = config.presetName == preset
                LiquidChip(
                    selected = isSelected,
                    onClick = {
                        val gains = AudioEffectConfig.PRESETS[preset] ?: return@LiquidChip
                        val updatedBands = config.bands.mapIndexed { idx, band ->
                            band.copy(gainDb = gains.getOrElse(idx) { 0f })
                        }
                        onConfigChange(config.copy(presetName = preset, bands = updatedBands, eqEnabled = true))
                    },
                    label = preset
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Preamp Gain Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Preamp Gain",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "%+.1f dB".format(config.preampGainDb),
                        style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.Bold)
                    )
                }
                LiquidGlassSlider(
                    value = config.preampGainDb,
                    onValueChange = { onConfigChange(config.copy(preampGainDb = it)) },
                    valueRange = -12f..12f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. 10-Band Biquad Graphic Equalizer Sliders
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "10-Band Biquad Filters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    config.bands.forEachIndexed { index, band ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "%+.1f".format(band.gainDb),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (band.gainDb != 0f) accent else Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LiquidSlider(
                                value = band.gainDb,
                                onValueChange = { newGain ->
                                    val updated = config.bands.toMutableList()
                                    updated[index] = band.copy(gainDb = (newGain * 2f).toInt() / 2f)
                                    onConfigChange(config.copy(bands = updated, eqEnabled = true))
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val freqLabel = when {
                                band.centerFreq >= 1000f -> "${(band.centerFreq / 1000f).toInt()}k"
                                else -> "${band.centerFreq.toInt()}"
                            }
                            Text(
                                text = freqLabel,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Sound Lab & Advanced Audio Processors
        Text(
            text = "Advanced DSP & Sound Lab",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Tone Controls (Bass & Treble Shelves)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tone Controls",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bass Shelf (100 Hz)", fontSize = 12.sp)
                    Text("%+.1f dB".format(config.toneControls.bassGainDb), fontSize = 12.sp, color = accent)
                }
                LiquidGlassSlider(
                    value = config.toneControls.bassGainDb,
                    onValueChange = { onConfigChange(config.copy(toneControls = config.toneControls.copy(bassGainDb = it))) },
                    valueRange = -12f..12f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Treble Shelf (10 kHz)", fontSize = 12.sp)
                    Text("%+.1f dB".format(config.toneControls.trebleGainDb), fontSize = 12.sp, color = accent)
                }
                LiquidGlassSlider(
                    value = config.toneControls.trebleGainDb,
                    onValueChange = { onConfigChange(config.copy(toneControls = config.toneControls.copy(trebleGainDb = it))) },
                    valueRange = -12f..12f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fletcher-Munson Loudness Equalizer
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Loudness Equalizer (ISO 226)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Dynamic Fletcher-Munson bass/treble compensation for low volumes",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    LiquidGlassSwitch(
                        checked = config.loudnessConfig.enabled,
                        onCheckedChange = { onConfigChange(config.copy(loudnessConfig = config.loudnessConfig.copy(enabled = it))) }
                    )
                }
                if (config.loudnessConfig.enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LiquidGlassSlider(
                        value = config.loudnessConfig.strength,
                        onValueChange = { onConfigChange(config.copy(loudnessConfig = config.loudnessConfig.copy(strength = it))) },
                        valueRange = 0f..1f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Harmonic Bass Exciter
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Harmonic Bass Exciter",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Psychoacoustic upper-harmonic sub-bass generation",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    LiquidGlassSwitch(
                        checked = config.harmonicBassConfig.enabled,
                        onCheckedChange = { onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(enabled = it))) }
                    )
                }
                if (config.harmonicBassConfig.enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Harmonic Blend", fontSize = 12.sp)
                        Text("${(config.harmonicBassConfig.harmonicGain * 100).toInt()}%", fontSize = 12.sp, color = accent)
                    }
                    LiquidGlassSlider(
                        value = config.harmonicBassConfig.harmonicGain,
                        onValueChange = { onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(harmonicGain = it))) },
                        valueRange = 0f..1f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spatial Stereo Width Expansion
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spatial Stereo Width",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${(config.stereoWidth * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                LiquidGlassSlider(
                    value = config.stereoWidth,
                    onValueChange = { onConfigChange(config.copy(stereoWidth = it)) },
                    valueRange = 0f..2f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Studio Reverb
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Studio Reverb",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Schroeder/Moorer algorithmic acoustic room simulator",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    LiquidGlassSwitch(
                        checked = config.reverbConfig.enabled,
                        onCheckedChange = { onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(enabled = it))) }
                    )
                }
                if (config.reverbConfig.enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Room Size", fontSize = 12.sp)
                        Text("${(config.reverbConfig.roomSize * 100).toInt()}%", fontSize = 12.sp, color = accent)
                    }
                    LiquidGlassSlider(
                        value = config.reverbConfig.roomSize,
                        onValueChange = { onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(roomSize = it))) },
                        valueRange = 0f..1f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Peak Limiter & Clip Guard
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lookahead Peak Limiter & Clip Guard",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Zero-distortion lookahead gain ceiling to prevent digital DAC clipping",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                LiquidGlassSwitch(
                    checked = config.limiterEnabled,
                    onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(LocalBottomBarPadding.current + 24.dp))
    }

    // AutoEQ Import Dialog
    if (showAutoEqDialog) {
        LiquidDialog(
            onDismissRequest = { showAutoEqDialog = false },
            title = { Text("Import AutoEQ Profile", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)) },
            text = {
                Column {
                    Text("Paste parametric EQ filter settings exported from AutoEQ or Peace GUI:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoEqName,
                        onValueChange = { autoEqName = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoEqText,
                        onValueChange = { autoEqText = it },
                        label = { Text("AutoEQ Filter String") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                LiquidButton(
                    onClick = {
                        onImportAutoEq(autoEqName, autoEqText)
                        showAutoEqDialog = false
                    }
                ) {
                    Text("Import", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                LiquidButton(
                    onClick = { showAutoEqDialog = false },
                    lighterStyle = true
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
