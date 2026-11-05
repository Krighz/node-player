package com.nodeplayer.network.sync

/**
 * Hybrid Logical Clock (HLC) for causal ordering and distributed conflict resolution in Node Connect.
 */
data class HlcTimestamp(
    val physicalTimeMs: Long,
    val logicalCounter: Int,
    val deviceId: String
) : Comparable<HlcTimestamp> {

    override fun compareTo(other: HlcTimestamp): Int {
        if (physicalTimeMs != other.physicalTimeMs) {
            return physicalTimeMs.compareTo(other.physicalTimeMs)
        }
        if (logicalCounter != other.logicalCounter) {
            return logicalCounter.compareTo(other.logicalCounter)
        }
        return deviceId.compareTo(other.deviceId)
    }

    override fun toString(): String {
        return "$physicalTimeMs-$logicalCounter-$deviceId"
    }

    companion object {
        fun fromString(s: String): HlcTimestamp? {
            val parts = s.split("-")
            if (parts.size < 3) return null
            val phys = parts[0].toLongOrNull() ?: return null
            val log = parts[1].toIntOrNull() ?: return null
            val id = parts.drop(2).joinToString("-")
            return HlcTimestamp(phys, log, id)
        }
    }
}

class HlcClock(
    private val deviceId: String
) {
    private var lastPhysicalMs: Long = 0L
    private var logicalCounter: Int = 0

    @Synchronized
    fun now(): HlcTimestamp {
        val nowMs = System.currentTimeMillis()
        if (nowMs > lastPhysicalMs) {
            lastPhysicalMs = nowMs
            logicalCounter = 0
        } else {
            logicalCounter++
        }
        return HlcTimestamp(lastPhysicalMs, logicalCounter, deviceId)
    }

    @Synchronized
    fun update(remote: HlcTimestamp): HlcTimestamp {
        val nowMs = System.currentTimeMillis()
        val maxPhys = Math.max(nowMs, Math.max(lastPhysicalMs, remote.physicalTimeMs))

        if (maxPhys == lastPhysicalMs && maxPhys == remote.physicalTimeMs) {
            logicalCounter = Math.max(logicalCounter, remote.logicalCounter) + 1
        } else if (maxPhys == lastPhysicalMs) {
            logicalCounter++
        } else if (maxPhys == remote.physicalTimeMs) {
            logicalCounter = remote.logicalCounter + 1
        } else {
            logicalCounter = 0
        }

        lastPhysicalMs = maxPhys
        return HlcTimestamp(lastPhysicalMs, logicalCounter, deviceId)
    }
}
