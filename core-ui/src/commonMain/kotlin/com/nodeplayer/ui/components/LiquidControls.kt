package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.model.GlassComponentType
import com.nodeplayer.ui.component.shapes.ContinuousCapsule
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.LocalGlassControlsConfig

/**
 * Liquid Glass Continuous Slider with 48dp interactive height and full accessibility semantics.
 * Supports continuous finger dragging, velocity stretch along movement axis, press-squish,
 * and spring release snapping per D-02 and CTRL-01.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    steps: Int = 0,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.SLIDERS, inList = false)
    val effectiveBackdrop = if (resolved.isPlainSquircle || !controlsConfig.masterEnabled) remember { emptyBackdrop() } else backdrop

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps
                )
            },
        contentAlignment = Alignment.Center
    ) {
        com.kyant.backdrop.catalog.components.LiquidSlider(
            value = { value },
            onValueChange = { if (enabled) onValueChange(it) },
            valueRange = valueRange,
            visibilityThreshold = if (steps > 0) (valueRange.endInclusive - valueRange.start) / steps.toFloat() else 0.001f,
            backdrop = effectiveBackdrop
        )
    }
}

/**
 * Alias for [LiquidSlider] for backwards compatibility.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    steps: Int = 0,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    LiquidSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        steps = steps,
        backdrop = backdrop
    )
}

/**
 * Liquid Glass Switch with minimum 48dp touch target and full accessibility semantics.
 * Renders Convx-style liquid glass toggle with tap and drag toggling and spring release snapping.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.TOGGLES, inList = false)
    val isPlain = resolved.isPlainSquircle || !controlsConfig.masterEnabled

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else Modifier.semantics { role = Role.Switch }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPlain) {
            val thumbOffset by animateDpAsState(
                targetValue = if (checked) 24.dp else 2.dp,
                animationSpec = spring(
                    stiffness = resolved.springStiffness,
                    dampingRatio = resolved.springDamping
                ),
                label = "plainToggleThumb"
            )
            val outerShape = ContinuousCapsule()
            val thumbShape = CircleShape
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(30.dp)
                    .clip(outerShape)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                        shape = outerShape
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(26.dp)
                        .clip(thumbShape)
                        .background(Color.White)
                        .border(0.5.dp, Color.White.copy(alpha = 0.8f), thumbShape)
                )
            }
        } else {
            com.kyant.backdrop.catalog.components.LiquidToggle(
                selected = { checked },
                onSelect = { if (enabled && onCheckedChange != null) onCheckedChange(it) },
                backdrop = backdrop
            )
        }
    }
}

/**
 * Standard alias for [LiquidToggle] matching Compose Switch naming.
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    LiquidToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        backdrop = backdrop
    )
}

/**
 * Alias for [LiquidToggle] for backwards compatibility.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    LiquidToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        backdrop = backdrop
    )
}

/**
 * Liquid Glass Button with continuous squircle shape, touch tracking specular highlight,
 * and minimum 48dp touch target. Supports lighter glass styling mode for list performance per D-05.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    backdrop: Backdrop = remember { emptyBackdrop() },
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.BUTTONS, inList = lighterStyle)
    val squircleShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)
    val effectiveLighter = lighterStyle || resolved.isPlainSquircle || !controlsConfig.masterEnabled

    if (effectiveLighter) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) resolved.pressScale else 1f,
            animationSpec = spring(stiffness = resolved.springStiffness, dampingRatio = resolved.springDamping),
            label = "lightBtnScale"
        )
        Row(
            modifier = modifier
                .heightIn(min = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(squircleShape)
                .background(
                    if (surfaceColor != Color.Unspecified) surfaceColor
                    else Color.White.copy(alpha = resolved.tintOpacity.coerceIn(0.04f, 0.40f))
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = (0.35f * resolved.highlightStrength).coerceIn(0.1f, 1f)),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = squircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .heightIn(min = 48.dp)
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            com.kyant.backdrop.catalog.components.LiquidButton(
                onClick = { if (enabled) onClick() },
                backdrop = backdrop,
                isInteractive = enabled,
                tint = tint,
                surfaceColor = surfaceColor,
                contentPadding = contentPadding,
                content = content
            )
        }
    }
}

/**
 * Alias for [LiquidButton] for backwards compatibility.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    backdrop: Backdrop = remember { emptyBackdrop() },
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    LiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        lighterStyle = lighterStyle,
        tint = tint,
        surfaceColor = surfaceColor,
        backdrop = backdrop,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Liquid Glass circular/squircle Icon Button with 48dp minimum touch target.
 * Supports lighter glass styling mode for list performance per D-05.
 */
@Composable
fun LiquidIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    contentDescription: String? = null,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.BUTTONS, inList = lighterStyle)
    val effectiveLighter = lighterStyle || resolved.isPlainSquircle || !controlsConfig.masterEnabled

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) resolved.pressScale else 1f,
        animationSpec = spring(stiffness = resolved.springStiffness, dampingRatio = resolved.springDamping),
        label = "iconBtnScale"
    )
    val squircleShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)
    val effectiveSize = size.coerceAtLeast(48.dp)

    Box(
        modifier = modifier
            .size(effectiveSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(squircleShape)
            .background(
                if (effectiveLighter) Color.White.copy(alpha = (resolved.tintOpacity * 0.3f).coerceIn(0.04f, 0.20f))
                else Color.White.copy(alpha = resolved.tintOpacity.coerceIn(0.08f, 0.35f))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = (0.45f * resolved.highlightStrength).coerceIn(0.12f, 1f)),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = squircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Alias for [LiquidIconButton] for backwards compatibility.
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    contentDescription: String? = null,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    LiquidIconButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        lighterStyle = lighterStyle,
        size = size,
        tint = tint,
        contentDescription = contentDescription,
        backdrop = backdrop
    )
}

