package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.component.backdrop.Backdrop
import com.nodeplayer.ui.component.backdrop.backdrops.emptyBackdrop
import com.nodeplayer.ui.component.backdrop.catalog.components.LiquidButton
import com.nodeplayer.ui.component.backdrop.catalog.components.LiquidSlider
import com.nodeplayer.ui.component.backdrop.catalog.components.LiquidToggle
import com.nodeplayer.ui.component.backdrop.drawBackdrop
import com.nodeplayer.ui.component.backdrop.effects.blur
import com.nodeplayer.ui.component.backdrop.effects.lens
import com.nodeplayer.ui.component.backdrop.highlight.Highlight
import com.nodeplayer.ui.component.backdrop.shadow.Shadow

/**
 * Liquid Glass Switch with minimum 48dp touch target and full accessibility semantics.
 * Renders Convx-style liquid glass toggle with dynamic refraction and specular rim.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
        LiquidToggle(
            selected = { checked },
            onSelect = { if (enabled && onCheckedChange != null) onCheckedChange(it) },
            backdrop = backdrop
        )
    }
}

/**
 * Liquid Glass Continuous Slider with 48dp interactive height and full accessibility semantics.
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
        LiquidSlider(
            value = { value },
            onValueChange = { if (enabled) onValueChange(it) },
            valueRange = valueRange,
            visibilityThreshold = if (steps > 0) (valueRange.endInclusive - valueRange.start) / steps.toFloat() else 0.001f,
            backdrop = backdrop
        )
    }
}

/**
 * Liquid Glass Button with continuous squircle shape, touch tracking specular highlight,
 * and minimum 48dp touch target.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    backdrop: Backdrop = remember { emptyBackdrop() },
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            isInteractive = enabled,
            tint = tint,
            surfaceColor = surfaceColor,
            content = content
        )
    }
}

/**
 * Liquid Glass circular/squircle Icon Button with 48dp minimum touch target.
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    contentDescription: String? = null,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size.coerceAtLeast(48.dp))
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
            .clickable(
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
 * Liquid Glass Segmented Control with sliding selection puck and continuous squircle styling.
 * Each tab segment meets the >= 48dp touch target height requirement.
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
    val shape = RoundedCornerShape(percent = 50)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .padding(4.dp)
    ) {
        val count = items.size.coerceAtLeast(1)
        val tabWidth = maxWidth / count
        val targetOffset = tabWidth * selectedIndex
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
            label = "liquidPuckOffset"
        )

        // Sliding selection puck (plain-Compose glass)
        val puckShape = RoundedCornerShape(percent = 50)
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
                            Color.White.copy(alpha = 0.35f),
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
 * Liquid Glass Chip with dynamic rim highlight and lens refraction.
 * Minimum 48dp touch height.
 */
@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    backdrop: Backdrop = remember { emptyBackdrop() }
) {
    val shape = RoundedCornerShape(percent = 50)
    val highlightAlpha by animateFloatAsState(
        targetValue = if (selected) 0.85f else 0.35f,
        label = "chipHighlight"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.22f else 0.08f,
        label = "chipBg"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha)
                else Color.White.copy(alpha = bgAlpha)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = highlightAlpha * 0.4f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
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
