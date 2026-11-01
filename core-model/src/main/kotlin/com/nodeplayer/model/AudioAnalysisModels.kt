package com.nodeplayer.model

enum class HiResStatus {
    GENUINE,
    LIKELY_LOSSY,
    LIKELY_UPSAMPLED,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            GENUINE -> "Genuine"
            LIKELY_LOSSY -> "Likely lossy source"
            LIKELY_UPSAMPLED -> "Likely upsampled"
            UNKNOWN -> "Unknown"
        }
}

data class HiResAnalysisResult(
    val status: HiResStatus,
    val confidence: Float, // 0.0 to 1.0
    val cutoffFrequencyHz: Float,
    val maxFrequencyHz: Float
) {
    val description: String
        get() = "${status.displayName} (~${(confidence * 100).toInt()}% confidence, cutoff ~${(cutoffFrequencyHz / 1000).toInt()} kHz)"
}

data class LoudnessAnalysisResult(
    val integratedLufs: Float,
    val peakDbfs: Float,
    val recommendedGainDb: Float
)

data class DynamicRangeResult(
    val score: Int, // e.g. 12 for DR12
    val label: String, // e.g. "Heavily compressed", "Moderate", "Dynamic"
    val peakDbfs: Float,
    val rmsTop20Dbfs: Float
) {
    val formattedScore: String
        get() = "DR$score"
}
