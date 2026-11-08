package com.nodeplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.*
import com.nodeplayer.ui.LocalBottomBarPadding
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.*
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlin.math.roundToInt

@Composable
fun CustomizeUiView(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()
    val controlsConfig = themeConfig.controlsConfig

    fun updateControls(newControls: GlassControlsConfig) {
        onThemeConfigChange(
            themeConfig.copy(
                controlsConfig = newControls,
                controlsGlassEnabled = newControls.masterEnabled
            )
        )
    }

    // Dialog state
    var pendingPresetChange by remember { mutableStateOf<GlassControlsPreset?>(null) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Accordion expansion state
    var expandedAccordions by remember { mutableStateOf(setOf<GlassComponentType>()) }

    // 1. Preset Change Confirmation Dialog (D-03)
    pendingPresetChange?.let { targetPreset ->
        LiquidDialog(
            onDismissRequest = { pendingPresetChange = null },
            title = {
                Text(
                    text = "Apply ${targetPreset.displayName}?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Do you want to reset all 9 per-component overrides to preset defaults, or keep your existing custom adjustments?",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                LiquidButton(
                    onClick = {
                        val presetConfig = when (targetPreset) {
                            GlassControlsPreset.FULL -> GlassControlsConfig.PRESET_FULL
                            GlassControlsPreset.BALANCED -> GlassControlsConfig.PRESET_BALANCED
                            GlassControlsPreset.LIGHT -> GlassControlsConfig.PRESET_LIGHT
                        }
                        updateControls(presetConfig)
                        pendingPresetChange = null
                    }
                ) {
                    Text("Reset All to Preset")
                }
            },
            dismissButton = {
                LiquidButton(
                    onClick = {
                        val presetConfig = when (targetPreset) {
                            GlassControlsPreset.FULL -> GlassControlsConfig.PRESET_FULL
                            GlassControlsPreset.BALANCED -> GlassControlsConfig.PRESET_BALANCED
                            GlassControlsPreset.LIGHT -> GlassControlsConfig.PRESET_LIGHT
                        }
                        updateControls(
                            controlsConfig.copy(
                                masterEnabled = presetConfig.masterEnabled,
                                preset = targetPreset,
                                global = presetConfig.global
                            )
                        )
                        pendingPresetChange = null
                    }
                ) {
                    Text("Keep Overrides")
                }
            }
        )
    }

    // 2. Reset All Confirmation Dialog (D-15)
    if (showResetAllConfirm) {
        LiquidDialog(
            onDismissRequest = { showResetAllConfirm = false },
            title = {
                Text(
                    text = "Reset All Controls?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will reset all master sliders and all 9 per-component overrides back to factory defaults (Balanced Preset).",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                LiquidButton(
                    onClick = {
                        updateControls(GlassControlsConfig.PRESET_BALANCED)
                        showResetAllConfirm = false
                    }
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                LiquidButton(onClick = { showResetAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Export JSON Dialog (D-14)
    if (showExportDialog) {
        val jsonPayload = remember(controlsConfig) { GlassControlsSerializer.toJson(controlsConfig) }
        var copied by remember { mutableStateOf(false) }
        val clipboardManager = LocalClipboardManager.current

        LiquidDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Export Glass Controls Theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versioned JSON Configuration (${GlassControlsSerializer.FORMAT_NAME} v${GlassControlsSerializer.CURRENT_VERSION}):",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(ContinuousRoundedRectangle(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), ContinuousRoundedRectangle(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = jsonPayload,
                            fontSize = 10.sp,
                            color = Color(0xFF67E8F9),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                LiquidButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(jsonPayload))
                        copied = true
                    }
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "Copied!" else "Copy JSON")
                }
            },
            dismissButton = {
                LiquidButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 4. Import JSON Dialog (D-14)
    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }
        val clipboardManager = LocalClipboardManager.current

        LiquidDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "Import Glass Controls Theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a exported theme JSON payload to restore custom settings:",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = {
                            importInput = it
                            importError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        placeholder = {
                            Text("Paste JSON here...", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                        }
                    )
                    if (importError != null) {
                        Text(
                            text = importError!!,
                            color = Color(0xFFF43F5E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val text = clipboardManager.getText()?.text
                                if (!text.isNullOrBlank()) {
                                    importInput = text
                                    importError = null
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste from Clipboard", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                LiquidButton(
                    onClick = {
                        if (importInput.isBlank()) {
                            importError = "Please enter or paste JSON payload"
                            return@LiquidButton
                        }
                        try {
                            val parsed = GlassControlsSerializer.fromJson(importInput)
                            updateControls(parsed)
                            showImportDialog = false
                        } catch (e: Exception) {
                            importError = "Invalid JSON payload: ${e.message ?: "Format error"}"
                        }
                    }
                ) {
                    Text("Apply Theme")
                }
            },
            dismissButton = {
                LiquidButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDesktopSplit = maxWidth > 840.dp

        if (isDesktopSplit) {
            // Desktop / 16:9 Landscape Split Layout: Left 45% Sticky Preview, Right 55% Controls (D-09)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Sticky Live Preview Canvas & Visual Atmosphere
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ScreenHeader()
                    LivePreviewCanvas(
                        config = controlsConfig,
                        accent = accent
                    )
                    PaletteAndBackgroundSection(
                        themeConfig = themeConfig,
                        onThemeConfigChange = onThemeConfigChange,
                        accent = accent
                    )
                }

                // Right Column: Master Glass Controls & 9 Overrides Accordions
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MasterControlsCard(
                        controlsConfig = controlsConfig,
                        onUpdateControls = ::updateControls,
                        onRequestPresetChange = { pendingPresetChange = it },
                        onResetAll = { showResetAllConfirm = true },
                        onExport = { showExportDialog = true },
                        onImport = { showImportDialog = true },
                        accent = accent
                    )

                    PerItemOverridesSection(
                        controlsConfig = controlsConfig,
                        onUpdateControls = ::updateControls,
                        expandedAccordions = expandedAccordions,
                        onToggleAccordion = { type ->
                            expandedAccordions = if (expandedAccordions.contains(type)) {
                                expandedAccordions - type
                            } else {
                                expandedAccordions + type
                            }
                        },
                        accent = accent
                    )

                    Spacer(modifier = Modifier.height(LocalBottomBarPadding.current + 24.dp))
                }
            }
        } else {
            // Mobile Portrait Layout: Sticky Live Preview Canvas at Top, Scrollable Controls Below (D-09)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ScreenHeader()
                Spacer(modifier = Modifier.height(8.dp))

                // Sticky Live Preview Canvas (D-09)
                LivePreviewCanvas(
                    config = controlsConfig,
                    accent = accent
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Controls List Below
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MasterControlsCard(
                        controlsConfig = controlsConfig,
                        onUpdateControls = ::updateControls,
                        onRequestPresetChange = { pendingPresetChange = it },
                        onResetAll = { showResetAllConfirm = true },
                        onExport = { showExportDialog = true },
                        onImport = { showImportDialog = true },
                        accent = accent
                    )

                    PerItemOverridesSection(
                        controlsConfig = controlsConfig,
                        onUpdateControls = ::updateControls,
                        expandedAccordions = expandedAccordions,
                        onToggleAccordion = { type ->
                            expandedAccordions = if (expandedAccordions.contains(type)) {
                                expandedAccordions - type
                            } else {
                                expandedAccordions + type
                            }
                        },
                        accent = accent
                    )

                    PaletteAndBackgroundSection(
                        themeConfig = themeConfig,
                        onThemeConfigChange = onThemeConfigChange,
                        accent = accent
                    )

                    Spacer(modifier = Modifier.height(LocalBottomBarPadding.current + 24.dp))
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "Customize UI & Visuals",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = "Real-time liquid glass shaders, physics tokens, and optical refraction",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
        )
    }
}

/**
 * Master "Liquid glass controls" Card per D-01, D-02, D-04, D-14, D-15.
 */
@Composable
private fun MasterControlsCard(
    controlsConfig: GlassControlsConfig,
    onUpdateControls: (GlassControlsConfig) -> Unit,
    onRequestPresetChange: (GlassControlsPreset) -> Unit,
    onResetAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    accent: Color
) {
    val global = controlsConfig.global

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Master Liquid Glass Controls",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Global master switch and baseline optical parameters (D-01, D-04)",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.60f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Rounded.FileUpload, "Export", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.FileDownload, "Import", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onResetAll) {
                        Icon(Icons.Rounded.RestartAlt, "Reset All", tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Master On/Off Toggle (D-01)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Master Liquid Glass", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("When OFF, small controls drop to plain squircle look for zero GPU load", fontSize = 11.sp, color = Color.White.copy(alpha = 0.60f))
                }
                LiquidGlassSwitch(
                    checked = controlsConfig.masterEnabled,
                    onCheckedChange = { onUpdateControls(controlsConfig.copy(masterEnabled = it)) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Master Preset Chips (D-02, D-03)
            Text("Presets", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassControlsPreset.values().forEach { preset ->
                    val isSelected = controlsConfig.preset == preset
                    LiquidChip(
                        selected = isSelected,
                        onClick = { if (!isSelected) onRequestPresetChange(preset) },
                        label = preset.displayName.substringBefore(" ("),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 12 Shared Master Sliders (D-04)
            Text("Master Optics & Springs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
            Spacer(modifier = Modifier.height(8.dp))

            SliderRow("Backdrop Blur Radius", "${global.blurDp.roundToInt()} dp", global.blurDp, 0f..32f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(blurDp = it)))
            }
            SliderRow("SDF Lens Height", "${global.lensHeightDp.roundToInt()} dp", global.lensHeightDp, 0f..32f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(lensHeightDp = it)))
            }
            SliderRow("Lens Refraction Amount", "${global.lensAmountDp.roundToInt()} dp", global.lensAmountDp, 0f..30f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(lensAmountDp = it)))
            }
            SliderRow("Vibrancy Saturation", "${(global.vibrancy * 100).roundToInt()}%", global.vibrancy, 0f..1f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(vibrancy = it)))
            }
            SliderRow("Tint Fill Opacity", "${(global.tintOpacity * 100).roundToInt()}%", global.tintOpacity, 0f..1f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(tintOpacity = it)))
            }
            SliderRow("Specular Highlight", "${(global.highlightStrength * 100).roundToInt()}%", global.highlightStrength, 0f..1f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(highlightStrength = it)))
            }
            SliderRow("Shadow Ambient Depth", "${(global.shadowStrength * 100).roundToInt()}%", global.shadowStrength, 0f..1f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(shadowStrength = it)))
            }
            SliderRow("Chromatic Dispersion", "${(global.chromaticAberration * 100).roundToInt()}%", global.chromaticAberration, 0f..1f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(chromaticAberration = it)))
            }
            SliderRow("Corner Radius", "${global.cornerShapeDp.roundToInt()} dp", global.cornerShapeDp, 0f..32f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(cornerShapeDp = it)))
            }
            SliderRow("Touch Press Scale", "${((global.pressScale * 100).roundToInt()) / 100f}x", global.pressScale, 0.85f..1.0f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(pressScale = it)))
            }
            SliderRow("Spring Stiffness", "${global.springStiffness.roundToInt()}", global.springStiffness, 100f..1000f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(springStiffness = it)))
            }
            SliderRow("Spring Damping Ratio", "${((global.springDamping * 100).roundToInt()) / 100f}", global.springDamping, 0.40f..1.0f, accent) {
                onUpdateControls(controlsConfig.copy(global = global.copy(springDamping = it)))
            }
        }
    }
}

