package com.nodeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.pressScale

enum class DeviceConnectStatus {
    DISCONNECTED,
    DISCOVERING,
    CONNECTED
}

data class DiscoveredPeerInfo(
    val id: String,
    val name: String,
    val platform: String, // "Android", "Windows"
    val isConnected: Boolean,
    val canReceiveCast: Boolean
)

fun cleanAudioDeviceName(rawName: String): String {
    if (rawName.isBlank()) return "Default Audio Output"
    var name = rawName
    val prefixesToStrip = listOf(
        "Windows Audio - ",
        "WASAPI - ",
        "WASAPI Exclusive - ",
        "WASAPI Shared - ",
        "WASAPI Exclusive Output",
        "WASAPI Shared Output",
        "[DirectSound] ",
        "DirectSound - ",
        "32-bit Float - ",
        "16-bit PCM - ",
        "24-bit PCM - ",
        "Primary Sound Driver - "
    )
    for (prefix in prefixesToStrip) {
        if (name.startsWith(prefix, ignoreCase = true)) {
            name = name.substring(prefix.length).trim()
        }
    }
    // Also remove generic trailing tags or driver identifiers if empty
    return name.ifBlank { "System Audio Output" }
}

@Composable
fun DeviceConnectPill(
    status: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED,
    connectedDeviceCount: Int = 0,
    pairedDevices: List<DiscoveredPeerInfo> = emptyList(),
    allowCastingToThisDevice: Boolean = true,
    compact: Boolean = false,
    outputDeviceName: String = "",
    onClick: (() -> Unit)? = null,
    onToggleAllowCasting: (Boolean) -> Unit = {},
    onPairNewDevice: () -> Unit = {},
    onCastToDevice: (DiscoveredPeerInfo) -> Unit = {},
    onOpenSyncCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val cleanedDeviceName = remember(outputDeviceName) {
        if (outputDeviceName.isNotBlank()) cleanAudioDeviceName(outputDeviceName) else ""
    }
    var isMenuOpen by remember { mutableStateOf(false) }

    // Pulsing animation for discovering state
    val infiniteTransition = rememberInfiniteTransition(label = "StatusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val dotColor = when (status) {
        DeviceConnectStatus.CONNECTED -> Color(0xFF00F0FF) // Cyan glow
        DeviceConnectStatus.DISCOVERING -> Color(0xFFF59E0B).copy(alpha = pulseAlpha) // Amber pulse
        DeviceConnectStatus.DISCONNECTED -> Color.White.copy(alpha = 0.35f)
    }

    val glowColor = when (status) {
        DeviceConnectStatus.CONNECTED -> Color(0xFF00F0FF).copy(alpha = 0.35f)
        DeviceConnectStatus.DISCOVERING -> Color(0xFFF59E0B).copy(alpha = 0.25f * pulseAlpha)
        DeviceConnectStatus.DISCONNECTED -> Color.Transparent
    }

    val pillShape = remember { ContinuousRoundedRectangle(19.dp) }
    val dropdownShape = remember { ContinuousRoundedRectangle(18.dp) }

    Box(modifier = modifier) {
        // Top Bar Pill Button with Convx-style liquid glass finish & spring press scale
        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(pillShape)
                .background(Color(0xFF131722).copy(alpha = 0.78f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = pillShape
                )
                .pressScale(targetScale = 0.95f) {
                    if (onClick != null) {
                        onClick()
                    } else {
                        isMenuOpen = !isMenuOpen
                    }
                }
                .padding(horizontal = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (status == DeviceConnectStatus.CONNECTED) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                contentDescription = "Device Connect",
                tint = if (status == DeviceConnectStatus.CONNECTED) accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp)
            )

            if (!compact) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (cleanedDeviceName.isNotBlank()) cleanedDeviceName else "Device Connect",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            // Glowing status indicator dot with outer glow halo
            Box(
                modifier = Modifier
                    .size(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(glowColor)
                )
                // Center crisp dot
                Box(
                    modifier = Modifier
                        .size(6.5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            // Optional connected count badge
            if (connectedDeviceCount > 0 && !compact) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(ContinuousRoundedRectangle(6.dp))
                        .background(accent.copy(alpha = 0.22f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$connectedDeviceCount",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }

        // Dropdown Panel (Desktop inline management)
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.94f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.94f)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 46.dp)
                    .width(320.dp)
                    .clip(dropdownShape)
                    .background(Color(0xFF13161F).copy(alpha = 0.96f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        dropdownShape
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connected Devices",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = { isMenuOpen = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Local node status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ContinuousRoundedRectangle(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (cleanedDeviceName.isNotBlank()) cleanedDeviceName else "This Device (Local Host)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (cleanedDeviceName.isNotBlank()) "Hardware Output • Port 47200" else "Listening on port 47200 (mTLS)",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Allow casting to this device toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allow Casting to this Device",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Switch(
                            checked = allowCastingToThisDevice,
                            onCheckedChange = onToggleAllowCasting,
                            colors = SwitchDefaults.colors(checkedThumbColor = accent)
                        )
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // Paired Devices List
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired devices found nearby.",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        pairedDevices.forEach { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ContinuousRoundedRectangle(8.dp))
                                    .clickable { onCastToDevice(peer) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (peer.platform == "Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop,
                                        contentDescription = null,
                                        tint = if (peer.isConnected) Color(0xFF00F0FF) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = peer.name,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "Cast",
                                    fontSize = 11.sp,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons: Pair New Device & Open Sync Center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isMenuOpen = false
                                onPairNewDevice()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = ContinuousRoundedRectangle(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair", fontSize = 11.5.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                isMenuOpen = false
                                onOpenSyncCenter()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = ContinuousRoundedRectangle(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.25f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Center", fontSize = 11.5.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
