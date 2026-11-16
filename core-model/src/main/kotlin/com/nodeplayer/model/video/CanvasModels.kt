package com.nodeplayer.model.video

enum class CanvasType {
    SPOTIFY,
    APPLE_MOTION,
    TIDAL,
    CUSTOM
}

data class CanvasItem(
    val id: String,
    val trackId: String,
    val videoUrl: String,
    val canvasType: CanvasType = CanvasType.SPOTIFY,
    val width: Int = 720,
    val height: Int = 1280,
    val durationMs: Long = 8000L
)
