package com.nodeplayer.music.sync

import android.content.Context
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * REST API v3 client for Google Drive hidden `appDataFolder` operations.
 * Strictly scoped to `spaces=appDataFolder` to guarantee zero access to personal files.
 */
class GoogleDriveApiClient(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveApiClient"
        private const val BASE_DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val BASE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val MAX_RETRIES = 3
    }

    /**
     * Finds the file ID of `user_music_db.json` in `appDataFolder`.
     * Returns null if the file does not exist yet.
     */
    suspend fun findBackupFileId(): Result<String?> = executeWithRetry { token ->
        val endpoint = "$BASE_DRIVE_API/files?spaces=appDataFolder&q=name%3D'${SyncSnapshot.FILE_NAME}'&fields=files(id%2Cname)"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code in 500..599 || code == 429) throw RetryableException("HTTP $code")

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IOException("Drive API find file failed with code $code: $response")
        }

        val json = JSONObject(response)
        val files = json.optJSONArray("files")
        if (files != null && files.length() > 0) {
            val firstFile = files.getJSONObject(0)
            firstFile.optString("id", "").takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    /**
     * Downloads existing `user_music_db.json` contents from `appDataFolder`.
     */
    suspend fun downloadBackup(fileId: String): Result<String> = executeWithRetry { token ->
        val endpoint = "$BASE_DRIVE_API/files/$fileId?alt=media"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 20000
        conn.readTimeout = 20000

        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code in 500..599 || code == 429) throw RetryableException("HTTP $code")

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IOException("Drive API download failed with code $code: $response")
        }

        response
    }

    /**
     * Creates `user_music_db.json` inside `appDataFolder` using multipart upload.
     */
    suspend fun createBackup(jsonContent: String): Result<String> = executeWithRetry { token ->
        val endpoint = "$BASE_UPLOAD_API/files?uploadType=multipart"
        val boundary = "nodeplayer_sync_boundary_${System.currentTimeMillis()}"

        val metadataJson = JSONObject().apply {
            put("name", SyncSnapshot.FILE_NAME)
            put("parents", org.json.JSONArray().put("appDataFolder"))
        }.toString()

        val multipartBody = buildMultipartBody(boundary, metadataJson, jsonContent)

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.setRequestProperty("Content-Length", multipartBody.size.toString())
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        conn.outputStream.use { it.write(multipartBody) }

        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code in 500..599 || code == 429) throw RetryableException("HTTP $code")

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IOException("Drive API create file failed with code $code: $response")
        }

        val json = JSONObject(response)
        json.getString("id")
    }

    /**
     * Updates existing `user_music_db.json` in `appDataFolder` using multipart PATCH upload.
     */
    suspend fun updateBackup(fileId: String, jsonContent: String): Result<Unit> = executeWithRetry { token ->
        val endpoint = "$BASE_UPLOAD_API/files/$fileId?uploadType=multipart"
        val boundary = "nodeplayer_sync_boundary_${System.currentTimeMillis()}"

        val metadataJson = JSONObject().apply {
            put("name", SyncSnapshot.FILE_NAME)
        }.toString()

        val multipartBody = buildMultipartBody(boundary, metadataJson, jsonContent)

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.setRequestProperty("Content-Length", multipartBody.size.toString())
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        conn.outputStream.use { it.write(multipartBody) }

        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code in 500..599 || code == 429) throw RetryableException("HTTP $code")

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw IOException("Drive API update file failed with code $code: $response")
        }

        Unit
    }

    /**
     * Deletes `user_music_db.json` from `appDataFolder`.
     */
    suspend fun deleteBackup(fileId: String): Result<Unit> = executeWithRetry { token ->
        val endpoint = "$BASE_DRIVE_API/files/$fileId"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val code = conn.responseCode
        if (code == 401) throw UnauthorizedException()
        if (code in 500..599 || code == 429) throw RetryableException("HTTP $code")
        conn.disconnect()

        if (code !in 200..299 && code != 404) {
            throw IOException("Drive API delete file failed with code $code")
        }

        Unit
    }

    private fun buildMultipartBody(boundary: String, metadata: String, media: String): ByteArray {
        val out = ByteArrayOutputStream()
        val crlf = "\r\n"

        // Part 1: Metadata
        out.write(("--$boundary$crlf").toByteArray(StandardCharsets.UTF_8))
        out.write(("Content-Type: application/json; charset=UTF-8$crlf$crlf").toByteArray(StandardCharsets.UTF_8))
        out.write(metadata.toByteArray(StandardCharsets.UTF_8))
        out.write(crlf.toByteArray(StandardCharsets.UTF_8))

        // Part 2: Media
        out.write(("--$boundary$crlf").toByteArray(StandardCharsets.UTF_8))
        out.write(("Content-Type: application/json; charset=UTF-8$crlf$crlf").toByteArray(StandardCharsets.UTF_8))
        out.write(media.toByteArray(StandardCharsets.UTF_8))
        out.write(crlf.toByteArray(StandardCharsets.UTF_8))

        // End Boundary
        out.write(("--$boundary--$crlf").toByteArray(StandardCharsets.UTF_8))

        return out.toByteArray()
    }

    private suspend fun <T> executeWithRetry(block: (token: String) -> T): Result<T> = withContext(Dispatchers.IO) {
        var attempt = 0
        var refreshedToken = false

        while (true) {
            try {
                val token = GoogleAuthManager.getValidAccessToken(context)
                    ?: return@withContext Result.failure<T>(IllegalStateException("Not authenticated with Google Drive"))

                val result = block(token)
                return@withContext Result.success(result)
            } catch (e: UnauthorizedException) {
                if (!refreshedToken) {
                    refreshedToken = true
                    NodeLog.w(TAG, "Encountered 401 Unauthorized, refreshing Google OAuth token...")
                    val refreshRes = GoogleAuthManager.refreshAccessToken(context)
                    if (refreshRes.isSuccess) {
                        continue // Retry with refreshed token
                    }
                }
                NodeLog.e(TAG, "Authentication failed after token refresh attempt", e)
                return@withContext Result.failure<T>(IllegalStateException("Google authentication expired. Please re-login."))
            } catch (e: Exception) {
                val isRetryable = e is RetryableException || e is IOException
                if (isRetryable && attempt < MAX_RETRIES) {
                    attempt++
                    val backoffMs = (1000L * (1 shl (attempt - 1))) + Random.nextLong(0, 400)
                    NodeLog.w(TAG, "Request failed (${e.message}), retrying in ${backoffMs}ms (attempt $attempt/$MAX_RETRIES)")
                    delay(backoffMs)
                } else {
                    NodeLog.e(TAG, "Request failed permanently: ${e.message}", e)
                    return@withContext Result.failure<T>(e)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure<T>(IllegalStateException("Loop terminated unexpectedly"))
    }

    private class UnauthorizedException : IOException("401 Unauthorized")
    private class RetryableException(msg: String) : IOException(msg)
}