/**
 * 9 Collapsible Per-Item Override Accordion Cards per D-05, D-06, D-07, D-08, D-15.
 */
@Composable
private fun PerItemOverridesSection(
    controlsConfig: GlassControlsConfig,
    onUpdateControls: (GlassControlsConfig) -> Unit,
    expandedAccordions: Set<GlassComponentType>,
    onToggleAccordion: (GlassComponentType) -> Unit,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Per-Component Overrides (9 Categories)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Independent optics and motion tuning with immediate 'Use Global' inheritance (D-05, D-06)",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.60f)
        )

        GlassComponentType.values().forEach { type ->
            val compConfig = controlsConfig.getComponent(type)
            val isExpanded = expandedAccordions.contains(type)
            val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevronRotate")

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Accordion Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleAccordion(type) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                                tint = accent
                            )
                            Column {
                                Text(
                                    text = type.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (compConfig.useGlobal) "Inheriting master settings" else "Custom override active",
                                    fontSize = 10.sp,
                                    color = if (compConfig.useGlobal) Color.White.copy(alpha = 0.50f) else accent
                                )
                            }
                        }

                        // Immediate Use Global Switch (D-06)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Use Global", fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
                            LiquidGlassSwitch(
                                checked = compConfig.useGlobal,
                                onCheckedChange = { useGlobal ->
                                    onUpdateControls(
                                        controlsConfig.updateComponent(
                                            type,
                                            compConfig.copy(useGlobal = useGlobal)
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // Accordion Body
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (compConfig.useGlobal) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ContinuousRoundedRectangle(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Inheriting master settings. Turn off 'Use Global' above to configure independent optical parameters and motion physics for this control.",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            } else {
                                // List performance override toggle (D-08)
                                if (type.supportsListOverride) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Force Lighter Style in Lists", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Eliminates backdrop blur passes in scrolling lists for 120fps", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                                        }
                                        LiquidGlassSwitch(
                                            checked = compConfig.forceLighterInLists,
                                            onCheckedChange = {
                                                onUpdateControls(
                                                    controlsConfig.updateComponent(
                                                        type,
                                                        compConfig.copy(forceLighterInLists = it)
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Granular Component Sliders
                                SliderRow("Component Blur Radius", "${compConfig.blurDp.roundToInt()} dp", compConfig.blurDp, 0f..32f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(blurDp = it)))
                                }
                                SliderRow("Lens Height", "${compConfig.lensHeightDp.roundToInt()} dp", compConfig.lensHeightDp, 0f..32f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(lensHeightDp = it)))
                                }
                                SliderRow("Lens Amount", "${compConfig.lensAmountDp.roundToInt()} dp", compConfig.lensAmountDp, 0f..30f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(lensAmountDp = it)))
                                }
                                SliderRow("Highlight Strength", "${(compConfig.highlightStrength * 100).roundToInt()}%", compConfig.highlightStrength, 0f..1f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(highlightStrength = it)))
                                }
                                SliderRow("Press Scale", "${((compConfig.pressScale * 100).roundToInt()) / 100f}x", compConfig.pressScale, 0.85f..1.0f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(pressScale = it)))
                                }
                                SliderRow("Spring Stiffness", "${compConfig.springStiffness.roundToInt()}", compConfig.springStiffness, 100f..1000f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(springStiffness = it)))
                                }
                                SliderRow("Spring Damping", "${((compConfig.springDamping * 100).roundToInt()) / 100f}", compConfig.springDamping, 0.40f..1.0f, accent) {
                                    onUpdateControls(controlsConfig.updateComponent(type, compConfig.copy(springDamping = it)))
                                }

                                // Reset to Global Button (D-15)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            onUpdateControls(
                                                controlsConfig.updateComponent(
                                                    type,
                                                    ComponentGlassConfig(useGlobal = true)
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reset to Global", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable slider control with label, value indicator, and accent styling.
 */
@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            Text(valueLabel, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
        }
        LiquidGlassSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

/**
 * Palette Presets and Animated Background Styles Section.
 */
@Composable
private fun PaletteAndBackgroundSection(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    accent: Color
) {
    val palettes = listOf(
        Triple("Cyberpunk", 0xFF8B5CF6L, Color(0xFF8B5CF6)),
        Triple("Sapphire", 0xFF0284C7L, Color(0xFF0284C7)),
        Triple("Emerald", 0xFF10B981L, Color(0xFF10B981)),
        Triple("Sunset", 0xFFF97316L, Color(0xFFF97316)),
        Triple("Rose", 0xFFF43F5EL, Color(0xFFF43F5E)),
        Triple("Monolith", 0xFF94A3B8L, Color(0xFF94A3B8)),
        Triple("Amber", 0xFFF59E0BL, Color(0xFFF59E0B)),
        Triple("Amethyst", 0xFF6366F1L, Color(0xFF6366F1))
    )

    val shaders = listOf(
        "LIQUID_BLOBS" to "Liquid Blobs (Metaballs)",
        "AURORA" to "Aurora Waves",
        "MESH_GRADIENT" to "Mesh Gradient",
        "ALBUM_ART_BLUR" to "Album Art Blur",
        "SLOW_CAUSTICS" to "Slow Caustics",
        "STATIC" to "Frozen Glass"
    )

    // Accent Color Palette
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Accent Color Palette",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                palettes.forEach { (name, colorArgb, color) ->
                    val isSelected = (themeConfig.fixedAccentColorArgb and 0xFFFFFFFFL) == (colorArgb and 0xFFFFFFFFL)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            onThemeConfigChange(
                                themeConfig.copy(
                                    fixedAccentColorArgb = colorArgb,
                                    backgroundColorSource = "CUSTOM"
                                )
                            )
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, fontSize = 9.sp, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }

    // Background Animation Style
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Atmosphere Shader Style",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            shaders.forEach { (styleKey, styleName) ->
                val isSelected = themeConfig.backgroundAnimationStyle == styleKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = styleName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accent else Color.White,
                            fontSize = 12.sp
                        )
                    )
                    LiquidRadioButton(
                        selected = isSelected,
                        onClick = { onThemeConfigChange(themeConfig.copy(backgroundAnimationStyle = styleKey)) }
                    )
                }
            }
        }
    }
}
