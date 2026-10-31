package com.nodeplayer.music.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.EqBandConfig
import com.nodeplayer.model.ReplayGainMode
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import dev.chrisbanes.haze.HazeState

@Composable
fun EqualizerScreen(
    config: AudioEffectConfig,
    onConfigChange: (AudioEffectConfig) -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Equalizer & DSP",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "32-bit float biquad pipeline",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                )
            }

            // Master EQ toggle
            Switch(
                checked = config.eqEnabled,
                onCheckedChange = { onConfigChange(config.copy(eqEnabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preamp Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Preamp Gain",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "%+.1f dB".format(config.preampGainDb),
                        style = MaterialTheme.typography.bodyMedium.copy(color = accent, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = config.preampGainDb,
                    onValueChange = { onConfigChange(config.copy(preampGainDb = it)) },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 10-Band EQ Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "10-Band Parametric EQ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                config.bands.forEachIndexed { index, band ->
                    val freqLabel = if (band.centerFreq >= 1000f) "${(band.centerFreq / 1000).toInt()} kHz" else "${band.centerFreq.toInt()} Hz"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = freqLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value = band.gainDb,
                            onValueChange = { newGain ->
                                val updatedBands = config.bands.toMutableList()
                                updatedBands[index] = band.copy(gainDb = newGain)
                                onConfigChange(config.copy(bands = updatedBands))
                            },
                            valueRange = -12f..12f,
                            enabled = config.eqEnabled,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%+.1f".format(band.gainDb),
                            style = MaterialTheme.typography.bodySmall.copy(color = accent),
                            modifier = Modifier.width(45.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ReplayGain & Effects Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ReplayGain & Spatial Effects",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // ReplayGain Mode
                Text(
                    text = "ReplayGain Mode: ${config.replayGainMode.name}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReplayGainMode.values().forEach { mode ->
                        val isSel = config.replayGainMode == mode
                        Button(
                            onClick = { onConfigChange(config.copy(replayGainMode = mode)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) accent else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(mode.name, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stereo Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stereo Width", style = MaterialTheme.typography.bodyMedium)
                    Text("${(config.stereoWidth * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = config.stereoWidth,
                    onValueChange = { onConfigChange(config.copy(stereoWidth = it)) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Crossfeed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Crossfeed (Bauer)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Reduces headphone fatigue",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    Switch(
                        checked = config.crossfeedEnabled,
                        onCheckedChange = { onConfigChange(config.copy(crossfeedEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Soft Limiter (Safe Audio Protection)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("32-bit Soft Limiter", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Anti-clip tanh saturation (-0.1 dBFS)",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        )
                    }
                    Switch(
                        checked = config.limiterEnabled,
                        onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(200.dp))
    }
}
