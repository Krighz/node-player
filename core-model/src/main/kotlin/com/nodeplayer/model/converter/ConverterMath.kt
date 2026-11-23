package com.nodeplayer.model.converter

import kotlin.math.roundToLong

/**
 * Mathematical utilities for audio transcoding size estimation and loudness gain conversion.
 */
object ConverterMath {

    /**
     * Estimates output file size in bytes for a given duration and target bitrate.
     * Formula: ms * (kbit / s) / 8 = bytes.
     */
    fun estimatedOutputBytes(durationMs: Long, bitrateKbps: Int): Long {
        if (durationMs <= 0L || bitrateKbps <= 0) return 0L
        return (durationMs * bitrateKbps.toLong()) / 8L
    }

    /**
     * Converts ReplayGain dB offset (-18 LUFS standard) to Opus R128 Q7.8 fixed-point offset (-23 LUFS standard).
     * Formula: q78 = round((replayGainDb - 5.0) * 256.0).
     */
    fun replayGainToR128Q78(replayGainDb: Double): Int {
        return Math.round((replayGainDb - 5.0) * 256.0).toInt()
    }

    /**
     * Human-readable byte formatting (e.g. 1.2 MB, 350.5 KB, 2.45 GB).
     */
    fun formatDiskSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "${(kb * 10).roundToLong() / 10.0} KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return "${(mb * 10).roundToLong() / 10.0} MB"
        val gb = mb / 1024.0
        return "${(gb * 100).roundToLong() / 100.0} GB"
    }
}
