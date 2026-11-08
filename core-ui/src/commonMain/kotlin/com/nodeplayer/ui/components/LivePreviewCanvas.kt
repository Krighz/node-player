package com.nodeplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nodeplayer.model.GlassComponentType
import com.nodeplayer.model.GlassControlsConfig
import com.nodeplayer.model.ResolvedGlassParams
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * GPU cost classification levels based on AGSL/SkSL shader pipeline load.
 */
enum class GpuCostCategory(
    val displayName: String,
    val color: Color,
    val targetFps: String
) {
    LOW("Low", Color(0xFF10B981), "~120fps (Zero overhead)"),
    MODERATE("Moderate", Color(0xFF06B6D4), "~120fps (Smooth)"),
    HIGH("High", Color(0xFFF59E0B), "~90-120fps (Balanced)"),
    INTENSE("Intense", Color(0xFFF43F5E), "~60fps (Heavy shader load)")
}

/**
 * Calibrated GPU cost estimate for current glass configuration.
 */
data class GpuCostEstimate(
    val estimatedMs: Float,
    val category: GpuCostCategory,
    val targetFps: String
)

/**
 * Computes estimated frame-time impact using empirical formula calibrated against shader passes:
 * - Master Off: 0.8ms (Low)
 * - Blur: (blurDp / 32) * 2.8ms
 * - Lens: ((lensHeight + lensAmount) / 62) * 2.2ms
 * - Chromatic: if > 0.05, aberration * 1.4ms
 * - Overrides: +0.25ms for each non-lightened override
 */
fun calculateGpuCost(config: GlassControlsConfig): GpuCostEstimate {
    if (!config.masterEnabled) {
        return GpuCostEstimate(
            estimatedMs = 0.8f,
            category = GpuCostCategory.LOW,
            targetFps = GpuCostCategory.LOW.targetFps
        )
    }

    val baseMs = 1.0f
    val blurMs = (config.global.blurDp.coerceIn(0f, 32f) / 32f) * 2.8f
    val lensMs = ((config.global.lensHeightDp.coerceIn(0f, 32f) + config.global.lensAmountDp.coerceIn(0f, 30f)) / 62f) * 2.2f
    val chromaticMs = if (config.global.chromaticAberration > 0.05f) {
        config.global.chromaticAberration.coerceIn(0f, 1f) * 1.4f
    } else 0f

    var overrideCost = 0f
    GlassComponentType.values().forEach { type ->
        val comp = config.getComponent(type)
        if (!comp.useGlobal && !comp.forceLighterInLists) {
            overrideCost += 0.25f
        }
    }

    val totalMs = baseMs + blurMs + lensMs + chromaticMs + overrideCost
    val category = when {
        totalMs < 3.0f -> GpuCostCategory.LOW
        totalMs < 6.5f -> GpuCostCategory.MODERATE
        totalMs < 10.5f -> GpuCostCategory.HIGH
        else -> GpuCostCategory.INTENSE
    }

    return GpuCostEstimate(
        estimatedMs = totalMs,
        category = category,
        targetFps = category.targetFps
    )
}

/**
 * GPU cost meter badge pill rendered in the top-right corner of LivePreviewCanvas.
 */
@Composable
fun GpuCostPill(
    cost: GpuCostEstimate,
    modifier: Modifier = Modifier
) {
    val roundedMs = ((cost.estimatedMs * 10f).roundToInt() / 10f).toString()
    val animatedColor by animateColorAsState(
        targetValue = cost.category.color,
        animationSpec = tween(durationMillis = 300),
        label = "gpuPillColor"
    )

    Row(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(Color.Black.copy(alpha = 0.60f))
            .border(
                width = 1.dp,
                color = animatedColor.copy(alpha = 0.65f),
                shape = ContinuousCapsule()
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Glowing status indicator dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(animatedColor)
        )
        Text(
            text = "GPU: ${cost.category.displayName} (${roundedMs}ms)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 11.sp
            )
        )
        Text(
            text = "• ${cost.targetFps.substringBefore(" ")}",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp
            )
        )
    }
}

