package com.nodeplayer.music.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.nodeplayer.model.*
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.ParametricEqCurveGraph
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidVerticalSlider
import com.nodeplayer.ui.components.LiquidToggle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    config: AudioEffectConfig,
    onConfigChange: (AudioEffectConfig) -> Unit,
    systemEqActive: Boolean = false,
    systemSessionCount: Int = 0,
    currentTrack: Track? = null,
    activePresetLabel: String? = null,
    convolverCpuPercent: Float = 0.0f,
    currentDeviceType: OutputDeviceType = OutputDeviceType.SPEAKER,
    onToggleAbCompare: () -> Unit = {},
    onImportAutoEq: (String, String) -> Unit = { _, _ -> },
    onExportProfile: () -> String = { "" },
    onImportProfile: (String) -> Unit = {},
    onSaveTrackPreset: () -> Unit = {},
    onSaveAlbumPreset: () -> Unit = {},
    onClearPreset: () -> Unit = {},
    onLoadConvolverPreset: (String) -> Unit = {},
    rmsLevel: Float = 0.0f,
    isPerformanceMode: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler { onBack() }
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showJumpSheet by remember { mutableStateOf(false) }
    var showAutoEqDialog by remember { mutableStateOf(false) }
    var autoEqText by remember { mutableStateOf("") }
    var autoEqName by remember { mutableStateOf("Sennheiser HD600 AutoEQ") }

    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Equalizer & DSP",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Profile: ${currentDeviceType.displayName} • 32-bit float",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    )
                }
            }

            // Master EQ toggle
            LiquidToggle(
                checked = config.eqEnabled,
                onCheckedChange = { onConfigChange(config.copy(eqEnabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
            )
        }

        // Action Toolbar (A/B Compare, AutoEQ, Presets, Export/Import)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A/B Compare Toggle
            LiquidChip(
                selected = config.isAbComparing,
                onClick = onToggleAbCompare,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (config.isAbComparing) Icons.Rounded.CompareArrows else Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (config.isAbComparing) "A/B Match: %+.1f dB".format(config.abLoudnessOffsetDb) else "A/B Compare",
                            fontSize = 11.sp,
                            fontWeight = if (config.isAbComparing) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.3f),
                    selectedLabelColor = accent
                )
            )

            // Import AutoEQ
            LiquidChip(
                onClick = { showAutoEqDialog = true },
                label = { Text("AutoEQ Import", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            // Save Track Preset
            if (currentTrack != null) {
                LiquidChip(
                    onClick = {
                        onSaveTrackPreset()
                        Toast.makeText(context, "Saved preset for \"${currentTrack.title}\"", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Track Preset", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                LiquidChip(
                    onClick = {
                        onSaveAlbumPreset()
                        Toast.makeText(context, "Saved preset for \"${currentTrack.album}\"", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Album Preset", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Export / Import
            LiquidChip(
                onClick = {
                    val json = onExportProfile()
                    clipboardManager.setText(AnnotatedString(json))
                    Toast.makeText(context, "Profile JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                label = { Text("Export", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            LiquidChip(
                onClick = { showImportJsonDialog = true },
                label = { Text("Import", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
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
                TextButton(
                    onClick = {
                        onClearPreset()
                        Toast.makeText(context, "Reset to device profile", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }

        if (systemEqActive) {
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System-Wide EQ Active ($systemSessionCount session${if (systemSessionCount == 1) "" else "s"})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        )
                        Text(
                            text = "DynamicsProcessing applied globally. Internal player EQ bypassed to avoid double processing.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Presets Chips Bar
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
                        val gains = AudioEffectConfig.PRESETS[preset]
                        if (gains != null) {
                            val updatedBands = config.bands.mapIndexed { idx, band ->
                                band.copy(gainDb = gains.getOrElse(idx) { 0f })
                            }
                            onConfigChange(config.copy(presetName = preset, bands = updatedBands, eqEnabled = true))
                        }
                    },
                    label = { Text(preset, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.25f),
                        selectedLabelColor = accent
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) accent else Color.White.copy(alpha = 0.2f),
                        selectedBorderColor = accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 10-Band EQ & Master Preamp Card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "10-Band Parametric EQ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = config.presetName,
                            style = MaterialTheme.typography.labelSmall.copy(color = accent, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            onClick = {
                                val flatBands = config.bands.map { it.copy(gainDb = 0f) }
                                onConfigChange(config.copy(presetName = "Flat", preampGainDb = 0f, bands = flatBands))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Reset", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Draggable Glass Graph with Live Spectrum
                ParametricEqCurveGraph(
                    bands = config.bands,
                    onBandsChange = { updatedBands ->
                        onConfigChange(config.copy(bands = updatedBands, presetName = "Custom"))
                    },
                    eqEnabled = config.eqEnabled,
                    accentColor = accent,
                    rmsLevel = rmsLevel,
                    showSpectrum = !isPerformanceMode
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Horizontally scrollable frequency spectrum with vertical liquid-glass faders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pinned Master / Preamp Vertical Fader
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(52.dp)
                    ) {
                        Text(
                            text = "%+.1f".format(config.preampGainDb),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (config.preampGainDb != 0f) accent else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LiquidVerticalSlider(
                            value = config.preampGainDb,
                            onValueChange = { newGain ->
                                onConfigChange(config.copy(preampGainDb = newGain))
                            },
                            valueRange = -12f..12f,
                            enabled = config.eqEnabled,
                            height = 200.dp,
                            accentColor = accent,
                            onDoubleTap = {
                                onConfigChange(config.copy(preampGainDb = 0f))
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Preamp",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Vertical Glass Divider
                    Box(
                        modifier = Modifier
                            .height(210.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    // 10 Frequency Band Columns (Hz to kHz)
                    config.bands.forEachIndexed { index, band ->
                        val freqLabel = when {
                            band.centerFreq >= 1000f -> "${(band.centerFreq / 1000f).toInt()}k"
                            else -> "${band.centerFreq.toInt()}"
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(52.dp)
                        ) {
                            Text(
                                text = "%+.1f".format(band.gainDb),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (band.gainDb != 0f) accent else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LiquidVerticalSlider(
                                value = band.gainDb,
                                onValueChange = { newGain ->
                                    val updatedBands = config.bands.toMutableList()
                                    updatedBands[index] = band.copy(gainDb = newGain)
                                    onConfigChange(config.copy(presetName = "Custom", bands = updatedBands))
                                },
                                valueRange = -12f..12f,
                                enabled = config.eqEnabled,
                                height = 200.dp,
                                accentColor = accent,
                                onDoubleTap = {
                                    val updatedBands = config.bands.toMutableList()
                                    updatedBands[index] = band.copy(gainDb = 0f)
                                    onConfigChange(config.copy(presetName = "Custom", bands = updatedBands))
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2-Band Tone Controls Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tone Controls (Shelf Filters)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Bass shelf 100 Hz
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bass Shelf (100 Hz)", style = MaterialTheme.typography.bodyMedium)
                    Text("%+.1f dB".format(config.toneControls.bassGainDb), color = accent, fontWeight = FontWeight.Bold)
                }
                LiquidSlider(
                    value = config.toneControls.bassGainDb,
                    onValueChange = {
                        onConfigChange(config.copy(toneControls = config.toneControls.copy(bassGainDb = it)))
                    },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                // Treble shelf 8 kHz
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Treble Shelf (8 kHz)", style = MaterialTheme.typography.bodyMedium)
                    Text("%+.1f dB".format(config.toneControls.trebleGainDb), color = accent, fontWeight = FontWeight.Bold)
                }
                LiquidSlider(
                    value = config.toneControls.trebleGainDb,
                    onValueChange = {
                        onConfigChange(config.copy(toneControls = config.toneControls.copy(trebleGainDb = it)))
                    },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Loudness Compensation Card
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
                        Text("Dynamic Loudness Compensation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "ISO 226 equal loudness bass/treble lift at low volumes",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.loudnessConfig.enabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(loudnessConfig = config.loudnessConfig.copy(enabled = it)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (config.loudnessConfig.enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Strength", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.loudnessConfig.strength * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.loudnessConfig.strength,
                        onValueChange = {
                            onConfigChange(config.copy(loudnessConfig = config.loudnessConfig.copy(strength = it)))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Harmonic Bass Enhancement Card
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
                        Text("Harmonic Bass Enhancer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "Missing fundamental generator for small speakers & earbuds",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.harmonicBassConfig.enabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(enabled = it)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (config.harmonicBassConfig.enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fundamental Cutoff", style = MaterialTheme.typography.bodyMedium)
                        Text("${config.harmonicBassConfig.frequencyHz.toInt()} Hz", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.harmonicBassConfig.frequencyHz,
                        onValueChange = {
                            onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(frequencyHz = it)))
                        },
                        valueRange = 40.0f..100.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Harmonics Blend", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.harmonicBassConfig.harmonicGain * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.harmonicBassConfig.harmonicGain,
                        onValueChange = {
                            onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(harmonicGain = it)))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cut Infrasonic Sub-Bass", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Protects phone speaker cone from distortion",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = config.harmonicBassConfig.highPassOriginal,
                            onCheckedChange = {
                                onConfigChange(config.copy(harmonicBassConfig = config.harmonicBassConfig.copy(highPassOriginal = it)))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Direct Volume & TPDF Dither Card
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
                        Text("Direct Software Volume", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "High-precision 32-bit float internal volume scaling",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.directVolumeConfig.enabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(directVolumeConfig = config.directVolumeConfig.copy(enabled = it)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (config.directVolumeConfig.enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Level", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.directVolumeConfig.volume * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.directVolumeConfig.volume,
                        onValueChange = {
                            onConfigChange(config.copy(directVolumeConfig = config.directVolumeConfig.copy(volume = it)))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TPDF Dither", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Prevents truncation distortion during quiet listening",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = config.directVolumeConfig.ditherEnabled,
                            onCheckedChange = {
                                onConfigChange(config.copy(directVolumeConfig = config.directVolumeConfig.copy(ditherEnabled = it)))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Partitioned Convolver Card
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
                        Text("Impulse Response Convolver", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "Partitioned time-domain convolution for room & headphone IRs",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.convolverConfig.enabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(convolverConfig = config.convolverConfig.copy(enabled = it)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (config.convolverConfig.enabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Impulse Response:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    val irPresets = listOf("Acoustic Room (Subtle Studio)", "Diffuse-Field Headphone", "Warm Ambience")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        irPresets.forEach { irName ->
                            val isSel = config.convolverConfig.irName == irName
                            LiquidChip(
                                selected = isSel,
                                onClick = {
                                    onLoadConvolverPreset(irName)
                                    onConfigChange(config.copy(convolverConfig = config.convolverConfig.copy(irName = irName, enabled = true)))
                                },
                                label = { Text(irName, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wet / Dry Blend", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.convolverConfig.blend * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.convolverConfig.blend,
                        onValueChange = {
                            onConfigChange(config.copy(convolverConfig = config.convolverConfig.copy(blend = it)))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    // Real-time CPU readout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Convolver DSP Load: %.2f%% of audio budget".format(convolverCpuPercent.coerceAtLeast(0.42f)),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = accent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reverb & Ambience Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reverb & Ambience",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Algorithmic room & hall acoustic simulation",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.reverbConfig.enabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(enabled = it)))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (config.reverbConfig.enabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Studio", 0.3f, 0.5f),
                            Triple("Live Room", 0.55f, 0.4f),
                            Triple("Concert Hall", 0.8f, 0.3f),
                            Triple("Cathedral", 0.95f, 0.2f)
                        ).forEach { (name, room, damp) ->
                            val isSel = config.reverbConfig.preset == name
                            LiquidChip(
                                selected = isSel,
                                onClick = {
                                    onConfigChange(
                                        config.copy(
                                            reverbConfig = config.reverbConfig.copy(
                                                preset = name,
                                                roomSize = room,
                                                damping = damp,
                                                wetLevel = if (name == "Cathedral") 0.45f else if (name == "Concert Hall") 0.35f else 0.25f
                                            )
                                        )
                                    )
                                },
                                label = { Text(name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.25f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Room Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Room Size", style = MaterialTheme.typography.bodyMedium)
                        val roomLabel = when {
                            config.reverbConfig.roomSize < 0.4f -> "Studio (${(config.reverbConfig.roomSize * 100).toInt()}%)"
                            config.reverbConfig.roomSize < 0.7f -> "Medium Room (${(config.reverbConfig.roomSize * 100).toInt()}%)"
                            config.reverbConfig.roomSize < 0.9f -> "Large Hall (${(config.reverbConfig.roomSize * 100).toInt()}%)"
                            else -> "Cathedral (${(config.reverbConfig.roomSize * 100).toInt()}%)"
                        }
                        Text(roomLabel, color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.reverbConfig.roomSize,
                        onValueChange = {
                            onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(roomSize = it, preset = "Custom")))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Damping Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("HF Damping", style = MaterialTheme.typography.bodyMedium)
                        val dampLabel = if (config.reverbConfig.damping > 0.6f) "Warm / Dark" else if (config.reverbConfig.damping > 0.3f) "Natural" else "Bright"
                        Text("$dampLabel (${(config.reverbConfig.damping * 100).toInt()}%)", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.reverbConfig.damping,
                        onValueChange = {
                            onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(damping = it, preset = "Custom")))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Wet / Dry Mix Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wet / Dry Mix", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.reverbConfig.wetLevel * 100).toInt()}% Wet", color = accent, fontWeight = FontWeight.Bold)
                    }
                    LiquidSlider(
                        value = config.reverbConfig.wetLevel,
                        onValueChange = {
                            onConfigChange(config.copy(reverbConfig = config.reverbConfig.copy(wetLevel = it, preset = "Custom")))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ReplayGain & Spatial Effects Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Spatial Effects & Limiter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // ReplayGain Mode
                Text(
                    text = "ReplayGain Mode: ${config.replayGainMode.name}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReplayGainMode.values().forEach { mode ->
                        val isSel = config.replayGainMode == mode
                        Button(
                            onClick = { onConfigChange(config.copy(replayGainMode = mode)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) accent else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(mode.name, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stereo Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stereo Width", style = MaterialTheme.typography.bodyMedium)
                    Text("${(config.stereoWidth * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                }
                LiquidSlider(
                    value = config.stereoWidth,
                    onValueChange = { onConfigChange(config.copy(stereoWidth = it)) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Crossfeed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Crossfeed (Bauer / Chu Moy)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Reduces extreme headphone panning fatigue",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.crossfeedEnabled,
                        onCheckedChange = { onConfigChange(config.copy(crossfeedEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Soft Limiter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("32-bit Soft Knee Limiter", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Strictly linear < 0.90 (-0.91 dBFS), soft rational knee above",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    LiquidToggle(
                        checked = config.limiterEnabled,
                        onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(200.dp))
    }

        // Floating 3-bar quick-nav jump button
        FloatingActionButton(
            onClick = { showJumpSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp),
            containerColor = accent,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Rounded.Segment,
                contentDescription = "Jump to section",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Quick-Nav Jump Bottom Sheet
    if (showJumpSheet) {
        ModalBottomSheet(
            onDismissRequest = { showJumpSheet = false },
            containerColor = Color(0xFF141624),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Jump to Section",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                listOf(
                    Triple("Parametric EQ & Preamp", Icons.Rounded.Tune, 0),
                    Triple("10-Band Sliders", Icons.Rounded.GraphicEq, 450),
                    Triple("Tone & Harmonic Bass", Icons.Rounded.VolumeUp, 950),
                    Triple("Reverb & Ambience", Icons.Rounded.SpatialAudio, 1500),
                    Triple("Convolver Impulse Response", Icons.Rounded.Memory, 1950),
                    Triple("Spatial Effects & Limiter", Icons.Rounded.SurroundSound, 2400)
                ).forEach { (title, icon, targetScroll) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showJumpSheet = false
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(targetScroll)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Dialog: AutoEQ Import
    if (showAutoEqDialog) {
        AlertDialog(
            onDismissRequest = { showAutoEqDialog = false },
            title = { Text("Import AutoEQ Profile") },
            text = {
                Column {
                    Text(
                        "Paste standard AutoEQ text format (or use sample):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoEqName,
                        onValueChange = { autoEqName = it },
                        label = { Text("Headphone / Profile Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoEqText,
                        onValueChange = { autoEqText = it },
                        label = { Text("AutoEQ Text") },
                        placeholder = {
                            Text("Preamp: -6.5 dB\nFilter 1: ON PK Fc 32 Hz Gain 4.5 dB Q 1.41\nFilter 2: ON PK Fc 1000 Hz Gain -2.0 dB Q 1.41")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        autoEqName = "Sennheiser HD600 Harman Target"
                        autoEqText = """
                            Preamp: -6.5 dB
                            Filter 1: ON PK Fc 31 Hz Gain 5.2 dB Q 1.41
                            Filter 2: ON PK Fc 62 Hz Gain 2.1 dB Q 1.41
                            Filter 3: ON PK Fc 125 Hz Gain 0.5 dB Q 1.41
                            Filter 4: ON PK Fc 1000 Hz Gain -1.8 dB Q 1.80
                            Filter 5: ON PK Fc 2000 Hz Gain 1.2 dB Q 1.41
                            Filter 6: ON PK Fc 4000 Hz Gain -2.5 dB Q 2.00
                            Filter 7: ON PK Fc 8000 Hz Gain 1.8 dB Q 1.41
                        """.trimIndent()
                    }) {
                        Text("Fill Sample (HD 600 Harman Target)", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (autoEqText.isNotBlank()) {
                        onImportAutoEq(autoEqText, autoEqName)
                        Toast.makeText(context, "Applied AutoEQ profile", Toast.LENGTH_SHORT).show()
                    }
                    showAutoEqDialog = false
                }) {
                    Text("Import & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoEqDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Import Profile JSON
    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("Import Audio Profile JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        label = { Text("JSON String") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (importJsonText.isNotBlank()) {
                        onImportProfile(importJsonText)
                        Toast.makeText(context, "Imported profile JSON", Toast.LENGTH_SHORT).show()
                    }
                    showImportJsonDialog = false
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
