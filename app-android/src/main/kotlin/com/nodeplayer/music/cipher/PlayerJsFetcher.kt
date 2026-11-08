package com.nodeplayer.music.cipher

import android.content.Context
import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches YouTube player hash from iframe_api, downloads base.js, and caches to disk with a 6-hour TTL.
 */
class PlayerJsFetcher(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, "cipher_cache")

    private fun getCacheFile(hash: String): File = File(cacheDir, "player_$hash.js")

    private fun getHashFile(): File = File(cacheDir, "current_hash.txt")

    suspend fun getPlayerJs(forceRefresh: Boolean = false): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val dir = cacheDir
            if (!dir.exists()) dir.mkdirs()

            if (!forceRefresh) {
                val cached = readFromCache()
                if (cached != null) {
                    NodeLog.d(TAG, "Using cached player JS (hash=${cached.second})")
                    return@withContext cached
                }
            }

            val hash = fetchPlayerHash()
            if (hash == null) {
                NodeLog.e(TAG, "Failed to extract player hash from iframe_api")
                return@withContext null
            }
            NodeLog.d(TAG, "Extracted player hash: $hash")

            val playerJs = downloadPlayerJs(hash)
            if (playerJs == null) {
                NodeLog.e(TAG, "Failed to download player JS for hash=$hash")
                return@withContext null
            }
            NodeLog.d(TAG, "Downloaded player JS: ${playerJs.length} chars")

            writeToCache(hash, playerJs)

            Pair(playerJs, hash)
        } catch (e: Exception) {
            NodeLog.e(TAG, "getPlayerJs exception: ${e.message}", e)
            null
        }
    }

    fun invalidateCache() {
        try {
            val dir = cacheDir
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            NodeLog.d(TAG, "Cache invalidated")
        } catch (e: Exception) {
            NodeLog.e(TAG, "Failed to invalidate cache: ${e.message}", e)
        }
    }

    private fun readFromCache(): Pair<String, String>? {
        try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) return null

            val hashData = hashFile.readText().split("\n")
            if (hashData.size < 2) return null

            val hash = hashData[0]
            val timestamp = hashData[1].toLongOrNull() ?: return null

            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                NodeLog.d(TAG, "Cache expired (hash=$hash)")
                return null
            }

            val cacheFile = getCacheFile(hash)
            if (!cacheFile.exists()) return null

            val playerJs = cacheFile.readText()
            if (playerJs.isEmpty()) return null

            return Pair(playerJs, hash)
        } catch (e: Exception) {
            NodeLog.e(TAG, "Error reading cache: ${e.message}", e)
            return null
        }
    }

    private fun writeToCache(hash: String, playerJs: String) {
        try {
            val dir = cacheDir
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("player_")) file.delete()
            }

            getCacheFile(hash).writeText(playerJs)
            getHashFile().writeText("$hash\n${System.currentTimeMillis()}")
        } catch (e: Exception) {
            NodeLog.e(TAG, "Error writing cache: ${e.message}", e)
        }
    }

    private fun fetchPlayerHash(): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(IFRAME_API_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode !in 200..299) {
                NodeLog.e(TAG, "iframe_api HTTP ${conn.responseCode}")
                return null
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val match = PLAYER_HASH_REGEX.find(body)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            NodeLog.e(TAG, "fetchPlayerHash error: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadPlayerJs(hash: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(String.format(PLAYER_JS_URL_TEMPLATE, hash))
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode !in 200..299) {
                NodeLog.e(TAG, "player JS download HTTP ${conn.responseCode}")
                return null
            }

            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            NodeLog.e(TAG, "downloadPlayerJs error: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "cipher/fetcher"
        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val PLAYER_HASH_REGEX = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""")
    }
}
