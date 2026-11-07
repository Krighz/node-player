package androidx.compose.ui.node

import androidx.compose.ui.graphics.layer.GraphicsContext

private val GlobalGraphicsContext = GraphicsContext()

fun DelegatableNode.requireGraphicsContext(): GraphicsContext = GlobalGraphicsContext
