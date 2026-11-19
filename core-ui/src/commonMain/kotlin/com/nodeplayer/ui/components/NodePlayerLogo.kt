package com.nodeplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nodeplayer.ui.theme.LocalAccentColor

/**
 * Flagship Node Player Logo.
 * Delegates to the unified [NodePlayerLogoMark] featuring the curved 'N' musical-note glyph.
 */
@Composable
fun NodePlayerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    accentColor: Color = LocalAccentColor.current
) {
    NodePlayerLogoMark(
        modifier = modifier,
        size = size,
        showSquircleBackground = true
    )
}
