package com.nodeplayer.ui.downloader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.nodeplayer.model.storage.DownloadState
import com.nodeplayer.model.storage.DownloadTask
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.components.GlassBadge
import com.nodeplayer.ui.components.GlassCard
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Liquid glass bottom sheet showing live download queue state, progress bars, speed,
 * and per-task controls.
 */
@Composable
fun DownloadQueueSheet(
    tasks: List<DownloadTask>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccentColor.current
    val activeCount = tasks.count { !it.isTerminal }
    val completedCount = tasks.count { it.state == DownloadState.COMPLETED }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = ContinuousRoundedRectangle(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = "Downloads",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Download Queue",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(ContinuousRoundedRectangle(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats and Bulk Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$activeCount active • $completedCount completed",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (activeCount > 0) {
                            SmallActionButton(
                                icon = Icons.Rounded.Pause,
                                label = "Pause",
                                onClick = onPauseAll
                            )
                            SmallActionButton(
                                icon = Icons.Rounded.PlayArrow,
                                label = "Resume",
                                onClick = onResumeAll
                            )
                        }
                        if (completedCount > 0) {
                            SmallActionButton(
                                icon = Icons.Rounded.ClearAll,
                                label = "Clear",
                                onClick = onClearCompleted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Downloading,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Queue is empty",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            DownloadTaskRow(
                                task = task,
                                accent = accent,
                                onPause = { onPauseTask(task.id) },
                                onResume = { onResumeTask(task.id) },
                                onCancel = { onCancelTask(task.id) },
                                onRetry = { onRetryTask(task.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LiquidButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTask,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val stateBadgeColor = when (task.state) {
        DownloadState.COMPLETED -> Color(0xFF4CAF50)
        DownloadState.DOWNLOADING -> accent
        DownloadState.TAGGING -> Color(0xFFFFB74D)
        DownloadState.RESOLVING -> Color(0xFF64B5F6)
        DownloadState.PAUSED_USER, DownloadState.PAUSED_NETWORK -> Color(0xFFFF9800)
        DownloadState.FAILED -> Color(0xFFE57373)
        DownloadState.QUEUED -> Color.White.copy(alpha = 0.4f)
    }

    val stateText = when (task.state) {
        DownloadState.COMPLETED -> "DONE"
        DownloadState.DOWNLOADING -> "DOWNLOADING"
        DownloadState.TAGGING -> "TAGGING"
        DownloadState.RESOLVING -> "RESOLVING"
        DownloadState.PAUSED_USER -> "PAUSED"
        DownloadState.PAUSED_NETWORK -> "WAITING WI-FI"
        DownloadState.FAILED -> "FAILED"
        DownloadState.QUEUED -> "QUEUED"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousRoundedRectangle(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.track.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.track.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassBadge(
                        text = stateText,
                        color = stateBadgeColor
                    )

                    when (task.state) {
                        DownloadState.DOWNLOADING, DownloadState.RESOLVING, DownloadState.QUEUED -> {
                            TaskIconButton(icon = Icons.Rounded.Pause, onClick = onPause)
                            TaskIconButton(icon = Icons.Rounded.Close, onClick = onCancel)
                        }
                        DownloadState.PAUSED_USER, DownloadState.PAUSED_NETWORK -> {
                            TaskIconButton(icon = Icons.Rounded.PlayArrow, onClick = onResume)
                            TaskIconButton(icon = Icons.Rounded.Close, onClick = onCancel)
                        }
                        DownloadState.FAILED -> {
                            TaskIconButton(icon = Icons.Rounded.Refresh, onClick = onRetry)
                            TaskIconButton(icon = Icons.Rounded.Close, onClick = onCancel)
                        }
                        DownloadState.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DownloadState.TAGGING -> {}
                    }
                }
            }

            if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.PAUSED_USER || task.state == DownloadState.PAUSED_NETWORK) {
                Spacer(modifier = Modifier.height(8.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(task.progressFraction)
                            .fillMaxHeight()
                            .background(accent)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val downloadedMb = "%.1f".format(task.downloadedBytes / (1024.0 * 1024.0))
                    val totalMb = if (task.totalBytes > 0) "%.1f MB".format(task.totalBytes / (1024.0 * 1024.0)) else "--"
                    Text(
                        text = "$downloadedMb / $totalMb",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )

                    if (task.speedBytesPerSec > 0) {
                        val speedFormatted = formatSpeed(task.speedBytesPerSec)
                        Text(
                            text = speedFormatted,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val errMsg = task.errorMessage
            if (task.state == DownloadState.FAILED && !errMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errMsg,
                    color = Color(0xFFE57373),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TaskIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return if (bytesPerSec >= 1024 * 1024) {
        "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
    } else {
        "%.0f KB/s".format(bytesPerSec / 1024.0)
    }
}
