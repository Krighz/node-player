package com.nodeplayer.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onScrubberWheel(onDelta: (Float) -> Unit): Modifier = this.onPointerEvent(
    PointerEventType.Scroll
) { event ->
    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
    if (deltaY != 0f) {
        onDelta(deltaY)
    }
}
