package androidx.compose.ui.graphics.layer

import android.graphics.Picture
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidRenderEffect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.setAndroidRenderEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

actual class GraphicsLayer actual constructor() {
    actual var topLeft: IntOffset = IntOffset.Zero
    actual var renderEffect: RenderEffect? = null
    actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
    actual var size: IntSize = IntSize.Zero
    actual var alpha: Float = 1f
    actual var blendMode: BlendMode = BlendMode.SrcOver

    internal var picture: Picture? = null
    private val drawScope = CanvasDrawScope()

    actual fun record(
        size: IntSize,
        block: DrawScope.() -> Unit
    ) {
        this.size = size
        val w = if (size.width > 0) size.width else 1
        val h = if (size.height > 0) size.height else 1

        val pic = Picture()
        val androidCanvas = pic.beginRecording(w, h)
        val composeCanvas = androidCanvas.asComposeCanvas()

        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = Size(w.toFloat(), h.toFloat())
        ) {
            block()
        }
        pic.endRecording()
        picture = pic
    }
}

actual fun DrawScope.drawLayer(graphicsLayer: GraphicsLayer) {
    val pic = graphicsLayer.picture ?: return
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        nativeCanvas.save()
        if (graphicsLayer.topLeft != IntOffset.Zero) {
            nativeCanvas.translate(graphicsLayer.topLeft.x.toFloat(), graphicsLayer.topLeft.y.toFloat())
        }
        val effect = graphicsLayer.renderEffect
        val needsLayer = effect != null || graphicsLayer.alpha < 1f || graphicsLayer.blendMode != BlendMode.SrcOver

        if (needsLayer) {
            val paint = android.graphics.Paint()
            if (effect != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                paint.setAndroidRenderEffect(effect.asAndroidRenderEffect())
            }
            if (graphicsLayer.alpha < 1f) {
                paint.alpha = (graphicsLayer.alpha.coerceIn(0f, 1f) * 255).toInt()
            }
            nativeCanvas.saveLayer(null, paint)
            nativeCanvas.drawPicture(pic)
            nativeCanvas.restore()
        } else {
            nativeCanvas.drawPicture(pic)
        }
        nativeCanvas.restore()
    }
}
