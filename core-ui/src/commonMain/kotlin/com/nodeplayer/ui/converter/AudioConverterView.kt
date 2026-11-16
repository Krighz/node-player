package com.nodeplayer.ui.converter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.library.converter.TranscodeBatchState
import com.nodeplayer.library.converter.TranscodeQueueManager
import com.nodeplayer.model.converter.*
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToLong

/**
 * Liquid glass FLAC to Opus batch audio converter interface.
 * Provides preset selection, custom encoder fine-tuning, size estimation,
 * atomic safety warnings, real-time multi-worker progress, and completion reporting.
 */
@Composable
fun AudioConverterView(
    queueManager: TranscodeQueueManager,
    selectedFiles: List<File>,
    onPickFiles: () -> Unit,
    destinationFolder: File?,
    onPickDestinationFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    val batchState by queueManager.state.collectAsState()

    var selectedPreset by remember { mutableStateOf(TranscodePreset.PRESET_128) }
    var isCustomExpanded by remember { mutableStateOf(false) }

    // Custom options
    var customBitrate by remember { mutableStateOf(160) }
    var customVbr by remember { mutableStateOf(true) }
    var customApplication by remember { mutableStateOf("audio") }
    var customCompressionLevel by remember { mutableStateOf(10) }

    // Safety and output mode
    var keepOriginal by remember { mutableStateOf(true) }
    var showDestructiveWarningDialog by remember { mutableStateOf(false) }
    var outputNextToOriginal by remember { mutableStateOf(false) }
    var writeR128Tags by remember { mutableStateOf(false) }
    var workersCount by remember { mutableStateOf(2) }

    // Completion / Cancellation summary dialog
    var showSummaryDialog by remember { mutableStateOf(false) }
    var summaryToDisplay by remember { mutableStateOf<TranscodeBatchSummary?>(null) }

    // Observe completion state
    LaunchedEffect(batchState) {
        when (val s = batchState) {
            is TranscodeBatchState.Completed -> {
                summaryToDisplay = s.summary
                showSummaryDialog = true
            }
            is TranscodeBatchState.Cancelled -> {
                showSummaryDialog = true
            }
            else -> {}
        }
    }

    val effectiveBitrate = if (selectedPreset == TranscodePreset.PRESET_CUSTOM) {
        customBitrate
    } else {
        selectedPreset.bitrateKbps
    }

    // Size estimation
    val totalInputBytes = remember(selectedFiles) {
        selectedFiles.sumOf { it.length() }
    }

    // Estimate based on typical FLAC compression ratio (assume 1000 kbps average FLAC input)
    val estimatedOutputBytes = remember(totalInputBytes, effectiveBitrate) {
        if (totalInputBytes <= 0L) 0L
        else {
            val estimatedDurationMs = (totalInputBytes * 8L) / 1000L // approx duration at ~1000 kbps
            ConverterMath.estimatedOutputBytes(estimatedDurationMs, effectiveBitrate)
        }
    }

    val projectedSavingsBytes = remember(totalInputBytes, estimatedOutputBytes) {
        if (totalInputBytes > 0L && estimatedOutputBytes > 0L) {
            (totalInputBytes - estimatedOutputBytes).coerceAtLeast(0L)
        } else 0L
    }

    val projectedSavingsPercent = remember(totalInputBytes, projectedSavingsBytes) {
        if (totalInputBytes > 0L) {
            ((projectedSavingsBytes.toDouble() / totalInputBytes.toDouble()) * 100.0).roundToLong()
        } else 0L
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ContinuousRoundedRectangle(19.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "FLAC to Opus Converter",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Batch transcode lossless files with ReplayGain to R128 injection",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }
            }

            GlassBadge(
                text = "Opus 1.4 / FFmpeg",
                color = accent
            )
        }

        // 1. Source Selection Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Source Files (${selectedFiles.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    LiquidButton(
                        onClick = onPickFiles,
                        enabled = batchState !is TranscodeBatchState.Running
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedFiles.isEmpty()) "Select Files / Folder" else "Change Source", fontSize = 12.sp)
                    }
                }

                if (selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total input size: ${ConverterMath.formatDiskSize(totalInputBytes)} across ${selectedFiles.size} FLAC tracks",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No FLAC source files selected. Pick a music folder or specific tracks to begin.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // 2. Transcode Preset Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Quality Preset",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetPill(
                        title = "192 kbps",
                        subtitle = "Audiophile",
                        isSelected = selectedPreset == TranscodePreset.PRESET_192,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPreset = TranscodePreset.PRESET_192
                            isCustomExpanded = false
                        }
                    )
                    PresetPill(
                        title = "128 kbps",
                        subtitle = "Balanced",
                        isSelected = selectedPreset == TranscodePreset.PRESET_128,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPreset = TranscodePreset.PRESET_128
                            isCustomExpanded = false
                        }
                    )
                    PresetPill(
                        title = "96 kbps",
                        subtitle = "Compact",
                        isSelected = selectedPreset == TranscodePreset.PRESET_96,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPreset = TranscodePreset.PRESET_96
                            isCustomExpanded = false
                        }
                    )
                    PresetPill(
                        title = "Custom",
                        subtitle = "${customBitrate}k",
                        isSelected = selectedPreset == TranscodePreset.PRESET_CUSTOM,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedPreset = TranscodePreset.PRESET_CUSTOM
                            isCustomExpanded = true
                        }
                    )
                }

                AnimatedVisibility(visible = isCustomExpanded || selectedPreset == TranscodePreset.PRESET_CUSTOM) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Custom Bitrate", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${customBitrate} kbps", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        LiquidSlider(
                            value = customBitrate.toFloat(),
                            onValueChange = { customBitrate = it.toInt() },
                            valueRange = 32f..320f
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Variable Bitrate (VBR)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Dynamic bitrate based on acoustic complexity", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            LiquidToggle(checked = customVbr, onCheckedChange = { customVbr = it })
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Encoder Tuning", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Optimized for full-band high fidelity audio", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                            Text(customApplication.uppercase(), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Compression Level (Complexity)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("$customCompressionLevel / 10", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        LiquidSlider(
                            value = customCompressionLevel.toFloat(),
                            onValueChange = { customCompressionLevel = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9
                        )
                    }
                }
            }
        }

        // 3. Storage Savings Estimation Card
        if (selectedFiles.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PieChart,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Disk Space Savings Projection",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Input Size", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(ConverterMath.formatDiskSize(totalInputBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Estimated Output", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Text("~${ConverterMath.formatDiskSize(estimatedOutputBytes)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                        Column {
                            Text("Projected Savings", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                "Save ~$projectedSavingsPercent% (${ConverterMath.formatDiskSize(projectedSavingsBytes)})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }

        // 4. Output Destination & Safety Options Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Destination & Safety Options",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Output Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save Next to Original FLAC", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Write .opus into the same folder as the source file", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                    LiquidToggle(
                        checked = outputNextToOriginal,
                        onCheckedChange = { outputNextToOriginal = it }
                    )
                }

                if (!outputNextToOriginal) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dedicated Output Folder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                destinationFolder?.absolutePath ?: "Default: ~/Music/Converted",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LiquidButton(
                            onClick = onPickDestinationFolder,
                            lighterStyle = true
                        ) {
                            Text("Browse", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Keep Original vs Replace Original Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Original FLAC Files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (keepOriginal) "Both FLAC and Opus files will be kept on disk"
                            else "Atomic replacement: Source FLAC will be deleted ONLY after output verifies",
                            fontSize = 11.sp,
                            color = if (keepOriginal) Color.White.copy(alpha = 0.55f) else Color(0xFFFFB74D)
                        )
                    }
                    LiquidToggle(
                        checked = keepOriginal,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                // Trigger destructive action warning confirmation
                                showDestructiveWarningDialog = true
                            } else {
                                keepOriginal = true
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // R128 Tag Injection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Write R128 Loudness Header", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Inject R128_TRACK_GAIN calculated from ReplayGain tag", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                    LiquidToggle(
                        checked = writeR128Tags,
                        onCheckedChange = { writeR128Tags = it }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                // Workers Count Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Parallel Workers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("$workersCount threads", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                LiquidSlider(
                    value = workersCount.toFloat(),
                    onValueChange = { workersCount = it.toInt() },
                    valueRange = 1f..4f,
                    steps = 2
                )
            }
        }

        // 5. Active Batch Execution & Progress
        val currentState = batchState
        if (currentState is TranscodeBatchState.Running) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Converting (${currentState.completedFiles + 1} of ${currentState.totalFiles})...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        LiquidButton(
                            onClick = { queueManager.cancel() },
                            lighterStyle = true
                        ) {
                            Text("Cancel", fontSize = 12.sp, color = Color(0xFFFF5252))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { currentState.overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentState.currentFileName.ifBlank { "Processing..." },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Track progress: ${(currentState.currentFileProgress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Overall: ${(currentState.overallProgress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }
            }
        }

        // 6. Action Button
        val canStart = selectedFiles.isNotEmpty() && currentState !is TranscodeBatchState.Running
        LiquidButton(
            onClick = {
                val effectivePreset = if (selectedPreset == TranscodePreset.PRESET_CUSTOM) {
                    TranscodePreset(
                        id = "CUSTOM",
                        name = "Custom",
                        bitrateKbps = customBitrate,
                        description = "Custom ${customBitrate}k"
                    )
                } else {
                    selectedPreset
                }

                val jobs = queueManager.prepareJobs(
                    inputFiles = selectedFiles,
                    targetDir = if (outputNextToOriginal) null else destinationFolder,
                    preset = effectivePreset,
                    overwriteExisting = false,
                    vbr = customVbr,
                    application = customApplication,
                    compressionLevel = customCompressionLevel
                )

                queueManager.startBatch(
                    jobs = jobs,
                    keepOriginal = keepOriginal,
                    workersCount = workersCount
                )
            },
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Rounded.Transform, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentState is TranscodeBatchState.Running) "Conversion in Progress..." else "Start Batch Conversion (${selectedFiles.size} tracks)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Confirmation dialog for Destructive Action: Replace Originals
    if (showDestructiveWarningDialog) {
        Dialog(onDismissRequest = { showDestructiveWarningDialog = false }) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Replace Original FLAC Files?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Warning: This is a destructive operation. Your original lossless FLAC files will be permanently deleted upon verified transcode to Opus.\n\n" +
                                "Node Player uses an atomic safety gate: if conversion or verification fails, the original FLAC is 100% preserved.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(
                            onClick = {
                                keepOriginal = true
                                showDestructiveWarningDialog = false
                            },
                            lighterStyle = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep Originals", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                keepOriginal = false
                                showDestructiveWarningDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete Originals", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Batch Completion / Cancellation Summary Dialog
    if (showSummaryDialog && summaryToDisplay != null) {
        val summary = summaryToDisplay!!
        Dialog(onDismissRequest = { showSummaryDialog = false }) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (summary.failed == 0) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                else Color(0xFFFF9800).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (summary.failed == 0) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (summary.failed == 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (batchState is TranscodeBatchState.Cancelled) "Batch Conversion Cancelled" else "Batch Conversion Completed",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryRow("Succeeded", "${summary.succeeded} tracks", Color(0xFF4CAF50))
                        SummaryRow("Failed", "${summary.failed} tracks", if (summary.failed > 0) Color(0xFFFF5252) else Color.White.copy(alpha = 0.6f))
                        val skipped = (summary.totalFiles - summary.succeeded - summary.failed).coerceAtLeast(0)
                        SummaryRow("Skipped", "$skipped tracks", Color.White.copy(alpha = 0.6f))
                        SummaryRow("Input Total", ConverterMath.formatDiskSize(summary.totalInputBytes), Color.White)
                        SummaryRow("Output Total", ConverterMath.formatDiskSize(summary.totalOutputBytes), accent)
                        SummaryRow("Space Saved", ConverterMath.formatDiskSize(summary.bytesSaved), Color(0xFF4CAF50))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    LiquidButton(
                        onClick = { showSummaryDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPill(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) accent.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) accent else Color.White
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
