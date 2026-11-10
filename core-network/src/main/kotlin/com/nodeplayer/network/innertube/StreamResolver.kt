package com.nodeplayer.network.innertube

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.media.CipherSolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
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
        NodeLog.d(TAG, "Resolving stream for videoId=$videoId across clients")
        val errors = mutableListOf<String>()

        for (client in CLIENT_ROTATION) {
            NodeLog.d(TAG, "Attempting stream resolution for videoId=$videoId with client=${client.clientName}")
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
            val audioCandidates = rankAudioFormats(allFormats)

            if (audioCandidates.isEmpty()) {
                val errorMsg = "Client ${client.clientName} has no valid audio formats"
                NodeLog.w(TAG, errorMsg)
                errors.add(errorMsg)
                continue
            }

            var chosenCandidate: Pair<PlayerResponse.StreamingData.Format, String>? = null

            var probeAttempted = false
            for (candidate in audioCandidates) {
                var url = candidate.url

                // Decipher if url is absent but cipher / signatureCipher is present
                val cipherString = candidate.signatureCipher ?: candidate.cipher
                if (url.isNullOrBlank() && !cipherString.isNullOrBlank()) {
                    val solver = cipherSolver ?: CipherSolver.current
                    if (solver != null) {
                        try {
                            url = solver.deobfuscateStreamUrl(cipherString, videoId)
                        } catch (e: Exception) {
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
                            NodeLog.w(TAG, "n-parameter transform failed: ${e.message}")
                        }
                    }
                }

                // Active HTTP Range Probe validation
                probeAttempted = true
                if (verifyStreamUrl(url, client.userAgent)) {
                    chosenCandidate = candidate to url
                    break
                } else {
                    NodeLog.w(TAG, "Range probe failed for client ${client.clientName}, itag ${candidate.itag}")
                }
            }

            // Fallback: Only if candidate URLs were present but no probe was attempted (e.g. all empty URLs)
            // or if direct fallback is needed without probe failure
            if (chosenCandidate == null && audioCandidates.isNotEmpty() && !probeAttempted) {
                val directFallback = audioCandidates.firstOrNull { !it.url.isNullOrBlank() }
                if (directFallback != null) {
                    NodeLog.i(TAG, "Using direct audio format itag=${directFallback.itag} as fallback for ${client.clientName}")
                    chosenCandidate = directFallback to directFallback.url!!
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
                    clientName = client.clientName
                )
                NodeLog.i(TAG, "Successfully resolved stream for $videoId via ${client.clientName} (itag=${format.itag})")
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
     * Sends GET request with Range: bytes=0-1024 and 3-second timeout.
     * Accepts HTTP 206 (Partial Content) or 200 (OK).
     */
    internal suspend fun verifyStreamUrl(url: String, userAgent: String): Boolean {
        return try {
            val response = probeClient.get(url) {
                header("Range", "bytes=0-1024")
                header("User-Agent", userAgent)
            }
            val status = response.status
            NodeLog.d(TAG, "verifyStreamUrl: url=$url, status=$status")
            status == HttpStatusCode.PartialContent || status == HttpStatusCode.OK
        } catch (e: Exception) {
            NodeLog.w(TAG, "Stream probe network error for $url: ${e.message}")
            false
        }
    }

    /**
     * Prioritizes formats:
     * 1. itag 251 (Opus 160k, 48kHz)
     * 2. itag 140 (AAC 128k, 44.1kHz)
     * 3. Other audio formats sorted by bitrate descending
     */
    internal fun rankAudioFormats(formats: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
        val audioFormats = formats.filter { it.isAudio || it.mimeType.contains("audio", ignoreCase = true) }
        val itag251 = audioFormats.filter { it.itag == 251 }
        val itag140 = audioFormats.filter { it.itag == 140 }
        val others = audioFormats.filter { it.itag != 251 && it.itag != 140 }
            .sortedByDescending { it.bitrate ?: 0 }

        return itag251 + itag140 + others
    }

    companion object {
        private const val TAG = "stream-resolver"
        private const val PROBE_TIMEOUT_MS = 5_000L

        val CLIENT_ROTATION = listOf(
            YouTubeClient.VISIONOS,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.IOS,
            YouTubeClient.WEB_REMIX,
        )
    }
}
