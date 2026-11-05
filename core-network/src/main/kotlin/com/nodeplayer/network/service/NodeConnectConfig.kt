package com.nodeplayer.network.service

import com.nodeplayer.common.platform.KeyValueStore
import com.nodeplayer.network.transport.SecurityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NodeConnectConfig(
    val enabled: Boolean = true,
    val deviceName: String = "",
    val discoverable: Boolean = true,
    val allowIncoming: Boolean = true,
    val securityLevel: SecurityLevel = SecurityLevel.SIMPLE,
    val askBeforeAllowing: Boolean = true,
    val trustAllOnNetwork: Boolean = false,
    val encryptionEnabled: Boolean = true,

    // Discovery methods
    val mdnsEnabled: Boolean = true,
    val broadcastEnabled: Boolean = true,
    val gatewayProbeEnabled: Boolean = true,
    val subnetScanEnabled: Boolean = true,

    // Feature switches
    val remoteControlEnabled: Boolean = true,
    val shareLibraryEnabled: Boolean = true,
    val audioCastEnabled: Boolean = true,
    val handoffEnabled: Boolean = true,
    val mirrorEnabled: Boolean = true,
    val universalSearchEnabled: Boolean = true,
    val syncRailEnabled: Boolean = true,
    val sleepTimerSyncEnabled: Boolean = true,
    val externalSessionsEnabled: Boolean = false,
    val multiRoomEnabled: Boolean = false,
    val chatClipboardEnabled: Boolean = false,
    val filePushEnabled: Boolean = false,
    val pluginSyncEnabled: Boolean = false,

    // Tuning
    val port: Int = 54820,
    val jitterBufferTargetMs: Int = 100,
    val keepaliveIntervalSec: Int = 5,
    val verboseLogging: Boolean = true
)

class NodeConnectSettingsStore(private val store: KeyValueStore) {

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<NodeConnectConfig> = _config.asStateFlow()

    fun loadConfig(): NodeConnectConfig {
        val secLevelStr = store.getString(KEY_SECURITY_LEVEL, SecurityLevel.SIMPLE.name)
        val secLevel = try {
            SecurityLevel.valueOf(secLevelStr)
        } catch (e: Exception) {
            SecurityLevel.SIMPLE
        }

        return NodeConnectConfig(
            enabled = store.getBoolean(KEY_ENABLED, true),
            deviceName = store.getString(KEY_DEVICE_NAME, ""),
            discoverable = store.getBoolean(KEY_DISCOVERABLE, true),
            allowIncoming = store.getBoolean(KEY_ALLOW_INCOMING, true),
            securityLevel = secLevel,
            askBeforeAllowing = store.getBoolean(KEY_ASK_BEFORE_ALLOWING, true),
            trustAllOnNetwork = store.getBoolean(KEY_TRUST_ALL_ON_NETWORK, false),
            encryptionEnabled = store.getBoolean(KEY_ENCRYPTION_ENABLED, true),
            mdnsEnabled = store.getBoolean(KEY_MDNS_ENABLED, true),
            broadcastEnabled = store.getBoolean(KEY_BROADCAST_ENABLED, true),
            gatewayProbeEnabled = store.getBoolean(KEY_GATEWAY_PROBE_ENABLED, true),
            subnetScanEnabled = store.getBoolean(KEY_SUBNET_SCAN_ENABLED, true),
            remoteControlEnabled = store.getBoolean(KEY_REMOTE_CONTROL_ENABLED, true),
            shareLibraryEnabled = store.getBoolean(KEY_SHARE_LIBRARY_ENABLED, true),
            audioCastEnabled = store.getBoolean(KEY_AUDIO_CAST_ENABLED, true),
            handoffEnabled = store.getBoolean(KEY_HANDOFF_ENABLED, true),
            mirrorEnabled = store.getBoolean(KEY_MIRROR_ENABLED, true),
            universalSearchEnabled = store.getBoolean(KEY_UNIVERSAL_SEARCH_ENABLED, true),
            syncRailEnabled = store.getBoolean(KEY_SYNC_RAIL_ENABLED, true),
            sleepTimerSyncEnabled = store.getBoolean(KEY_SLEEP_TIMER_SYNC_ENABLED, true),
            externalSessionsEnabled = store.getBoolean(KEY_EXTERNAL_SESSIONS_ENABLED, false),
            multiRoomEnabled = store.getBoolean(KEY_MULTI_ROOM_ENABLED, false),
            chatClipboardEnabled = store.getBoolean(KEY_CHAT_CLIPBOARD_ENABLED, false),
            filePushEnabled = store.getBoolean(KEY_FILE_PUSH_ENABLED, false),
            pluginSyncEnabled = store.getBoolean(KEY_PLUGIN_SYNC_ENABLED, false),
            port = store.getInt(KEY_PORT, 54820),
            jitterBufferTargetMs = store.getInt(KEY_JITTER_BUFFER_TARGET_MS, 100),
            keepaliveIntervalSec = store.getInt(KEY_KEEPALIVE_INTERVAL_SEC, 5),
            verboseLogging = store.getBoolean(KEY_VERBOSE_LOGGING, true)
        )
    }

    fun updateConfig(transform: (NodeConnectConfig) -> NodeConnectConfig) {
        val newConfig = transform(_config.value)
        saveConfig(newConfig)
        _config.value = newConfig
    }

