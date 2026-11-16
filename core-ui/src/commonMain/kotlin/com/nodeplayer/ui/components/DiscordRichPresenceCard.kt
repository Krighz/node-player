package com.nodeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.presence.PresenceStatus
import com.nodeplayer.ui.theme.LiquidColors
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Liquid-glass settings card for configuring Discord Rich Presence on desktop.
 */
@Composable
fun DiscordRichPresenceCard(
    status: PresenceStatus,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    isIncognito: Boolean,
    onToggleIncognito: (Boolean) -> Unit,
    showCover: Boolean,
    onToggleShowCover: (Boolean) -> Unit,
    showAlbum: Boolean,
    onToggleShowAlbum: (Boolean) -> Unit,
    showWhilePaused: Boolean,
    onToggleShowWhilePaused: (Boolean) -> Unit,
    appId: String,
    onAppIdChange: (String) -> Unit,
    accent: Color = LocalAccentColor.current,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Icon, Title & Master Enable Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Discord Rich Presence",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                LiquidToggle(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Badge Row
            val (statusText, statusColor) = when {
                !enabled -> "Disabled" to Color.White.copy(alpha = 0.5f)
                isIncognito -> "Incognito Active (Stealth)" to Color(0xFF64B5F6)
                status == PresenceStatus.APP_ID_REQUIRED -> "Application ID Required" to Color(0xFFFFB74D)
                status == PresenceStatus.CONNECTING -> "Connecting to Discord..." to Color(0xFFFFD54F)
                status == PresenceStatus.CONNECTED -> "Connected" to Color(0xFF81C784)
                status == PresenceStatus.NOT_RUNNING -> "Discord Not Running" to Color(0xFFFF8A65)
                status == PresenceStatus.ERROR -> "IPC Pipe Error" to Color(0xFFE57373)
                else -> "Disabled" to Color.White.copy(alpha = 0.5f)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBadge(text = statusText, color = statusColor)
            }

            AnimatedVisibility(visible = enabled) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Application ID Input
                    Text(
                        text = "APPLICATION CLIENT ID",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    BasicTextField(
                        value = appId,
                        onValueChange = onAppIdChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (appId.isEmpty()) {
                                    Text(
                                        text = "Enter Discord Application ID (Developer Portal)",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 12.5.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Incognito Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Incognito Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Immediately clear presence and suspend rich updates",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = isIncognito,
                            onCheckedChange = onToggleIncognito
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Show Cover Artwork Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Artwork", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Display album art in Discord profile card",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = showCover,
                            onCheckedChange = onToggleShowCover
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Show Album Name Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Album Name", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Display album alongside artist name",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = showAlbum,
                            onCheckedChange = onToggleShowAlbum
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Show While Paused Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show While Paused", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Retain status card when playback is paused instead of clearing",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                        LiquidToggle(
                            checked = showWhilePaused,
                            onCheckedChange = onToggleShowWhilePaused
                        )
                    }
                }
            }
        }
    }
}
