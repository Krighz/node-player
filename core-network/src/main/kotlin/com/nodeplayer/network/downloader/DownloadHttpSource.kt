package com.nodeplayer.network.downloader

import com.nodeplayer.common.logging.NodeLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Thrown when the stream URL has expired (HTTP 403 or 410) and requires re-resolution.
 */
class StreamExpiredException(message: String) : Exception(message)

/**
 * Abstraction for streaming bytes from an HTTP source to a local `.part` file.
 */
interface DownloadDataSource {
    suspend fun downloadToFile(
        url: String,
        destinationPartFile: File,
        expectedLength: Long = 0L,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): Result<Long>
}

/**
 * Resilient HTTP range-supporting streaming downloader.
 */
class DownloadHttpSource(
    engine: HttpClientEngine? = null
) : DownloadDataSource {

    private companion object {
        const val TAG = "download/http-source"
        const val BUFFER_SIZE = 32 * 1024
        const val PROGRESS_INTERVAL_MS = 100L
    }

    private val client: HttpClient = if (engine != null) {
        HttpClient(engine)
    } else {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    override suspend fun downloadToFile(
        url: String,
        destinationPartFile: File,
        expectedLength: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            destinationPartFile.parentFile?.mkdirs()
            val existingLength = if (destinationPartFile.exists()) destinationPartFile.length() else 0L

            client.prepareGet(url) {
                if (existingLength > 0L) {
                    header(HttpHeaders.Range, "bytes=$existingLength-")
                }
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.execute { response ->
                val status = response.status
                if (status == HttpStatusCode.Forbidden || status == HttpStatusCode.Gone) {
                    throw StreamExpiredException("Stream URL expired or forbidden: HTTP ${status.value}")
                }
                if (!status.value.toString().startsWith("2")) {
                    throw IllegalStateException("Unexpected HTTP response: ${status.value} ${status.description}")
                }

                val isPartial = status == HttpStatusCode.PartialContent
                val channel: ByteReadChannel = response.bodyAsChannel()

                val totalLength = if (isPartial) {
                    val contentRange = response.headers[HttpHeaders.ContentRange]
                    val parsedTotal = contentRange?.substringAfterLast("/")?.toLongOrNull()
                    parsedTotal ?: (existingLength + (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L))
                } else {
                    response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: expectedLength
                }

                val append = isPartial && existingLength > 0L
                val outputStream = FileOutputStream(destinationPartFile, append)

                var currentBytes = if (append) existingLength else 0L
                val buffer = ByteArray(BUFFER_SIZE)

                var lastProgressTime = System.currentTimeMillis()
                var bytesSinceLastProgress = 0L
                var speedBps = 0L

                outputStream.use { out ->
                    while (!channel.isClosedForRead) {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesRead <= 0) break

                        out.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        bytesSinceLastProgress += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastProgressTime
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            speedBps = if (elapsed > 0) (bytesSinceLastProgress * 1000L) / elapsed else 0L
                            lastProgressTime = now
                            bytesSinceLastProgress = 0L
                            onProgress(currentBytes, totalLength, speedBps)
                        }
                    }
                    out.flush()
                }

                onProgress(currentBytes, totalLength, 0L)
                NodeLog.d(TAG, "Download stream finished: $currentBytes bytes")
                Result.success(currentBytes)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            NodeLog.w(TAG, "Download failed: ${e.message}")
            Result.failure(e)
        }
    }
}
