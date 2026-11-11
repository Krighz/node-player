package com.nodeplayer.network.library

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe manager for online library content (saved songs, liked songs, playlists,
 * derived albums, artists, and recently added carousel items) (LBRY-01, LBRY-02).
 *
 * Excludes local filesystem files (/storage, file://, drive letters) to enforce
 * strict online vs local separation (D-03, LBRY-03).
 */
class OnlineLibraryManager(
    private val storageDir: File? = null
) {
    private val TAG = "nodeplayer/online_library"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(OnlineLibraryState())
    val state: StateFlow<OnlineLibraryState> = _state.asStateFlow()

    // In-memory records
    private val savedTrackRecords = ConcurrentHashMap<Long, SavedTrackRecord>()
    private val onlinePlaylists = ConcurrentHashMap<String, OnlinePlaylist>()

    private val libraryFile: File? by lazy {
        try {
            val baseDir = storageDir ?: File(System.getProperty("user.home"), ".nodeplayer/library")
            if (!baseDir.exists()) baseDir.mkdirs()
            File(baseDir, "online_library.json")
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to resolve library file path: ${t.message}")
            null
        }
    }

    init {
        loadFromDisk()
    }

    /**
     * Checks if a track represents an online streaming entity (HTTP, HTTPS, or stream URI).
     * Local storage files are strictly excluded from the online library (D-03).
     */
    fun isOnlineTrack(track: Track): Boolean {
        val p = track.path.trim()
        if (p.startsWith("http://", ignoreCase = true) ||
            p.startsWith("https://", ignoreCase = true) ||
            p.startsWith("nodeplayer://stream", ignoreCase = true) ||
            p.startsWith("innertube://", ignoreCase = true) ||
            p.startsWith("ytmusic://", ignoreCase = true)
        ) {
            return true
        }

        // Local storage paths are NOT online tracks
        if (p.startsWith("/") ||
            p.startsWith("file:", ignoreCase = true) ||
            (p.length > 2 && p[1] == ':' && (p[2] == '\\' || p[2] == '/'))
        ) {
            return false
        }

        // Default: if non-empty and doesn't match local file syntax, assume online ID/URI
        return p.isNotEmpty()
    }

    /**
     * Marks a track as liked / saved into the online library.
     */
    @Synchronized
    fun likeTrack(track: Track) {
        if (!isOnlineTrack(track)) {
            NodeLog.d(TAG, "Ignored local track for online library like: '${track.title}' (${track.path})")
            return
        }

        val updatedTrack = track.copy(isFavorite = true)
        val existing = savedTrackRecords[track.id]
        val timestamp = existing?.addedTimestamp ?: System.currentTimeMillis()
        savedTrackRecords[track.id] = SavedTrackRecord.fromTrack(updatedTrack, timestamp)
        recalculateState()
        saveToDisk()
    }

    /**
     * Removes like status or deletes track from online library.
     */
    @Synchronized
    fun unlikeTrack(trackId: Long) {
        val existing = savedTrackRecords[trackId] ?: return
        if (existing.isFavorite) {
            savedTrackRecords[trackId] = existing.copy(isFavorite = false)
        }
        recalculateState()
        saveToDisk()
    }

    /**
     * Saves an online track into the library without requiring favorite status.
     */
    @Synchronized
    fun saveTrack(track: Track) {
        if (!isOnlineTrack(track)) return
        val existing = savedTrackRecords[track.id]
        val timestamp = existing?.addedTimestamp ?: System.currentTimeMillis()
        savedTrackRecords[track.id] = SavedTrackRecord.fromTrack(track, timestamp)
        recalculateState()
        saveToDisk()
    }

    /**
     * Removes a track entirely from the online library.
     */
    @Synchronized
    fun removeTrack(trackId: Long) {
        if (savedTrackRecords.remove(trackId) != null) {
            recalculateState()
            saveToDisk()
        }
    }

    fun isLiked(trackId: Long): Boolean {
        return savedTrackRecords[trackId]?.isFavorite == true
    }

    fun isSaved(trackId: Long): Boolean {
        return savedTrackRecords.containsKey(trackId)
    }

    fun isPlaylistSaved(playlistId: String): Boolean {
        return onlinePlaylists.containsKey(playlistId)
    }

    /**
     * Records an online track being streamed (increments play count / updates last played).
     */
    @Synchronized
    fun recordStreamedTrack(track: Track) {
        if (!isOnlineTrack(track)) return
        val existing = savedTrackRecords[track.id]
        val now = System.currentTimeMillis()
        if (existing != null) {
            savedTrackRecords[track.id] = existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedTimestamp = now
            )
        } else {
            savedTrackRecords[track.id] = SavedTrackRecord.fromTrack(
                track.copy(playCount = 1, lastPlayedTimestamp = now),
                addedTimestamp = now
            )
        }
        recalculateState()
        saveToDisk()
    }

    /**
     * Adds an online playlist to the library.
     */
    @Synchronized
    fun addPlaylist(playlist: OnlinePlaylist) {
        onlinePlaylists[playlist.id] = playlist
        recalculateState()
        saveToDisk()
    }

    /**
     * Removes a playlist by ID.
     */
    @Synchronized
    fun removePlaylist(playlistId: String) {
        if (onlinePlaylists.remove(playlistId) != null) {
            recalculateState()
            saveToDisk()
        }
    }

    /**
     * Clears all library items (used for testing or account switch).
     */
    @Synchronized
    fun clear() {
        savedTrackRecords.clear()
        onlinePlaylists.clear()
        recalculateState()
        saveToDisk()
    }

    private fun recalculateState() {
        val tracksList = savedTrackRecords.values
            .sortedByDescending { it.addedTimestamp }
            .map { it.toTrack() }

        // Derive online albums
        val albumMap = LinkedHashMap<String, MutableList<SavedTrackRecord>>()
        for (record in savedTrackRecords.values) {
            if (record.album.isNotBlank() && record.album != "Unknown Album") {
                albumMap.getOrPut(record.album) { mutableListOf() }.add(record)
            }
        }
        val albumsList = albumMap.map { (albumTitle, records) ->
            val first = records.first()
            OnlineAlbum(
                id = albumTitle.lowercase().replace(" ", "_"),
                title = albumTitle,
                artist = first.artist,
                trackCount = records.size,
                artworkUrl = first.artworkUrl,
                addedTimestamp = records.maxOfOrNull { it.addedTimestamp } ?: System.currentTimeMillis()
            )
        }.sortedByDescending { it.addedTimestamp }

        // Derive online artists
        val artistMap = LinkedHashMap<String, MutableList<SavedTrackRecord>>()
        for (record in savedTrackRecords.values) {
            if (record.artist.isNotBlank() && record.artist != "Unknown Artist") {
                artistMap.getOrPut(record.artist) { mutableListOf() }.add(record)
            }
        }
        val artistsList = artistMap.map { (artistName, records) ->
            val first = records.first()
            OnlineArtist(
                id = artistName.lowercase().replace(" ", "_"),
                name = artistName,
                trackCount = records.size,
                artworkUrl = first.artworkUrl,
                addedTimestamp = records.maxOfOrNull { it.addedTimestamp } ?: System.currentTimeMillis()
            )
        }.sortedByDescending { it.addedTimestamp }

        val playlistsList = onlinePlaylists.values
            .sortedByDescending { it.addedTimestamp }

        // Derive Recently Added media items (combines albums, playlists, and songs)
        val recentItems = ArrayList<OnlineMediaItem>()

        for (album in albumsList.take(10)) {
            recentItems.add(
                OnlineMediaItem(
                    id = album.id,
                    title = album.title,
                    subtitle = album.artist,
                    artworkUrl = album.artworkUrl,
                    type = OnlineMediaType.ALBUM,
                    addedTimestamp = album.addedTimestamp
                )
            )
        }

        for (pl in playlistsList.take(10)) {
            recentItems.add(
                OnlineMediaItem(
                    id = pl.id,
                    title = pl.title,
                    subtitle = if (pl.author.isNotBlank()) pl.author else "${pl.trackCount} songs",
                    artworkUrl = pl.artworkUrl,
                    type = OnlineMediaType.PLAYLIST,
                    addedTimestamp = pl.addedTimestamp
                )
            )
        }

        // If very few albums/playlists, add standalone recent tracks
        if (recentItems.size < 6) {
            for (trackRecord in savedTrackRecords.values.sortedByDescending { it.addedTimestamp }.take(8)) {
                recentItems.add(
                    OnlineMediaItem(
                        id = trackRecord.id.toString(),
                        title = trackRecord.title,
                        subtitle = trackRecord.artist,
                        artworkUrl = trackRecord.artworkUrl,
                        type = OnlineMediaType.TRACK,
                        addedTimestamp = trackRecord.addedTimestamp
                    )
                )
            }
        }

        val sortedRecentlyAdded = recentItems
            .sortedByDescending { it.addedTimestamp }
            .take(20)

        _state.value = OnlineLibraryState(
            songs = tracksList,
            albums = albumsList,
            artists = artistsList,
            playlists = playlistsList,
            recentlyAdded = sortedRecentlyAdded
        )
    }

    private fun loadFromDisk() {
        val file = libraryFile ?: return
        if (!file.exists() || file.length() == 0L) {
            recalculateState()
            return
        }

        try {
            val content = file.readText()
            val payload = json.decodeFromString<OnlineLibraryDiskPayload>(content)
            savedTrackRecords.clear()
            for (rec in payload.savedTracks) {
                savedTrackRecords[rec.id] = rec
            }
            onlinePlaylists.clear()
            for (pl in payload.playlists) {
                onlinePlaylists[pl.id] = pl
            }
            recalculateState()
            NodeLog.i(TAG, "Loaded online library: ${savedTrackRecords.size} songs, ${onlinePlaylists.size} playlists")
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to load library from disk: ${t.message}")
            recalculateState()
        }
    }

    private fun saveToDisk() {
        val file = libraryFile ?: return
        try {
            val payload = OnlineLibraryDiskPayload(
                savedTracks = savedTrackRecords.values.toList(),
                playlists = onlinePlaylists.values.toList(),
                lastUpdated = System.currentTimeMillis()
            )
            file.writeText(json.encodeToString(payload))
        } catch (t: Throwable) {
            NodeLog.w(TAG, "Failed to persist online library to disk: ${t.message}")
        }
    }
}
