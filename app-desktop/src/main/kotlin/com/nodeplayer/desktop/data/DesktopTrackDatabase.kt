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
                        play_count INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
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
}
