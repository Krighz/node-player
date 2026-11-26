package com.nodeplayer.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Centralized haptic feedback controller respecting the user's theme haptics settings.
 */
class NodeHaptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean
) {
    /**
     * Standard click/tap for list items, buttons, or navigation tabs.
     */
    fun click() {
        if (enabled) {
            try { feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
        }
    }

    /**
     * Subtle tick for sliders, scrubbers, or wheel rotations.
     */
    fun tick() {
        if (enabled) {
            try { feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
        }
    }

    /**
     * Stronger confirmation for long presses, queuing actions, or major state switches.
     */
    fun impact() {
        if (enabled) {
            try { feedback.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
        }
    }
}

@Composable
fun rememberNodeHaptics(enabled: Boolean = true): NodeHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { NodeHaptics(feedback, enabled) }
}