/**
 * Animated dynamic caustics and chromatic metaballs backdrop.
 * High-contrast filaments and moving fluid light patches maximize visibility of refraction.
 */
@Composable
fun DynamicPreviewCausticsBackdrop(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "causticsLoop")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "causticPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep obsidian base
        drawRect(Color(0xFF0B0E17))

        // Harmonic chromatic orb 1 (Accent color)
        val x1 = w * (0.35f + 0.20f * sin(phase))
        val y1 = h * (0.40f + 0.25f * cos(phase))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(x1, y1),
                radius = w * 0.45f
            ),
            center = Offset(x1, y1),
            radius = w * 0.45f
        )

        // Harmonic chromatic orb 2 (Cyan / Turquoise)
        val x2 = w * (0.70f + 0.18f * cos(phase + 1.5f))
        val y2 = h * (0.65f + 0.20f * sin(phase + 1.2f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.70f), Color(0xFF06B6D4).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(x2, y2),
                radius = w * 0.40f
            ),
            center = Offset(x2, y2),
            radius = w * 0.40f
        )

        // Harmonic chromatic orb 3 (Magenta / Rose)
        val x3 = w * (0.20f + 0.15f * cos(phase * 0.8f))
        val y3 = h * (0.75f - 0.22f * sin(phase * 0.9f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEC4899).copy(alpha = 0.65f), Color(0xFFEC4899).copy(alpha = 0.05f), Color.Transparent),
                center = Offset(x3, y3),
                radius = w * 0.35f
            ),
            center = Offset(x3, y3),
            radius = w * 0.35f
        )

        // Caustic high-contrast optical refraction filaments
        val causticPath = Path()
        val segments = 12
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val cx = w * t
            val cy = h * (0.50f + 0.18f * sin(phase + t * 4f) * cos(phase * 0.5f + t * 2f))
            if (i == 0) causticPath.moveTo(cx, cy) else causticPath.lineTo(cx, cy)
        }
        drawPath(
            path = causticPath,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
                    Color(0xFF67E8F9).copy(alpha = 0.60f),
                    Color.White.copy(alpha = 0.40f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

/**
 * Pinned interactive Live Preview Canvas presenting a complete miniature component suite
 * over dynamic animated caustics backdrop with real-time GPU cost meter.
 */
@Composable
fun LivePreviewCanvas(
    config: GlassControlsConfig,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val gpuCost = remember(config) { calculateGpuCost(config) }
    val canvasShape = remember { ContinuousRoundedRectangle(22.dp) }
    val previewBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(canvasShape)
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = canvasShape
            )
    ) {
        // 1. Animated Caustics Backdrop Layer captured into stable backdrop instance
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(previewBackdrop)
        ) {
            DynamicPreviewCausticsBackdrop(accent = accent)
        }

        // 2. Foreground Container with Miniature Component Suite
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title & Calibrated GPU Cost Meter Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Preview,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LIVE OPTICAL PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.90f),
                            letterSpacing = 1.2.sp
                        )
                    )
                }
                GpuCostPill(cost = gpuCost)
            }

            // Suite Item 1: Miniature Mini Player Capsule
            MiniaturePreviewMiniPlayer(
                config = config,
                backdrop = previewBackdrop,
                accent = accent
            )

            // Suite Item 2: Interactive Controls Row (Button, Toggle, Chip)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniaturePreviewButton(
                    config = config,
                    backdrop = previewBackdrop,
                    accent = accent,
                    modifier = Modifier.weight(1.1f)
                )
                MiniaturePreviewToggle(
                    config = config,
                    accent = accent
                )
                MiniaturePreviewChip(
                    config = config,
                    accent = accent,
                    modifier = Modifier.weight(1.0f)
                )
            }

            // Suite Item 3: Miniature Continuous Slider with squish
            MiniaturePreviewSlider(
                config = config,
                accent = accent
            )

            // Suite Item 4: Miniature Dock Capsule with interactive sliding puck
            MiniaturePreviewDock(
                config = config,
                backdrop = previewBackdrop,
                accent = accent
            )
        }
    }
}

