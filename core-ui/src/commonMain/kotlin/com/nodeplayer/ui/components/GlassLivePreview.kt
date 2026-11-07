package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.ui.theme.GlassDesignTokens

/**
 * Live interactive liquid glass preview card modeled after the Kyant0 demo app.
 * Dynamically reacts to user adjustments of glass tokens and settings.
 */
@Composable
fun GlassLivePreview(
    config: GlassThemeConfig,
    modifier: Modifier = Modifier
) {
    val tokens = GlassDesignTokens.resolve(config, isDark = true)
    val cardShape = RoundedCornerShape(tokens.cardRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A),
                        Color(0xFF3B82F6).copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                width = tokens.rimWidth,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = tokens.rimSpecularAlpha * 0.5f),
                        Color.White.copy(alpha = tokens.rimAmbientAlpha * 0.2f)
                    )
                ),
                shape = cardShape
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Inner glass capsule simulation
        val innerShape = RoundedCornerShape(percent = 50)
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp)
                .clip(innerShape)
                .background(Color.White.copy(alpha = config.glassOpacity * 0.25f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = tokens.rimSpecularAlpha * 0.6f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = innerShape
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Liquid Glass Live Preview",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "${config.glassQuality.displayName} • ${config.motionLevel.displayName}",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
