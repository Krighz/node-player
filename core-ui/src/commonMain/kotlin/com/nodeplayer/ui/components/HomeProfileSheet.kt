package com.nodeplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Sliding Liquid-Glass Profile & Top-Level Destinations Bottom Sheet.
 * Hosts 8 top-level actions per MENU-02:
 * 1. Account header & Sign in / Switch
 * 2. Listening History
 * 3. Equalizer
 * 4. Device Connect
 * 5. Offline Music
 * 6. Sleep Timer
 * 7. Region Selection
 * 8. Settings & About
 */
@Composable
fun HomeProfileSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    accountName: String? = null,
    accountEmail: String? = null,
    onSignInClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onEqualizerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOfflineMusicClick: () -> Unit,
    onDeviceConnectClick: () -> Unit,
    onSleepTimerClick: () -> Unit = {},
    onRegionClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    backdrop: Backdrop = remember { emptyBackdrop() },
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 400f)) + slideInVertically(spring(stiffness = 400f)) { it },
        exit = fadeOut(spring(stiffness = 400f)) + slideOutVertically(spring(stiffness = 400f)) { it },
        modifier = modifier
    ) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetShape = remember { ContinuousRoundedRectangle(28.dp) }

            // Sheet container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click
                    )
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { sheetShape },
                        effects = {
                            blur(24.dp.toPx())
                            lens(16.dp.toPx(), 8.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = {
                            Highlight.Ambient.copy(width = 1.dp, alpha = 0.45f)
                        },
                        shadow = {
                            Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.60f))
                        },
                        onDrawSurface = {
                            drawRect(Color(0xFF12161F).copy(alpha = 0.88f))
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Account Header Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), ContinuousRoundedRectangle(16.dp))
                        .clickable {
                            onDismiss()
                            onSignInClick()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accent, accent.copy(alpha = 0.5f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountName ?: "Guest User",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = accountEmail ?: "Sign in to YouTube Music",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.60f)
                            )
                        )
                    }

                    if (accountName == null) {
                        Button(
                            onClick = onSignInClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent.copy(alpha = 0.35f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = ContinuousCapsule()
                        ) {
                            Text("Sign In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Destinations List (MENU-02: 8 standard options)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileSheetItem(
                        icon = Icons.Rounded.History,
                        title = "Listening History",
                        subtitle = "Recently played tracks & personalized seeds",
                        onClick = {
                            onDismiss()
                            onHistoryClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Equalizer",
                        subtitle = "10-band parametric EQ, bass boost & preamp",
                        onClick = {
                            onDismiss()
                            onEqualizerClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.Cast,
                        title = "Device Connect",
                        subtitle = "Node Connect P2P streaming & casting",
                        onClick = {
                            onDismiss()
                            onDeviceConnectClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Offline Music",
                        subtitle = "Switch to local audio library & offline tracks",
                        onClick = {
                            onDismiss()
                            onOfflineMusicClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.Timer,
                        title = "Sleep Timer",
                        subtitle = "Automatically pause playback after delay",
                        onClick = {
                            onDismiss()
                            onSleepTimerClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.Public,
                        title = "Content Region",
                        subtitle = "Explore trending music by country",
                        onClick = {
                            onDismiss()
                            onRegionClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.Settings,
                        title = "Settings",
                        subtitle = "Audio output, appearance & customization",
                        onClick = {
                            onDismiss()
                            onSettingsClick()
                        }
                    )

                    ProfileSheetItem(
                        icon = Icons.Rounded.Info,
                        title = "About Node Player",
                        subtitle = "Version, licenses & release notes",
                        onClick = {
                            onDismiss()
                            onAboutClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}
