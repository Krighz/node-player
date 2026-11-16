package com.nodeplayer.network.downloader

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.storage.AudioStreamOption
import com.nodeplayer.model.storage.DownloadContainerFormat
import com.nodeplayer.model.storage.DownloadQualityPreset
import com.nodeplayer.common.media.CipherSolver
import com.nodeplayer.network.innertube.InnerTubeClient
import com.nodeplayer.network.innertube.PlayerResponse
import com.nodeplayer.network.innertube.StreamResolver
import com.nodeplayer.network.innertube.YouTubeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Dynamically queries and parses all available audio-only streams for a YouTube video.
 * Zero hardcoded stream URLs, itags, or fixed bitrate values.
 */
class StreamFormatResolver(
    private val innerTubeClient: InnerTubeClient = InnerTubeClient(),
    private val cipherSolver: CipherSolver? = CipherSolver.current
) {

    private companion object {
        const val TAG = "stream/format-resolver"
    }

    /**
     * Resolves all available audio stream options for [videoId] with dynamic bitrate and container metadata.
     */
    suspend fun resolveAvailableAudioOptions(videoId: String): Result<List<AudioStreamOption>> {
        NodeLog.d(TAG, "Resolving available audio options for videoId")
        val errors = mutableListOf<String>()

        val clients = if (!innerTubeClient.sessionCookie.isNullOrBlank()) {
            listOf(YouTubeClient.WEB_REMIX) + StreamResolver.CLIENT_ROTATION.filterNot { it == YouTubeClient.WEB_REMIX }
        } else {
            StreamResolver.CLIENT_ROTATION
        }

        for (client in clients) {
            currentCoroutineContext().ensureActive()
            val responseResult = innerTubeClient.getPlayerResponse(videoId, client)
            if (responseResult.isFailure) {
                errors.add("Client ${client.clientName} failed: ${responseResult.exceptionOrNull()?.message}")
                continue
            }

            val response = responseResult.getOrThrow()
            val playability = response.playabilityStatus
            if (playability != null && playability.status != "OK") {
                errors.add("Client ${client.clientName} playability not OK: ${playability.reason ?: playability.status}")
                continue
            }

            val streamingData = response.streamingData ?: continue
            val formats = (streamingData.adaptiveFormats + (streamingData.formats ?: emptyList()))
                .filter { it.isAudio || it.mimeType.contains("audio", ignoreCase = true) }

            if (formats.isEmpty()) continue

            val resolvedOptions = ArrayList<AudioStreamOption>()
            for (format in formats) {
                val directUrl = resolveFormatUrl(format, videoId) ?: continue
                val container = parseContainer(format.mimeType)
                val codec = parseCodec(format.mimeType)
                val bitrateKbps = ((format.bitrate ?: format.averageBitrate ?: 128000) / 1000).coerceAtLeast(32)
                val contentLength = format.contentLength ?: 0L

                resolvedOptions.add(
                    AudioStreamOption(
                        itag = format.itag,
                        codec = codec,
                        container = container,
                        bitrateKbps = bitrateKbps,
                        contentLengthBytes = contentLength,
                        url = directUrl
                    )
                )
            }

            if (resolvedOptions.isNotEmpty()) {
                val distinctOptions = resolvedOptions
                    .distinctBy { "${it.container}_${it.bitrateKbps}" }
                    .sortedByDescending { it.bitrateKbps }
                NodeLog.d(TAG, "Resolved ${distinctOptions.size} distinct audio options via ${client.clientName}")
                return Result.success(distinctOptions)
            }
        }

        return Result.failure(IllegalStateException("No playable audio stream formats could be resolved. Errors: $errors"))
    }

    /**
     * Selects the closest matching option based on [preset] and [preferredFormat].
     */
    fun selectBestOption(
        options: List<AudioStreamOption>,
        preset: DownloadQualityPreset = DownloadQualityPreset.HIGHEST,
        preferredFormat: DownloadContainerFormat = DownloadContainerFormat.AUTO
    ): AudioStreamOption? {
        if (options.isEmpty()) return null

        val formatFiltered = when (preferredFormat) {
            DownloadContainerFormat.OPUS -> options.filter { it.container.equals("opus", ignoreCase = true) }
            DownloadContainerFormat.M4A -> options.filter { it.container.equals("m4a", ignoreCase = true) }
            DownloadContainerFormat.AUTO -> options
        }.ifEmpty { options }

        return when (preset) {
            DownloadQualityPreset.HIGHEST -> formatFiltered.maxByOrNull { it.bitrateKbps }
            DownloadQualityPreset.DATA_SAVER -> formatFiltered.minByOrNull { it.bitrateKbps }
            DownloadQualityPreset.BALANCED -> {
                // Aim for balanced ~128-160 kbps
                formatFiltered.minByOrNull { kotlin.math.abs(it.bitrateKbps - 140) }
            }
            DownloadQualityPreset.ASK -> formatFiltered.maxByOrNull { it.bitrateKbps }
        }
    }

    private suspend fun resolveFormatUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        var url = format.url
        val cipherString = format.signatureCipher ?: format.cipher

        if (url.isNullOrBlank() && !cipherString.isNullOrBlank()) {
            val solver = cipherSolver ?: CipherSolver.current
            if (solver != null) {
                try {
                    url = solver.deobfuscateStreamUrl(cipherString, videoId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    NodeLog.w(TAG, "Cipher deobfuscation failed for itag ${format.itag}: ${e.message}")
                }
            }
        }

        if (url.isNullOrBlank()) return null

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
                    NodeLog.w(TAG, "n-transform failed for itag ${format.itag}: ${e.message}")
                }
            }
        }

        return url
    }

    private fun parseContainer(mimeType: String): String {
        return when {
            mimeType.contains("webm", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "opus"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "m4a"
            else -> "opus"
        }
    }

    private fun parseCodec(mimeType: String): String {
        val codecMatch = Regex("""codecs="?([^",;]+)""").find(mimeType)
        return codecMatch?.groupValues?.get(1) ?: if (mimeType.contains("webm")) "opus" else "aac"
    }
}
