package com.nodeplayer.music.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.SmartMixtapeConfig

private val ZincCardBackground = Color(0xFF141417)
private val ZincBorder = Color(0xFF27272A)
private val ZincTextPrimary = Color(0xFFFAFAFA)
private val ZincTextSecondary = Color(0xFFA1A1AA)
private val AccentLiquidBlue = Color(0xFF38BDF8)

@Composable
fun SmartMixtapeSettingsCard(
    config: SmartMixtapeConfig,
    cachedCount: Int,
    usedStorageMb: Long,
    isSyncing: Boolean,
    onConfigChange: (SmartMixtapeConfig) -> Unit,
    onSyncNowClick: () -> Unit,
    onClearMixtapeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderSongCount by remember(config.targetSongCount) {
        mutableFloatStateOf(config.targetSongCount.toFloat())
    }
    val currentCapMb = (config.maxStorageCapBytes / (1024 * 1024)).toInt()
    var sliderStorageCapMb by remember(currentCapMb) {
        mutableFloatStateOf(currentCapMb.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZincCardBackground, RoundedCornerShape(16.dp))
            .border(1.dp, ZincBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Offline Mixtape",
                    color = ZincTextPrimary,
                    fontSize = 17.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Auto-caches fresh discovery & top tracks for zero-data playback.",
                    color = ZincTextSecondary,
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = config.isEnabled,
                onCheckedChange = { enabled ->
                    onConfigChange(config.copy(isEnabled = enabled))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentLiquidBlue
                )
            )
        }

        AnimatedVisibility(visible = config.isEnabled) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = ZincBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$cachedCount / ${config.targetSongCount} songs cached • $usedStorageMb MB used",
                    color = AccentLiquidBlue,
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Download Count: ${sliderSongCount.toInt()} songs",
                    color = ZincTextPrimary,
                    fontSize = 14.sp
                )
                Slider(
                    value = sliderSongCount,
                    onValueChange = { sliderSongCount = it },
                    onValueChangeFinished = {
                        onConfigChange(config.copy(targetSongCount = sliderSongCount.toInt()))
                    },
                    valueRange = 15f..100f,
                    steps = 16,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentLiquidBlue,
                        activeTrackColor = AccentLiquidBlue
                    )
                )

                Text(
                    text = "Storage Limit: ${sliderStorageCapMb.toInt()} MB",
                    color = ZincTextPrimary,
                    fontSize = 14.sp
                )
                Slider(
                    value = sliderStorageCapMb,
                    onValueChange = { sliderStorageCapMb = it },
                    onValueChangeFinished = {
                        onConfigChange(config.copy(maxStorageCapBytes = sliderStorageCapMb.toLong() * 1024L * 1024L))
                    },
                    valueRange = 250f..4000f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentLiquidBlue,
                        activeTrackColor = AccentLiquidBlue
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Download on unmetered Wi-Fi only", color = ZincTextPrimary, fontSize = 14.sp)
                    Switch(
                        checked = config.requireUnmeteredWifi,
                        onCheckedChange = { requireWifi ->
                            onConfigChange(config.copy(requireUnmeteredWifi = requireWifi))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSyncNowClick,
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLiquidBlue)
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Sync Now", color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = onClearMixtapeClick,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("Clear Mixtape", color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}
