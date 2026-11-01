package com.nodeplayer.music.library

import android.content.Context
import android.util.Log
import com.nodeplayer.library.LastFmSigner
import com.nodeplayer.model.Track
import com.nodeplayer.music.BuildConfig
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.data.ScrobbleQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LastFmClient {

    private const val TAG = "LastFmClient"
    private const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"

    private val apiKey: String
        get() = BuildConfig.LASTFM_API_KEY

    private val apiSecret: String
        get() = BuildConfig.LASTFM_API_SECRET

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && apiSecret.isNotBlank()
    }

    fun getAuthUrl(token: String): String {
        return "http://www.last.fm/api/auth/?api_key=$apiKey&token=$token"
    }

    suspend fun getRequestToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Last.fm API key or secret missing"))
            val params = mapOf(
                "method" to "auth.getToken",
                "api_key" to apiKey
            )
            val sig = LastFmSigner.createApiSignature(params, apiSecret)
            val fullParams = params.toMutableMap().apply {
                put("api_sig", sig)
                put("format", "json")
            }
            val response = executePost(fullParams)
            val json = JSONObject(response)
            val token = json.getString("token")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get request token", e)
            Result.failure(e)
        }
    }

    suspend fun getSession(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Last.fm API key or secret missing"))
            val params = mapOf(
                "method" to "auth.getSession",
                "api_key" to apiKey,
                "token" to token
            )
            val sig = LastFmSigner.createApiSignature(params, apiSecret)
            val fullParams = params.toMutableMap().apply {
                put("api_sig", sig)
                put("format", "json")
            }
            val response = executePost(fullParams)
            val json = JSONObject(response)
            val session = json.getJSONObject("session")
            val sessionKey = session.getString("key")
            val username = session.getString("name")
            Result.success(sessionKey to username)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session", e)
            Result.failure(e)
        }
    }

    suspend fun updateNowPlaying(track: Track, sessionKey: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || sessionKey.isBlank()) return@withContext false
        try {
            val params = mutableMapOf(
                "method" to "track.updateNowPlaying",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to track.artist,
                "track" to track.title
            )
            if (track.album.isNotBlank()) params["album"] = track.album
            val sig = LastFmSigner.createApiSignature(params, apiSecret)
            params["api_sig"] = sig
            params["format"] = "json"
            executePost(params)
            Log.d(TAG, "Updated Now Playing on Last.fm: ${track.artist} - ${track.title}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update now playing: ${e.message}")
            false
        }
    }

    suspend fun scrobble(track: Track, timestampSec: Long, sessionKey: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || sessionKey.isBlank()) return@withContext false
        val dao = NodePlayerApplication.instance.database.playerDao()
        try {
            val params = mutableMapOf(
                "method" to "track.scrobble",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "timestamp" to timestampSec.toString(),
                "artist" to track.artist,
                "track" to track.title
            )
            if (track.album.isNotBlank()) params["album"] = track.album
            val sig = LastFmSigner.createApiSignature(params, apiSecret)
            params["api_sig"] = sig
            params["format"] = "json"
            executePost(params)
            Log.d(TAG, "Scrobbled track to Last.fm: ${track.artist} - ${track.title}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Scrobble failed; queueing locally: ${e.message}")
            // Enqueue in Room for offline retry
            try {
                dao.enqueueScrobble(
                    ScrobbleQueueEntity(
                        artist = track.artist,
                        track = track.title,
                        album = track.album,
                        timestampSeconds = timestampSec
                    )
                )
            } catch (dbEx: Exception) {
                Log.e(TAG, "Failed to enqueue scrobble in Room", dbEx)
            }
            false
        }
    }

    suspend fun flushPendingQueue(sessionKey: String): Int = withContext(Dispatchers.IO) {
        if (!isConfigured() || sessionKey.isBlank()) return@withContext 0
        val dao = NodePlayerApplication.instance.database.playerDao()
        val pending = dao.getPendingScrobbles()
        var flushedCount = 0

        for (item in pending) {
            try {
                val params = mutableMapOf(
                    "method" to "track.scrobble",
                    "api_key" to apiKey,
                    "sk" to sessionKey,
                    "timestamp" to item.timestampSeconds.toString(),
                    "artist" to item.artist,
                    "track" to item.track
                )
                if (item.album.isNotBlank()) params["album"] = item.album
                val sig = LastFmSigner.createApiSignature(params, apiSecret)
                params["api_sig"] = sig
                params["format"] = "json"
                executePost(params)
                dao.removeScrobble(item.id)
                flushedCount++
            } catch (e: Exception) {
                dao.incrementScrobbleRetry(item.id)
                Log.w(TAG, "Failed to flush scrobble ${item.id}: ${e.message}")
                break // Stop queue retry if network/auth fails
            }
        }
        flushedCount
    }

    private fun executePost(params: Map<String, String>): String {
        val url = URL(API_ROOT)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
        }

        val body = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(body)
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $response")
        }
        return response
    }
}
