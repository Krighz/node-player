package com.nodeplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.video.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualitySheet(
    currentHeight: Int?,
    availableHeights: List<Int>,
    selectedQuality: VideoQuality,
    onDismiss: () -> Unit,
    onSelectQuality: (VideoQuality) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161618),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Video Playback Quality",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Quality Options
            val qualities = listOf(
                VideoQuality.AUTO,
                VideoQuality.P1080,
                VideoQuality.P720,
                VideoQuality.P480,
                VideoQuality.P360
            )

            qualities.forEach { quality ->
                val isSelected = if (quality == VideoQuality.AUTO) {
                    currentHeight == null || selectedQuality == VideoQuality.AUTO
                } else {
                    currentHeight == quality.height || selectedQuality == quality
                }

                val isAvailable = quality == VideoQuality.AUTO ||
                    availableHeights.isEmpty() ||
                    availableHeights.contains(quality.height)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isAvailable) {
                            onSelectQuality(quality)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = quality.label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isAvailable) {
                                Color.White.copy(alpha = 0.35f)
                            } else if (isSelected) {
                                Color.Cyan
                            } else {
                                Color.White
                            }
                        )
                        if (quality == VideoQuality.AUTO && currentHeight != null) {
                            Text(
                                text = "Currently streaming ${currentHeight}p",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.Cyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
