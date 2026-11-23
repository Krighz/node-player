package com.nodeplayer.model.video

enum class VideoQuality(val key: String, val label: String, val height: Int?) {
    AUTO("auto", "Auto", null),
    P360("360", "360p", 360),
    P480("480", "480p", 480),
    P720("720", "720p", 720),
    P1080("1080", "1080p", 1080);

    companion object {
        fun fromKey(key: String?): VideoQuality {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: AUTO
        }

        fun fromHeight(height: Int?): VideoQuality {
            if (height == null) return AUTO
            return entries.filter { it.height != null }.minByOrNull { kotlin.math.abs(it.height!! - height) } ?: AUTO
        }
    }
}

enum class GestureZone {
    BRIGHTNESS,
    VOLUME
}

enum class SurfacePriority(val level: Int) {
    MINI_PLAYER(0),
    NOW_PLAYING_CARD(1),
    FULLSCREEN_CINEMA(2)
}

data class VideoPlayerState(
    val isVideoActive: Boolean = false,
    val isAvailable: Boolean = false,
    val selectedQuality: VideoQuality = VideoQuality.P480,
    val currentHeight: Int? = null,
    val availableHeights: List<Int> = emptyList(),
    val isBuffering: Boolean = false,
    val isCinemaExpanded: Boolean = false,
    val hasRenderedFirstFrame: Boolean = false,
    val aspect: Float = 16f / 9f,
    val activeSurfacePriority: SurfacePriority? = null
)

object VideoAspectMath {
    /**
     * Calculates width and height preserving aspect ratio according to fit (contain) or crop (cover).
     */
    fun calculateDimensions(
        containerWidth: Int,
        containerHeight: Int,
        videoAspect: Float,
        fit: Boolean
    ): Pair<Int, Int> {
        val safeAspect = if (videoAspect <= 0f) 16f / 9f else videoAspect
        val boundsAspect = containerWidth.toFloat() / containerHeight.coerceAtLeast(1)
        val wider = boundsAspect > safeAspect
        return if (wider == fit) {
            (containerHeight * safeAspect).toInt() to containerHeight
        } else {
            containerWidth to (containerWidth / safeAspect).toInt()
        }
    }

    /**
     * Calculates clock sync drift compensation.
     * Under 25ms: ignore (0 drift nudge)
     * Between 25ms and 450ms: rate nudge [-0.10, +0.10]
     * Over 450ms: requires seek
     */
    fun calculateRateNudge(driftMs: Long): Float {
        val absDrift = kotlin.math.abs(driftMs)
        if (absDrift <= 25L) return 0f
        return (-driftMs / 1000f).coerceIn(-0.10f, 0.10f)
    }
}

object VideoMath {

    /**
     * Pure function resolving best video height from available candidates.
     * If preferred is null (Auto) or available is empty, returns null to allow adaptive selection.
     */
    fun chooseHeight(available: List<Int>, preferred: Int?): Int? {
        if (preferred == null || available.isEmpty()) return null
        val sorted = available.distinct().sorted()
        return sorted.lastOrNull { it <= preferred } ?: sorted.first()
    }

    /**
     * Maps x-coordinate to screen gesture zone (left half = Brightness, right half = Volume).
     */
    fun resolveGestureZone(x: Float, width: Float): GestureZone {
        return if (x < width / 2f) GestureZone.BRIGHTNESS else GestureZone.VOLUME
    }

    /**
     * Calculates double-tap seek direction and delta based on tap location.
     */
    fun resolveDoubleTapSeekDeltaMs(x: Float, width: Float, seekStepSec: Int): Long {
        val stepMs = seekStepSec.toLong() * 1000L
        return if (x < width / 2f) -stepMs else stepMs
    }
}

object MediaArtConstants {
    val SIZE_SEGMENT = Regex("=w\\d+-h\\d+")
    const val MIN_BUCKET_PX = 256
    const val MAX_BUCKET_PX = 1080
    const val BUCKET_STEP_PX = 128

    fun upscaleCoverUrl(url: String, targetPx: Int): String {
        if (url.isBlank()) return url
        val bucketed = ((targetPx.coerceIn(MIN_BUCKET_PX, MAX_BUCKET_PX) + BUCKET_STEP_PX - 1) / BUCKET_STEP_PX) * BUCKET_STEP_PX
        return if (SIZE_SEGMENT.containsMatchIn(url)) {
            url.replace(SIZE_SEGMENT, "=w$bucketed-h$bucketed")
        } else {
            url
        }
    }
}

sealed interface ResolvedPlaybackSource {
    data class Muxed(val streamUrl: String, val height: Int? = null, val availableHeights: List<Int> = emptyList()) : ResolvedPlaybackSource
    data class Separate(val videoUrl: String, val audioUrl: String, val height: Int? = null, val availableHeights: List<Int> = emptyList()) : ResolvedPlaybackSource
    data class AudioOnly(val audioUrl: String) : ResolvedPlaybackSource
    data object Empty : ResolvedPlaybackSource
}
