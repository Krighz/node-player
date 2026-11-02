package com.nodeplayer.music.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nodeplayer.music.MainActivity
import com.nodeplayer.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object UpdateDownloadManager {

    private const val TAG = "UpdateDownloadManager"
    private const val NOTIFICATION_CHANNEL_ID = "nodeplayer_updates_channel"
    private const val NOTIFICATION_ID = 4040

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private var activeApkFile: File? = null

    private fun getUpdatesDir(context: Context): File {
        return File(context.filesDir, "updates").apply { mkdirs() }
    }

    fun getDownloadedApk(context: Context, versionName: String): File? {
        val file = File(getUpdatesDir(context), "NodePlayer-$versionName.apk")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun deleteUpdateFiles(context: Context) {
        try {
            getUpdatesDir(context).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete updates dir", e)
        }
    }

    /**
     * Verifies that the download URL points to the official repository releases
     * or a trusted debug mock URL.
     */
    fun isAllowedDownloadUrl(context: Context, url: String): Boolean {
        val mockUrl = UpdatePreferences.getDebugMockUrl(context)
        if (!mockUrl.isNullOrBlank() && url.startsWith(mockUrl.substringBeforeLast("/"))) {
            return true
        }
        return url.startsWith("https://github.com/Krighz/node-player/releases/download/") ||
                url.startsWith("https://objects.githubusercontent.com/")
    }

    /**
     * Checks if available storage space is sufficient.
     */
    fun hasSufficientStorage(dir: File, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(dir.path)
            val available = stat.availableBytes
            // Require at least asset size + 50 MB buffer
            val buffer = 50L * 1024L * 1024L
            available > (requiredBytes + buffer)
        } catch (_: Exception) {
            true
        }
    }

    fun startDownload(context: Context, updateInfo: AppUpdateInfo) {
        if (_downloadStatus.value is DownloadStatus.Downloading || _downloadStatus.value is DownloadStatus.Verifying) {
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            createNotificationChannel(context)
            val updatesDir = getUpdatesDir(context)
            val targetApk = File(updatesDir, "NodePlayer-${updateInfo.versionName}.apk")
            val partFile = File(updatesDir, "NodePlayer-${updateInfo.versionName}.apk.part")
            activeApkFile = targetApk

            // Security check on download URL
            if (!isAllowedDownloadUrl(context, updateInfo.downloadUrl)) {
                val errorMsg = "Untrusted download URL refused: ${updateInfo.downloadUrl}"
                Log.e(TAG, errorMsg)
                _downloadStatus.value = DownloadStatus.Error(errorMsg)

                return@launch
            }

            // Storage check
            if (!hasSufficientStorage(updatesDir, updateInfo.assetSize)) {
                val errorMsg = "Low storage: at least 50 MB free space required for update."
                Log.e(TAG, errorMsg)
                _downloadStatus.value = DownloadStatus.Error(errorMsg)

                return@launch
            }

            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

                val url = URL(updateInfo.downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "NodePlayer/${UpdateChecker.getInstalledVersionName(context)}")

                // Resume header if partial download exists
                if (downloadedBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                }

                connection.connect()

                val responseCode = connection.responseCode
                val isResumed = responseCode == HttpURLConnection.HTTP_PARTIAL
                val isOk = responseCode == HttpURLConnection.HTTP_OK

                if (!isResumed && !isOk) {
                    throw IllegalStateException("HTTP $responseCode from download server")
                }

                val contentLength = connection.contentLengthLong
                val totalBytes = if (isResumed) {
                    downloadedBytes + contentLength
                } else {
                    downloadedBytes = 0L // Server did not support range or restarted
                    contentLength
                }

                outputStream = FileOutputStream(partFile, isResumed)
                inputStream = connection.inputStream

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgressUpdate = 0L

                _downloadStatus.value = DownloadStatus.Downloading(
                    progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes
                )

                showDownloadNotification(context, (downloadedBytes * 100 / totalBytes.coerceAtLeast(1)).toInt())

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300L) {
                        lastProgressUpdate = now
                        val fraction = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
                        _downloadStatus.value = DownloadStatus.Downloading(
                            progress = fraction,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
        
                        showDownloadNotification(context, (fraction * 100).toInt())
                    }
                }
                outputStream.flush()

                // Download completed, start SHA-256 verification
                _downloadStatus.value = DownloadStatus.Verifying

                dismissNotification(context)

                val expectedSha256 = fetchExpectedSha256(context, updateInfo.sha256Url)
                if (expectedSha256.isNullOrBlank()) {
                    partFile.delete()
                    throw IllegalStateException("Failed to retrieve release SHA-256 checksum asset")
                }

                val actualSha256 = calculateSha256(partFile)
                Log.d(TAG, "Checksum verification: expected=$expectedSha256, actual=$actualSha256")

                if (!expectedSha256.equals(actualSha256, ignoreCase = true)) {
                    partFile.delete()
                    val mismatchMsg = "Checksum mismatch! Expected $expectedSha256 but got $actualSha256. File deleted."
                    Log.e(TAG, mismatchMsg)
                    _downloadStatus.value = DownloadStatus.Error("Verification failed: corrupt or tampered APK")
    
                    return@launch
                }

                // Checksum verified! Rename part to final APK
                if (targetApk.exists()) targetApk.delete()
                if (!partFile.renameTo(targetApk)) {
                    partFile.copyTo(targetApk, overwrite = true)
                    partFile.delete()
                }

                _downloadStatus.value = DownloadStatus.ReadyToInstall(targetApk)

                showReadyToInstallNotification(context, updateInfo.versionName)
                Log.i(TAG, "Update APK successfully downloaded and verified at ${targetApk.absolutePath}")

            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled by user")
                partFile.delete()
                _downloadStatus.value = DownloadStatus.Idle

                dismissNotification(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed", e)
                _downloadStatus.value = DownloadStatus.Error(e.message ?: "Download failed")

                dismissNotification(context)
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                connection?.disconnect()
            }
        }
    }

    fun cancelDownload(context: Context) {
        downloadJob?.cancel()
        downloadJob = null
        deleteUpdateFiles(context)
        _downloadStatus.value = DownloadStatus.Idle
        dismissNotification(context)
    }

    /**
     * Downloads and parses the expected SHA-256 checksum string from the .sha256 asset.
     */
    fun fetchExpectedSha256(context: Context, sha256Url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(sha256Url)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "NodePlayer/${UpdateChecker.getInstalledVersionName(context)}")
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                // Typical format: "<hash>  NodePlayer.apk" or just "<hash>"
                text.split("\\s+".toRegex()).firstOrNull()?.trim()?.lowercase(Locale.US)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch SHA-256 file", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Computes the SHA-256 hash of a local file.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download and installation progress for Node Player updates"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showDownloadNotification(context: Context, progressPercent: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading Node Player Update")
                .setContentText("$progressPercent%")
                .setProgress(100, progressPercent, progressPercent < 0)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun showReadyToInstallNotification(context: Context, version: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Node Player v$version Ready")
                .setContentText("Update verified. Tap to install.")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun dismissNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
