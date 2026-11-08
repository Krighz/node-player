package com.nodeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor
import com.nodeplayer.ui.theme.pressScale

/**
 * 4-Item Horizontal Shortcuts Grid for Node Player mobile and desktop home views:
 * 1. Offline Music - switches to offline mode or navigates to local library
 * 2. Device Connect - opens NodeConnect pairing dialog or active cast sheet
 * 3. Hi-Res Collection - filters library by Hi-Res lossless tracks (> 48kHz / 24-bit)
 * 4. My Playlists - opens playlists browser
 *
 * Visuals:
 * - Continuous squircle icon backgrounds (`ContinuousRoundedRectangle(16.dp)`)
 * - Frosted glassmorphism gradient tint & subtle rim highlight
 * - Fluid spring press feedback with haptic response
 */
@Composable
fun HomeShortcutsGrid(
    onOfflineMusicClick: () -> Unit,
    onDeviceConnectClick: () -> Unit,
    onHiResCollectionClick: () -> Unit,
    onMyPlaylistsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutTile(
            title = "Offline Music",
            icon = Icons.Rounded.Folder,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
            onClick = {
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                onOfflineMusicClick()
            },
            modifier = Modifier.weight(1f)
        )

        ShortcutTile(
            title = "Device Connect",
            icon = Icons.Rounded.Devices,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857)),
            onClick = {
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                onDeviceConnectClick()
            },
            modifier = Modifier.weight(1f)
        )

        ShortcutTile(
            title = "Hi-Res Collection",
            icon = Icons.Rounded.GraphicEq,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
            onClick = {
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                onHiResCollectionClick()
            },
            modifier = Modifier.weight(1f)
        )

        ShortcutTile(
            title = "My Playlists",
            icon = Icons.Rounded.QueueMusic,
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            onClick = {
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                onMyPlaylistsClick()
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutTile(
    title: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val squircleShape = remember { ContinuousRoundedRectangle(16.dp) }
    val tileShape = remember { ContinuousRoundedRectangle(14.dp) }

    Column(
        modifier = modifier
            .pressScale(targetScale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(squircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            gradientColors[0].copy(alpha = 0.28f),
                            gradientColors[1].copy(alpha = 0.12f)
                        )
                    ),
                    squircleShape
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    squircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Icon container with inner frosted glow
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(tileShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                gradientColors[0].copy(alpha = 0.70f),
                                gradientColors[1].copy(alpha = 0.85f)
                            )
                        ),
                        tileShape
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), tileShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.1.sp,
                color = Color.White.copy(alpha = 0.90f)
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
