package com.nodeplayer.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.diagnostics.GlassShaderSelfTest
import com.nodeplayer.music.diagnostics.GpuInspector
import com.nodeplayer.music.diagnostics.ShaderSelfTestResult
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.HazeState
import com.nodeplayer.music.ui.components.glassRefraction
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.ui.components.LiquidToggle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicsDiagnosticsScreen(
    themeConfig: GlassThemeConfig,
    selfTestResult: ShaderSelfTestResult,
    onToggleForceFail: (Boolean) -> Unit,
    onReportProblemClick: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    val deviceInfo = remember { GpuInspector.getDeviceInfo(context) }
    var forceFailEnabled by remember { mutableStateOf(GlassShaderSelfTest.isForceFailEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graphics Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = deviceInfo.toSanitizedPlainText(selfTestResult, themeConfig.shaderMode)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Device graphics info copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = accent
                )
            )
        },
        containerColor = Color(0xFF0C0D14),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Device & GPU Hardware Card
            Text(
                text = "HARDWARE & GPU INSPECTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DiagRow("Device", "${deviceInfo.manufacturer} ${deviceInfo.model} (${deviceInfo.device})")
                    DiagRow("Android OS", "${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})")
                    DiagRow("SoC Model", "${deviceInfo.socModel} (Hardware: ${deviceInfo.hardware})")
                    DiagRow("GPU Renderer", deviceInfo.gpuRenderer, isHighlight = true, highlightColor = accent)
                    DiagRow("Screen Size", "${deviceInfo.screenWidthPx} x ${deviceInfo.screenHeightPx} px")
                    DiagRow("Refresh Rate", "${String.format(Locale.US, "%.1f", deviceInfo.refreshRateHz)} Hz")
                    DiagRow("Screen Density", "${deviceInfo.densityDpi} dpi (${String.format(Locale.US, "%.1f", deviceInfo.densityScale)}x)")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Self-Test & Active Mode Card
            Text(
                text = "SHADER SELF-TEST STATUS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Self-Test Result", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f)))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selfTestResult.passed) Color(0x3310B981) else Color(0x33EF4444)
                        ) {
                            Text(
                                text = if (selfTestResult.passed) "PASSED" else "FAILED",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selfTestResult.passed) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = selfTestResult.reason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    DiagRow("Current Shader Mode", themeConfig.shaderMode)
                    DiagRow("Dock Target Bounds", "${GlassShaderSelfTest.DOCK_WIDTH} x ${GlassShaderSelfTest.DOCK_HEIGHT} px")
                    DiagRow("Spill Check Buffer", "${GlassShaderSelfTest.BITMAP_WIDTH} x ${GlassShaderSelfTest.BITMAP_HEIGHT} px")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Debug Switch: Force Self-Test Failure
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Self-Test Failure", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Simulates a GPU spill failure to test safe fallback on phone/emulator", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                        }
                        LiquidToggle(
                            checked = forceFailEnabled,
                            onCheckedChange = {
                                forceFailEnabled = it
                                onToggleForceFail(it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.10f))

                    // Debug Switch: Mock Update Available
                    var mockUpdateEnabled by remember {
                        mutableStateOf(com.nodeplayer.music.update.UpdatePreferences.getDebugMockUrl(context) != null)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Debug Mock Update", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text("Simulates v9.9.0 update banner for UI testing", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                        }
                        LiquidToggle(
                            checked = mockUpdateEnabled,
                            onCheckedChange = { enabled ->
                                mockUpdateEnabled = enabled
                                if (enabled) {
                                    com.nodeplayer.music.update.UpdatePreferences.setDebugMockUrl(context, "mock://v9.9.0")
                                    com.nodeplayer.music.update.UpdateManager.setAvailableUpdate(
                                        com.nodeplayer.music.update.AppUpdateInfo(
                                            versionName = "9.9.0",
                                            releaseTag = "v9.9.0",
                                            downloadUrl = "https://github.com/Krighz/node-player/releases/download/v9.9.0/NodePlayer.apk",
                                            sha256Url = "https://github.com/Krighz/node-player/releases/download/v9.9.0/NodePlayer.apk.sha256",
                                            releaseNotes = "• Test release notes\n• Verified banner rendering\n• Safe verification",
                                            assetSize = 4194304L,
                                            publishedAt = "2026-11-02T12:00:00Z"
                                        )
                                    )
                                } else {
                                    com.nodeplayer.music.update.UpdatePreferences.setDebugMockUrl(context, null)
                                    com.nodeplayer.music.update.UpdateManager.clearCachedUpdate(context)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Live Previews of Shader Variants Side by Side
            Text(
                text = "LIVE SHADER PREVIEWS (SIDE BY SIDE)",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Compare variants live. If a shader shows red/orange slabs or glitch artifacts, it indicates a GPU shader spill.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShaderVariantPreviewCard(
                    title = "Full AGSL",
                    subtitle = "Refraction + Fringe + Grain",
                    enabled = true,
                    chromaticFringe = 1.0f,
                    grainStrength = 1.0f,
                    modifier = Modifier.weight(1f),
                    
                )
                ShaderVariantPreviewCard(
                    title = "Simple AGSL",
                    subtitle = "Refraction Only",
                    enabled = true,
                    chromaticFringe = 0.0f,
                    grainStrength = 0.0f,
                    modifier = Modifier.weight(1f),
                    
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShaderVariantPreviewCard(
                    title = "Safe Fallback",
                    subtitle = "Haze Blur + Specular Rim",
                    enabled = false,
                    chromaticFringe = 0.0f,
                    grainStrength = 0.0f,
                    modifier = Modifier.weight(1f),
                    
                )
                ShaderVariantPreviewCard(
                    title = "Off / Flat",
                    subtitle = "Zero Shader Overhead",
                    enabled = false,
                    chromaticFringe = 0.0f,
                    grainStrength = 0.0f,
                    isOff = true,
                    modifier = Modifier.weight(1f),
                    
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Action Buttons
            Button(
                onClick = {
                    val text = deviceInfo.toSanitizedPlainText(selfTestResult, themeConfig.shaderMode)
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "Device graphics info copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Device Info", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onReportProblemClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B))
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report a Graphics Problem")
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ShaderVariantPreviewCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    chromaticFringe: Float,
    grainStrength: Float,
    isOff: Boolean = false,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(115.dp)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E2235),
                        Color(0xFF131522)
                    )
                )
            )
            .then(
                if (!isOff) {
                    Modifier
                        .glassRefraction(
                            shape = cardShape,
                            enabled = enabled,
                            refractionIndex = 0.85f,
                            chromaticFringe = chromaticFringe,
                            grainStrength = grainStrength
                        )
                        .glassSurface( shape = cardShape, tintAlpha = 0.60f)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), cardShape)
                }
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (enabled) Color(0xFF10B981) else Color(0xFF94A3B8))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (enabled) "AGSL Active" else (if (isOff) "Shader Off" else "Haze Fallback"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                )
            }
        }
    }
}

@Composable
private fun DiagRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    highlightColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                color = if (isHighlight) highlightColor else Color.White
            )
        )
    }
}
