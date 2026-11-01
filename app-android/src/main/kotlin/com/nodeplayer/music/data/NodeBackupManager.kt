package com.nodeplayer.music.data

import android.content.Context
import android.util.Log
import com.nodeplayer.library.M3uParser
import com.nodeplayer.model.AudioEffectConfig
import com.nodeplayer.model.GlassThemeConfig
import com.nodeplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NodeBackupManager {

    private const val TAG = "NodeBackupManager"

    suspend fun exportNodeBackup(
        context: Context,
        database: NodeDatabase,
        effectConfig: AudioEffectConfig,
        themeConfig: GlassThemeConfig,
        allTracks: List<Track>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("format", "NodePlayerBackup")
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            // 1. Play counts
            val playCountsObj = JSONObject()
            allTracks.forEach { track ->
                if (track.playCount > 0) {
                    playCountsObj.put(track.path, track.playCount)
                }
            }
            root.put("playCounts", playCountsObj)

            // 2. Favorites
            val favArray = JSONArray()
            allTracks.filter { it.isFavorite }.forEach { track ->
                favArray.put(track.path)
            }
            root.put("favorites", favArray)

            // 3. Playlists from database
            val playlistsArray = JSONArray()
            val storedPlaylists = database.playerDao().getAllPlaylistsList()
            storedPlaylists.forEach { playlist ->
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("name", playlist.name)
                val trackIds = database.playerDao().getPlaylistTrackIds(playlist.id)
                val trackPaths = trackIds.mapNotNull { id ->
                    allTracks.find { it.id == id }?.path
                }
                val pathsArray = JSONArray()
                trackPaths.forEach { pathsArray.put(it) }
                pObj.put("tracks", pathsArray)
                playlistsArray.put(pObj)
            }
            root.put("playlists", playlistsArray)

            // 4. Glass Theme Config
            val themeObj = JSONObject()
            themeObj.put("glassStrength", themeConfig.glassStrength)
            themeObj.put("themeMode", themeConfig.themeMode)
            themeObj.put("accentSource", themeConfig.accentSource)
            themeObj.put("spacingDensity", themeConfig.spacingDensity)
            themeObj.put("cornerRoundness", themeConfig.cornerRoundness)
            themeObj.put("animatedBackdrop", themeConfig.animatedBackdrop)
            themeObj.put("hapticsEnabled", themeConfig.hapticsEnabled)
            root.put("themeConfig", themeObj)

            // 5. Audio EQ Config
            val eqObj = JSONObject()
            eqObj.put("eqEnabled", effectConfig.eqEnabled)
            eqObj.put("presetName", effectConfig.presetName)
            eqObj.put("preampGainDb", effectConfig.preampGainDb.toDouble())
            eqObj.put("limiterEnabled", effectConfig.limiterEnabled)
            val bandsArr = JSONArray()
            effectConfig.bands.forEach { band ->
                val bObj = JSONObject()
                bObj.put("freq", band.centerFreq.toDouble())
                bObj.put("gain", band.gainDb.toDouble())
                bObj.put("q", band.q.toDouble())
                bandsArr.put(bObj)
            }
            eqObj.put("bands", bandsArr)
            root.put("eqConfig", eqObj)

            val backupFile = File(context.cacheDir, "node_player_backup_${System.currentTimeMillis()}.nodebackup")
            backupFile.writeText(root.toString(2))
            Log.i(TAG, "Exported .nodebackup successfully (${backupFile.length()} bytes)")
            backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting .nodebackup", e)
            null
        }
    }

    suspend fun importNodeBackup(
        jsonString: String,
        database: NodeDatabase,
        allTracks: List<Track>,
        onThemeRestored: ((GlassThemeConfig) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("format") || root.getString("format") != "NodePlayerBackup") {
                return@withContext "Invalid backup file: Format mismatch."
            }

            var restoredPlaylists = 0
            var restoredPlayCounts = 0
            var restoredFavorites = 0

            // 1. Play counts
            if (root.has("playCounts")) {
                val playCountsObj = root.getJSONObject("playCounts")
                val keys = playCountsObj.keys()
                while (keys.hasNext()) {
                    val path = keys.next()
                    val count = playCountsObj.getInt(path)
                    val track = allTracks.find { it.path == path }
                    if (track != null) {
                        database.playerDao().updatePlayCount(track.id, count)
                        restoredPlayCounts++
                    }
                }
            }

            // 2. Favorites
            if (root.has("favorites")) {
                val favArray = root.getJSONArray("favorites")
                for (i in 0 until favArray.length()) {
                    val path = favArray.getString(i)
                    val track = allTracks.find { it.path == path }
                    if (track != null) {
                        database.playerDao().setFavorite(track.id, true)
                        restoredFavorites++
                    }
                }
            }

            // 3. Playlists
            if (root.has("playlists")) {
                val playlistsArray = root.getJSONArray("playlists")
                for (i in 0 until playlistsArray.length()) {
                    val pObj = playlistsArray.getJSONObject(i)
                    val name = pObj.getString("name")
                    val existing = database.playerDao().getPlaylistByName(name)
                    val playlistId = existing?.id ?: database.playerDao().insertPlaylist(
                        PlaylistEntity(name = name)
                    )
                    val tracksArr = pObj.getJSONArray("tracks")
                    val crossRefs = mutableListOf<PlaylistTrackCrossRef>()
                    for (t in 0 until tracksArr.length()) {
                        val path = tracksArr.getString(t)
                        val track = allTracks.find { it.path == path }
                        if (track != null) {
                            crossRefs.add(
                                PlaylistTrackCrossRef(
                                    playlistId = playlistId,
                                    trackId = track.id,
                                    position = t
                                )
                            )
                        }
                    }
                    if (crossRefs.isNotEmpty()) {
                        database.playerDao().insertPlaylistTracks(crossRefs)
                    }
                    restoredPlaylists++
                }
            }

            // 4. Theme
            if (root.has("themeConfig") && onThemeRestored != null) {
                val tObj = root.getJSONObject("themeConfig")
                val theme = GlassThemeConfig(
                    glassStrength = tObj.optString("glassStrength", "BALANCED"),
                    themeMode = tObj.optString("themeMode", "SYSTEM"),
                    accentSource = tObj.optString("accentSource", "MATERIAL_YOU"),
                    spacingDensity = tObj.optString("spacingDensity", "COMFORTABLE"),
                    cornerRoundness = tObj.optString("cornerRoundness", "MEDIUM"),
                    animatedBackdrop = tObj.optBoolean("animatedBackdrop", true),
                    hapticsEnabled = tObj.optBoolean("hapticsEnabled", true)
                )
                onThemeRestored(theme)
            }

            "Restored: $restoredPlaylists playlists, $restoredFavorites favorites, $restoredPlayCounts play counts."
        } catch (e: Exception) {
            Log.e(TAG, "Error importing .nodebackup", e)
            "Restore failed: ${e.message}"
        }
    }

    suspend fun importM3uPlaylist(
        m3uContent: String,
        playlistName: String,
        database: NodeDatabase,
        allTracks: List<Track>
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val paths = M3uParser.parse(m3uContent)
        if (paths.isEmpty()) return@withContext Pair(0, "No audio paths found in M3U file.")

        val matchedTracks = mutableListOf<Track>()
        paths.forEach { entry ->
            val filename = File(entry).name
            val match = allTracks.find {
                it.path == entry || File(it.path).name.equals(filename, ignoreCase = true)
            }
            if (match != null) matchedTracks.add(match)
        }

        if (matchedTracks.isEmpty()) {
            return@withContext Pair(0, "No tracks in your library matched the paths in this M3U file.")
        }

        val playlistId = database.playerDao().insertPlaylist(
            PlaylistEntity(name = playlistName)
        )
        val crossRefs = matchedTracks.mapIndexed { index, track ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = index
            )
        }
        database.playerDao().insertPlaylistTracks(crossRefs)

        return@withContext Pair(matchedTracks.size, "Imported ${matchedTracks.size} tracks into playlist '$playlistName'.")
    }
}
