package com.nodeplayer.model.converter

import java.io.File

/**
 * Predefined audio encoding presets for FLAC to Opus transcoding.
 */
data class TranscodePreset(
    val id: String,
    val name: String,
    val bitrateKbps: Int,
    val description: String
) {
    companion object {
        val PRESET_192 = TranscodePreset(
            id = "192",
            name = "Audiophile Transparent",
            bitrateKbps = 192,
            description = "Perceptually indistinguishable from lossless master"
        )
        val PRESET_128 = TranscodePreset(
            id = "128",
            name = "Balanced",
            bitrateKbps = 128,
            description = "High fidelity transparent audio with ~70% space savings"
        )
        val PRESET_96 = TranscodePreset(
            id = "96",
            name = "Storage Saver",
            bitrateKbps = 96,
            description = "Solid acoustic quality with maximum disk space reduction"
        )
        val PRESET_CUSTOM = TranscodePreset(
            id = "CUSTOM",
            name = "Custom",
            bitrateKbps = 160,
            description = "User-customized bitrate and Opus encoding parameters"
        )

        val ALL_PRESETS = listOf(PRESET_192, PRESET_128, PRESET_96, PRESET_CUSTOM)

        fun fromId(id: String): TranscodePreset =
            ALL_PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PRESET_128
    }
}

/**
 * Task specification for transcoding a single audio file.
 */
data class TranscodeJob(
    val input: File,
    val output: File,
    val bitrateKbps: Int,
    val vbr: Boolean = true,
    val application: String = "audio",
    val compressionLevel: Int = 10,
    val frameDurationMs: Int = 20,
    val writeR128: Boolean = false,
    val keepTags: Boolean = true,
    val keepCover: Boolean = true
)

/**
 * Aggregated summary report of a completed batch transcoding operation.
 */
data class TranscodeBatchSummary(
    val totalFiles: Int,
    val succeeded: Int,
    val failed: Int,
    val totalInputBytes: Long,
    val totalOutputBytes: Long,
    val failedFiles: List<Pair<File, String>> = emptyList()
) {
    val bytesSaved: Long
        get() = (totalInputBytes - totalOutputBytes).coerceAtLeast(0L)

    val percentSavings: Float
        get() = if (totalInputBytes > 0L) (bytesSaved.toFloat() / totalInputBytes.toFloat()) * 100f else 0f
}

/**
 * Multiplatform contract for audio encoding backends.
 */
interface AudioTranscoder {
    /**
     * Transcodes [job] to the output file with real-time [onProgress] reporting (0.0f..1.0f).
     */
    suspend fun transcode(job: TranscodeJob, onProgress: (Float) -> Unit): Result<File>

    /**
     * Verifies that the transcoded audio file is uncorrupted, playable, and within duration tolerance.
     */
    suspend fun verifyIntegrity(file: File, expectedDurationMs: Long = 0L): Boolean
}
