package com.nodeplayer.music.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.glassSurface

@Composable
fun UpdateOptInDialog(
    onChoice: (agreed: Boolean) -> Unit
) {
    val accent = LocalAccentColor.current

    Dialog(onDismissRequest = { /* Require explicit choice */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C24).copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = "Check for updates automatically?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Node Player can check GitHub Releases periodically to ensure you always have the latest improvements, audio bug fixes, and features.\n\n" +
                            "Privacy notice: When enabled, the app sends a request to the public GitHub API with a User-Agent header (NodePlayer/<version>). This reveals your IP address and app version to GitHub. You can change this or switch to Wi-Fi only anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.80f),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onChoice(false) }
                    ) {
                        Text("No thanks", color = Color.White.copy(alpha = 0.65f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onChoice(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Yes, check automatically", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
