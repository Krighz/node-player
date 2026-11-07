package com.nodeplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodeplayer.ui.component.shapes.ContinuousRoundedRectangle
import com.nodeplayer.ui.theme.GlassTokens
import com.nodeplayer.ui.theme.glassSurface
import com.nodeplayer.ui.theme.pressScale

import androidx.compose.foundation.background
import androidx.compose.foundation.border

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    customTint: Color? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.pressScale(targetScale = 0.97f, onClick = onClick)
    } else Modifier

    val baseColor = when {
        isOled -> Color.Black
        customTint != null -> customTint
        else -> Color(0xFF161B2E)
    }
    val alpha = tintAlpha ?: if (isOled) 0.92f else 0.55f
    val surfaceColor = baseColor.copy(alpha = alpha)
    val effectiveBorderColor = borderColor ?: Color.White.copy(alpha = if (isOled) 0.15f else 0.08f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor, shape)
            .border(borderWidth, effectiveBorderColor, shape)
            .then(clickModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}

@Composable
fun SquircleCard(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(16.dp),
    backgroundColor: Color = Color(0xFF161B2E).copy(alpha = 0.55f),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.pressScale(targetScale = 0.97f, onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape)
            .then(clickModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(GlassTokens.pillCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = shape,
        isOled = isOled,
        isDark = isDark,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    contentDescription: String? = null
) {
    GlassCard(
        modifier = modifier.size(size),
        shape = CircleShape,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size * 0.52f)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF6366F1),
    textColor: Color = Color.White
) {
    val badgeShape = remember { ContinuousRoundedRectangle(6.dp) }
    Box(
        modifier = modifier
            .clip(badgeShape)
            .background(color.copy(alpha = 0.28f), badgeShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), badgeShape)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
