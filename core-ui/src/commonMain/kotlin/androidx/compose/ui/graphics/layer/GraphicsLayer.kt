package androidx.compose.ui.graphics.layer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

expect class GraphicsLayer() {
    var topLeft: IntOffset
    var renderEffect: RenderEffect?
    var compositingStrategy: CompositingStrategy
    var size: IntSize
    var alpha: Float
    var blendMode: BlendMode
    fun record(size: IntSize = IntSize.Zero, block: DrawScope.() -> Unit)
}

expect fun DrawScope.drawLayer(graphicsLayer: GraphicsLayer)

@Composable
fun rememberGraphicsLayer(): GraphicsLayer {
    return remember { GraphicsLayer() }
}

@kotlin.jvm.JvmInline
value class CompositingStrategy internal constructor(val value: Int) {
    companion object {
        val Auto = CompositingStrategy(0)
        val Offscreen = CompositingStrategy(1)
        val ModulateAlpha = CompositingStrategy(2)
    }
}

class GraphicsContext {
    fun createGraphicsLayer(): GraphicsLayer = GraphicsLayer()
    fun releaseGraphicsLayer(layer: GraphicsLayer) {}
}
