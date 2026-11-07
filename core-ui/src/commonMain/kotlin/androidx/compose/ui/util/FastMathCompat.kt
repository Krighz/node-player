package androidx.compose.ui.util

import kotlin.math.roundToInt

fun Float.fastCoerceAtLeast(minimumValue: Float): Float = if (this < minimumValue) minimumValue else this
fun Float.fastCoerceAtMost(maximumValue: Float): Float = if (this > maximumValue) maximumValue else this

fun Float.fastCoerceIn(minimumValue: Float, maximumValue: Float): Float =
    this.coerceIn(minimumValue, maximumValue)

fun Int.fastCoerceIn(minimumValue: Int, maximumValue: Int): Int =
    this.coerceIn(minimumValue, maximumValue)

fun Float.fastRoundToInt(): Int = this.roundToInt()
