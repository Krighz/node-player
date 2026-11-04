package com.nodeplayer.ui.components

import androidx.compose.ui.Modifier

actual fun Modifier.onScrubberWheel(onDelta: (Float) -> Unit): Modifier = this

actual fun Modifier.onHorizontalScrollWheel(onDelta: (Float) -> Unit): Modifier = this
