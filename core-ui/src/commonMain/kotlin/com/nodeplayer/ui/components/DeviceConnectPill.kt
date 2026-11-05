package com.nodeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.theme.LocalAccentColor

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

@Composable
fun DeviceConnectPill(
    status: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED,
    connectedDeviceCount: Int = 0,
    pairedDevices: List<DiscoveredPeerInfo> = emptyList(),
    allowCastingToThisDevice: Boolean = true,
    onToggleAllowCasting: (Boolean) -> Unit = {},
    onPairNewDevice: () -> Unit = {},
    onCastToDevice: (DiscoveredPeerInfo) -> Unit = {},
    onOpenSyncCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var isMenuOpen by remember { mutableStateOf(false) }

    val dotColor = when (status) {
        DeviceConnectStatus.CONNECTED -> Color(0xFF00F0FF) // Cyan glow
        DeviceConnectStatus.DISCOVERING -> Color(0xFFF59E0B) // Amber pulse
        DeviceConnectStatus.DISCONNECTED -> Color.White.copy(alpha = 0.3f)
    }

    Box(modifier = modifier) {
        // Top Bar Pill Button
        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFF161922).copy(alpha = 0.75f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(19.dp)
                )
                .clickable { isMenuOpen = !isMenuOpen }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = "Device Connect",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(17.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Device Connect",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Glowing status indicator dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        // Dropdown Panel
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .padding(top = 46.dp)
                    .width(320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF13161F).copy(alpha = 0.96f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
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
                            .clip(RoundedCornerShape(10.dp))
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
                                text = "This Device (Local Host)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Listening on port 47200 (mTLS)",
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

                    Divider(
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
                                    .clip(RoundedCornerShape(8.dp))
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
