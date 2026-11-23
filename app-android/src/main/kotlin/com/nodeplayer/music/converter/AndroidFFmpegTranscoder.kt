package com.nodeplayer.music.converter

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.converter.FlacMetadataExtractor
import com.nodeplayer.model.converter.AudioTranscoder
import com.nodeplayer.model.converter.TranscodeJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android FFmpeg transcoding engine using LGPL ffmpeg-kit-audio.
 * Strictly muxes pure libopus audio into .opus containers with RFC 7845 Vorbis comments
 * for metadata, cover art (METADATA_BLOCK_PICTURE), and lyrics.
 */
class AndroidFFmpegTranscoder : AudioTranscoder {

    companion object {
        private const val TAG = "converter/android"

        fun buildArguments(
            job: TranscodeJob,
            base64Picture: String? = null,
            lyrics: String? = null
        ): List<String> {
            val args = mutableListOf(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-i", job.input.absolutePath,
                "-map", "0:a:0"
            )

            if (job.keepTags) {
                args.addAll(listOf("-map_metadata", "0"))
            }

            // In Opus / Ogg containers, cover art must NOT be muxed as a video stream (-c:v copy),
            // which crashes the Opus muxer. Instead, embed via RFC 7845 Vorbis comment METADATA_BLOCK_PICTURE.
            if (job.keepCover && !base64Picture.isNullOrBlank()) {
                args.addAll(listOf("-metadata", "METADATA_BLOCK_PICTURE=$base64Picture"))
            }

            if (!lyrics.isNullOrBlank()) {
                args.addAll(listOf("-metadata", "LYRICS=$lyrics"))
            }

            args.addAll(
                listOf(
                    "-c:a", "libopus",
                    "-b:a", "${job.bitrateKbps}k",
                    "-vbr", if (job.vbr) "on" else "off",
                    "-compression_level", job.compressionLevel.coerceIn(0, 10).toString(),
                    "-application", job.application,
                    job.output.absolutePath
                )
            )

            return args
        }

        fun buildCommand(job: TranscodeJob): String {
            return buildArguments(job).joinToString(" ")
        }
    }

    override suspend fun transcode(job: TranscodeJob, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            job.output.parentFile?.mkdirs()

            // Pre-extract embedded artwork and lyrics for RFC-compliant Vorbis comments
            val flacPic = if (job.keepCover) FlacMetadataExtractor.extractPicture(job.input) else null
            val companionLrc = FlacMetadataExtractor.findCompanionLrc(job.input)
            val lyrics = companionLrc?.readText(Charsets.UTF_8) ?: FlacMetadataExtractor.extractLyrics(job.input)

            val args = buildArguments(job, flacPic?.base64Payload, lyrics).toTypedArray()

            suspendCancellableCoroutine { continuation ->
                val session = FFmpegKit.executeWithArgumentsAsync(
                    args,
                    { completedSession ->
                        val returnCode = completedSession.returnCode
                        if (ReturnCode.isSuccess(returnCode)) {
                            onProgress(1.0f)
                            continuation.resume(Result.success(job.output))
                        } else {
                            job.output.delete()
                            val allLogs = completedSession.allLogsAsString ?: "No FFmpeg logs"
                            val msg = "FFmpeg failed with return code $returnCode: $allLogs"
                            NodeLog.w(TAG, msg)
                            continuation.resume(Result.failure(IllegalStateException(msg)))
                        }
                    },
                    { _ -> /* log message callback */ },
                    { statistics ->
                        val timeMs = statistics.time.toLong()
                        if (timeMs > 0L) {
                            onProgress((timeMs % 100_000L) / 100_000f)
                        }
                    }
                )

                continuation.invokeOnCancellation {
                    try {
                        FFmpegKit.cancel(session.sessionId)
                    } catch (_: Throwable) {}
                    job.output.delete()
                }
            }
        }

    override suspend fun verifyIntegrity(file: File, expectedDurationMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) return@withContext false

            return@withContext try {
                val session = FFprobeKit.getMediaInformation(file.absolutePath)
                val info = session.mediaInformation
                ReturnCode.isSuccess(session.returnCode) && info != null && info.streams.isNotEmpty()
            } catch (e: Exception) {
                NodeLog.w(TAG, "Integrity check failed: ${e.message}")
                false
            }
        }
}
