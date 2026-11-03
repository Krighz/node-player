package com.nodeplayer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual object ArtworkImageDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
