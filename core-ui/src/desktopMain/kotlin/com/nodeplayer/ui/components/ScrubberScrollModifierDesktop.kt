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

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onHorizontalScrollWheel(onDelta: (Float) -> Unit): Modifier = this.onPointerEvent(
    PointerEventType.Scroll
) { event ->
    val change = event.changes.firstOrNull()
    val delta = if ((change?.scrollDelta?.x ?: 0f) != 0f) change?.scrollDelta?.x ?: 0f else change?.scrollDelta?.y ?: 0f
    if (delta != 0f) {
        onDelta(delta)
    }
}
