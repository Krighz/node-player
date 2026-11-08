package com.nodeplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.music.audio.AudioTestTools
import com.nodeplayer.music.data.DailyListeningStatEntity
import com.nodeplayer.music.feedback.FeedbackHelper
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.components.HazeState
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.ui.components.LiquidSlider
import java.time.LocalDate

@Composable
fun MoreScreen(
    dailyStats: List<DailyListeningStatEntity>,
    sessionListeningSeconds: Long,
    onOpenStats: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenSmartPlaylists: () -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onOpenDiagnostics: () -> Unit = {}
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Listening time calculations
    val now = remember { LocalDate.now() }
    val todayStr = remember { now.toString() }
    val currentMonthPrefix = remember { "${now.year}-${"%02d".format(now.monthValue)}" }
    val currentYearPrefix = remember { "${now.year}" }

    val todaySec = remember(dailyStats) {
        dailyStats.firstOrNull { it.date == todayStr }?.listeningSeconds ?: 0L
    }
    val monthSec = remember(dailyStats) {
        dailyStats.filter { it.date.startsWith(currentMonthPrefix) }.sumOf { it.listeningSeconds }
    }
    val yearSec = remember(dailyStats) {
        dailyStats.filter { it.date.startsWith(currentYearPrefix) }.sumOf { it.listeningSeconds }
    }

    // Audio test tool state
    var activeTestMode by remember { mutableStateOf(AudioTestTools.TestMode.NONE) }
    var channelIsLeft by remember { mutableStateOf(true) }
    var sineFreqHz by remember { mutableFloatStateOf(1000f) }

    DisposableEffect(Unit) {
        onDispose {
            AudioTestTools.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Analytics, library tools & acoustic test lab",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. Listening Time Dashboard (Today / Month / Year / Session)
        Text(
            text = "LISTENING TIME",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListeningCounterCard("Today", "${todaySec / 60}m", "${todaySec % 60}s", Modifier.weight(1f))
            ListeningCounterCard("Month", "%.1fh".format(monthSec / 3600f), "${(monthSec % 3600) / 60}m", Modifier.weight(1f))
            ListeningCounterCard("Year", "%.1fh".format(yearSec / 3600f), "", Modifier.weight(1f))
            ListeningCounterCard("Session", "${sessionListeningSeconds / 60}m", "${sessionListeningSeconds % 60}s", Modifier.weight(1f), isAccent = true)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Navigation Hub Cards
        Text(
            text = "FEATURES & TOOLS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        HubRow(
            icon = Icons.Rounded.BarChart,
            title = "Listening Stats",
            subtitle = "Daily activity charts, top tracks and artists",
            onClick = onOpenStats,
            
        )
        Spacer(modifier = Modifier.height(8.dp))

        HubRow(
            icon = Icons.Rounded.Celebration,
            title = "Yearly Recap",
            subtitle = "Your year in sound, streak, and shareable cards",
            onClick = onOpenRecap,
            
        )
        Spacer(modifier = Modifier.height(8.dp))

        HubRow(
            icon = Icons.Rounded.AutoAwesome,
            title = "Smart Playlists",
            subtitle = "Rule builder with lossless, DR, year, and M3U export",
            onClick = onOpenSmartPlaylists,
            
        )
        Spacer(modifier = Modifier.height(8.dp))

        HubRow(
            icon = Icons.Rounded.ContentCopy,
            title = "Duplicate Finder",
            subtitle = "Exact hash & metadata duplicate detection with keep best",
            onClick = onOpenDuplicateFinder,
            
        )
        Spacer(modifier = Modifier.height(8.dp))

        HubRow(
            icon = Icons.Rounded.Monitor,
            title = "Graphics Diagnostics",
            subtitle = "GPU hardware inspection, shader self-test & previews",
            onClick = onOpenDiagnostics,
            
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Community & Feedback
        Text(
            text = "COMMUNITY & FEEDBACK",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                
                onClick = { FeedbackHelper.openFeedback(context) },
                tintAlpha = 0.40f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Forum, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Send feedback", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Discussions", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp))
                    }
                }
            }

            GlassCard(
                modifier = Modifier.weight(1f),
                
                onClick = { FeedbackHelper.openBugReport(context) },
                tintAlpha = 0.40f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Report a bug", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Device pre-filled", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        com.nodeplayer.music.ui.components.SupportCard()

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Audio Test Tools (Channel check, Polarity, Sweep, Noise, Sine tone)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACOUSTIC TEST TOOLS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = accent
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Safety Cap (25% max)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            
            tintAlpha = 0.45f
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Active Test Indicator & Stop button
                if (activeTestMode != AudioTestTools.TestMode.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active: ${activeTestMode.name.replace("_", " ")}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = accent)
                        )
                        Button(
                            onClick = {
                                AudioTestTools.stop()
                                activeTestMode = AudioTestTools.TestMode.NONE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Stop", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 1. Channel Check (Left / Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Channel Check", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Alternating 440 Hz Left & Right tone", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                    }
                    Button(
                        onClick = {
                            if (activeTestMode == AudioTestTools.TestMode.LEFT_CHANNEL || activeTestMode == AudioTestTools.TestMode.RIGHT_CHANNEL) {
                                AudioTestTools.stop()
                                activeTestMode = AudioTestTools.TestMode.NONE
                            } else {
                                AudioTestTools.playChannelCheck { isLeft -> channelIsLeft = isLeft }
                                activeTestMode = AudioTestTools.TestMode.LEFT_CHANNEL
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTestMode == AudioTestTools.TestMode.LEFT_CHANNEL || activeTestMode == AudioTestTools.TestMode.RIGHT_CHANNEL) accent else Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        Text(if (activeTestMode == AudioTestTools.TestMode.LEFT_CHANNEL || activeTestMode == AudioTestTools.TestMode.RIGHT_CHANNEL) if (channelIsLeft) "LEFT" else "RIGHT" else "Test")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // 2. Speaker Polarity (In-phase vs Out-of-phase)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Polarity Check", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("In-phase centered vs out-of-phase wide", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                AudioTestTools.playPolarityCheck(inPhase = true)
                                activeTestMode = AudioTestTools.TestMode.POLARITY_IN_PHASE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.POLARITY_IN_PHASE) accent else Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("In-Phase", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                AudioTestTools.playPolarityCheck(inPhase = false)
                                activeTestMode = AudioTestTools.TestMode.POLARITY_OUT_PHASE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.POLARITY_OUT_PHASE) accent else Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Out-Phase", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // 3. Frequency Sweep (20 Hz - 20 kHz)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Frequency Sweep", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Logarithmic 20 Hz – 20,000 Hz sweep", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                    }
                    Button(
                        onClick = {
                            if (activeTestMode == AudioTestTools.TestMode.FREQ_SWEEP) {
                                AudioTestTools.stop()
                                activeTestMode = AudioTestTools.TestMode.NONE
                            } else {
                                AudioTestTools.playFrequencySweep()
                                activeTestMode = AudioTestTools.TestMode.FREQ_SWEEP
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.FREQ_SWEEP) accent else Color.White.copy(alpha = 0.12f))
                    ) {
                        Text(if (activeTestMode == AudioTestTools.TestMode.FREQ_SWEEP) "Playing" else "Sweep")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // 4. Pink & White Noise
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Noise Generator", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Pink noise (1/f) or flat White noise", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                AudioTestTools.playNoise(isPink = true)
                                activeTestMode = AudioTestTools.TestMode.PINK_NOISE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.PINK_NOISE) accent else Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Pink", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                AudioTestTools.playNoise(isPink = false)
                                activeTestMode = AudioTestTools.TestMode.WHITE_NOISE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.WHITE_NOISE) accent else Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("White", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // 5. Pure Sine Tone Generator
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sine Tone Generator", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(
                            text = "${sineFreqHz.toInt()} Hz",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LiquidSlider(
                        value = sineFreqHz,
                        onValueChange = {
                            sineFreqHz = it
                            if (activeTestMode == AudioTestTools.TestMode.SINE_TONE) {
                                AudioTestTools.playSineTone(sineFreqHz.toDouble())
                            }
                        },
                        valueRange = 20f..20000f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (activeTestMode == AudioTestTools.TestMode.SINE_TONE) {
                                    AudioTestTools.stop()
                                    activeTestMode = AudioTestTools.TestMode.NONE
                                } else {
                                    AudioTestTools.playSineTone(sineFreqHz.toDouble())
                                    activeTestMode = AudioTestTools.TestMode.SINE_TONE
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeTestMode == AudioTestTools.TestMode.SINE_TONE) accent else Color.White.copy(alpha = 0.12f))
                        ) {
                            Text(if (activeTestMode == AudioTestTools.TestMode.SINE_TONE) "Stop Tone" else "Play Tone")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(180.dp))
    }
}

@Composable
private fun ListeningCounterCard(
    label: String,
    primaryValue: String,
    secondaryValue: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isAccent: Boolean = false
) {
    val accent = LocalAccentColor.current
    GlassCard(
        modifier = modifier,
        
        tintAlpha = if (isAccent) 0.65f else 0.40f
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isAccent) accent else Color.White,
                    fontSize = 15.sp
                )
            )
            if (secondaryValue.isNotEmpty()) {
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    hazeState: HazeState? = null
) {
    val accent = LocalAccentColor.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        
        onClick = onClick,
        tintAlpha = 0.40f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.60f)
                    ),
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
