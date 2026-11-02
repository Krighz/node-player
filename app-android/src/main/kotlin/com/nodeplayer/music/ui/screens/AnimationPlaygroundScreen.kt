package com.nodeplayer.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.MotionTokens
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.music.ui.theme.pressScale
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

@Composable
fun AnimationPlaygroundScreen(
    onBack: () -> Unit = {},
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val accent = LocalAccentColor.current
    var selectedSpringProfile by remember { mutableStateOf("Smooth") }
    var interactiveBoxToggled by remember { mutableStateOf(false) }
    var tapCounter by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val activeSpring = when (selectedSpringProfile) {
        "Snappy" -> MotionTokens.Snappy
        "Bouncy" -> MotionTokens.Bouncy
        "Gentle" -> MotionTokens.Gentle
        else -> MotionTokens.Smooth
    }

    val animatedToggleScale by animateFloatAsState(
        targetValue = if (interactiveBoxToggled) 1.15f else 1.0f,
        animationSpec = activeSpring,
        label = "ToggleScale"
    )

    val animatedSpringX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = activeSpring,
        label = "DragSpringX"
    )
    val animatedSpringY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = activeSpring,
        label = "DragSpringY"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Motion Playground",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "Interactive iOS-style spring physics & touch testing",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Spring Profile Selector
        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ACTIVE SPRING SPECIFICATION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Snappy", "Smooth", "Bouncy", "Gentle").forEach { profile ->
                        FilterChip(
                            selected = selectedSpringProfile == profile,
                            onClick = {
                                selectedSpringProfile = profile
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                            label = { Text(profile, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Press-Scale Card Test
        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TOUCH SCALE TEST (.pressScale)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Text(
                    text = "Tap or press-hold to feel tactile spring compression",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6)
                                )
                            )
                        )
                        .pressScale(targetScale = 0.94f) {
                            tapCounter++
                            interactiveBoxToggled = !interactiveBoxToggled
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pressed $tapCounter times (Toggled: $interactiveBoxToggled)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Draggable Spring Puck
        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DRAG & RELEASE SPRING PHYSICS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Text(
                    text = "Drag the puck anywhere; release to see it snap back with the active profile",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedSpringX.roundToInt(), animatedSpringY.roundToInt()) }
                            .size(60.dp)
                            .graphicsLayer {
                                scaleX = animatedToggleScale
                                scaleY = animatedToggleScale
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF0284C7)
                                    )
                                )
                            )
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    },
                                    onDragEnd = {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    },
                                    onDragCancel = {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-120f, 120f)
                                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-50f, 50f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AllOut, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
