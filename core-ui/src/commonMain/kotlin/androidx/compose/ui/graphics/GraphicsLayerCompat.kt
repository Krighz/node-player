package androidx.compose.ui.graphics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.rememberGraphicsLayer as layerRememberGraphicsLayer

@Composable
fun rememberGraphicsLayer(): GraphicsLayer = layerRememberGraphicsLayer()

fun Modifier.graphicsLayer(colorFilter: ColorFilter): Modifier = this.drawWithContent {
    val paint = Paint().apply {
        this.colorFilter = colorFilter
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        canvas.restore()
    }
}
