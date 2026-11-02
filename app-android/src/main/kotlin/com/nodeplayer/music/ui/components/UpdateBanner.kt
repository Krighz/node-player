package com.nodeplayer.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.update.AppUpdateInfo
import com.nodeplayer.music.update.DownloadStatus
import dev.chrisbanes.haze.HazeState
import java.util.Locale

@Composable
fun UpdateBanner(
    updateInfo: AppUpdateInfo,
    downloadStatus: DownloadStatus,
    hazeState: HazeState?,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        hazeState = hazeState,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = "Update",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Update available: v${updateInfo.versionName}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    if (updateInfo.assetSize > 0L) {
                        val mb = updateInfo.assetSize / (1024.0 * 1024.0)
                        Text(
                            text = String.format(Locale.US, "Release asset: %.1f MB", mb),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.65f))
                        )
                    }
                }
            }

            // Release Notes
            if (updateInfo.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = updateInfo.releaseNotes.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (updateInfo.releaseNotes.lines().size > 3 || updateInfo.releaseNotes.length > 140) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Show less" else "Read release notes",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // State-Specific Action UI
            when (downloadStatus) {
                is DownloadStatus.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            TextButton(onClick = onLater) {
                                Text("Later", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = onSkip) {
                                Text("Skip this version", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = onUpdateNow,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is DownloadStatus.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val percent = (downloadStatus.progress * 100f).toInt()
                            val dlMb = downloadStatus.downloadedBytes / (1024.0 * 1024.0)
                            val totalMb = downloadStatus.totalBytes / (1024.0 * 1024.0)
                            Text(
                                text = if (downloadStatus.totalBytes > 0) {
                                    String.format(Locale.US, "Downloading... %d%% (%.1f / %.1f MB)", percent, dlMb, totalMb)
                                } else {
                                    String.format(Locale.US, "Downloading... %.1f MB", dlMb)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                            )

                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Cancel", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        if (downloadStatus.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { downloadStatus.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = accent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = accent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                    }
                }

                is DownloadStatus.Verifying -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Verifying SHA-256 checksum against release...",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                        )
                    }
                }

                is DownloadStatus.ReadyToInstall -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verified & ready to install",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Button(
                            onClick = onInstall,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is DownloadStatus.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = downloadStatus.message,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFCA5A5))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onLater) {
                                Text("Dismiss", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