/**
 * Liquid Glass Chip with dynamic rim highlight and lens refraction.
 * Enforces minimum 48dp touch height per D-02.
 */
@Composable
fun LiquidChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.CHIPS, inList = lighterStyle)
    val squircleShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)

    val highlightAlpha by animateFloatAsState(
        targetValue = if (selected) resolved.highlightStrength else 0.35f * resolved.highlightStrength,
        label = "chipHighlight"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) (if (lighterStyle || resolved.isPlainSquircle) 0.20f else 0.25f) else (resolved.tintOpacity * 0.25f).coerceIn(0.04f, 0.15f),
        label = "chipBg"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(squircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha)
                else Color.White.copy(alpha = bgAlpha)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = (highlightAlpha * 0.5f).coerceIn(0.08f, 1f)),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = squircleShape
            )
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Alias for [LiquidChip] for backwards compatibility.
 */
@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    LiquidChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        lighterStyle = lighterStyle,
        backdrop = backdrop
    )
}

/**
 * Liquid Glass Segmented Control with sliding selection puck and continuous squircle styling.
 * Each tab segment meets the >= 48dp touch target height requirement.
 */
@Composable
fun <T> LiquidSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = remember { emptyBackdrop() },
    itemLabel: (T) -> String
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.DOCK, inList = false)
    val outerShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)
    val puckShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(outerShape)
            .background(Color.White.copy(alpha = (resolved.tintOpacity * 0.20f).coerceIn(0.04f, 0.15f)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = (0.35f * resolved.highlightStrength).coerceIn(0.1f, 1f)),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = outerShape
            )
            .padding(4.dp)
    ) {
        val count = items.size.coerceAtLeast(1)
        val tabWidth = maxWidth / count
        val targetOffset = tabWidth * selectedIndex
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(dampingRatio = resolved.springDamping, stiffness = resolved.springStiffness),
            label = "liquidPuckOffset"
        )

        // Sliding selection puck with Continuous squircle geometry
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(puckShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = (0.45f * resolved.highlightStrength).coerceIn(0.15f, 1f)),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = puckShape
                )
        )

        // Segments
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Alias for [LiquidSegmentedControl] for backwards compatibility.
 */
@Composable
fun <T> LiquidGlassSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = remember { emptyBackdrop() },
    itemLabel: (T) -> String
) {
    LiquidSegmentedControl(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        backdrop = backdrop,
        itemLabel = itemLabel
    )
}

/**
 * Liquid Glass Modal Dialog surface using continuous squircle backdrop container with
 * subtle blur and specular rim border per D-01.
 */
@Composable
fun LiquidDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val controlsConfig = LocalGlassControlsConfig.current
    val resolved = controlsConfig.resolve(GlassComponentType.DIALOGS, inList = false)
    val dialogShape = ContinuousRoundedRectangle(resolved.cornerShapeDp.dp)

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(dialogShape)
                .background(Color(0xFF1E1E22).copy(alpha = 0.92f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = (0.35f * resolved.highlightStrength).coerceIn(0.12f, 1f)),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = dialogShape
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (title != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        title()
                    }
                }
                if (text != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        text()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                    }
                    confirmButton()
                }
            }
        }
    }
}

/**
 * Liquid Glass Checkbox with continuous squircle container and animated checkmark.
 * Enforces minimum 48dp touch hitbox bounds with Role.Checkbox semantics per D-02.
 */
@Composable
fun LiquidCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val checkShape = ContinuousRoundedRectangle(6.dp)
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
        label = "chkScale"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange
                    )
                } else Modifier.semantics { role = Role.Checkbox }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(checkShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = if (lighterStyle) 0.85f else 0.95f)
                    else Color.White.copy(alpha = 0.08f)
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            if (checked) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = checkShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checkScale > 0.05f) {
                Canvas(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                ) {
                    val strokeWidth = 2.dp.toPx()
                    val p1 = Offset(size.width * 0.15f, size.height * 0.50f)
                    val p2 = Offset(size.width * 0.42f, size.height * 0.80f)
                    val p3 = Offset(size.width * 0.88f, size.height * 0.22f)
                    drawLine(
                        color = Color.White,
                        start = p1,
                        end = p2,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = p2,
                        end = p3,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Liquid Glass Radio Button with circular container and spring-scaled inner indicator dot.
 * Enforces minimum 48dp touch hitbox bounds with Role.RadioButton semantics per D-02.
 */
@Composable
fun LiquidRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lighterStyle: Boolean = false,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val outerShape = CircleShape
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
        label = "radioDotScale"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onClick
                    )
                } else Modifier.semantics { role = Role.RadioButton }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(outerShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = if (lighterStyle) 0.18f else 0.25f)
                    else Color.White.copy(alpha = 0.08f)
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = outerShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (dotScale > 0.05f) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
