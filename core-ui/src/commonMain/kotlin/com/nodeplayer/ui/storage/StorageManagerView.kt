@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nodeplayer.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.nodeplayer.model.storage.StorageUsageStats
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.components.LiquidSwitch
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Storage & cache partition view with multi-category breakdown, per-partition clearance,
 * and limit configuration.
 */
@Composable
fun StorageManagerView(
    usageStats: List<StorageUsageStats>,
    onClearCategory: (String) -> Unit,
    concurrentLimit: Int,
    onConcurrentLimitChanged: (Int) -> Unit,
    wifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    qualityPreset: String,
    onQualityPresetChanged: (String) -> Unit,
    preferredFormat: String,
    onPreferredFormatChanged: (String) -> Unit,
    onOpenDownloadQueue: () -> Unit,
    onBack: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null
) {
    val accent = LocalAccentColor.current
    var categoryToClear by remember { mutableStateOf<StorageUsageStats?>(null) }

    val totalBytes = usageStats.sumOf { it.sizeBytes }
    val totalFormatted = formatBytes(totalBytes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ContinuousRoundedRectangle(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Storage & Downloads",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LiquidButton(
                onClick = onOpenDownloadQueue,
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Downloading,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Queue", color = Color.White, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (headerContent != null) {
                item(key = "mixtape_header") {
                    headerContent()
                }
            }

            // Storage Summary Card
            item {
                StorageBreakdownCard(
                    totalFormatted = totalFormatted,
                    usageStats = usageStats,
                    accent = accent
                )
            }

            // Section: Storage Partitions
            item {
                Text(
                    text = "Storage Partitions",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            items(usageStats, key = { it.categoryId }) { stat ->
                PartitionCard(
                    stat = stat,
                    accent = accent,
                    onClear = { categoryToClear = stat }
                )
            }

            // Section: Download Preferences
            item {
                Text(
                    text = "Download Preferences",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                DownloadPreferencesCard(
                    concurrentLimit = concurrentLimit,
                    onConcurrentLimitChanged = onConcurrentLimitChanged,
                    wifiOnly = wifiOnly,
                    onWifiOnlyChanged = onWifiOnlyChanged,
                    qualityPreset = qualityPreset,
                    onQualityPresetChanged = onQualityPresetChanged,
                    preferredFormat = preferredFormat,
                    onPreferredFormatChanged = onPreferredFormatChanged,
                    accent = accent
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirmation Dialog
    categoryToClear?.let { cat ->
        Dialog(onDismissRequest = { categoryToClear = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = ContinuousRoundedRectangle(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Clear ${cat.displayName}?",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will free up ${cat.sizeMbFormatted} of storage. Cached data will be cleared.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(
                            onClick = { categoryToClear = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                        }
                        LiquidButton(
                            onClick = {
                                onClearCategory(cat.categoryId)
                                categoryToClear = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(
    totalFormatted: String,
    usageStats: List<StorageUsageStats>,
    accent: Color
) {
    val totalBytes = usageStats.sumOf { it.sizeBytes }.coerceAtLeast(1L)
    val categoryColors = listOf(
        Color(0xFF4CAF50), // Downloads
        Color(0xFF29B6F6), // Audio cache
        Color(0xFFAB47BC), // Image cache
        Color(0xFFFFA726), // Lyrics cache
        Color(0xFF26A69A), // Feed cache
        Color(0xFFEF5350)  // Taste cache
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousRoundedRectangle(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total App Storage",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = totalFormatted,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.PieChart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment horizontal bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    usageStats.forEachIndexed { index, stat ->
                        val fraction = (stat.sizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            val color = categoryColors[index % categoryColors.size]
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(fraction.coerceAtLeast(0.001f))
                                    .background(color)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                usageStats.forEachIndexed { index, stat ->
                    val color = categoryColors[index % categoryColors.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = "${stat.displayName}: ${stat.sizeMbFormatted}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionCard(
    stat: StorageUsageStats,
    accent: Color,
    onClear: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousRoundedRectangle(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stat.sizeMbFormatted,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (stat.clearable && stat.sizeBytes > 0) {
                LiquidButton(
                    onClick = onClear,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Clear",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            } else if (stat.clearable) {
                GlassBadge(text = "EMPTY", color = Color.White.copy(alpha = 0.3f))
            } else {
                GlassBadge(text = "MANAGED", color = Color(0xFF64B5F6))
            }
        }
    }
}

@Composable
private fun DownloadPreferencesCard(
    concurrentLimit: Int,
    onConcurrentLimitChanged: (Int) -> Unit,
    wifiOnly: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit,
    qualityPreset: String,
    onQualityPresetChanged: (String) -> Unit,
    preferredFormat: String,
    onPreferredFormatChanged: (String) -> Unit,
    accent: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousRoundedRectangle(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wi-Fi Only Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download over Wi-Fi Only",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pause downloads when connected to mobile data",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                LiquidSwitch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Concurrency Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Concurrent Workers",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Simultaneous active downloads (1 to 4)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepperButton(
                        icon = Icons.Rounded.Remove,
                        enabled = concurrentLimit > 1,
                        onClick = { onConcurrentLimitChanged(concurrentLimit - 1) }
                    )
                    Text(
                        text = "$concurrentLimit",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp)
                    )
                    StepperButton(
                        icon = Icons.Rounded.Add,
                        enabled = concurrentLimit < 4,
                        onClick = { onConcurrentLimitChanged(concurrentLimit + 1) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Quality Preset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default Quality",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Quality selection mode",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ASK", "HIGHEST", "BALANCED", "SAVER").forEach { q ->
                        val selected = (q == "SAVER" && qualityPreset == "DATA_SAVER") || q == qualityPreset
                        SegmentPill(
                            label = q,
                            selected = selected,
                            accent = accent,
                            onClick = {
                                onQualityPresetChanged(if (q == "SAVER") "DATA_SAVER" else q)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Preferred Format
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Container Format",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Opus, M4A or Auto",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("AUTO", "OPUS", "M4A").forEach { fmt ->
                        SegmentPill(
                            label = fmt,
                            selected = fmt == preferredFormat,
                            accent = accent,
                            onClick = { onPreferredFormatChanged(fmt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SegmentPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return if (bytes >= 1024L * 1024L * 1024L) {
        "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    } else {
        "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