/**
 * Miniature Mini Player capsule for LivePreviewCanvas.
 */
@Composable
private fun MiniaturePreviewMiniPlayer(
    config: GlassControlsConfig,
    backdrop: Backdrop,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.MINI_PLAYER) }
    val shape = remember { ContinuousRoundedRectangle(16.dp) }
    val artShape = remember { ContinuousRoundedRectangle(8.dp) }
    var isPlaying by remember { mutableStateOf(true) }

    val glassModifier = if (!resolved.isPlainSquircle && config.masterEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(resolved.blurDp.dp.toPx())
                lens(
                    resolved.lensHeightDp.dp.toPx(),
                    resolved.lensAmountDp.dp.toPx(),
                    chromaticAberration = resolved.chromaticAberration > 0.05f
                )
                vibrancy()
            },
            highlight = {
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = (0.75f * resolved.highlightStrength).coerceIn(0.1f, 1f)
                )
            },
            shadow = {
                Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.30f * resolved.shadowStrength))
            }
        )
    } else {
        Modifier
            .clip(shape)
            .background(Color(0xFF141923).copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(glassModifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini album artwork squircle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(artShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent, Color(0xFF1E1B4B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = "Liquid Glass Revamp",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "120fps Spring Dynamics • Convx",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Interactive Play/Pause Mini Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Miniature Liquid Button with spring press scale.
 */
@Composable
private fun MiniaturePreviewButton(
    config: GlassControlsConfig,
    backdrop: Backdrop,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.BUTTONS) }
    val shape = remember { ContinuousRoundedRectangle(percent = 50) }
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) resolved.pressScale else 1f,
        animationSpec = spring(
            stiffness = resolved.springStiffness,
            dampingRatio = resolved.springDamping
        ),
        label = "miniBtnScale"
    )

    val glassModifier = if (!resolved.isPlainSquircle && config.masterEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(resolved.blurDp.dp.toPx())
                lens(
                    resolved.lensHeightDp.dp.toPx(),
                    resolved.lensAmountDp.dp.toPx(),
                    chromaticAberration = resolved.chromaticAberration > 0.05f
                )
                vibrancy()
            },
            highlight = {
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = (0.75f * resolved.highlightStrength).coerceIn(0.1f, 1f)
                )
            }
        )
    } else {
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
    }

    Box(
        modifier = modifier
            .height(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(glassModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Button",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * Miniature Liquid Toggle Switch with draggable/tappable puck.
 */
@Composable
private fun MiniaturePreviewToggle(
    config: GlassControlsConfig,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.TOGGLES) }
    var checked by remember { mutableStateOf(true) }
    val outerShape = remember { ContinuousCapsule() }
    val thumbShape = remember { CircleShape }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(
            stiffness = resolved.springStiffness,
            dampingRatio = resolved.springDamping
        ),
        label = "miniToggleOffset"
    )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(outerShape)
            .background(
                if (checked) accent.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(
                width = 1.dp,
                color = if (checked) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.25f),
                shape = outerShape
            )
            .clickable { checked = !checked },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(thumbShape)
                .background(Color.White)
                .border(0.5.dp, Color.White.copy(alpha = 0.8f), thumbShape)
        )
    }
}

/**
 * Miniature Filter/Action Chip with animated rim highlight.
 */
