package com.nodeplayer.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.music.ui.theme.GlassTokens
import com.nodeplayer.music.ui.theme.glassSurface
import com.nodeplayer.music.ui.theme.pressScale

typealias HazeState = Any

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(GlassTokens.cardCornerRadius),
    isOled: Boolean = false,
    isDark: Boolean = true,
    tintAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.pressScale(targetScale = 0.97f, onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickModifier)
    ) {
        // 1. Frosted Glass Background Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSurface(
                    
                    shape = shape,
                    isOled = isOled,
                    isDark = isDark,
                    tintAlpha = tintAlpha
                )
        )
        // 2. High-contrast Crisp Foreground Content
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    tint: Color = Color.White,
    size: Dp = 44.dp
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSurface(
                    
                    shape = shape,
                    tintAlpha = 0.5f
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
