package com.nodeplayer.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller managing dock auto-hide and collapse-on-scroll animations.
 * When scrolling down past 32dp threshold, collapses dock and mini-player into a unified single bottom row.
 * Scrolling up or tapping restores full dock.
 */
class DockScrollController(
    private val scope: CoroutineScope,
    var sensitivity: Float = 1.0f,
    var collapseThresholdDp: Float = 32.0f,
    var returnDelayMs: Long = 4000L,
    enabled: Boolean = true
) {
    var enabled: Boolean = enabled
        set(value) {
            field = value
            if (!value) {
                restoreDock()
            }
        }

    // 0f = fully expanded (2-tier), 1f = fully collapsed into single row
    private val _collapseProgress = MutableStateFlow(0f)
    val collapseProgress: StateFlow<Float> = _collapseProgress.asStateFlow()

    // Backward-compatible offsetFraction
    val offsetFraction: StateFlow<Float> get() = _collapseProgress

    private val _isCollapsed = MutableStateFlow(false)
    val isCollapsed: StateFlow<Boolean> = _isCollapsed.asStateFlow()

    private var downwardScrollAccumulator: Float = 0f
    private var returnTimerJob: Job? = null

    fun onScroll(deltaY: Float) {
        if (!enabled) {
            if (_collapseProgress.value != 0f) restoreDock()
            return
        }

        val scaledDelta = deltaY * sensitivity

        if (scaledDelta < -4f) {
            // Scrolling down
            downwardScrollAccumulator += (-scaledDelta)
            if (downwardScrollAccumulator >= collapseThresholdDp) {
                collapseDock()
            }
        } else if (scaledDelta > 4f) {
            // Scrolling up -> expand immediately
            downwardScrollAccumulator = 0f
            restoreDock()
        }
    }

    fun onListScrolledToTop(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
            restoreDock()
        }
    }

    fun onBottomEdgeTap() {
        restoreDock()
    }

    fun collapseDock() {
        returnTimerJob?.cancel()
        _isCollapsed.value = true
        _collapseProgress.value = 1f
    }

    fun restoreDock() {
        returnTimerJob?.cancel()
        downwardScrollAccumulator = 0f
        _isCollapsed.value = false
        _collapseProgress.value = 0f
    }

    fun hideDock() {
        collapseDock()
    }

    fun collapse() {
        collapseDock()
    }

    fun expand() {
        restoreDock()
    }

    fun toggle() {
        toggleCollapse()
    }

    fun toggleCollapse() {
        if (_isCollapsed.value) {
            restoreDock()
        } else {
            collapseDock()
        }
    }
}

/**
 * Remembers a NestedScrollConnection linked to the dock scroll controller.
 */
@Composable
fun rememberDockScrollConnection(
    controller: DockScrollController?,
    enabled: Boolean = true
): NestedScrollConnection {
    return remember(controller, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (enabled && controller != null) {
                    controller.onScroll(available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 1000f && controller != null) {
                    // Strong fling up -> immediately bring dock back
                    controller.restoreDock()
                }
                return Velocity.Zero
            }
        }
    }
}
