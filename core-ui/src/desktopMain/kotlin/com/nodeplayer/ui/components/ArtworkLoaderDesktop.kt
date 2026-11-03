package com.nodeplayer.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual object ArtworkImageDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        return try {
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
