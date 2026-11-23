package com.nodeplayer.desktop.converter

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.library.converter.FlacMetadataExtractor
import com.nodeplayer.model.converter.AudioTranscoder
import com.nodeplayer.model.converter.TranscodeJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Windows Desktop FFmpeg process-based audio transcoder.
 * Transcodes FLAC to Opus with live progress reporting, verified integrity checks,
 * and RFC 7845 Vorbis comment embedding for tags, cover art, and lyrics.
 */
class DesktopFFmpegTranscoder(
    private val ffmpegPathProvider: () -> String = { "" }
) : AudioTranscoder {

    companion object {
        private const val TAG = "converter/ffmpeg"

        /**
         * Resolves the effective FFmpeg binary path without downloading.
         */
        fun resolveFFmpegBinary(configuredPath: String = ""): String {
            if (configuredPath.isNotBlank()) {
                val customFile = File(configuredPath.trim())
                if (customFile.exists() && customFile.canExecute()) {
                    return customFile.absolutePath
                }
            }

            // Check adjacent directory to current executable
            val localWin = File("ffmpeg.exe")
            if (localWin.exists() && localWin.canExecute()) {
                return localWin.absolutePath
            }
            val localUnix = File("ffmpeg")
            if (localUnix.exists() && localUnix.canExecute()) {
                return localUnix.absolutePath
            }

            // Fallback to system PATH
            return "ffmpeg"
        }

        /**
         * Builds the argument vector for FFmpeg transcoding.
         */
        fun buildArguments(
            job: TranscodeJob,
            binary: String = "ffmpeg",
            base64Picture: String? = null,
            lyrics: String? = null
        ): List<String> {
            val args = mutableListOf(
                binary,
                "-hide_banner",
                "-nostdin",
                "-y",
                "-i", job.input.absolutePath,
                "-map", "0:a:0"
            )

            if (job.keepTags) {
                args.addAll(listOf("-map_metadata", "0"))
            }

            // In Opus / Ogg containers, cover art must not be muxed as a video stream (-c:v copy).
            // Embed via RFC 7845 Vorbis comment METADATA_BLOCK_PICTURE instead.
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
                    "-progress", "pipe:1",
                    "-nostats",
                    job.output.absolutePath
                )
            )

            return args
        }
    }

    override suspend fun transcode(job: TranscodeJob, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val binary = resolveFFmpegBinary(ffmpegPathProvider())
            val flacPic = if (job.keepCover) FlacMetadataExtractor.extractPicture(job.input) else null
            val companionLrc = FlacMetadataExtractor.findCompanionLrc(job.input)
            val lyrics = companionLrc?.readText(Charsets.UTF_8) ?: FlacMetadataExtractor.extractLyrics(job.input)

            val args = buildArguments(job, binary, flacPic?.base64Payload, lyrics)

            job.output.parentFile?.mkdirs()

            var process: Process? = null
            try {
                val pb = ProcessBuilder(args)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String? = reader.readLine()

                // Progress loop parsing -progress pipe:1 key=value lines
                while (line != null && coroutineContext.isActive) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("out_time_ms=")) {
                        val usVal = trimmed.substringAfter("out_time_ms=").toLongOrNull() ?: 0L
                        val msVal = usVal / 1000L
                        if (msVal > 0L) {
                            onProgress((msVal % 100_000L) / 100_000f)
                        }
                    } else if (trimmed.startsWith("progress=end")) {
                        onProgress(1.0f)
                    }
                    line = reader.readLine()
                }

                val exitCode = proc.waitFor()
                if (exitCode != 0) {
                    job.output.delete()
                    return@withContext Result.failure(
                        IllegalStateException("FFmpeg exited with error code $exitCode")
                    )
                }

                onProgress(1.0f)
                Result.success(job.output)
            } catch (e: Throwable) {
                try {
                    process?.destroyForcibly()
                } catch (_: Throwable) {}
                job.output.delete()
                Result.failure(e)
            }
        }

    override suspend fun verifyIntegrity(file: File, expectedDurationMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext false
            }

            val binary = resolveFFmpegBinary(ffmpegPathProvider())
            // Use ffprobe or ffmpeg -v error to quickly verify file packet streams
            val probeCmd = listOf(
                binary,
                "-v", "error",
                "-i", file.absolutePath,
                "-f", "null",
                "-"
            )

            return@withContext try {
                val pb = ProcessBuilder(probeCmd)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val exitCode = proc.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                NodeLog.w(TAG, "Integrity check failed: ${e.message}")
                false
            }
        }
}
