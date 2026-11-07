package com.nodeplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.network.discovery.DiscoveredNodePeer
import com.nodeplayer.network.pairing.PairingSession
import com.nodeplayer.network.pairing.QrCodeGenerator
import com.nodeplayer.network.service.ConnectedPeer
import com.nodeplayer.network.service.NodeConnectConfig
import com.nodeplayer.network.transport.SecurityLevel
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor

@Composable
fun NodeConnectPairingDialog(
    onDismissRequest: () -> Unit,
    activeSession: PairingSession?,
    localIp: String,
    localDeviceId: String,
    localDeviceName: String,
    localPort: Int = 54820,
    localCandidateIps: List<String> = listOf(localIp),
    discoveredPeers: List<DiscoveredNodePeer> = emptyList(),
    onStartSession: () -> Unit = {},
    onCancelSession: () -> Unit = {},
    onPairWithHost: (host: String, port: Int, pin: String) -> Unit = { _, _, _ -> },
    onConnectToPeerDirectly: ((DiscoveredNodePeer) -> Unit)? = null,
    onConnectToAddress: ((host: String, port: Int) -> Unit)? = null,
    onScanQrClicked: (() -> Unit)? = null,
    onScanNetworkClicked: (() -> Unit)? = null,
    onProbeGatewayClicked: (() -> Unit)? = null,
    onTestPingClicked: ((host: String, port: Int, callback: (Result<Long>) -> Unit) -> Unit)? = null,
    onCopyLogClicked: (() -> Unit)? = null,
    onExportLogClicked: (() -> Unit)? = null,
    onStartUsbLink: (() -> Unit)? = null,
    config: NodeConnectConfig = NodeConnectConfig(),
    onConfigChange: ((NodeConnectConfig) -> Unit)? = null,
    connectedPeers: List<ConnectedPeer> = emptyList(),
    onSendRemotePlayPause: ((String) -> Unit)? = null,
    onSendRemotePrevious: ((String) -> Unit)? = null,
    onSendRemoteNext: ((String) -> Unit)? = null,
    onDisconnectPeer: ((String) -> Unit)? = null,
    onTriggerAudioCast: ((String) -> Unit)? = null,
    onTriggerHandoff: ((String) -> Unit)? = null,
    isPairingInProgress: Boolean = false,
    pairingStatusMessage: String? = null
) {
    val accent = LocalAccentColor.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Devices, 1: Show QR, 2: Doctor, 3: Settings

    // Client form state
    var targetHost by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf(localPort.toString()) }
    var enteredPin by remember { mutableStateOf("") }

    // Doctor test tool state
    var doctorHost by remember { mutableStateOf(localIp) }
    var doctorPort by remember { mutableStateOf(localPort.toString()) }
    var doctorResultText by remember { mutableStateOf<String?>(null) }
    var doctorTesting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        val dialogShape = remember { ContinuousRoundedRectangle(24.dp) }
        Surface(
            modifier = Modifier
                .width(460.dp)
                .wrapContentHeight()
                .clip(dialogShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = dialogShape
                ),
            color = Color(0xFF141721).copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CastConnected,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Node Connect",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Simple Mode • Local Zero-Cloud Link",
                                fontSize = 11.sp,
                                color = Color(0xFF00E5FF).copy(alpha = 0.85f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Switcher: 0: Connect, 1: Show QR, 2: Doctor, 3: Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(3.dp)
                ) {
                    listOf("Devices", "My QR", "Doctor", "Settings").forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedTab) {
                    0 -> {
                        // TAB 0: DEVICES & CONNECT
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Quick Action Buttons Row: Scan QR & Scan Network
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onScanQrClicked != null) {
                                    Button(
                                        onClick = onScanQrClicked,
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan QR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }

                                Button(
                                    onClick = { onScanNetworkClicked?.invoke() },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Network", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }

                            if (config.securityLevel == SecurityLevel.SIMPLE) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Simple Mode: Opportunistic local link. Use Strict Mode on public Wi-Fi.",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFDE68A),
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (connectedPeers.isNotEmpty()) {
                                Text(
                                    text = "ACTIVE CONNECTION (${connectedPeers.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    connectedPeers.forEach { peer ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF10B981).copy(alpha = 0.08f))
                                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            if (peer.platform == "Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop,
                                                            contentDescription = null,
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = peer.deviceName,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "[CONNECTED]",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF10B981)
                                                            )
                                                        }
                                                        Text(
                                                            text = "${peer.hostAddress}:${peer.port} • ${peer.latencyMs}ms",
                                                            fontSize = 10.sp,
                                                            color = Color.White.copy(alpha = 0.6f),
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }

                                                Button(
                                                    onClick = { onDisconnectPeer?.invoke(peer.deviceId) },
                                                    modifier = Modifier.height(28.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) {
                                                    Text("Disconnect", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Transport Controls (F1 Remote Control)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { onSendRemotePrevious?.invoke(peer.deviceId) },
                                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                                                ) {
                                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(18.dp))
                                                }

                                                Button(
                                                    onClick = { onSendRemotePlayPause?.invoke(peer.deviceId) },
                                                    modifier = Modifier.weight(1f).height(34.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play / Pause", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                }

                                                IconButton(
                                                    onClick = { onSendRemoteNext?.invoke(peer.deviceId) },
                                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                                                ) {
                                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Audio Cast (F4) & Handoff (F5)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = { onTriggerAudioCast?.invoke(peer.deviceId) },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Cast, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Cast Audio", fontSize = 10.sp, color = Color.White)
                                                }

                                                Button(
                                                    onClick = { onTriggerHandoff?.invoke(peer.deviceId) },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Handoff", fontSize = 10.sp, color = Color.White)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "ENABLED FEATURES FOR THIS LINK",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    letterSpacing = 0.8.sp
                                                )
                                                Text(
                                                    text = "More Settings →",
                                                    fontSize = 10.sp,
                                                    color = accent,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable { selectedTab = 3 }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                QuickFeatureChip(
                                                    label = "Audio Cast",
                                                    icon = Icons.Rounded.Cast,
                                                    enabled = config.audioCastEnabled,
                                                    onClick = { onConfigChange?.invoke(config.copy(audioCastEnabled = !config.audioCastEnabled)) },
                                                    accent = accent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                QuickFeatureChip(
                                                    label = "Remote Control",
                                                    icon = Icons.Rounded.PlayCircle,
                                                    enabled = config.remoteControlEnabled,
                                                    onClick = { onConfigChange?.invoke(config.copy(remoteControlEnabled = !config.remoteControlEnabled)) },
                                                    accent = accent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                QuickFeatureChip(
                                                    label = "Library Share",
                                                    icon = Icons.Rounded.LibraryMusic,
                                                    enabled = config.shareLibraryEnabled,
                                                    onClick = { onConfigChange?.invoke(config.copy(shareLibraryEnabled = !config.shareLibraryEnabled)) },
                                                    accent = accent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                QuickFeatureChip(
                                                    label = "Handoff",
                                                    icon = Icons.Rounded.SwapHoriz,
                                                    enabled = config.handoffEnabled,
                                                    onClick = { onConfigChange?.invoke(config.copy(handoffEnabled = !config.handoffEnabled)) },
                                                    accent = accent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                QuickFeatureChip(
                                                    label = "Mute Local Audio",
                                                    icon = Icons.Rounded.VolumeOff,
                                                    enabled = config.muteLocalOutputDuringCast,
                                                    onClick = { onConfigChange?.invoke(config.copy(muteLocalOutputDuringCast = !config.muteLocalOutputDuringCast)) },
                                                    accent = accent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            val unconnectedDiscovered = remember(discoveredPeers, connectedPeers) {
                                discoveredPeers.filter { disc ->
                                    connectedPeers.none { conn -> conn.deviceId == disc.deviceId || conn.hostAddress == disc.hostAddress }
                                }
                            }

                            Text(
                                text = "DISCOVERED PEERS (${unconnectedDiscovered.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (unconnectedDiscovered.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = accent
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Broadcasting & searching local Wi-Fi / Hotspot...",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    unconnectedDiscovered.forEach { peer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    if (peer.platform == "Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = peer.deviceName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "[${peer.discoveryMethod}]",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF00E5FF),
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    Text(
                                                        text = "${peer.hostAddress}:${peer.port}",
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    if (onConnectToPeerDirectly != null) {
                                                        onConnectToPeerDirectly(peer)
                                                    } else {
                                                        targetHost = peer.hostAddress
                                                        targetPort = peer.port.toString()
                                                        onConnectToAddress?.invoke(peer.hostAddress, peer.port)
                                                    }
                                                },
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Manual Connect by IP & Port
                            Text(
                                text = "CONNECT MANUALLY BY IP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = targetHost,
                                    onValueChange = { targetHost = it },
                                    label = { Text("IP Address", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. 10.222.143.172", fontSize = 10.sp) },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = targetPort,
                                    onValueChange = { targetPort = it },
                                    label = { Text("Port", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Button(
                                    onClick = {
                                        val p = targetPort.toIntOrNull() ?: localPort
                                        if (targetHost.isNotBlank()) {
                                            if (onConnectToAddress != null) {
                                                onConnectToAddress(targetHost.trim(), p)
                                            } else {
                                                onPairWithHost(targetHost.trim(), p, "")
                                            }
                                        }
                                    },
                                    enabled = !isPairingInProgress && targetHost.isNotBlank(),
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Go", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            if (!pairingStatusMessage.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pairingStatusMessage,
                                    fontSize = 11.5.sp,
                                    color = if (pairingStatusMessage.contains("success", ignoreCase = true)) Color(0xFF10B981) else Color(0xFFEF4444),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: SHOW MY CODE / QR (Simple Mode: zero PIN, zero fingerprint)
                        val candidateIpsStr = (localCandidateIps + localIp).distinct().filter { it.isNotBlank() }
                        val qrPayload = remember(candidateIpsStr, localPort) {
                            "nodeconnect://${candidateIpsStr.joinToString(",")}:$localPort?dev=$localDeviceId&name=$localDeviceName"
                        }
                        val qrMatrix = remember(qrPayload) {
                            try {
                                QrCodeGenerator.encode(qrPayload)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (qrMatrix != null) {
                                Box(
                                    modifier = Modifier
                                        .size(175.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val count = qrMatrix.size
                                        val cellSize = size.width / count
                                        for (y in 0 until count) {
                                            for (x in 0 until count) {
                                                if (qrMatrix.get(x, y)) {
                                                    drawRect(
                                                        color = Color.Black,
                                                        topLeft = Offset(x * cellSize, y * cellSize),
                                                        size = Size(cellSize + 0.4f, cellSize + 0.4f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("Failed to generate QR code", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = localDeviceName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "Listening on: 0.0.0.0:$localPort",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Candidate IPs: ${candidateIpsStr.joinToString(", ")}",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(qrPayload))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Connect URI", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: CONNECTION DOCTOR
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "LOCAL INTERFACES & PORT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            val ips = (localCandidateIps + localIp).distinct()
                            ips.forEach { ip ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ip, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                    Text("0.0.0.0:$localPort [OPEN]", fontSize = 10.sp, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Test Connection Tool
                            Text(
                                text = "SOCKET PING TEST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = doctorHost,
                                    onValueChange = { doctorHost = it },
                                    label = { Text("Target IP", fontSize = 10.sp) },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = doctorPort,
                                    onValueChange = { doctorPort = it },
                                    label = { Text("Port", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Button(
                                    onClick = {
                                        val p = doctorPort.toIntOrNull() ?: localPort
                                        doctorTesting = true
                                        doctorResultText = "Testing socket..."
                                        onTestPingClicked?.invoke(doctorHost.trim(), p) { res ->
                                            doctorTesting = false
                                            doctorResultText = if (res.isSuccess) {
                                                "SUCCESS: Responded in ${res.getOrNull()}ms"
                                            } else {
                                                "FAILED: ${res.exceptionOrNull()?.message ?: "Timeout"}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Test", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            if (doctorResultText != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = doctorResultText ?: "",
                                    fontSize = 11.sp,
                                    color = if (doctorResultText?.startsWith("SUCCESS") == true) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onProbeGatewayClicked?.invoke() },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Probe Gateway", fontSize = 11.sp, color = Color.White)
                                }

                                Button(
                                    onClick = { onCopyLogClicked?.invoke() },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Copy Log", fontSize = 11.sp, color = Color.White)
                                }

                                Button(
                                    onClick = { onExportLogClicked?.invoke() },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Export Log", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            if (onStartUsbLink != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onStartUsbLink.invoke() },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Setup USB Debugging Link (ADB 127.0.0.1)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                                }
                            }
                        }
                    }
                    3 -> {
                        // TAB 3: SETTINGS & PERMISSIONS
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "MASTER & ACCESS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            ConfigSwitchRow(
                                title = "Allow Incoming Connections",
                                subtitle = "Accept connection requests from other nodes on your network",
                                checked = config.allowIncoming,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(allowIncoming = it)) },
                                accent = accent
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "SECURITY & TRUST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Security Level", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Text(
                                        if (config.securityLevel == SecurityLevel.SIMPLE)
                                            "Simple: Encrypted TLS, no passwords, no PIN"
                                        else
                                            "Strict: Pinned EC-256 certificate + 6-digit PIN",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        val next = if (config.securityLevel == SecurityLevel.SIMPLE) SecurityLevel.STRICT else SecurityLevel.SIMPLE
                                        onConfigChange?.invoke(config.copy(securityLevel = next))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (config.securityLevel == SecurityLevel.SIMPLE) accent.copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        config.securityLevel.name,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (config.securityLevel == SecurityLevel.SIMPLE) accent else Color(0xFFEF4444)
                                    )
                                }
                            }

                            ConfigSwitchRow(
                                title = "Ask Before Allowing New Devices",
                                subtitle = "Shows approval prompt when an un-trusted device connects",
                                checked = config.askBeforeAllowing,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(askBeforeAllowing = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Trust All Devices On Network",
                                subtitle = "Auto-allows any device on this Wi-Fi without prompting",
                                checked = config.trustAllOnNetwork,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(trustAllOnNetwork = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Transport Encryption (TLS 1.3)",
                                subtitle = "Encrypts all Node Connect traffic (disable for raw TCP debugging)",
                                checked = config.encryptionEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(encryptionEnabled = it)) },
                                accent = accent
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "DISCOVERY METHODS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            ConfigSwitchRow(
                                title = "Multicast DNS (mDNS)",
                                subtitle = "Zero-config peer announcement and resolution",
                                checked = config.mdnsEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(mdnsEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "UDP Broadcast Beacons",
                                subtitle = "Directed subnet & global broadcast on port 54821",
                                checked = config.broadcastEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(broadcastEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Hotspot Gateway Probe",
                                subtitle = "Probes default gateway IP when connected to mobile hotspot",
                                checked = config.gatewayProbeEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(gatewayProbeEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Parallel Subnet Scan",
                                subtitle = "Fast /24 local subnet sweep for isolated AP networks",
                                checked = config.subnetScanEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(subnetScanEnabled = it)) },
                                accent = accent
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "FEATURE TOGGLES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            ConfigSwitchRow(
                                title = "Remote Control",
                                subtitle = "Allow transport controls (Play, Pause, Seek, Volume)",
                                checked = config.remoteControlEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(remoteControlEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Audio Cast",
                                subtitle = "Live raw PCM audio streaming between devices",
                                checked = config.audioCastEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(audioCastEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Mute Local Device While Casting",
                                subtitle = "Silence local speakers and output audio only on receiving device",
                                checked = config.muteLocalOutputDuringCast,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(muteLocalOutputDuringCast = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Library Sharing",
                                subtitle = "Allow peers to search and browse songs (zero path exposure)",
                                checked = config.shareLibraryEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(shareLibraryEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Playback Handoff",
                                subtitle = "Transfer queue and position mid-playback to target device",
                                checked = config.handoffEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(handoffEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Now Playing Mirror",
                                subtitle = "Display active track metadata and artwork thumbnail",
                                checked = config.mirrorEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(mirrorEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Data & Settings Sync",
                                subtitle = "Sync playlists, favorites, and EQ presets with 3-snapshot undo",
                                checked = config.syncRailEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(syncRailEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Sleep Timer Sync",
                                subtitle = "Keep sleep timer countdown synchronized across devices",
                                checked = config.sleepTimerSyncEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(sleepTimerSyncEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Third-Party Media Sessions",
                                subtitle = "Display & control Spotify/YouTube Music (metadata only)",
                                checked = config.externalSessionsEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(externalSessionsEnabled = it)) },
                                accent = accent
                            )


                            ConfigSwitchRow(
                                title = "Chat & Clipboard Mirror",
                                subtitle = "Instantly share copied text and links across connected devices",
                                checked = config.chatClipboardEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(chatClipboardEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Fast File Push",
                                subtitle = "Push audio tracks and playlists directly to remote storage",
                                checked = config.filePushEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(filePushEnabled = it)) },
                                accent = accent
                            )

                            ConfigSwitchRow(
                                title = "Plugin & Extension Sync",
                                subtitle = "Synchronize plugins and themes between devices",
                                checked = config.pluginSyncEnabled,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(pluginSyncEnabled = it)) },
                                accent = accent
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "ADVANCED & JITTER TUNING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            ConfigSwitchRow(
                                title = "Verbose Network Logging",
                                subtitle = "Write full trace to NodeLog ring buffer and persistent log file",
                                checked = config.verboseLogging,
                                onCheckedChange = { onConfigChange?.invoke(config.copy(verboseLogging = it)) },
                                accent = accent
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Jitter Buffer Target", fontSize = 12.sp, color = Color.White)
                                Text("${config.jitterBufferTargetMs} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                            }
                            Slider(
                                value = config.jitterBufferTargetMs.toFloat(),
                                onValueChange = { onConfigChange?.invoke(config.copy(jitterBufferTargetMs = it.toInt())) },
                                valueRange = 50f..500f,
                                steps = 8,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Keepalive Ping Interval", fontSize = 12.sp, color = Color.White)
                                Text("${config.keepaliveIntervalSec} s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                            }
                            Slider(
                                value = config.keepaliveIntervalSec.toFloat(),
                                onValueChange = { onConfigChange?.invoke(config.copy(keepaliveIntervalSec = it.toInt())) },
                                valueRange = 2f..30f,
                                steps = 13,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), lineHeight = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = accent)
        )
    }
}

@Composable
private fun QuickFeatureChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp).clip(RoundedCornerShape(8.dp)),
        color = if (enabled) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) accent else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
