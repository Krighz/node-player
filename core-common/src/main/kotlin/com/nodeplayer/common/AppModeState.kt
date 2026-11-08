package com.nodeplayer.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global application-wide mode state holder controlling Online vs. Offline behavior.
 *
 * - When [isOnlineMode] is false (Default - Offline Mode):
 *   Strictly zero network requests are dispatched to external audio providers (e.g. YouTube).
 *   The home view and search screens display local files, scanned albums, and cached music only.
 *
 * - When [isOnlineMode] is true (Online Mode):
 *   The home view surfaces curated online recommendations and online search queries InnerTube.
 */
object AppModeState {
    private val _isOnlineMode = MutableStateFlow(false)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    fun setOnlineMode(enabled: Boolean) {
        _isOnlineMode.value = enabled
    }

    fun toggleMode() {
        _isOnlineMode.value = !_isOnlineMode.value
    }
}
