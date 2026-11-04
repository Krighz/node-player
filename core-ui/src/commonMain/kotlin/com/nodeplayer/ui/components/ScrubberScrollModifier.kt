package com.nodeplayer.ui.components

import androidx.compose.ui.Modifier

expect fun Modifier.onScrubberWheel(onDelta: (Float) -> Unit): Modifier

expect fun Modifier.onHorizontalScrollWheel(onDelta: (Float) -> Unit): Modifier
