package com.nodeplayer.music.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.AlbumArtPalette
import com.nodeplayer.model.Track
import java.io.File
import java.io.FileOutputStream

/**
 * High-resolution 9:16 vertical Lyric Card renderer for sharing to Instagram Stories, WhatsApp, and social media.
 *
 * Renders album art palette gradient background, Node Player branding, bold lyric typography,
 * and squircle-rounded album art with track title and artist.
 */
object LyricCardRenderer {

    private const val TAG = "LyricCardRenderer"
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920

    /**
     * Renders a high-resolution 1080x1920 Bitmap from the selected lyrics and track info.
     */
    fun renderCardBitmap(
        context: Context,
        track: Track?,
        lines: List<String>,
        palette: AlbumArtPalette,
        artwork: Bitmap? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Vertical ambient gradient background
        val topColor = if (palette.darkMuted != 0L) (palette.darkMuted or 0xFF000000L).toInt() else 0xFF181820.toInt()
        val midColor = if (palette.dominant != 0L) (palette.dominant or 0xFF000000L).toInt() else 0xFF282835.toInt()
        val botColor = 0xFF09090B.toInt()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                intArrayOf(topColor, midColor, botColor),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Subtle outer border vignette
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(30, 255, 255, 255)
        }
        canvas.drawRect(1f, 1f, CARD_WIDTH - 1f, CARD_HEIGHT - 1f, borderPaint)

        // 3. Header: Node Player branding (Top Right)
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
        }
        val brandText = "Node Player"
        val brandTextWidth = headerPaint.measureText(brandText)
        val headerRightMargin = 88f
        val headerTop = 135f

        // Draw 3 mini soundwave bars next to brand text
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val barStartX = CARD_WIDTH - headerRightMargin - brandTextWidth - 42f
        val barWidth = 6f
        val barSpacing = 6f
        val heights = floatArrayOf(20f, 32f, 16f)
        for (i in heights.indices) {
            val bx = barStartX + i * (barWidth + barSpacing)
            val h = heights[i]
            val by = headerTop - (h / 2f) - 12f
            canvas.drawRoundRect(bx, by, bx + barWidth, by + h, 3f, 3f, barPaint)
        }
        canvas.drawText(brandText, CARD_WIDTH - headerRightMargin - brandTextWidth, headerTop, headerPaint)

        // 4. Middle Content: Selected Lyrics Lines
        val contentPaddingX = 96f
        val availableContentWidth = CARD_WIDTH - (contentPaddingX * 2)

        val lyricTextSize = when {
            lines.size <= 2 -> 68f
            lines.size <= 4 -> 56f
            else -> 46f
        }
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = lyricTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.03f
        }

        val layouts = lines.map { text ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, lyricPaint, availableContentWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(16f, 1.15f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, lyricPaint, availableContentWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.15f, 16f, false)
            }
        }

        val spacingBetweenLines = if (lines.size <= 3) 54f else 36f
        val totalLyricsHeight = layouts.sumOf { it.height } + (layouts.size - 1) * spacingBetweenLines
        val targetCenterY = CARD_HEIGHT * 0.48f
        var currentY = (targetCenterY - (totalLyricsHeight / 2f)).coerceAtLeast(240f)

        for (layout in layouts) {
            canvas.save()
            canvas.translate(contentPaddingX, currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height + spacingBetweenLines
        }

        // 5. Footer: Squircle Artwork, Song Title, and Artist
        val footerY = CARD_HEIGHT - 210f
        val thumbSize = 120f
        val thumbLeft = contentPaddingX

        if (artwork != null && !artwork.isRecycled) {
            val thumbPath = Path().apply {
                addRoundRect(
                    RectF(thumbLeft, footerY, thumbLeft + thumbSize, footerY + thumbSize),
                    24f, 24f,
                    Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = android.graphics.Rect(0, 0, artwork.width, artwork.height)
            val dstRect = RectF(thumbLeft, footerY, thumbLeft + thumbSize, footerY + thumbSize)
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(artwork, srcRect, dstRect, filterPaint)
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255)
            }
            canvas.drawRoundRect(
                RectF(thumbLeft, footerY, thumbLeft + thumbSize, footerY + thumbSize),
                24f, 24f, placeholderPaint
            )
        }

        // Footer Text
        val textLeft = thumbLeft + thumbSize + 32f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.01f
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 255, 255, 255)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val maxTextWidth = CARD_WIDTH - textLeft - contentPaddingX
        val rawTitle = track?.title ?: "Unknown Track"
        val displayTitle = android.text.TextUtils.ellipsize(rawTitle, titlePaint, maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()
        val rawArtist = track?.artist ?: "Unknown Artist"
        val displayArtist = android.text.TextUtils.ellipsize(rawArtist, artistPaint, maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()

        canvas.drawText(displayTitle, textLeft, footerY + 52f, titlePaint)
        canvas.drawText(displayArtist, textLeft, footerY + 100f, artistPaint)

        return bitmap
    }

    /**
     * Renders the lyric card, writes it to the app cache directory, and invokes the Android Share Sheet.
     */
    fun shareCard(
        context: Context,
        track: Track?,
        lines: List<String>,
        palette: AlbumArtPalette,
        artwork: Bitmap? = null
    ) {
        try {
            val bitmap = renderCardBitmap(context, track, lines, palette, artwork)
            val cacheDir = File(context.cacheDir, "lyric_cards").apply { mkdirs() }
            val imageFile = File(cacheDir, "node_lyric_${System.currentTimeMillis()}.png")

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Lyrics from ${track?.title ?: "Node Player"}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Lyric Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            NodeLog.i(TAG, "Launched Lyric Card share intent for: ${track?.title}")
        } catch (e: Exception) {
            NodeLog.e(TAG, "Failed to generate or share lyric card: ${e.message}", e)
            Toast.makeText(context, "Failed to share lyric card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves the high-resolution lyric card PNG to the device's public Pictures / NodePlayer directory.
     */
    fun saveCardToGallery(
        context: Context,
        track: Track?,
        lines: List<String>,
        palette: AlbumArtPalette,
        artwork: Bitmap? = null
    ): Boolean {
        return try {
            val bitmap = renderCardBitmap(context, track, lines, palette, artwork)
            val filename = "NodePlayer_Lyrics_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NodePlayer")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val nodeDir = File(picturesDir, "NodePlayer").apply { mkdirs() }
                val targetFile = File(nodeDir, filename)

                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            Toast.makeText(context, "Saved lyric card to Pictures", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            NodeLog.e(TAG, "Failed to save lyric card to gallery: ${e.message}", e)
            Toast.makeText(context, "Failed to save card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
