package com.nodeplayer.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.music.ui.components.GlassCard
import com.nodeplayer.music.ui.theme.LocalAccentColor
import com.nodeplayer.music.ui.theme.MotionTokens
import com.nodeplayer.music.ui.theme.pressScale
import com.nodeplayer.ui.components.LiquidChip
import com.nodeplayer.ui.components.LiquidSlider
import com.nodeplayer.ui.components.LiquidToggle
import kotlin.math.roundToInt

@Composable
fun AnimationPlaygroundScreen(
    themeConfig: GlassThemeConfig,
    onThemeConfigChange: (GlassThemeConfig) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val accent = LocalAccentColor.current
    var interactiveBoxToggled by remember { mutableStateOf(false) }
    var tapCounter by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var testSliderValue by remember { mutableFloatStateOf(0.5f) }
    var testSwitchState by remember { mutableStateOf(true) }

    val activeSpring = remember(themeConfig) {
        MotionTokens.resolveSpringSpec(themeConfig, MotionTokens.Smooth)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Motion & Animations",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "iOS-grade spring physics, speed & gesture dials",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 1. Animation Preset Picker
        GlassCard(modifier = Modifier.fillMaxWidth(), ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ANIMATION PRESET",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("OFF" to "Off", "MINIMAL" to "Minimal", "SMOOTH" to "Smooth (Default)", "BOUNCY" to "Bouncy", "CUSTOM" to "Custom").forEach { (preset, label) ->
                        LiquidChip(
                            selected = themeConfig.animationPreset == preset,
                            onClick = {
                                onThemeConfigChange(themeConfig.copy(animationPreset = preset))
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Motion Speed Dial / Slider (0.5x to 2.0x)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Motion Speed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("%.1fx".format(themeConfig.motionSpeed), style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                }
                LiquidSlider(
                    value = themeConfig.motionSpeed,
                    onValueChange = { onThemeConfigChange(themeConfig.copy(motionSpeed = it)) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                if (themeConfig.animationPreset == "CUSTOM") {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Bounciness (damping ratio)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Custom Bounciness (Damping)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("%.2f".format(themeConfig.springBounciness), style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                    }
                    LiquidSlider(
                        value = themeConfig.springBounciness,
                        onValueChange = { onThemeConfigChange(themeConfig.copy(springBounciness = it)) },
                        valueRange = 0.35f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )

                    // Stiffness
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Custom Stiffness", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("${themeConfig.springStiffness.roundToInt()}", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                    }
                    LiquidSlider(
                        value = themeConfig.springStiffness,
                        onValueChange = { onThemeConfigChange(themeConfig.copy(springStiffness = it)) },
                        valueRange = 150f..1000f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Motion Behavior Toggles
        GlassCard(modifier = Modifier.fillMaxWidth(), ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MOTION BEHAVIORS & TOGGLES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Reduce motion
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce Motion", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Replaces springs and large slides with immediate cuts", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.reduceMotion,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(reduceMotion = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                // Switch overshoot
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Switch & Toggle Overshoot", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Thumb springs slightly past destination before resting", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.switchOvershootEnabled && !themeConfig.reduceMotion,
                        enabled = !themeConfig.reduceMotion,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(switchOvershootEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                // Press scale effect
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Press-Scale Feedback (.pressScale)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Cards and buttons compress slightly under your touch", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.pressScaleEnabled && !themeConfig.reduceMotion,
                        enabled = !themeConfig.reduceMotion,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(pressScaleEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                if (themeConfig.pressScaleEnabled && !themeConfig.reduceMotion) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Press Compression Depth", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f)))
                        Text("${((1.0f - themeConfig.pressScaleStrength) * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold))
                    }
                    LiquidSlider(
                        value = themeConfig.pressScaleStrength,
                        onValueChange = { onThemeConfigChange(themeConfig.copy(pressScaleStrength = it)) },
                        valueRange = 0.90f..0.98f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                // Staggered list animation
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Staggered List Cascade", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Smooth staggered entrance cascade for initial screen lists", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.staggeredListAnimation && !themeConfig.reduceMotion,
                        enabled = !themeConfig.reduceMotion,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(staggeredListAnimation = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                // Lyrics animation
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lyrics Active Line Springs", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text("Smooth scale & focal blur for active karaoke lyric lines", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.60f)))
                    }
                    LiquidToggle(
                        checked = themeConfig.lyricsAnimationEnabled && !themeConfig.reduceMotion,
                        enabled = !themeConfig.reduceMotion,
                        onCheckedChange = { onThemeConfigChange(themeConfig.copy(lyricsAnimationEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                // Screen transition style
                Text("Screen Transition Style", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SLIDE" to "Spring Slide", "FADE" to "Crossfade", "SHARED" to "Shared Expand").forEach { (style, label) ->
                        LiquidChip(
                            selected = themeConfig.screenTransitionStyle == style,
                            onClick = { onThemeConfigChange(themeConfig.copy(screenTransitionStyle = style)) },
                            label = { Text(label, fontSize = 12.sp) },
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

        // 3. Interactive Touch & Spring Testing Area
        GlassCard(modifier = Modifier.fillMaxWidth(), ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "INTERACTIVE TESTING SUITE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.sp)
                )
                Text(
                    text = "Tune springs by feel with live interactive elements below",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Test Press-scale card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))))
                        .pressScale(targetScale = themeConfig.pressScaleStrength) {
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
                            text = "Tapped $tapCounter times • Press to Compress",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Draggable Puck Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedSpringX.roundToInt(), animatedSpringY.roundToInt()) }
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = animatedToggleScale
                                scaleY = animatedToggleScale
                            }
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
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

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Switch Test with Overshoot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interactive Switch Test", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                    LiquidToggle(
                        checked = testSwitchState,
                        onCheckedChange = {
                            testSwitchState = it
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reset Motion Button
                OutlinedButton(
                    onClick = {
                        onThemeConfigChange(
                            themeConfig.copy(
                                animationPreset = "SMOOTH",
                                motionSpeed = 1.0f,
                                springBounciness = 0.82f,
                                springStiffness = 380f,
                                switchOvershootEnabled = true,
                                pressScaleEnabled = true,
                                pressScaleStrength = 0.96f,
                                staggeredListAnimation = true,
                                lyricsAnimationEnabled = true,
                                screenTransitionStyle = "SLIDE",
                                reduceMotion = false
                            )
                        )
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Motion to Default (Smooth)")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
