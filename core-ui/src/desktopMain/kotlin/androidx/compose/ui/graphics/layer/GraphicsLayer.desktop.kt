package androidx.compose.ui.graphics.layer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asDesktopImageFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

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
        val w = if (size.width > 0) size.width.toFloat() else 1f
        val h = if (size.height > 0) size.height.toFloat() else 1f

        val recorder = PictureRecorder()
        val skiaCanvas = recorder.beginRecording(Rect.makeWH(w, h))
        val composeCanvas = skiaCanvas.asComposeCanvas()

        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = Size(w, h)
        ) {
            block()
        }
        picture = recorder.finishRecordingAsPicture()
        recorder.close()
    }
}

actual fun DrawScope.drawLayer(graphicsLayer: GraphicsLayer) {
    val pic = graphicsLayer.picture ?: return
    drawIntoCanvas { canvas ->
        val skiaCanvas = canvas.nativeCanvas
        skiaCanvas.save()
        if (graphicsLayer.topLeft != IntOffset.Zero) {
            skiaCanvas.translate(graphicsLayer.topLeft.x.toFloat(), graphicsLayer.topLeft.y.toFloat())
        }
        val effect = graphicsLayer.renderEffect
        if (effect != null || graphicsLayer.alpha < 1f || graphicsLayer.blendMode != BlendMode.SrcOver) {
            val paint = Paint()
            if (effect != null) {
                paint.imageFilter = effect.asDesktopImageFilter()
            }
            if (graphicsLayer.alpha < 1f) {
                paint.alpha = (graphicsLayer.alpha.coerceIn(0f, 1f) * 255).toInt()
            }
            skiaCanvas.saveLayer(null, paint)
            skiaCanvas.drawPicture(pic)
            skiaCanvas.restore()
            paint.close()
        } else {
            skiaCanvas.drawPicture(pic)
        }
        skiaCanvas.restore()
    }
}
