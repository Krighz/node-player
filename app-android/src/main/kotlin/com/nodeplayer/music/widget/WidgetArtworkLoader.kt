package com.nodeplayer.music.widget

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.nodeplayer.model.Track
import java.io.File

object WidgetArtworkLoader {

    fun loadArtwork(context: Context, track: Track?): Bitmap? {
        if (track == null) return null

        // 1. Android 10+ MediaStore thumbnail by track ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.id > 0) {
            try {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
                val thumb = context.contentResolver.loadThumbnail(trackUri, Size(512, 512), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        // 2. Embedded picture via MediaMetadataRetriever from file path
        if (track.path.isNotBlank()) {
            val file = File(track.path)
            if (file.exists() && file.canRead()) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val pic = mmr.embeddedPicture
                    if (pic != null) {
                        val thumb = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        if (thumb != null) return thumb
                    }
                } catch (_: Exception) {
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            }
        }

        // 3. MediaStore album art URI
        if (track.albumId > 0) {
            try {
                val uri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val thumb = BitmapFactory.decodeStream(stream)
                    if (thumb != null) return thumb
                }
            } catch (_: Exception) {}
        }

        // 4. Android 10+ MediaStore thumbnail by album ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.albumId > 0) {
            try {
                val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, track.albumId)
                val thumb = context.contentResolver.loadThumbnail(albumUri, Size(512, 512), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        // 5. Procedural elegant fallback artwork (so the widget artwork box is never empty)
        return generateFallbackArtwork(track.title)
    }

    fun generateFallbackArtwork(seed: String): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val hash = seed.hashCode()
        val c1 = 0xFF000000.toInt() or (hash and 0x00FFFFFF)
        val c2 = 0xFF1E293B.toInt()

        val shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            c1, c2, android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Draw clean music symbol / initial
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 180
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        val letter = seed.take(1).uppercase()
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(if (letter.isNotBlank()) letter else "♪", size / 2f, yPos, paint)

        return bitmap
    }

    fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}
