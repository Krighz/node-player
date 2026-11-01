package com.nodeplayer.music.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nodeplayer.dsp.DynamicRangeMeter
import com.nodeplayer.dsp.HiResAnalyzer
import com.nodeplayer.dsp.LoudnessR128
import com.nodeplayer.model.Track
import com.nodeplayer.music.R
import com.nodeplayer.music.data.PlayerDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

object AudioAnalysisManager {

    private const val TAG = "AudioAnalysisManager"
    const val CHANNEL_ID = "audio_analysis_channel"
    const val NOTIFICATION_ID = 5050
    const val WORK_NAME = "node_player_audio_analysis_work"

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress = _progress.asStateFlow()

    private val _currentSongTitle = MutableStateFlow<String?>(null)
    val currentSongTitle = _currentSongTitle.asStateFlow()

    val isUserScrolling = AtomicBoolean(false)

    fun setUserScrolling(scrolling: Boolean) {
        isUserScrolling.set(scrolling)
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            _progress.value = null
            _currentSongTitle.value = null
            _isPaused.value = false
        }
    }

    fun setProgress(current: Int, total: Int, title: String) {
        _progress.value = current to total
        _currentSongTitle.value = title
    }

    fun pause(context: Context) {
        _isPaused.value = true
        _progress.value?.let { (cur, tot) ->
            updateNotification(context, cur, tot, _currentSongTitle.value ?: "Audio Quality Analysis", paused = true)
        }
    }

    fun resume(context: Context) {
        _isPaused.value = false
        _progress.value?.let { (cur, tot) ->
            updateNotification(context, cur, tot, _currentSongTitle.value ?: "Audio Quality Analysis", paused = false)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        cancelNotification(context)
        setRunning(false)
    }

    fun schedule(context: Context, chargingOnly: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .apply {
                if (chargingOnly) setRequiresCharging(true)
            }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Quality Analysis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for Dynamic Range, Loudness and Hi-Res spectral scans"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(context: Context, current: Int, total: Int, trackTitle: String, paused: Boolean = false) {
        createNotificationChannel(context)

        val pauseIntent = Intent(context, AudioAnalysisReceiver::class.java).apply {
            action = if (paused) AudioAnalysisReceiver.ACTION_RESUME else AudioAnalysisReceiver.ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, AudioAnalysisReceiver::class.java).apply {
            action = AudioAnalysisReceiver.ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (paused) "Audio Quality Analysis (Paused)" else "Analyzing Audio Quality ($current / $total)"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(trackTitle)
            .setSmallIcon(R.drawable.ic_waveform)
            .setProgress(total, current, false)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                if (paused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                cancelPendingIntent
            )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int
    )

    /**
     * Decodes up to 15 seconds into a flat primitive FloatArray buffer.
     * Prevents object-boxing overhead and guarantees strictly flat memory consumption.
     */
    fun decodeAudioSample(path: String, maxSeconds: Int = 15): DecodedAudio? {
        val file = File(path)
        if (!file.exists() || file.length() < 1000L) {
            return null
        }

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

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            // Seek past first 15% to skip silent intro
            if (durationUs > 8_000_000L) {
                extractor.seekTo((durationUs * 0.15).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxSamples = (sampleRate * channels * maxSeconds).coerceIn(44100 * 2 * 3, 192000 * 2 * 15)
            val pcmBuffer = FloatArray(maxSamples)
            var samplesWritten = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val timeoutUs = 2500L
            var idleCount = 0

            while (!sawOutputEOS && samplesWritten < maxSamples && idleCount < 120) {
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

                        while (outputBuf.remaining() >= 2 && samplesWritten < maxSamples) {
                            val pcm16 = outputBuf.short
                            pcmBuffer[samplesWritten++] = pcm16 / 32768.0f
                        }
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                    idleCount = 0
                } else {
                    idleCount++
                }
            }

            if (samplesWritten == 0) return null

            val resultSamples = if (samplesWritten == maxSamples) pcmBuffer else pcmBuffer.copyOf(samplesWritten)
            return DecodedAudio(
                samples = resultSamples,
                sampleRate = sampleRate,
                channels = channels
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error decoding PCM for $path: ${t.message}")
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Runs Hi-Res spectral analysis, EBU R128 Loudness and Dynamic Range Meter.
     * Updates database on success, or records failure.
     */
    suspend fun analyzeSingleTrackDirect(track: Track, dao: PlayerDao): Boolean {
        try {
            val decoded = decodeAudioSample(track.path, maxSeconds = 15)
            if (decoded != null && decoded.samples.isNotEmpty()) {
                val sampleRate = decoded.sampleRate
                val channels = decoded.channels

                // 1. Hi-Res Spectral Purity (mono downmix for optimal FFT accuracy)
                val monoSamples = if (channels == 2) {
                    val half = decoded.samples.size / 2
                    val mono = FloatArray(half)
                    for (i in 0 until half) {
                        mono[i] = (decoded.samples[i * 2] + decoded.samples[i * 2 + 1]) * 0.5f
                    }
                    mono
                } else {
                    decoded.samples
                }
                val hiResResult = HiResAnalyzer.analyze(monoSamples, sampleRate)

                // 2. EBU R128 Integrated Loudness
                val loudnessResult = LoudnessR128.analyze(
                    samples = decoded.samples,
                    sampleRate = sampleRate,
                    channels = channels,
                    targetLevelLufs = -14.0f,
                    clipGuard = true
                )

                // 3. TT Dynamic Range Meter (3s blocks)
                val drResult = DynamicRangeMeter.analyze(
                    samples = decoded.samples,
                    sampleRate = sampleRate,
                    channels = channels
                )

                dao.updateTrackAudioAnalysis(
                    id = track.id,
                    status = hiResResult.status.name,
                    confidence = hiResResult.confidence,
                    cutoffHz = hiResResult.cutoffFrequencyHz,
                    lufs = loudnessResult.integratedLufs,
                    drScore = drResult.score,
                    drLabel = drResult.label
                )
                return true
            } else {
                dao.incrementAnalysisAttempts(track.id)
                if (track.analysisAttempts + 1 >= 3) {
                    dao.markTrackAnalysisFailed(track.id)
                }
                return false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal analysis failure on track ${track.title}: ${t.message}", t)
            dao.incrementAnalysisAttempts(track.id)
            if (track.analysisAttempts + 1 >= 3) {
                dao.markTrackAnalysisFailed(track.id)
            }
            return false
        }
    }
}