@Composable
private fun MiniaturePreviewChip(
    config: GlassControlsConfig,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.CHIPS) }
    val shape = remember { ContinuousRoundedRectangle(percent = 50) }
    var selected by remember { mutableStateOf(true) }

    val highlightAlpha by animateFloatAsState(
        targetValue = if (selected) resolved.highlightStrength else 0.25f,
        label = "miniChipHighlight"
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = highlightAlpha * 0.5f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .clickable { selected = !selected }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.Tune,
                contentDescription = null,
                tint = if (selected) accent else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Chip",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * Miniature Continuous Slider with touch-drag consuming pointer events to avoid outer scroll conflicts (T-04-05).
 */
@Composable
private fun MiniaturePreviewSlider(
    config: GlassControlsConfig,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.SLIDERS) }
    var sliderFraction by remember { mutableFloatStateOf(0.65f) }
    var isDragging by remember { mutableStateOf(false) }
    val trackShape = remember { ContinuousCapsule() }

    val squishScale by animateFloatAsState(
        targetValue = if (isDragging) resolved.pressScale else 1f,
        animationSpec = spring(
            stiffness = resolved.springStiffness,
            dampingRatio = resolved.springDamping
        ),
        label = "miniSliderSquish"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .graphicsLayer {
                scaleY = squishScale
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        sliderFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        sliderFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val activeWidth = totalWidth * sliderFraction

        // Background Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(trackShape)
                .background(Color.White.copy(alpha = 0.15f))
        )

        // Active Track Fill
        Box(
            modifier = Modifier
                .width(activeWidth)
                .height(8.dp)
                .clip(trackShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.70f), accent)
                    )
                )
        )

        // Draggable Thumb Puck
        Box(
            modifier = Modifier
                .offset(x = (activeWidth - 10.dp).coerceAtLeast(0.dp))
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, accent, CircleShape)
        )
    }
}

/**
 * Miniature Dock capsule with sliding puck responding to tab clicks per D-10, D-12.
 */
@Composable
private fun MiniaturePreviewDock(
    config: GlassControlsConfig,
    backdrop: Backdrop,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val resolved = remember(config) { config.resolve(GlassComponentType.DOCK) }
    val dockShape = remember { ContinuousCapsule() }
    val puckShape = remember { ContinuousRoundedRectangle(12.dp) }
    var activeTabIndex by remember { mutableIntStateOf(1) }

    val tabs = listOf(
        Icons.Rounded.Home to "Home",
        Icons.Rounded.LibraryMusic to "Library",
        Icons.Rounded.Tune to "Customize"
    )

    val glassModifier = if (!resolved.isPlainSquircle && config.masterEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { dockShape },
            effects = {
                blur(resolved.blurDp.dp.toPx())
                lens(
                    resolved.lensHeightDp.dp.toPx(),
                    resolved.lensAmountDp.dp.toPx(),
                    chromaticAberration = resolved.chromaticAberration > 0.05f
                )
                vibrancy()
            },
            highlight = {
                Highlight.Ambient.copy(
                    width = 1.2.dp,
                    alpha = (0.75f * resolved.highlightStrength).coerceIn(0.1f, 1f)
                )
            },
            shadow = {
                Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.35f * resolved.shadowStrength))
            }
        )
    } else {
        Modifier
            .clip(dockShape)
            .background(Color(0xFF141923).copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = dockShape
            )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(glassModifier)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = maxWidth / tabs.size
        val animatedPuckOffset by animateDpAsState(
            targetValue = tabWidth * activeTabIndex,
            animationSpec = spring(
                stiffness = resolved.springStiffness,
                dampingRatio = resolved.springDamping
            ),
            label = "miniPuckOffset"
        )

        // Sliding selection puck
        Box(
            modifier = Modifier
                .offset(x = animatedPuckOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(puckShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = puckShape
                )
        )

        // Navigation Tabs Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (icon, label) ->
                val isSelected = index == activeTabIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { activeTabIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) accent else Color.White.copy(alpha = 0.50f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accent else Color.White.copy(alpha = 0.50f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
