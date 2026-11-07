package androidx.compose.ui.graphics

import android.os.Build
import androidx.annotation.RequiresApi

fun android.graphics.Canvas.asComposeCanvas(): Canvas = Canvas(this)

@RequiresApi(Build.VERSION_CODES.S)
fun RenderEffect.asAndroidRenderEffect(): android.graphics.RenderEffect {
    return try {
        val method = this::class.java.getMethod("getAndroidRenderEffect")
        method.invoke(this) as android.graphics.RenderEffect
    } catch (_: Throwable) {
        val field = this::class.java.getDeclaredField("androidRenderEffect").apply { isAccessible = true }
        field.get(this) as android.graphics.RenderEffect
    }
}
