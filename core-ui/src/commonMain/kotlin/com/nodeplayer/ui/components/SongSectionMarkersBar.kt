package com.nodeplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.model.SongSectionMarker
import kotlinx.coroutines.delay

private val PILL_SHAPE = RoundedCornerShape(12.dp)

/**
 * High-performance 120 FPS interactive song section markers bar.
 * Renders intro, verse, chorus, drop, solo, and outro pills above the scrubber.
 * Uses an isolated internal ticker so position changes only recompose this component.
 */
@Composable
fun SongSectionMarkersBar(
    sections: List<SongSectionMarker>,
    positionProvider: () -> Long,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sections.isEmpty()) return

    // Isolated position sampling every 250ms for highlight evaluation
    val currentPosMs by produceState(initialValue = positionProvider()) {
        while (true) {
            value = positionProvider()
            delay(250)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        items(
            items = sections,
            key = { "${it.title}_${it.startMs}" }
        ) { section ->
            val end = section.endMs
            val isActive = currentPosMs >= section.startMs && (end == null || currentPosMs < end)

            val bgColor by animateColorAsState(
                targetValue = if (isActive) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                label = "section_pill_bg"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isActive) accentColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
                label = "section_pill_border"
            )

            val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.70f)

            Box(
                modifier = Modifier
                    .clip(PILL_SHAPE)
                    .background(bgColor)
                    .border(width = 1.dp, color = borderColor, shape = PILL_SHAPE)
                    .clickable { onSeek(section.startMs) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = section.title,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}
