package com.nodeplayer.music.playback

import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.DeliveryMethod

object NewPipeFallback {
    private const val TAG = "NewPipeFallback"
    @Volatile
    private var initialized = false

    fun init(okHttpClient: okhttp3.OkHttpClient) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                NewPipe.init(object : Downloader() {
                    override fun execute(request: Request): Response {
                        val reqBuilder = okhttp3.Request.Builder()
                            .url(request.url())
                            .method(request.httpMethod(), request.dataToSend()?.let { okhttp3.RequestBody.create(null, it) })
                        request.headers().forEach { (k, v) ->
                            v.forEach { reqBuilder.addHeader(k, it) }
                        }
                        if (request.headers()["User-Agent"] == null) {
                            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                        }
                        val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                        val responseBody = resp.body?.string()
                        return Response(resp.code, resp.message, resp.headers.toMultimap(), responseBody, resp.request.url.toString())
                    }
                })
                initialized = true
                NodeLog.i(TAG, "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                NodeLog.e(TAG, "Failed to initialize NewPipeExtractor: ${e.message}", e)
            }
        }
    }

    suspend fun resolveStream(videoId: String): Pair<String, Map<String, String>> = withContext(Dispatchers.IO) {
        NodeLog.i(TAG, "Resolving stream via NewPipe for videoId=$videoId")
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val audioStream = extractor.audioStreams
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
            ?: error("No audio stream found for $videoId")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            "Referer" to "https://www.youtube.com/"
        )
        NodeLog.i(TAG, "NewPipe resolved stream for videoId=$videoId (format=${audioStream.format?.name}, bitrate=${audioStream.averageBitrate}kbps)")
        Pair(audioStream.content, headers)
    }

    suspend fun resolveVideoStream(
        videoId: String,
        preferredHeight: Int? = null
    ): Pair<String, Map<String, String>> = withContext(Dispatchers.IO) {
        NodeLog.i(TAG, "Resolving video stream via NewPipe for videoId=$videoId (preferredHeight=$preferredHeight)")
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val allVideoStreams = (extractor.videoOnlyStreams ?: emptyList()) + (extractor.videoStreams ?: emptyList())
        val videoStream = (if (preferredHeight != null) {
            allVideoStreams.firstOrNull { it.height == preferredHeight }
        } else null)
            ?: allVideoStreams.filter { it.height in 360..720 }.maxByOrNull { it.height }
            ?: allVideoStreams.firstOrNull()
            ?: error("No video stream found for $videoId via NewPipe")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            "Referer" to "https://www.youtube.com/"
        )
        NodeLog.i(TAG, "NewPipe resolved video stream for videoId=$videoId (height=${videoStream.height}, format=${videoStream.format?.name})")
        Pair(videoStream.content, headers)
    }
}
