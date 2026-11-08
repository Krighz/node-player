package com.nodeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.common.AppModeState
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor

enum class SearchTab {
    LOCAL_LIBRARY,
    ONLINE_YOUTUBE
}

@Composable
fun SearchCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search artists, tracks, or playlists...",
    focusRequester: FocusRequester? = null,
    onClear: () -> Unit = { onQueryChange("") },
    activeTab: SearchTab? = null,
    onTabSelect: (SearchTab) -> Unit = {}
) {
    val accent = LocalAccentColor.current
    val isOnline by AppModeState.isOnlineMode.collectAsState()
    val resolvedTab = activeTab ?: if (isOnline) SearchTab.ONLINE_YOUTUBE else SearchTab.LOCAL_LIBRARY

    val capsuleShape = remember { ContinuousRoundedRectangle(21.dp) }

    Column(
        modifier = modifier.widthIn(min = 340.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Segmented sub-tabs
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(ContinuousCapsule)
                .background(Color(0xFF14171A).copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), ContinuousCapsule)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(if (resolvedTab == SearchTab.LOCAL_LIBRARY) accent.copy(alpha = 0.32f) else Color.Transparent)
                    .clickable { onTabSelect(SearchTab.LOCAL_LIBRARY) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Local Library",
                    fontSize = 11.5.sp,
                    fontWeight = if (resolvedTab == SearchTab.LOCAL_LIBRARY) FontWeight.Bold else FontWeight.Normal,
                    color = if (resolvedTab == SearchTab.LOCAL_LIBRARY) Color.White else Color.White.copy(alpha = 0.60f)
                )
            }

            Box(
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(if (resolvedTab == SearchTab.ONLINE_YOUTUBE) Color(0xFF00ADB5).copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { onTabSelect(SearchTab.ONLINE_YOUTUBE) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "YouTube Music",
                    fontSize = 11.5.sp,
                    fontWeight = if (resolvedTab == SearchTab.ONLINE_YOUTUBE) FontWeight.Bold else FontWeight.Normal,
                    color = if (resolvedTab == SearchTab.ONLINE_YOUTUBE) Color(0xFFE0F7FA) else Color.White.copy(alpha = 0.60f)
                )
            }
        }

        // Input Capsule
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(capsuleShape)
                .background(Color(0xFF161922).copy(alpha = 0.72f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = capsuleShape
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }

        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Clear search",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
}
