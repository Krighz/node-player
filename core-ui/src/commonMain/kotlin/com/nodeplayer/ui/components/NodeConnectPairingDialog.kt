package com.nodeplayer.ui.components

import androidx.compose.foundation.Canvas
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
    isPairingInProgress: Boolean = false,
    pairingStatusMessage: String? = null
) {
    val accent = LocalAccentColor.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Devices & Connect, 1: Show QR, 2: Doctor

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
        Surface(
            modifier = Modifier
                .width(440.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
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

                // Tab Switcher: 0: Connect, 1: Show QR, 2: Doctor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(3.dp)
                ) {
                    listOf("Devices", "My QR Code", "Doctor").forEachIndexed { index, title ->
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
                                fontSize = 11.5.sp,
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

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "DISCOVERED PEERS (${discoveredPeers.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (discoveredPeers.isEmpty()) {
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
                                    discoveredPeers.forEach { peer ->
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
                        }
                    }
                }
            }
        }
    }
}
