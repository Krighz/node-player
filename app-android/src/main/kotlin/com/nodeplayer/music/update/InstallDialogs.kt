package com.nodeplayer.music.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.music.ui.theme.LocalAccentColor

@Composable
fun InstallUnknownAppsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccentColor.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        },
        title = {
            Text("Install permission required", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "To install updates directly from GitHub Releases, Android requires permission to install unknown apps. Tap 'Open Settings' to allow 'Node Player' to install apps, then return here to proceed.\n\n" +
                        "Note: On Android 12+, updates may install without confirmation if allowed, but Android may still present a system confirmation depending on your device settings.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Open Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.65f))
            }
        },
        containerColor = Color(0xFF1E212B),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun PlaybackStopConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalAccentColor.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(28.dp))
        },
        title = {
            Text("Playback will stop", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Installing the update will stop audio playback and restart Node Player. Would you like to proceed?",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Install & restart", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.65f))
            }
        },
        containerColor = Color(0xFF1E212B),
        shape = RoundedCornerShape(22.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    versionName: String,
    changelogText: String,
    onDismiss: () -> Unit
) {
    val accent = LocalAccentColor.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181A22),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "What's new in v$versionName",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Node Player successfully updated",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 350.dp)
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = changelogText.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Got it", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
