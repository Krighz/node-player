package com.nodeplayer.library.lyrics

import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight, zero-dependency HTTP helper for lyrics fetching in :core-library.
 * Complies with strict pure Kotlin JVM module boundaries (no android.* dependencies).
 */
object LyricsHttp {

    private const val DEFAULT_USER_AGENT = "NodePlayer/2.0.0 (Audiophile Music Player; +https://github.com/nodeplayer)"

    suspend fun get(
        urlStr: String,
        timeoutMs: Int = 6000,
        maxRetries: Int = 1,
        headers: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        var attempt = 0
        var backoffMs = 400L
        while (attempt <= maxRetries) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs + 2000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                    for ((k, v) in headers) {
                        setRequestProperty(k, v)
                    }
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    return@withContext connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else if (code in 500..599 && attempt < maxRetries) {
                    attempt++
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    attempt++
                    delay(backoffMs)
                    backoffMs *= 2
                } else {
                    NodeLog.w("LyricsHttp", "GET failed ($urlStr): ${e.message}")
                    return@withContext null
                }
            } finally {
                connection?.disconnect()
            }
        }
        null
    }
}
