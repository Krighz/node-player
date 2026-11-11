package com.nodeplayer.music.library

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.Track
import com.nodeplayer.music.data.NodeDatabase
import com.nodeplayer.music.data.PlaylistEntity
import com.nodeplayer.music.data.PlaylistTrackCrossRef
import com.nodeplayer.music.data.TrackEntity
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.YouTubePlaylistSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Imports YouTube account playlists as local snapshot playlists into Node Player's database.
 * Supports smart local matching (saving bandwidth if user already owns the local lossless track)
 * and re-syncing.
 */
class YouTubePlaylistImporter(
    private val database: NodeDatabase,
    private val innerTubeClient: InnerTubeClient
) {

    /**
     * Imports a YouTube playlist snapshot into local Room database.
     * @return playlist ID of the imported or updated playlist.
     */
    suspend fun importPlaylist(
        summary: YouTubePlaylistSummary,
        preferLocalMatch: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = database.playerDao()
            val remoteTracks = innerTubeClient.fetchPlaylistTracks(summary.id).getOrThrow()
            if (remoteTracks.isEmpty()) {
                NodeLog.w(TAG, "No tracks found in YouTube playlist ${summary.id}")
            }

            // Check if playlist was already imported by externalId
            val existing = dao.getPlaylistByExternalId(summary.id)
            val playlistId = existing?.id ?: dao.insertPlaylist(
                PlaylistEntity(
                    name = summary.title,
                    source = "youtube",
                    externalId = summary.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Update title & timestamp if already exists
            if (existing != null) {
                dao.renamePlaylist(playlistId, summary.title)
            }

            // Re-sync: clear previous cross-references and re-insert fresh
            dao.clearPlaylistTracks(playlistId)

            val allLocalTracks = if (preferLocalMatch) dao.getAllTracksList() else emptyList()

            val crossRefs = mutableListOf<PlaylistTrackCrossRef>()
            for ((index, remoteTrack) in remoteTracks.withIndex()) {
                val matchedLocal = if (preferLocalMatch) {
                    findMatchingLocalTrack(remoteTrack, allLocalTracks)
                } else null

                val effectiveTrackId = if (matchedLocal != null) {
                    NodeLog.d(TAG, "Matched online '${remoteTrack.title}' with local '${matchedLocal.title}' (id=${matchedLocal.id})")
                    matchedLocal.id
                } else {
                    // Ensure the online track exists in Room `tracks` table
                    ensureTrackPersisted(dao, remoteTrack)
                    remoteTrack.id
                }

                crossRefs.add(
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = effectiveTrackId,
                        position = index
                    )
                )
            }

            if (crossRefs.isNotEmpty()) {
                dao.insertPlaylistTracks(crossRefs)
            }

            NodeLog.i(TAG, "Imported YouTube playlist '${summary.title}' ($playlistId) with ${crossRefs.size} tracks")
            playlistId
        }
    }

    /**
     * Re-syncs an already imported playlist from YouTube.
     */
    suspend fun resyncPlaylist(playlistId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = database.playerDao()
            val playlist = dao.getAllPlaylistsList().find { it.id == playlistId }
                ?: throw IllegalArgumentException("Playlist $playlistId not found")

            val extId = playlist.externalId ?: throw IllegalStateException("Playlist is not linked to YouTube")
            val summary = YouTubePlaylistSummary(
                id = extId,
                title = playlist.name
            )
            importPlaylist(summary).getOrThrow()
            Unit
        }
    }

    private suspend fun ensureTrackPersisted(dao: com.nodeplayer.music.data.PlayerDao, track: Track) {
        val existing = dao.getTrackById(track.id)
        if (existing == null) {
            dao.insertTracks(listOf(TrackEntity.fromTrack(track)))
        }
    }

    private fun findMatchingLocalTrack(online: Track, localList: List<TrackEntity>): TrackEntity? {
        val normOnlineTitle = normalize(online.title)
        val normOnlineArtist = normalize(online.artist)

        return localList.firstOrNull { local ->
            val normLocalTitle = normalize(local.title)
            val normLocalArtist = normalize(local.artist)

            // Exact or substring match on title and artist, plus reasonable duration proximity (+/- 5 seconds)
            val titleMatches = if (normLocalTitle.isNotBlank() && normOnlineTitle.isNotBlank()) {
                normLocalTitle == normOnlineTitle ||
                        normOnlineTitle.contains(normLocalTitle) ||
                        normLocalTitle.contains(normOnlineTitle)
            } else false

            val artistMatches = if (normLocalArtist.isNotBlank() && normOnlineArtist.isNotBlank()) {
                normLocalArtist == normOnlineArtist ||
                        normOnlineArtist.contains(normLocalArtist) ||
                        normLocalArtist.contains(normOnlineArtist)
            } else normLocalArtist.isBlank() && normOnlineArtist.isBlank()

            val durationMatches = if (online.durationMs > 0 && local.durationMs > 0) {
                abs(online.durationMs - local.durationMs) < 5000L
            } else true

            titleMatches && artistMatches && durationMatches
        }
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace(Regex("""\s*\(official\s*(music\s*)?video\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[official\s*(music\s*)?video\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(audio\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[audio\]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(lyrics?\)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
    }

    companion object {
        private const val TAG = "yt-playlist-importer"
    }
}
