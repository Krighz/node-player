package com.nodeplayer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nodeplayer.model.Track
import java.io.File

actual object PlatformArtworkProvider {

    actual suspend fun loadArtwork(track: Track): ImageBitmap? {
        val path = track.path
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val picture = mmr.embeddedPicture
                if (picture != null) {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, opts)
                    bitmap?.asImageBitmap()
                } else {
                    null
                }
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }
}
