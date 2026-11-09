package com.nodeplayer.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.ui.components.GlassControlSurface
import com.nodeplayer.ui.components.LiquidButton
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Lyrics timing offset dialog with coarse (±500ms) and fine (±50ms) steppers.
 * Persists per-track offset adjustments and allows resetting to global default.
 */
@Composable
fun LyricsOffsetDialog(
    currentOffsetMs: Long,
    onOffsetChanged: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val baseTextColor = MaterialTheme.colorScheme.onBackground

    Dialog(onDismissRequest = onDismiss) {
        GlassControlSurface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F1424).copy(alpha = 0.85f))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Lyrics Sync Offset",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = baseTextColor
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                val sign = if (currentOffsetMs > 0) "+" else ""
                Text(
                    text = "$sign$currentOffsetMs ms",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (currentOffsetMs != 0L) accent else baseTextColor
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Fine steppers (-50ms / +50ms)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidButton(onClick = { onOffsetChanged(currentOffsetMs - 50L) }) {
                        Text("-50 ms", style = MaterialTheme.typography.labelMedium.copy(color = baseTextColor))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    LiquidButton(onClick = { onOffsetChanged(currentOffsetMs + 50L) }) {
                        Text("+50 ms", style = MaterialTheme.typography.labelMedium.copy(color = baseTextColor))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Coarse steppers (-500ms / +500ms)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidButton(onClick = { onOffsetChanged(currentOffsetMs - 500L) }) {
                        Text("-500 ms", style = MaterialTheme.typography.labelMedium.copy(color = baseTextColor))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    LiquidButton(onClick = { onOffsetChanged(currentOffsetMs + 500L) }) {
                        Text("+500 ms", style = MaterialTheme.typography.labelMedium.copy(color = baseTextColor))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFF5252)),
                        modifier = Modifier
                            .clickable { onReset() }
                            .padding(8.dp)
                    )

                    LiquidButton(onClick = onDismiss) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = baseTextColor
                            )
                        )
                    }
                }
            }
        }
    }
}
