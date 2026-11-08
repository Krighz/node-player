package com.nodeplayer.desktop.audio

import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Persistent 512MB LRU audio cache for Desktop.
 * Stores audio streams in `%LOCALAPPDATA%/NodePlayer/audio_cache` (fallback to `~/.nodeplayer/audio_cache`).
 * Cache files are keyed by clean YouTube videoId: `{videoId}.opus` or `{videoId}.m4a`.
 */
class DesktopAudioCache(
    val cacheDir: File = defaultCacheDirectory(),
    val maxCacheBytes: Long = MAX_CACHE_BYTES
) {

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Extracts clean YouTube videoId from synthetic URI or standard YouTube URL.
     * Supports:
     * - `nodeplayer://youtube/{videoId}`
     * - `https://.../watch?v={videoId}`
     * - `youtu.be/{videoId}`
     * - raw 11-char videoId
     */
    fun extractVideoId(uriOrId: String): String? {
        val trimmed = uriOrId.trim()
        if (trimmed.startsWith("nodeplayer://youtube/", ignoreCase = true)) {
            val after = trimmed.substring("nodeplayer://youtube/".length).trimStart('/')
            return after.substringBefore('?').substringBefore('#').substringBefore('/').takeIf { it.isNotBlank() }
        }

        val vParamRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{11})""")
        vParamRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        val youtuBeRegex = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""")
        youtuBeRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        val shortsRegex = Regex("""/(?:shorts|embed)/([a-zA-Z0-9_-]{11})""")
        shortsRegex.find(trimmed)?.let { match ->
            return match.groupValues.getOrNull(1)
        }

        // Direct video ID check (standard YouTube video IDs are 11 chars)
        if (trimmed.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
            return trimmed
        }

        return null
    }

    /**
     * Returns the cached audio file for a given videoId (or URI) if it exists, updating its lastModified timestamp.
     */
    fun getCachedFile(videoIdOrUri: String): File? {
        val videoId = extractVideoId(videoIdOrUri) ?: videoIdOrUri.trim()
        if (videoId.isBlank()) return null

        val files = cacheDir.listFiles { file ->
            file.isFile && (file.name == "$videoId.opus" || file.name == "$videoId.m4a" || file.name.startsWith("$videoId.")) && !file.name.endsWith(".tmp")
        } ?: return null

        val match = files.firstOrNull { it.length() > 0 } ?: return null
        // Update access time for LRU tracking
        match.setLastModified(System.currentTimeMillis())
        return match
    }

    /**
     * Returns true if a cached file exists and is non-empty for this videoId (or URI).
     */
    fun isCached(videoIdOrUri: String): Boolean {
        return getCachedFile(videoIdOrUri) != null
    }

    /**
     * Returns a target File for writing the cache stream with the given videoId and extension.
     */
    fun getCacheFileForWriting(videoId: String, extension: String = "opus"): File {
        val cleanExt = extension.trimStart('.')
        return File(cacheDir, "$videoId.$cleanExt")
    }

    /**
     * Downloads an audio stream URL to the disk cache asynchronously and enforces the LRU limit on completion.
     * Returns the cached File on success, or null on failure.
     */
    suspend fun cacheStream(videoId: String, streamUrl: String, extension: String = "opus"): File? = withContext(Dispatchers.IO) {
        val targetFile = getCacheFileForWriting(videoId, extension)
        val tempFile = File(cacheDir, "${targetFile.name}.tmp.${System.currentTimeMillis()}")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(streamUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                NodeLog.w(TAG, "Failed to download stream for $videoId: HTTP $responseCode")
                return@withContext null
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }

            if (tempFile.length() <= 0) {
                tempFile.delete()
                return@withContext null
            }

            // Atomic rename or replace
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.setLastModified(System.currentTimeMillis())

            enforceLruLimit()
            NodeLog.i(TAG, "Successfully cached stream for videoId=$videoId to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            targetFile
        } catch (e: Exception) {
            NodeLog.w(TAG, "Exception while caching stream for $videoId: ${e.message}")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Enforces the LRU disk cache limit. If total cache size exceeds [maxCacheBytes],
     * deletes files in ascending order of lastModified timestamp until under the limit.
     */
    @Synchronized
    fun enforceLruLimit() {
        try {
            val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
            var totalBytes = files.sumOf { it.length() }
            if (totalBytes <= maxCacheBytes) return

            // Sort by oldest lastModified first
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                if (totalBytes <= maxCacheBytes) break
                val size = file.length()
                if (file.delete()) {
                    totalBytes -= size
                    NodeLog.d(TAG, "LRU evicted cache file: ${file.name} ($size bytes)")
                }
            }
        } catch (e: Exception) {
            NodeLog.w(TAG, "Error enforcing LRU limit: ${e.message}")
        }
    }

    /**
     * Clears all files in the cache.
     */
    @Synchronized
    fun clearCache() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    /**
     * Returns total bytes currently stored in the cache.
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    companion object {
        private const val TAG = "DesktopAudioCache"
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB

        private val defaultInstance by lazy { DesktopAudioCache() }

        /**
         * Global default singleton instance.
         */
        val default: DesktopAudioCache
            get() = defaultInstance

        fun defaultCacheDirectory(): File {
            val localAppData = System.getenv("LOCALAPPDATA")
            val baseDir = if (!localAppData.isNullOrBlank()) {
                File(localAppData, "NodePlayer")
            } else {
                File(System.getProperty("user.home"), ".nodeplayer")
            }
            return File(baseDir, "audio_cache")
        }

        fun extractVideoId(uriOrId: String): String? = defaultInstance.extractVideoId(uriOrId)
        fun getCachedFile(videoIdOrUri: String): File? = defaultInstance.getCachedFile(videoIdOrUri)
        fun isCached(videoIdOrUri: String): Boolean = defaultInstance.isCached(videoIdOrUri)
        fun getCacheFileForWriting(videoId: String, extension: String = "opus"): File =
            defaultInstance.getCacheFileForWriting(videoId, extension)
        suspend fun cacheStream(videoId: String, streamUrl: String, extension: String = "opus"): File? =
            defaultInstance.cacheStream(videoId, streamUrl, extension)
        fun enforceLruLimit() = defaultInstance.enforceLruLimit()
    }
}
