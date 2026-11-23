package com.nodeplayer.desktop.data

import com.nodeplayer.model.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite Database for Node Player Windows desktop.
 * Stores library index, playlists, play counts, and favorites at %APPDATA%\NodePlayer\library.db.
 */
class DesktopTrackDatabase {

    private val dbPath: String

    init {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "NodePlayer")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val dbFile = File(dir, "library.db")
        dbPath = "jdbc:sqlite:${dbFile.absolutePath}"
        initDatabase()
    }

    private fun getConnection(): Connection {
        return DriverManager.getConnection(dbPath)
    }

    private fun initDatabase() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        path TEXT UNIQUE NOT NULL,
                        size INTEGER NOT NULL,
                        mime_type TEXT NOT NULL,
                        sample_rate INTEGER NOT NULL,
                        bit_depth INTEGER NOT NULL,
                        channels INTEGER NOT NULL,
                        bitrate_kbps INTEGER NOT NULL,
                        format_description TEXT NOT NULL,
                        is_lossless INTEGER NOT NULL,
                        is_hires INTEGER NOT NULL,
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        play_count INTEGER NOT NULL DEFAULT 0,
                        last_played INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT 'local',
                        external_id TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        thumbnail_url TEXT
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlist_id INTEGER NOT NULL,
                        track_id INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (playlist_id, track_id)
                    );
                    """.trimIndent()
                )
                val optionalColumns = listOf(
                    "ALTER TABLE tracks ADD COLUMN last_played INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE tracks ADD COLUMN analysis_status TEXT",
                    "ALTER TABLE tracks ADD COLUMN analysis_confidence REAL",
                    "ALTER TABLE tracks ADD COLUMN cutoff_hz REAL",
                    "ALTER TABLE tracks ADD COLUMN lufs REAL",
                    "ALTER TABLE tracks ADD COLUMN dr_score INTEGER",
                    "ALTER TABLE tracks ADD COLUMN dr_label TEXT"
                )
                for (colSql in optionalColumns) {
                    try {
                        stmt.execute(colSql)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun insertOrUpdateTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val sql = """
            INSERT INTO tracks (title, artist, album, duration_ms, path, size, mime_type, sample_rate, bit_depth, channels, bitrate_kbps, format_description, is_lossless, is_hires, is_favorite, play_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                title=excluded.title,
                artist=excluded.artist,
                album=excluded.album,
                duration_ms=excluded.duration_ms,
                sample_rate=excluded.sample_rate,
                bit_depth=excluded.bit_depth,
                bitrate_kbps=excluded.bitrate_kbps,
                format_description=excluded.format_description;
        """.trimIndent()

        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { ps ->
                    for (t in tracks) {
                        ps.setString(1, t.title)
                        ps.setString(2, t.artist)
                        ps.setString(3, t.album)
                        ps.setLong(4, t.durationMs)
                        ps.setString(5, t.path)
                        ps.setLong(6, t.size)
                        ps.setString(7, t.mimeType)
                        ps.setInt(8, t.sampleRate)
                        ps.setInt(9, t.bitDepth)
                        ps.setInt(10, t.channels)
                        ps.setInt(11, t.bitrateKbps)
                        ps.setString(12, t.formatDescription)
                        ps.setInt(13, if (t.isLossless) 1 else 0)
                        ps.setInt(14, if (t.isHiRes) 1 else 0)
                        ps.setInt(15, if (t.isFavorite) 1 else 0)
                        ps.setInt(16, t.playCount)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun getAllTracks(): List<Track> {
        val tracks = mutableListOf<Track>()
        val sql = "SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC"
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    val isHiRes = rs.getInt("is_hires") == 1
                    tracks.add(
                        Track(
                            id = rs.getLong("id"),
                            title = rs.getString("title"),
                            artist = rs.getString("artist"),
                            album = rs.getString("album"),
                            durationMs = rs.getLong("duration_ms"),
                            path = rs.getString("path"),
                            size = rs.getLong("size"),
                            mimeType = rs.getString("mime_type"),
                            sampleRate = rs.getInt("sample_rate"),
                            bitDepth = rs.getInt("bit_depth"),
                            channels = rs.getInt("channels"),
                            bitrateKbps = rs.getInt("bitrate_kbps"),
                            formatDescription = rs.getString("format_description"),
                            isLossless = rs.getInt("is_lossless") == 1,
                            isHiRes = isHiRes,
                            hiResStatus = if (isHiRes) HiResStatus.GENUINE else HiResStatus.UNKNOWN,
                            isFavorite = rs.getInt("is_favorite") == 1,
                            playCount = rs.getInt("play_count")
                        )
                    )
                }
            }
        }
        return tracks
    }

    fun extractAlbums(tracks: List<Track>): List<Album> {
        return tracks.groupBy { it.album to it.artist }.toList().mapIndexed { index, (key, albumTracks) ->
            Album(
                id = index.toLong() + 1L,
                title = key.first,
                artist = key.second,
                trackCount = albumTracks.size,
                year = albumTracks.firstOrNull()?.year ?: 0
            )
        }
    }

    fun extractArtists(tracks: List<Track>): List<Artist> {
        return tracks.groupBy { it.artist }.toList().mapIndexed { index, (artistName, artistTracks) ->
            Artist(
                id = index.toLong() + 1L,
                name = artistName,
                trackCount = artistTracks.size,
                albumCount = artistTracks.map { it.album }.distinct().size
            )
        }
    }

    fun extractFolders(tracks: List<Track>): List<MusicFolder> {
        return tracks.mapNotNull { File(it.path).parentFile }
            .distinctBy { it.absolutePath }
            .map { folder ->
                val count = tracks.count { it.path.startsWith(folder.absolutePath) }
                MusicFolder(
                    name = folder.name,
                    path = folder.absolutePath,
                    trackCount = count
                )
            }
    }

    fun setFavorite(trackId: Long, isFavorite: Boolean) {
        val sql = "UPDATE tracks SET is_favorite = ? WHERE id = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, if (isFavorite) 1 else 0)
                ps.setLong(2, trackId)
                ps.executeUpdate()
            }
        }
    }

    fun incrementPlayCount(trackId: Long) {
        val sql = "UPDATE tracks SET play_count = play_count + 1 WHERE id = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, trackId)
                ps.executeUpdate()
            }
        }
    }

    fun recordTrackPlayed(trackId: Long, timestamp: Long) {
        val sql = "UPDATE tracks SET play_count = play_count + 1, last_played = ? WHERE id = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, timestamp)
                ps.setLong(2, trackId)
                ps.executeUpdate()
            }
        }
    }

    fun updateTrackAudioAnalysis(
        id: Long,
        status: String,
        confidence: Float,
        cutoffHz: Float,
        lufs: Float,
        drScore: Int,
        drLabel: String
    ) {
        val sql = """
            UPDATE tracks SET 
                analysis_status = ?,
                analysis_confidence = ?,
                cutoff_hz = ?,
                lufs = ?,
                dr_score = ?,
                dr_label = ?
            WHERE id = ?
        """.trimIndent()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, status)
                ps.setFloat(2, confidence)
                ps.setFloat(3, cutoffHz)
                ps.setFloat(4, lufs)
                ps.setInt(5, drScore)
                ps.setString(6, drLabel)
                ps.setLong(7, id)
                ps.executeUpdate()
            }
        }
    }

    fun getAllPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val sql = """
            SELECT p.*, COUNT(pt.track_id) as track_count 
            FROM playlists p 
            LEFT JOIN playlist_tracks pt ON p.id = pt.playlist_id 
            GROUP BY p.id 
            ORDER BY p.name COLLATE NOCASE ASC
        """.trimIndent()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    list.add(
                        Playlist(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            trackCount = rs.getInt("track_count"),
                            source = rs.getString("source") ?: "local",
                            externalId = rs.getString("external_id"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                            thumbnailUrl = rs.getString("thumbnail_url")
                        )
                    )
                }
            }
        }
        return list
    }

    fun insertPlaylist(playlist: Playlist): Long {
        val sql = """
            INSERT INTO playlists (name, source, external_id, created_at, updated_at, thumbnail_url) 
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        getConnection().use { conn ->
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, playlist.name)
                ps.setString(2, playlist.source)
                ps.setString(3, playlist.externalId)
                ps.setLong(4, playlist.createdAt)
                ps.setLong(5, playlist.updatedAt)
                ps.setString(6, playlist.thumbnailUrl)
                ps.executeUpdate()
                val rs = ps.generatedKeys
                if (rs.next()) {
                    return rs.getLong(1)
                }
            }
        }
        return 1L
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val sql = "UPDATE playlists SET name = ?, updated_at = ? WHERE id = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, newName)
                ps.setLong(2, System.currentTimeMillis())
                ps.setLong(3, playlistId)
                ps.executeUpdate()
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use { ps ->
                ps.setLong(1, playlistId)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM playlists WHERE id = ?").use { ps ->
                ps.setLong(1, playlistId)
                ps.executeUpdate()
            }
        }
    }

    fun getTracksForPlaylist(playlistId: Long): List<Track> {
        val tracks = mutableListOf<Track>()
        val sql = """
            SELECT t.* FROM tracks t
            JOIN playlist_tracks pt ON t.id = pt.track_id
            WHERE pt.playlist_id = ?
            ORDER BY pt.sort_order ASC
        """.trimIndent()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, playlistId)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    val isHiRes = rs.getInt("is_hires") == 1
                    tracks.add(
                        Track(
                            id = rs.getLong("id"),
                            title = rs.getString("title"),
                            artist = rs.getString("artist"),
                            album = rs.getString("album"),
                            durationMs = rs.getLong("duration_ms"),
                            path = rs.getString("path"),
                            size = rs.getLong("size"),
                            mimeType = rs.getString("mime_type"),
                            sampleRate = rs.getInt("sample_rate"),
                            bitDepth = rs.getInt("bit_depth"),
                            channels = rs.getInt("channels"),
                            bitrateKbps = rs.getInt("bitrate_kbps"),
                            formatDescription = rs.getString("format_description"),
                            isLossless = rs.getInt("is_lossless") == 1,
                            isHiRes = isHiRes,
                            hiResStatus = if (isHiRes) HiResStatus.GENUINE else HiResStatus.UNKNOWN,
                            isFavorite = rs.getInt("is_favorite") == 1,
                            playCount = rs.getInt("play_count")
                        )
                    )
                }
            }
        }
        return tracks
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val maxOrderSql = "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM playlist_tracks WHERE playlist_id = ?"
        var nextOrder = 0
        getConnection().use { conn ->
            conn.prepareStatement(maxOrderSql).use { ps ->
                ps.setLong(1, playlistId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    nextOrder = rs.getInt(1)
                }
            }
            val insertSql = "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, sort_order) VALUES (?, ?, ?)"
            conn.prepareStatement(insertSql).use { ps ->
                ps.setLong(1, playlistId)
                ps.setLong(2, trackId)
                ps.setInt(3, nextOrder)
                ps.executeUpdate()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val sql = "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, playlistId)
                ps.setLong(2, trackId)
                ps.executeUpdate()
            }
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        val sql = "UPDATE playlist_tracks SET sort_order = ? WHERE playlist_id = ? AND track_id = ?"
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { ps ->
                    orderedTrackIds.forEachIndexed { index, trackId ->
                        ps.setInt(1, index)
                        ps.setLong(2, playlistId)
                        ps.setLong(3, trackId)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }
}
