package com.nodeplayer.music.converter

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.model.converter.AudioTranscoder
import com.nodeplayer.model.converter.TranscodeJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android FFmpeg transcoding engine using LGPL ffmpeg-kit-audio.
 */
class AndroidFFmpegTranscoder : AudioTranscoder {

    companion object {
        private const val TAG = "converter/android"

        fun buildCommand(job: TranscodeJob): String {
            val parts = mutableListOf(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-i", "\"${job.input.absolutePath}\"",
                "-map", "0:a:0"
            )

            if (job.keepCover) {
                parts.addAll(listOf("-map", "0:v:0?"))
            }

            if (job.keepTags) {
                parts.addAll(listOf("-map_metadata", "0"))
            }

            parts.addAll(
                listOf(
                    "-c:a", "libopus",
                    "-b:a", "${job.bitrateKbps}k",
                    "-vbr", if (job.vbr) "on" else "off",
                    "-compression_level", job.compressionLevel.coerceIn(0, 10).toString(),
                    "-application", job.application
                )
            )

            if (job.keepCover) {
                parts.addAll(listOf("-c:v", "copy", "-disposition:v:0", "attached_pic"))
            }

            parts.add("\"${job.output.absolutePath}\"")
            return parts.joinToString(" ")
        }
    }

    override suspend fun transcode(job: TranscodeJob, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            job.output.parentFile?.mkdirs()
            val command = buildCommand(job)

            suspendCancellableCoroutine { continuation ->
                val session = FFmpegKit.executeAsync(
                    command,
                    { completedSession ->
                        val returnCode = completedSession.returnCode
                        if (ReturnCode.isSuccess(returnCode)) {
                            onProgress(1.0f)
                            continuation.resume(Result.success(job.output))
                        } else {
                            job.output.delete()
                            val msg = "FFmpeg failed with return code: $returnCode"
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
