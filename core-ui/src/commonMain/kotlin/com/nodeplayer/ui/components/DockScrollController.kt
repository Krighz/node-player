package com.nodeplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.nodeplayer.model.GlassThemeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller managing dock auto-hide and slide-down animations during list scrolling.
 */
class DockScrollController(
    private val scope: CoroutineScope,
    var sensitivity: Float = 1.0f,
    var returnDelayMs: Long = 1200L
) {
    private val _offsetFraction = MutableStateFlow(0f)
    val offsetFraction: StateFlow<Float> = _offsetFraction.asStateFlow()

    private var returnTimerJob: Job? = null

    fun onScroll(deltaY: Float) {
        val scaledDelta = deltaY * sensitivity

        if (scaledDelta < -6f) {
            // Scrolling down -> hide dock (offset towards 1f)
            _offsetFraction.value = (_offsetFraction.value + (-scaledDelta) / 250f).coerceIn(0f, 1f)
            scheduleReturn()
        } else if (scaledDelta > 6f) {
            // Scrolling up -> restore dock (offset towards 0f)
            _offsetFraction.value = (_offsetFraction.value - scaledDelta / 180f).coerceIn(0f, 1f)
            scheduleReturn()
        }
    }

    fun restoreDock() {
        returnTimerJob?.cancel()
        _offsetFraction.value = 0f
    }

    fun hideDock() {
        returnTimerJob?.cancel()
        _offsetFraction.value = 1f
    }

    private fun scheduleReturn() {
        returnTimerJob?.cancel()
        returnTimerJob = scope.launch {
            delay(returnDelayMs)
            _offsetFraction.value = 0f
        }
    }
}

/**
 * Remembers a NestedScrollConnection linked to the dock scroll controller.
 */
@Composable
fun rememberDockScrollConnection(
    controller: DockScrollController,
    enabled: Boolean = true
): NestedScrollConnection {
    return remember(controller, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (enabled) {
                    controller.onScroll(available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 1000f) {
                    // Strong fling up -> immediately bring dock back
                    controller.restoreDock()
                }
                return Velocity.Zero
            }
        }
    }
}