    fun saveConfig(c: NodeConnectConfig) {
        store.putBoolean(KEY_ENABLED, c.enabled)
        store.putString(KEY_DEVICE_NAME, c.deviceName)
        store.putBoolean(KEY_DISCOVERABLE, c.discoverable)
        store.putBoolean(KEY_ALLOW_INCOMING, c.allowIncoming)
        store.putString(KEY_SECURITY_LEVEL, c.securityLevel.name)
        store.putBoolean(KEY_ASK_BEFORE_ALLOWING, c.askBeforeAllowing)
        store.putBoolean(KEY_TRUST_ALL_ON_NETWORK, c.trustAllOnNetwork)
        store.putBoolean(KEY_ENCRYPTION_ENABLED, c.encryptionEnabled)
        store.putBoolean(KEY_MDNS_ENABLED, c.mdnsEnabled)
        store.putBoolean(KEY_BROADCAST_ENABLED, c.broadcastEnabled)
        store.putBoolean(KEY_GATEWAY_PROBE_ENABLED, c.gatewayProbeEnabled)
        store.putBoolean(KEY_SUBNET_SCAN_ENABLED, c.subnetScanEnabled)
        store.putBoolean(KEY_REMOTE_CONTROL_ENABLED, c.remoteControlEnabled)
        store.putBoolean(KEY_SHARE_LIBRARY_ENABLED, c.shareLibraryEnabled)
        store.putBoolean(KEY_AUDIO_CAST_ENABLED, c.audioCastEnabled)
        store.putBoolean(KEY_HANDOFF_ENABLED, c.handoffEnabled)
        store.putBoolean(KEY_MIRROR_ENABLED, c.mirrorEnabled)
        store.putBoolean(KEY_UNIVERSAL_SEARCH_ENABLED, c.universalSearchEnabled)
        store.putBoolean(KEY_SYNC_RAIL_ENABLED, c.syncRailEnabled)
        store.putBoolean(KEY_SLEEP_TIMER_SYNC_ENABLED, c.sleepTimerSyncEnabled)
        store.putBoolean(KEY_EXTERNAL_SESSIONS_ENABLED, c.externalSessionsEnabled)
        store.putBoolean(KEY_MULTI_ROOM_ENABLED, c.multiRoomEnabled)
        store.putBoolean(KEY_CHAT_CLIPBOARD_ENABLED, c.chatClipboardEnabled)
        store.putBoolean(KEY_FILE_PUSH_ENABLED, c.filePushEnabled)
        store.putBoolean(KEY_PLUGIN_SYNC_ENABLED, c.pluginSyncEnabled)
        store.putInt(KEY_PORT, c.port)
        store.putInt(KEY_JITTER_BUFFER_TARGET_MS, c.jitterBufferTargetMs)
        store.putInt(KEY_KEEPALIVE_INTERVAL_SEC, c.keepaliveIntervalSec)
        store.putBoolean(KEY_VERBOSE_LOGGING, c.verboseLogging)
    }

    companion object {
        const val KEY_ENABLED = "nc_enabled"
        const val KEY_DEVICE_NAME = "nc_device_name"
        const val KEY_DISCOVERABLE = "nc_discoverable"
        const val KEY_ALLOW_INCOMING = "nc_allow_incoming"
        const val KEY_SECURITY_LEVEL = "nc_security_level"
        const val KEY_ASK_BEFORE_ALLOWING = "nc_ask_before_allowing"
        const val KEY_TRUST_ALL_ON_NETWORK = "nc_trust_all_on_network"
        const val KEY_ENCRYPTION_ENABLED = "nc_encryption_enabled"
        const val KEY_MDNS_ENABLED = "nc_mdns_enabled"
        const val KEY_BROADCAST_ENABLED = "nc_broadcast_enabled"
        const val KEY_GATEWAY_PROBE_ENABLED = "nc_gateway_probe_enabled"
        const val KEY_SUBNET_SCAN_ENABLED = "nc_subnet_scan_enabled"
        const val KEY_REMOTE_CONTROL_ENABLED = "nc_remote_control_enabled"
        const val KEY_SHARE_LIBRARY_ENABLED = "nc_share_library_enabled"
        const val KEY_AUDIO_CAST_ENABLED = "nc_audio_cast_enabled"
        const val KEY_HANDOFF_ENABLED = "nc_handoff_enabled"
        const val KEY_MIRROR_ENABLED = "nc_mirror_enabled"
        const val KEY_UNIVERSAL_SEARCH_ENABLED = "nc_universal_search_enabled"
        const val KEY_SYNC_RAIL_ENABLED = "nc_sync_rail_enabled"
        const val KEY_SLEEP_TIMER_SYNC_ENABLED = "nc_sleep_timer_sync_enabled"
        const val KEY_EXTERNAL_SESSIONS_ENABLED = "nc_external_sessions_enabled"
        const val KEY_MULTI_ROOM_ENABLED = "nc_multi_room_enabled"
        const val KEY_CHAT_CLIPBOARD_ENABLED = "nc_chat_clipboard_enabled"
        const val KEY_FILE_PUSH_ENABLED = "nc_file_push_enabled"
        const val KEY_PLUGIN_SYNC_ENABLED = "nc_plugin_sync_enabled"
        const val KEY_PORT = "nc_port"
        const val KEY_JITTER_BUFFER_TARGET_MS = "nc_jitter_buffer_target_ms"
        const val KEY_KEEPALIVE_INTERVAL_SEC = "nc_keepalive_interval_sec"
        const val KEY_VERBOSE_LOGGING = "nc_verbose_logging"
    }
}
