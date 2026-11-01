package com.nodeplayer.music.library

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.nodeplayer.library.AudioFormatInfo
import com.nodeplayer.library.AudioHeaderParser
import com.nodeplayer.model.Album
import com.nodeplayer.model.Artist
import com.nodeplayer.model.MusicFolder
import com.nodeplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LibraryScanner(private val context: Context) {

    suspend fun scanTracks(
        minDurationMs: Long = 10_000L,
        includedFolders: Set<String> = emptySet(),
        excludedFolders: Set<String> = emptySet(),
        whitelistedTrackIds: Set<Long> = emptySet()
    ): List<Track> = withContext(Dispatchers.IO) {
        scanAllTracks(
            minDurationMs = minDurationMs,
            includedFolders = includedFolders,
            whitelistedTrackIds = whitelistedTrackIds
        ).map { track ->
            val isExcluded = excludedFolders.any { track.path.startsWith(it, ignoreCase = true) }
            track.copy(isHidden = isExcluded)
        }
    }

    suspend fun scanAllTracks(
        minDurationMs: Long = 10_000L,
        includedFolders: Set<String> = emptySet(),
        whitelistedTrackIds: Set<Long> = emptySet()
    ): List<Track> = withContext(Dispatchers.IO) {
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

        // Strict non-music exclusion: ringtones, notifications, and alarms only.
        // Duration is secondary so short music tracks (intros/interludes) in music folders are kept.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 " +
                "AND ${MediaStore.Audio.Media.IS_RINGTONE} == 0 " +
                "AND ${MediaStore.Audio.Media.IS_NOTIFICATION} == 0 " +
                "AND ${MediaStore.Audio.Media.IS_ALARM} == 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        // Default voice note & recording directories (Telegram excluded by default is REMOVED)
        val defaultNonMusicKeywords = listOf(
            "/whatsapp/media/whatsapp audio",
            "/whatsapp/media/whatsapp voice",
            "/recordings/call recordings",
            "/callrecordings",
            "/sound_recorder"
        )

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
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
                    val path = cursor.getString(dataCol) ?: ""
                    val lowerPath = path.lowercase().replace('\\', '/')
                    val duration = cursor.getLong(durCol)

                    // 1. Whitelist override: user explicitly un-excluded this track
                    val isWhitelisted = whitelistedTrackIds.contains(id)

                    if (!isWhitelisted) {
                        // Automatic voice note / recording directory exclusion unless explicitly included
                        val isNonMusicDir = defaultNonMusicKeywords.any { lowerPath.contains(it) }
                        val isExplicitlyIncluded = includedFolders.any { path.startsWith(it, ignoreCase = true) }
                        if (isNonMusicDir && !isExplicitlyIncluded) {
                            continue
                        }

                        if (includedFolders.isNotEmpty() && !isExplicitlyIncluded) {
                            continue
                        }

                        // Duration filter: Secondary to music folders. Intros/interludes in music folders are kept!
                        val isMusicFolder = lowerPath.contains("/music") ||
                                lowerPath.contains("/download") ||
                                lowerPath.contains("/seal") ||
                                lowerPath.contains("/telegram")
                        if (!isMusicFolder && duration in 1 until minDurationMs) {
                            continue
                        }
                    }
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val artistId = cursor.getLong(artistIdCol)
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
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

                    val formatInfo = resolveAudioFormat(path, mime, size, duration, isLossless)
                    val isHiRes = formatInfo.sampleRate > 48000 || formatInfo.bitDepth > 16

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
                            sampleRate = formatInfo.sampleRate,
                            bitDepth = formatInfo.bitDepth,
                            channels = formatInfo.channels,
                            bitrateKbps = formatInfo.bitrateKbps,
                            formatDescription = formatInfo.formatDescription,
                            trackNumber = trackNo,
                            discNumber = discNo,
                            year = year,
                            dateAdded = dateAdded,
                            isLossless = formatInfo.isLossless,
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

    fun extractFolders(tracks: List<Track>): List<MusicFolder> {
        return tracks.groupBy { track ->
            val parent = File(track.path).parent
            parent ?: "Internal Storage"
        }.map { (folderPath, folderTracks) ->
            val folderName = File(folderPath).name.ifEmpty { "Music" }
            MusicFolder(
                name = folderName,
                path = folderPath,
                trackCount = folderTracks.size
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun scanExcludedTracks(
        currentIncludedTracks: List<Track>
    ): List<Track> = withContext(Dispatchers.IO) {
        val includedIds = currentIncludedTracks.map { it.id }.toSet()
        val excluded = mutableListOf<Track>()
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

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
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
                    if (includedIds.contains(id)) continue

                    val path = cursor.getString(dataCol) ?: ""
                    val duration = cursor.getLong(durCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val artistId = cursor.getLong(artistIdCol)
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/*"
                    val trackNo = cursor.getInt(trackCol)
                    val year = cursor.getInt(yearCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val isLossless = mime.contains("flac", ignoreCase = true) ||
                            mime.contains("alac", ignoreCase = true) ||
                            mime.contains("wav", ignoreCase = true) ||
                            path.endsWith(".flac", ignoreCase = true) ||
                            path.endsWith(".wav", ignoreCase = true) ||
                            path.endsWith(".alac", ignoreCase = true)

                    val formatInfo = resolveAudioFormat(path, mime, size, duration, isLossless)
                    val isHiRes = formatInfo.sampleRate > 48000 || formatInfo.bitDepth > 16

                    excluded.add(
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
                            sampleRate = formatInfo.sampleRate,
                            bitDepth = formatInfo.bitDepth,
                            channels = formatInfo.channels,
                            bitrateKbps = formatInfo.bitrateKbps,
                            formatDescription = formatInfo.formatDescription,
                            trackNumber = trackNo,
                            discNumber = 1,
                            year = year,
                            dateAdded = dateAdded,
                            isLossless = formatInfo.isLossless,
                            isHiRes = isHiRes
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        excluded
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveAudioFormat(
        path: String,
        mime: String,
        size: Long,
        durationMs: Long,
        isLossless: Boolean
    ): AudioFormatInfo {
        val ext = path.substringAfterLast('.', "").uppercase()
        val formatName = when {
            mime.contains("flac", ignoreCase = true) || ext == "FLAC" -> "FLAC"
            mime.contains("wav", ignoreCase = true) || ext == "WAV" -> "WAV"
            mime.contains("alac", ignoreCase = true) || ext == "ALAC" -> "ALAC"
            mime.contains("aac", ignoreCase = true) || ext == "M4A" || ext == "AAC" -> "AAC"
            mime.contains("mp3", ignoreCase = true) || ext == "MP3" -> "MP3"
            mime.contains("ogg", ignoreCase = true) || ext == "OGG" -> "OGG"
            mime.contains("opus", ignoreCase = true) || ext == "OPUS" -> "Opus"
            else -> ext.ifBlank { mime.substringAfterLast("/").uppercase() }
        }

        val actuallyLossless = isLossless || formatName in listOf("FLAC", "WAV", "ALAC", "AIFF")

        var sampleRate = 0
        var bitDepth = 0
        var bitrateKbps = 0
        var channels = 2

        // 1. Primary: MediaMetadataRetriever (API 31+ METADATA_KEY_SAMPLERATE and METADATA_KEY_BITS_PER_SAMPLE)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                    if (it in 8000..768000) sampleRate = it
                }
                if (actuallyLossless) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let {
                        if (it in 8..32) bitDepth = it
                    }
                }
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let {
                if (it > 0) bitrateKbps = it / 1000
            }
            retriever.release()
        } catch (_: Exception) {}

        // 2. Secondary: MediaExtractor track format
        if (sampleRate == 0 || (actuallyLossless && bitDepth == 0) || (!actuallyLossless && bitrateKbps == 0)) {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(path)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (trackMime.startsWith("audio/")) {
                        if (sampleRate == 0 && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (sr in 8000..768000) sampleRate = sr
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (actuallyLossless && bitDepth == 0) {
                            if (format.containsKey("bits-per-sample")) {
                                val bd = format.getInteger("bits-per-sample")
                                if (bd in 8..32) bitDepth = bd
                            } else if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                val enc = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                val bd = when (enc) {
                                    AudioFormat.ENCODING_PCM_8BIT -> 8
                                    AudioFormat.ENCODING_PCM_16BIT -> 16
                                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                                    else -> 0
                                }
                                if (bd in 8..32) bitDepth = bd
                            }
                        }
                        if (!actuallyLossless && bitrateKbps == 0 && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            val br = format.getInteger(MediaFormat.KEY_BIT_RATE)
                            if (br > 0) bitrateKbps = br / 1000
                        }
                        break
                    }
                }
                extractor.release()
            } catch (_: Exception) {}
        }

        // 3. Fallback: Direct header parser (for FLAC STREAMINFO skipping ID3, WAV fmt chunk, MP3 frames)
        if (sampleRate == 0 || (actuallyLossless && bitDepth == 0) || (!actuallyLossless && bitrateKbps == 0)) {
            try {
                val file = File(path)
                val parsed = AudioHeaderParser.parseFile(file, durationMs)
                if (parsed != null) {
                    if (sampleRate == 0 && parsed.sampleRate in 8000..768000) {
                        sampleRate = parsed.sampleRate
                    }
                    if (actuallyLossless && bitDepth == 0 && parsed.bitDepth in 8..32) {
                        bitDepth = parsed.bitDepth
                    }
                    if (!actuallyLossless && bitrateKbps == 0 && parsed.bitrateKbps in 8..1411) {
                        bitrateKbps = parsed.bitrateKbps
                    }
                    if (parsed.channels > 0) {
                        channels = parsed.channels
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Plausibility checks: Never calculate from file size, bitrate, or duration
        val validSampleRate = if (sampleRate in 8000..768000) sampleRate else 0
        val validBitDepth = if (actuallyLossless && bitDepth in 8..32) bitDepth else 0
        val validBitrate = if (!actuallyLossless && bitrateKbps in 8..1411) bitrateKbps else 0

        val desc = if (actuallyLossless) {
            val depthPart = if (validBitDepth > 0) "$validBitDepth-bit" else "—"
            val ratePart = if (validSampleRate > 0) AudioHeaderParser.formatSampleRate(validSampleRate) else "—"
            "$formatName $depthPart / $ratePart"
        } else {
            // Lossy files show the bitrate and NO bit depth
            if (validBitrate > 0) {
                "$formatName $validBitrate kbps"
            } else if (validSampleRate > 0) {
                "$formatName ${AudioHeaderParser.formatSampleRate(validSampleRate)}"
            } else {
                formatName
            }
        }

        return AudioFormatInfo(
            formatName = formatName,
            sampleRate = validSampleRate,
            bitDepth = validBitDepth,
            bitrateKbps = validBitrate,
            channels = channels,
            isLossless = actuallyLossless,
            formatDescription = desc
        )
    }
}
