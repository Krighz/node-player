package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.media.CipherSolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable

/**
 * Resolved audio stream information containing direct streaming URL and metadata.
 */
data class ResolvedStream(
    val videoId: String,
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Long,
    val durationSeconds: Long,
    val loudnessDb: Double?,
    val clientName: String,
    val height: Int? = null,
    val availableHeights: List<Int> = emptyList()
)

/**
 * High-performance, pure Kotlin multi-client audio stream resolver with cipher solving
 * and HTTP range probe verification.
 *
 * Implements client rotation fallback:
 * 1. `YouTubeClient.ANDROID_VR` (direct unthrottled streams, no cipher required)
 * 2. `YouTubeClient.IOS`
 * 3. `YouTubeClient.VISIONOS`
 * 4. `YouTubeClient.WEB_REMIX` (deciphering with `CipherSolver`)
 *
 * Prioritizes high-fidelity audio streams:
 * - Itag 251 (Opus 160k, 48kHz)
 * - Itag 140 (AAC 128k, 44.1kHz)
 * - Highest audio bitrate format fallback
 */
class StreamResolver(
    val innerTubeClient: InnerTubeClient = InnerTubeClient(),
    private val cipherSolver: CipherSolver? = CipherSolver.current,
    probeEngine: HttpClientEngine? = null,
) : Closeable {

    constructor(
        innerTubeClient: InnerTubeClient = InnerTubeClient(),
        cipherSolver: CipherSolver? = CipherSolver.current,
    ) : this(innerTubeClient, cipherSolver, null)

    private val probeClient: HttpClient = if (probeEngine != null) {
        HttpClient(probeEngine) { configureProbeClient() }
    } else {
        HttpClient(CIO) { configureProbeClient() }
    }

    private fun io.ktor.client.HttpClientConfig<*>.configureProbeClient() {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = PROBE_TIMEOUT_MS
            requestTimeoutMillis = PROBE_TIMEOUT_MS
            socketTimeoutMillis = PROBE_TIMEOUT_MS
        }
    }

    override fun close() {
        probeClient.close()
    }

    /**
     * Resolves playable audio stream for [videoId] executing client rotation and active range probing.
     */
    suspend fun resolveStream(videoId: String): Result<ResolvedStream> {
        return resolveStreamInternal(videoId, isVideo = false)
    }

    /**
     * Resolves video stream for [videoId] with optional [preferredHeight] executing client rotation.
     */
    suspend fun resolveVideoStream(videoId: String, preferredHeight: Int? = null): Result<ResolvedStream> {
        return resolveStreamInternal(videoId, isVideo = true, preferredHeight = preferredHeight)
    }

    private suspend fun resolveStreamInternal(
        videoId: String,
        isVideo: Boolean,
        preferredHeight: Int? = null
    ): Result<ResolvedStream> {
        NodeLog.d(TAG, "Resolving stream for videoId=$videoId across clients (isVideo=$isVideo, preferredHeight=$preferredHeight)")
        val errors = mutableListOf<String>()

        val clients = if (!innerTubeClient.sessionCookie.isNullOrBlank()) {
            listOf(YouTubeClient.WEB_REMIX) + CLIENT_ROTATION.filterNot { it == YouTubeClient.WEB_REMIX }
        } else if (isVideo) {
            CLIENT_ROTATION.filterNot { it == YouTubeClient.VISIONOS }
        } else CLIENT_ROTATION
        for (client in clients) {
            currentCoroutineContext().ensureActive()
            NodeLog.d(TAG, "Attempting stream resolution for videoId=$videoId with client=${client.clientName} (isVideo=$isVideo)")
            val responseResult = innerTubeClient.getPlayerResponse(videoId, client)

            if (responseResult.isFailure) {
                val errorMsg = "Client ${client.clientName} getPlayerResponse failed: ${responseResult.exceptionOrNull()?.message}"
                NodeLog.w(TAG, errorMsg)
                errors.add(errorMsg)
                continue
            }

            val response = responseResult.getOrThrow()
            val playability = response.playabilityStatus
            if (playability != null && playability.status != "OK") {
                val reason = playability.reason ?: playability.status ?: "Unknown playability status"
                val errorMsg = "Client ${client.clientName} playabilityStatus not OK: $reason"
                NodeLog.w(TAG, errorMsg)
                errors.add(errorMsg)
                continue
            }

            val streamingData = response.streamingData
            if (streamingData == null) {
                val errorMsg = "Client ${client.clientName} returned empty streamingData"
                NodeLog.w(TAG, errorMsg)
                errors.add(errorMsg)
                continue
            }

            val allFormats = streamingData.adaptiveFormats + (streamingData.formats ?: emptyList())
            val availableHeights = allFormats.mapNotNull { it.height }.distinct().sortedDescending()
            val streamCandidates = if (isVideo) rankVideoFormats(allFormats, preferredHeight) else rankAudioFormats(allFormats)

            if (streamCandidates.isEmpty()) {
                val errorMsg = "Client ${client.clientName} has no valid ${if (isVideo) "video" else "audio"} formats"
                NodeLog.w(TAG, errorMsg)
                errors.add(errorMsg)
                continue
            }

            var chosenCandidate: Pair<PlayerResponse.StreamingData.Format, String>? = null

            var probeAttempted = false
            for (candidate in streamCandidates) {
                var url = candidate.url

                // Decipher if url is absent but cipher / signatureCipher is present
                val cipherString = candidate.signatureCipher ?: candidate.cipher
                if (url.isNullOrBlank() && !cipherString.isNullOrBlank()) {
                    val solver = cipherSolver ?: CipherSolver.current
                    if (solver != null) {
                        try {
                            url = solver.deobfuscateStreamUrl(cipherString, videoId)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            NodeLog.w(TAG, "Cipher deobfuscation failed for itag ${candidate.itag}: ${e.message}")
                        }
                    } else {
                        NodeLog.w(TAG, "Stream requires deciphering but no CipherSolver registered")
                    }
                }

                if (url.isNullOrBlank()) {
                    continue
                }

                // Apply n-parameter transform if n= query parameter is present
                if (url.contains("n=")) {
                    val solver = cipherSolver ?: CipherSolver.current
                    if (solver != null) {
                        try {
                            val transformed = solver.transformNParamInUrl(url)
                            if (transformed.isNotBlank()) {
                                url = transformed
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            NodeLog.w(TAG, "n-parameter transform failed: ${e.message}")
                        }
                    }
                }

                // Active HTTP Range Probe validation
                probeAttempted = true
                if (verifyStreamUrl(url, client.userAgent, isVideo = isVideo)) {
                    chosenCandidate = candidate to url
                    break
                } else {
                    NodeLog.w(TAG, "Range probe failed for client ${client.clientName}, itag ${candidate.itag} (isVideo=$isVideo)")
                }
            }

            if (chosenCandidate != null) {
                val (format, validUrl) = chosenCandidate
                val durationSeconds = response.videoDetails?.lengthSeconds?.toLongOrNull()
                    ?: (format.approxDurationMs?.toLongOrNull()?.let { it / 1000 })
                    ?: 0L
                val loudnessDb = response.playerConfig?.audioConfig?.loudnessDb ?: format.loudnessDb

                val resolved = ResolvedStream(
                    videoId = videoId,
                    streamUrl = validUrl,
                    itag = format.itag,
                    mimeType = format.mimeType,
                    bitrate = format.bitrate?.toLong() ?: 0L,
                    durationSeconds = durationSeconds,
                    loudnessDb = loudnessDb,
                    clientName = client.clientName,
                    height = format.height,
                    availableHeights = availableHeights
                )
                NodeLog.i(TAG, "Successfully resolved ${if (isVideo) "video" else "audio"} stream for $videoId via ${client.clientName} (itag=${format.itag})")
                return Result.success(resolved)
            } else {
                errors.add("All candidates failed probe for client ${client.clientName}")
            }
        }

        val finalError = "Failed to resolve playable stream for video $videoId across all clients: ${errors.joinToString("; ")}"
        NodeLog.e(TAG, finalError)
        return Result.failure(IllegalStateException(finalError))
    }

    /**
     * Active HTTP Range Probe:
     * For audio: Probes starting at 1 MiB (AUTH_BOUNDARY_BYTES) if the file is longer than 1 MiB,
     * ensuring streams that get 403 cut off at 1:04 on unauthenticated clients are caught and rejected.
     * For video: Probes offset 0-1024 to verify immediate URL liveness.
     */
    internal suspend fun verifyStreamUrl(url: String, userAgent: String, isVideo: Boolean = false): Boolean {
        val length = url.substringAfter("clen=", "").substringBefore("&").toLongOrNull()
        val start = if (!isVideo && length != null && length > AUTH_BOUNDARY_BYTES + PROBE_READ_BYTES) {
            AUTH_BOUNDARY_BYTES
        } else {
            0L
        }
        val window = if (!isVideo && start > 0L) 128L * 1024 else 1024L
        val end = if (length != null) minOf(start + window, length) - 1 else start + window - 1
        val probeRange = "bytes=$start-$end"

        return try {
            probeClient.prepareGet(url) {
                header("Range", probeRange)
                header("User-Agent", userAgent)
            }.execute { response ->
                response.status == HttpStatusCode.PartialContent || response.status == HttpStatusCode.OK
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            NodeLog.w(TAG, "Stream probe error for $url at $probeRange: ${e.message}")
            false
        }
    }

    /**
     * Prioritizes formats:
     * 1. itag 140 (AAC 128k, 44.1kHz - native Apple client streaming format, resilient to offset chunking)
     * 2. itag 251 (Opus 160k, 48kHz)
     * 3. Other audio formats sorted by bitrate descending
     */
    internal fun rankAudioFormats(formats: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
        val audioFormats = formats.filter { it.isAudio || it.mimeType.contains("audio", ignoreCase = true) }
        val itag140 = audioFormats.filter { it.itag == 140 }
        val itag251 = audioFormats.filter { it.itag == 251 }
        val others = audioFormats.filter { it.itag != 140 && it.itag != 251 }
            .sortedByDescending { it.bitrate ?: 0 }

        return itag140 + itag251 + others
    }

    /**
     * Ranks video formats by preferred height and bitrate.
     * If [preferredHeight] is specified, formats closest to and <= preferredHeight are prioritized.
     */
    internal fun rankVideoFormats(
        formats: List<PlayerResponse.StreamingData.Format>,
        preferredHeight: Int? = null
    ): List<PlayerResponse.StreamingData.Format> {
        val videoFormats = formats.filter {
            it.height != null || it.mimeType.contains("video", ignoreCase = true)
        }
        val available = videoFormats.mapNotNull { it.height }.distinct().sorted()
        val effPreferred = preferredHeight ?: 480
        val targetHeight = if (available.isNotEmpty()) {
            available.lastOrNull { it <= effPreferred } ?: available.first()
        } else null

        return videoFormats.sortedWith(
            compareByDescending<PlayerResponse.StreamingData.Format> { format ->
                if (targetHeight != null && format.height == targetHeight) 1000
                else if (targetHeight != null && format.height != null) -(kotlin.math.abs(format.height - targetHeight))
                else 0
            }.thenByDescending { it.height ?: 360 }
             .thenByDescending { it.bitrate ?: 0 }
        )
    }

    companion object {
        private const val TAG = "stream-resolver"
        private const val PROBE_TIMEOUT_MS = 3_000L
        private const val AUTH_BOUNDARY_BYTES = 1024L * 1024 // 1 MiB boundary
        private const val PROBE_READ_BYTES = 16L * 1024

        val CLIENT_ROTATION = listOf(
            YouTubeClient.IOS,
            YouTubeClient.IPADOS,
            YouTubeClient.VISIONOS,
            YouTubeClient.WEB_REMIX
        )
    }
}
