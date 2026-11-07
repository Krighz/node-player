package androidx.compose.ui.graphics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.rememberGraphicsLayer as layerRememberGraphicsLayer

@Composable
fun rememberGraphicsLayer(): GraphicsLayer = layerRememberGraphicsLayer()
