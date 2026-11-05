package com.nodeplayer.network.pairing

import java.io.File
import java.nio.charset.StandardCharsets

data class DevicePermissions(
    val canReceiveCast: Boolean = true,
    val canSendCast: Boolean = true,
    val canBrowseLibrary: Boolean = true,
    val canRemoteControl: Boolean = true,
    val canSyncData: Boolean = true,
    val canControlExternalSessions: Boolean = false
)

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String, // "Android", "Windows", "TV"
    val certificateFingerprintSha256: String,
    val permissions: DevicePermissions = DevicePermissions(),
    val pairedTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

class DeviceRegistry(
    private val storageFile: File = getDefaultRegistryFile()
) {
    companion object {
        fun getDefaultRegistryFile(): File {
            val appData = System.getenv("APPDATA")
            val baseDir = if (!appData.isNullOrBlank()) {
                File(appData, "NodePlayer/registry")
            } else {
                val androidFiles = File("/data/data/com.nodeplayer.music/files")
                if (androidFiles.exists()) {
                    File(androidFiles, "nodeplayer/registry")
                } else {
                    val userHome = System.getProperty("user.home", ".")
                    if (userHome == "/" || userHome == ".") {
                        File(System.getProperty("java.io.tmpdir", "."), ".nodeplayer/registry")
                    } else {
                        File(userHome, ".nodeplayer/registry")
                    }
                }
            }
            baseDir.mkdirs()
            return File(baseDir, "paired_devices.txt")
        }
    }

    private val devices = mutableMapOf<String, PairedDevice>()

    init {
        load()
    }

    @Synchronized
    fun isTrustedFingerprint(fingerprint: String): Boolean {
        val normalized = fingerprint.trim().uppercase()
        return devices.values.any { it.certificateFingerprintSha256.trim().uppercase() == normalized }
    }

    @Synchronized
    fun isTrustedDeviceId(deviceId: String): Boolean {
        return devices.containsKey(deviceId)
    }

    @Synchronized
    fun getPairedDeviceByFingerprint(fingerprint: String): PairedDevice? {
        val normalized = fingerprint.trim().uppercase()
        return devices.values.find { it.certificateFingerprintSha256.trim().uppercase() == normalized }
    }

    @Synchronized
    fun getPairedDeviceById(deviceId: String): PairedDevice? {
        return devices[deviceId]
    }

    @Synchronized
    fun getAllPairedDevices(): List<PairedDevice> {
        return devices.values.toList()
    }

    @Synchronized
    fun registerDevice(device: PairedDevice) {
        devices[device.deviceId] = device
        save()
    }

    @Synchronized
    fun revokeDevice(deviceId: String): Boolean {
        val removed = devices.remove(deviceId) != null
        if (removed) {
            save()
        }
        return removed
    }

    @Synchronized
    fun updateLastSeen(deviceId: String) {
        val existing = devices[deviceId] ?: return
        devices[deviceId] = existing.copy(lastSeenTimestamp = System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun updatePermissions(deviceId: String, permissions: DevicePermissions): Boolean {
        val existing = devices[deviceId] ?: return false
        devices[deviceId] = existing.copy(permissions = permissions)
        save()
        return true
    }

    @Synchronized
    fun clearAll() {
        devices.clear()
        if (storageFile.exists()) {
            storageFile.delete()
        }
    }

    private fun save() {
        try {
            storageFile.parentFile?.mkdirs()
            val lines = devices.values.map { dev ->
                listOf(
                    dev.deviceId,
                    dev.deviceName.replace("|", "_"),
                    dev.platform,
                    dev.certificateFingerprintSha256,
                    dev.permissions.canReceiveCast,
                    dev.permissions.canSendCast,
                    dev.permissions.canBrowseLibrary,
                    dev.permissions.canRemoteControl,
                    dev.permissions.canSyncData,
                    dev.permissions.canControlExternalSessions,
                    dev.pairedTimestamp,
                    dev.lastSeenTimestamp
                ).joinToString("|")
            }
            storageFile.writeText(lines.joinToString("\n"), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    private fun load() {
        if (!storageFile.exists()) return
        try {
            val lines = storageFile.readLines(StandardCharsets.UTF_8)
            for (line in lines) {
                val parts = line.split("|")
                if (parts.size >= 12) {
                    val device = PairedDevice(
                        deviceId = parts[0],
                        deviceName = parts[1],
                        platform = parts[2],
                        certificateFingerprintSha256 = parts[3],
                        permissions = DevicePermissions(
                            canReceiveCast = parts[4].toBooleanStrictOrNull() ?: true,
                            canSendCast = parts[5].toBooleanStrictOrNull() ?: true,
                            canBrowseLibrary = parts[6].toBooleanStrictOrNull() ?: true,
                            canRemoteControl = parts[7].toBooleanStrictOrNull() ?: true,
                            canSyncData = parts[8].toBooleanStrictOrNull() ?: true,
                            canControlExternalSessions = parts[9].toBooleanStrictOrNull() ?: false
                        ),
                        pairedTimestamp = parts[10].toLongOrNull() ?: System.currentTimeMillis(),
                        lastSeenTimestamp = parts[11].toLongOrNull() ?: System.currentTimeMillis()
                    )
                    devices[device.deviceId] = device
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted lines
        }
    }
}
