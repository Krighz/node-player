package com.nodeplayer.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.nodeplayer.common.logging.NodeLog
import java.io.IOException

/**
 * Custom upstream [DataSource] that partitions requests to Google Video CDN streams
 * into small bounded range chunks (default 512 KB) for iOS streams, while allowing
 * unthrottled passthrough for non-iOS streams (e.g. ANDROID_VR).
 *
 * Includes HTTP 403 Forbidden auto-recovery by refreshing the stream URL via [AudioCache].
 */
@UnstableApi
class RangedUpstreamDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : BaseDataSource(/* isNetwork = */ true) {

    init {
        require(chunkSize > 0) { "chunkSize must be greater than 0" }
    }

    private var upstream: DataSource? = null
    private var spec: DataSpec? = null
    private var headers: Map<String, List<String>> = emptyMap()
    private var passthrough = false
    private var opened = false
    private var remaining = C.LENGTH_UNSET.toLong()
    private var position = 0L
    private var chunkRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        remaining = dataSpec.length
        position = dataSpec.position
        passthrough = !dataSpec.uri.requiresChunkedRanging()

        val length = if (passthrough) {
            openChildWithRetry(dataSpec.length)
        } else {
            openNextChunk()
            val available = upstream?.contentRangeLength()?.let { (it - position).coerceAtLeast(0L) }
            if (available != null) {
                remaining = if (remaining == C.LENGTH_UNSET.toLong()) available else minOf(remaining, available)
            }
            remaining
        }

        transferStarted(dataSpec)
        opened = true
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        if (!passthrough && chunkRemaining == 0L) {
            closeUpstream()
            if (!openNextChunk()) return C.RESULT_END_OF_INPUT
        }

        val allowed = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val wanted = if (passthrough) allowed else minOf(allowed.toLong(), chunkRemaining).toInt()
        val read = requireNotNull(upstream).read(buffer, offset, wanted)

        if (read == C.RESULT_END_OF_INPUT) {
            closeUpstream()
            remaining = 0
        } else if (read > 0) {
            position += read
            if (!passthrough) chunkRemaining -= read
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = upstream?.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = headers

    override fun close() {
        try {
            closeUpstream()
        } finally {
            spec = null
            headers = emptyMap()
            remaining = C.LENGTH_UNSET.toLong()
            chunkRemaining = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun openNextChunk(): Boolean {
        if (remaining == 0L) return false
        val wanted = if (remaining == C.LENGTH_UNSET.toLong()) chunkSize else minOf(chunkSize, remaining)
        val length = try {
            openChildWithRetry(wanted)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode != 416 || position == requireNotNull(spec).position) throw error
            remaining = 0
            return false
        }
        chunkRemaining = if (length == C.LENGTH_UNSET.toLong()) wanted else minOf(wanted, length)
        if (chunkRemaining < wanted) {
            remaining = if (remaining == C.LENGTH_UNSET.toLong()) chunkRemaining else minOf(remaining, chunkRemaining)
        }
        return chunkRemaining > 0
    }

    private fun openChildWithRetry(length: Long): Long {
        return try {
            openChild(length)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 403) {
                NodeLog.w(TAG, "HTTP 403 encountered on chunk at pos=$position, attempting token/URL refresh")
                val currentSpec = spec
                if (currentSpec != null) {
                    val freshUrl = AudioCache.refreshStreamUrl(currentSpec.uri.toString())
                    if (!freshUrl.isNullOrBlank()) {
                        NodeLog.i(TAG, "Stream URL refreshed successfully after 403, retrying connection")
                        val newUri = Uri.parse(freshUrl)
                        spec = currentSpec.buildUpon().setUri(newUri).build()
                        return openChild(length)
                    }
                }
            }
            throw e
        }
    }

    private fun openChild(length: Long): Long {
        val child = upstreamFactory.createDataSource()
        upstream = child
        return try {
            val childSpec = requireNotNull(spec).buildUpon()
                .setPosition(position)
                .setLength(length)
                .build()
            val result = child.open(childSpec)
            headers = child.responseHeaders
            result
        } catch (error: Exception) {
            closeUpstream()
            throw error
        }
    }

    private fun closeUpstream() {
        val child = upstream ?: return
        upstream = null
        try {
            child.close()
        } catch (_: IOException) {
        }
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkSize: Long = DEFAULT_CHUNK_SIZE
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RangedUpstreamDataSource(upstreamFactory, chunkSize)
    }

    companion object {
        private const val TAG = "RangedUpstreamDS"
        const val DEFAULT_CHUNK_SIZE = 512L * 1024 // 512 KB chunks for GoogleVideo CDN
    }
}

internal fun Uri.isGoogleVideoUrl(): Boolean =
    scheme == "https" && (host == "googlevideo.com" || host.orEmpty().endsWith(".googlevideo.com"))

/**
 * Google Video streams originating from the iOS client (c=IOS) enforce subsegment bounded range slicing.
 * Non-iOS streams (like ANDROID_VR or WEB) can stream continuously without forced 512 KB slicing.
 */
internal fun Uri.requiresChunkedRanging(): Boolean {
    if (!isGoogleVideoUrl()) return false
    val clientParam = getQueryParameter("c")
    return clientParam.equals("IOS", ignoreCase = true)
}

@UnstableApi
internal fun DataSource.contentRangeLength(): Long? = responseHeaders.entries
    .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
    ?.value?.firstOrNull()?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
