package androidx.compose.ui.unit

import androidx.compose.ui.geometry.Size

fun Size.toIntSize(): IntSize = IntSize(width.toInt(), height.toInt())
