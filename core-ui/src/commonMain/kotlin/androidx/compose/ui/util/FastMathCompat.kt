package androidx.compose.ui.util

fun Float.fastCoerceAtLeast(minimumValue: Float): Float = if (this < minimumValue) minimumValue else this
fun Float.fastCoerceAtMost(maximumValue: Float): Float = if (this > maximumValue) maximumValue else this
