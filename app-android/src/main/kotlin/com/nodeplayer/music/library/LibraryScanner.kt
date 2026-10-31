package com.nodeplayer.music.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {

    suspend fun scanTracks(minDurationMs: Long = 10_000L): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val artistId = cursor.getLong(artistIdCol)
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/*"
                    val rawTrackNo = cursor.getInt(trackCol)
                    val trackNo = if (rawTrackNo >= 1000) rawTrackNo % 1000 else rawTrackNo
                    val discNo = if (rawTrackNo >= 1000) rawTrackNo / 1000 else 1
                    val year = cursor.getInt(yearCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val isLossless = mime.contains("flac", ignoreCase = true) ||
                            mime.contains("alac", ignoreCase = true) ||
                            mime.contains("wav", ignoreCase = true) ||
                            path.endsWith(".flac", ignoreCase = true) ||
                            path.endsWith(".wav", ignoreCase = true) ||
                            path.endsWith(".alac", ignoreCase = true)

                    // Baseline sample rate / bit depth detection
                    val sampleRate = if (isLossless && (path.contains("96k") || path.contains("192k"))) 96000 else 44100
                    val bitDepth = if (isLossless && (path.contains("24bit") || path.contains("24b"))) 24 else 16
                    val isHiRes = sampleRate > 48000 || bitDepth > 16

                    tracks.add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            artistId = artistId,
                            album = album,
                            albumId = albumId,
                            durationMs = duration,
                            path = path,
                            size = size,
                            mimeType = mime,
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                            trackNumber = trackNo,
                            discNumber = discNo,
                            year = year,
                            dateAdded = dateAdded,
                            isLossless = isLossless,
                            isHiRes = isHiRes
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }

    fun extractAlbums(tracks: List<Track>): List<Album> {
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
        return tracks.groupBy { it.albumId }.map { (albumId, albumTracks) ->
            val first = albumTracks.first()
            val artworkUri = ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
            Album(
                id = albumId,
                title = first.album,
                artist = first.artist,
                artistId = first.artistId,
                trackCount = albumTracks.size,
                year = albumTracks.maxOfOrNull { it.year } ?: 0,
                artworkUri = artworkUri
            )
        }.sortedBy { it.title.lowercase() }
    }

    fun extractArtists(tracks: List<Track>): List<Artist> {
        return tracks.groupBy { it.artistId }.map { (artistId, artistTracks) ->
            val first = artistTracks.first()
            val albumCount = artistTracks.map { it.albumId }.distinct().size
            Artist(
                id = artistId,
                name = first.artist,
                trackCount = artistTracks.size,
                albumCount = albumCount
            )
        }.sortedBy { it.name.lowercase() }
    }
}
