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
    discoveredPeers: List<DiscoveredNodePeer> = emptyList(),
    onStartSession: () -> Unit,
    onCancelSession: () -> Unit,
    onPairWithHost: (host: String, port: Int, pin: String) -> Unit,
    isPairingInProgress: Boolean = false,
    pairingStatusMessage: String? = null
) {
    val accent = LocalAccentColor.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(if (activeSession != null) 0 else 1) }

    // Client form state
    var targetHost by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("54820") }
    var enteredPin by remember { mutableStateOf("") }

    // Auto-select discovered peer if only one
    LaunchedEffect(discoveredPeers) {
        if (targetHost.isEmpty() && discoveredPeers.isNotEmpty()) {
            val first = discoveredPeers.first()
            targetHost = first.hostAddress
            targetPort = first.port.toString()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(420.dp)
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
                                text = "Node Connect Pairing",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Zero-cloud encrypted mTLS sync",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Switcher: Host Mode vs Client Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedTab == 0) accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Show PIN / QR",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedTab == 1) accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Connect with PIN",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TAB 0: HOST MODE (Display QR and 6-Digit PIN)
                if (selectedTab == 0) {
                    if (activeSession == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Start a pairing session to generate a secure 6-digit PIN and QR code.",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onStartSession,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate 6-Digit PIN", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    } else {
                        // Render QR Code
                        val qrPayload = remember(activeSession.sessionToken, localIp) {
                            activeSession.toQrPayload(localIp, localDeviceId, localDeviceName)
                        }
                        val qrMatrix = remember(qrPayload) {
                            try {
                                QrCodeGenerator.encode(qrPayload)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (qrMatrix != null) {
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
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
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Large 6-digit PIN display
                        Text(
                            text = "OR ENTER 6-DIGIT PIN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val pin = activeSession.sixDigitPin
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pin.forEachIndexed { index, ch ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp, 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch.toString(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (index == 2) {
                                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Copy PIN Button
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(pin))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy PIN", fontSize = 11.5.sp, color = accent, fontWeight = FontWeight.SemiBold)
                        }

                        Text(
                            text = "Host Address: $localIp:${activeSession.port}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Session active (valid for 3 minutes)",
                            fontSize = 10.5.sp,
                            color = Color(0xFF10B981)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(onClick = onCancelSession) {
                            Text("Cancel Session", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    // TAB 1: CLIENT MODE (Connect to another device)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Nearby Devices list
                        Text(
                            text = "NEARBY DEVICES ON WI-FI",
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
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = accent
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Searching local network for Node devices...",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                discoveredPeers.forEach { peer ->
                                    val isSelected = targetHost == peer.hostAddress
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) accent else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                targetHost = peer.hostAddress
                                                targetPort = peer.port.toString()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (peer.platform == "Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop,
                                                contentDescription = null,
                                                tint = if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = peer.deviceName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${peer.hostAddress}:${peer.port}",
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (isSelected) "Selected" else "Select",
                                            fontSize = 11.sp,
                                            color = if (isSelected) accent else Color.White.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Host IP & Port Inputs
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = targetHost,
                                onValueChange = { targetHost = it },
                                label = { Text("Host IP Address", fontSize = 11.sp) },
                                modifier = Modifier.weight(2.5f),
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
                                label = { Text("Port", fontSize = 11.sp) },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // PIN Input
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { if (it.length <= 6) enteredPin = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Enter 6-Digit PIN from Host", fontSize = 11.sp) },
                            placeholder = { Text("e.g. 831492", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

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

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val portInt = targetPort.toIntOrNull() ?: 54820
                                onPairWithHost(targetHost.trim(), portInt, enteredPin.trim())
                            },
                            enabled = !isPairingInProgress && targetHost.isNotBlank() && enteredPin.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isPairingInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting & Verifying...", fontWeight = FontWeight.Bold, color = Color.Black)
                            } else {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect & Pair Device", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
