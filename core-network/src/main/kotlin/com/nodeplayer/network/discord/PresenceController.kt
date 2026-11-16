package com.nodeplayer.network.discord

import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.common.platform.KeyValueStore
import com.nodeplayer.model.presence.DiscordIpcConstants
import com.nodeplayer.model.presence.PresenceState
import com.nodeplayer.model.presence.PresenceStatus
import com.nodeplayer.model.presence.PresenceTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.min

/**
 * Orchestrator for Discord Rich Presence lifecycle, rate limiting, and privacy guardrails.
 */
class PresenceController(
    private val transport: PresenceTransport,
    private val store: KeyValueStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "discord/presence"
        const val KEY_ENABLED = "discord_enabled"
        const val KEY_INCOGNITO = "discord_incognito"
        const val KEY_SHOW_COVER = "discord_show_cover"
        const val KEY_SHOW_ALBUM = "discord_show_album"
        const val KEY_SHOW_WHILE_PAUSED = "discord_show_while_paused"
        const val KEY_APP_ID = "discord_app_id"

        private const val BASE_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val SEEK_THRESHOLD_MS = 3_000L
    }

    private val _status = MutableStateFlow(PresenceStatus.DISABLED)
    val status: StateFlow<PresenceStatus> = _status.asStateFlow()

    private val _isIncognito = MutableStateFlow(store.getBoolean(KEY_INCOGNITO, false))
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private var currentPlaybackState: PresenceState? = null
    private var lastDispatchedState: PresenceState? = null
    private var lastDispatchTimeMs: Long = 0L

    private var debounceJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0
    private var isConnected: Boolean = false

    init {
        refreshState()
    }

    /**
     * Refreshes internal status based on persistent settings and initiates/terminates connection.
     */
    fun refreshState() {
        val enabled = store.getBoolean(KEY_ENABLED, false)
        val incognito = store.getBoolean(KEY_INCOGNITO, false)
        _isIncognito.value = incognito
        val appId = store.getString(KEY_APP_ID, "").trim()

        if (!enabled) {
            transitionStatus(PresenceStatus.DISABLED)
            cancelScheduledWork()
            scope.launch {
                safeClearAndClose()
            }
            return
        }

        if (appId.isEmpty()) {
            transitionStatus(PresenceStatus.APP_ID_REQUIRED)
            cancelScheduledWork()
            scope.launch {
                safeClearAndClose()
            }
            return
        }

        if (incognito) {
            transitionStatus(PresenceStatus.CONNECTED)
            cancelScheduledWork()
            scope.launch {
                safeClearAndClose()
            }
            return
        }

        if (!isConnected && reconnectJob == null) {
            scheduleReconnect(immediate = true)
        }
    }

    /**
     * Updates rich presence based on current audio player state.
     */
    fun updatePlayback(state: PresenceState) {
        val previousState = currentPlaybackState
        currentPlaybackState = state

        val enabled = store.getBoolean(KEY_ENABLED, false)
        val incognito = _isIncognito.value
        val appId = store.getString(KEY_APP_ID, "").trim()

        if (!enabled || appId.isEmpty()) {
            return
        }

        if (incognito) {
            return
        }

        val showWhilePaused = store.getBoolean(KEY_SHOW_WHILE_PAUSED, false)
        if (!state.isPlaying && !showWhilePaused) {
            // Immediately clear presence when paused
            debounceJob?.cancel()
            debounceJob = null
            lastDispatchedState = null
            scope.launch {
                try {
                    transport.clear()
                } catch (e: Throwable) {
                    handleTransportFailure(e)
                }
            }
            return
        }

        val now = clock()
        val timeSinceLast = now - lastDispatchTimeMs

        // Detect substantial seek jumps (> 3000ms offset change)
        val isSeek = previousState != null &&
            previousState.title == state.title &&
            abs(state.positionMs - previousState.positionMs) > SEEK_THRESHOLD_MS

        val requiresImmediate = (previousState == null || previousState.title != state.title || isSeek) &&
            timeSinceLast >= DiscordIpcConstants.MIN_UPDATE_INTERVAL_MS

        if (requiresImmediate) {
            debounceJob?.cancel()
            debounceJob = null
            dispatchActivity(state)
        } else {
            // Coalesce / debounce rapid updates within the 15-second rate window
            if (debounceJob?.isActive == true) return
            val delayMs = (DiscordIpcConstants.MIN_UPDATE_INTERVAL_MS - timeSinceLast).coerceAtLeast(100L)
            debounceJob = scope.launch {
                delay(delayMs)
                currentPlaybackState?.let { latest ->
                    dispatchActivity(latest)
                }
            }
        }
    }

    /**
     * Toggles incognito mode immediately.
     */
    fun setIncognito(enabled: Boolean) {
        store.putBoolean(KEY_INCOGNITO, enabled)
        _isIncognito.value = enabled
        if (enabled) {
            cancelScheduledWork()
            scope.launch {
                safeClearAndClose()
            }
        } else {
            refreshState()
            currentPlaybackState?.let { updatePlayback(it) }
        }
    }

    private fun dispatchActivity(state: PresenceState) {
        lastDispatchTimeMs = clock()
        lastDispatchedState = state

        scope.launch {
            if (!isConnected) {
                if (!tryConnect()) return@launch
            }

            try {
                val showCover = store.getBoolean(KEY_SHOW_COVER, true)
                val showAlbum = store.getBoolean(KEY_SHOW_ALBUM, true)
                val json = DiscordActivityBuilder.buildSetActivityJson(
                    state = state,
                    showCover = showCover,
                    showAlbum = showAlbum,
                    currentEpochMs = clock()
                )
                val success = transport.setActivity(json)
                if (success) {
                    transitionStatus(PresenceStatus.CONNECTED)
                } else {
                    handleTransportFailure(Exception("Failed to send frame"))
                }
            } catch (e: Throwable) {
                handleTransportFailure(e)
            }
        }
    }

    private suspend fun tryConnect(): Boolean {
        transitionStatus(PresenceStatus.CONNECTING)
        return try {
            val connected = transport.connect()
            if (connected) {
                isConnected = true
                reconnectAttempt = 0
                transitionStatus(PresenceStatus.CONNECTED)
                true
            } else {
                isConnected = false
                transitionStatus(PresenceStatus.NOT_RUNNING)
                scheduleReconnect(immediate = false)
                false
            }
        } catch (e: Throwable) {
            isConnected = false
            handleTransportFailure(e)
            false
        }
    }

    private fun handleTransportFailure(e: Throwable) {
        isConnected = false
        NodeLog.w(TAG, "Transport error: ${e::class.simpleName ?: "unknown"}")
        transitionStatus(PresenceStatus.ERROR)
        scheduleReconnect(immediate = false)
    }

    private fun scheduleReconnect(immediate: Boolean) {
        reconnectJob?.cancel()
        val enabled = store.getBoolean(KEY_ENABLED, false)
        val appId = store.getString(KEY_APP_ID, "").trim()
        val incognito = _isIncognito.value

        if (!enabled || appId.isEmpty() || incognito) {
            return
        }

        reconnectJob = scope.launch {
            if (!immediate) {
                val backoffMs = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1 shl min(reconnectAttempt, 5)))
                reconnectAttempt++
                delay(backoffMs)
            }
            if (isActive) {
                tryConnect()
                currentPlaybackState?.let { latest ->
                    if (latest.isPlaying) {
                        dispatchActivity(latest)
                    }
                }
            }
        }
    }

    private suspend fun safeClearAndClose() {
        try {
            if (isConnected) {
                transport.clear()
            }
        } catch (_: Throwable) {
        } finally {
            try {
                transport.close()
            } catch (_: Throwable) {
            }
            isConnected = false
        }
    }

    private fun cancelScheduledWork() {
        debounceJob?.cancel()
        debounceJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
    }

    private fun transitionStatus(newStatus: PresenceStatus) {
        if (_status.value != newStatus) {
            _status.value = newStatus
            NodeLog.d(TAG, "Status -> $newStatus")
        }
    }

    fun close() {
        cancelScheduledWork()
        scope.launch {
            safeClearAndClose()
        }
    }
}
