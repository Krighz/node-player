package com.nodeplayer.music.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nodeplayer.dsp.DynamicRangeMeter
import com.nodeplayer.dsp.HiResAnalyzer
import com.nodeplayer.dsp.LoudnessR128
import com.nodeplayer.music.NodePlayerApplication
import com.nodeplayer.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AudioAnalysisWorker"
        const val CHANNEL_ID = "audio_analysis_channel"
        const val NOTIFICATION_ID = 5050
        const val WORK_NAME = "node_player_audio_analysis_work"

        fun schedule(context: Context, chargingOnly: Boolean = false) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .apply {
                    if (chargingOnly) setRequiresCharging(true)
                }
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancel(context: Context) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = NodePlayerApplication.instance.database
        val dao = db.playerDao()

        val tracksToAnalyze = dao.getTracksNeedingAnalysis()
        if (tracksToAnalyze.isEmpty()) {
            return@withContext Result.success()
        }

        createNotificationChannel()
        val total = tracksToAnalyze.size

        for ((index, track) in tracksToAnalyze.withIndex()) {
            if (isStopped) {
                return@withContext Result.retry()
            }

            // Update foreground progress notification
            updateNotification(index + 1, total, track.title)

            try {
                val file = File(track.path)
                if (!file.exists() || file.length() < 1000) {
                    continue
                }

                // Single-pass decode: extracts up to 20 seconds of PCM audio
                val decodedPcm = decodeAudioSample(track.path, maxSeconds = 20)
                if (decodedPcm != null && decodedPcm.samples.isNotEmpty()) {
                    val sampleRate = decodedPcm.sampleRate
                    val channels = decodedPcm.channels

                    // 1. Fake Hi-Res Detector
                    val hiResResult = HiResAnalyzer.analyze(decodedPcm.samples, sampleRate)

                    // 2. Library Loudness Scan (EBU R128)
                    val loudnessResult = LoudnessR128.analyze(
                        samples = decodedPcm.samples,
                        sampleRate = sampleRate,
                        channels = channels,
                        targetLevelLufs = -14.0f,
                        clipGuard = true
                    )

                    // 3. Dynamic Range Meter
                    val drResult = DynamicRangeMeter.analyze(
                        samples = decodedPcm.samples,
                        sampleRate = sampleRate,
                        channels = channels
                    )

                    // Cache in Room database
                    dao.updateTrackAudioAnalysis(
                        id = track.id,
                        status = hiResResult.status.name,
                        confidence = hiResResult.confidence,
                        cutoffHz = hiResResult.cutoffFrequencyHz,
                        lufs = loudnessResult.integratedLufs,
                        drScore = drResult.score,
                        drLabel = drResult.label
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed analyzing track ${track.title}: ${e.message}")
            }
        }

        Result.success()
    }

    data class DecodedPcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int
    )

    private fun decodeAudioSample(path: String, maxSeconds: Int): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // Seek to ~15% into track to avoid intro silence
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (durationUs > 10_000_000L) {
                extractor.seekTo((durationUs * 0.15).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxSamples = sampleRate * channels * maxSeconds
            val floatList = ArrayList<Float>(maxSamples.coerceAtMost( sampleRate * channels * 10))
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val timeoutUs = 5000L

            while (!sawOutputEOS && floatList.size < maxSamples) {
                if (!sawInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufIndex >= 0) {
                    val outputBuf = codec.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuf.order(ByteOrder.LITTLE_ENDIAN)

                        // Decode 16-bit PCM into float [-1.0f, 1.0f]
                        while (outputBuf.remaining() >= 2 && floatList.size < maxSamples) {
                            val pcm16 = outputBuf.short
                            floatList.add(pcm16 / 32768.0f)
                        }
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            val samplesArray = FloatArray(floatList.size)
            for (i in floatList.indices) {
                samplesArray[i] = floatList[i]
            }

            return DecodedPcm(
                samples = samplesArray,
                sampleRate = sampleRate,
                channels = channels
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding PCM for $path: ${e.message}")
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Library Audio Analysis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Analyzes dynamic range, integrated loudness, and hi-res genuine status"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun updateNotification(current: Int, total: Int, trackTitle: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Analyzing Audio Quality")
            .setContentText("$current / $total: $trackTitle")
            .setSmallIcon(R.drawable.ic_waveform)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
    }
}
