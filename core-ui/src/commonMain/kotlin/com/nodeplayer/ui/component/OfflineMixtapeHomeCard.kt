package com.nodeplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OfflineMixtapeHomeCard(
    cachedSongCount: Int,
    onPlayMixtape: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cachedSongCount <= 0) return

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(gradient, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF312E81), RoundedCornerShape(14.dp))
            .clickable(onClick = onPlayMixtape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Offline Mixtape",
                color = Color.White,
                fontSize = 16.sp,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$cachedSongCount songs ready • Always with you offline",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF38BDF8), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "▶", color = Color.Black, fontSize = 14.sp)
        }
    }
}
