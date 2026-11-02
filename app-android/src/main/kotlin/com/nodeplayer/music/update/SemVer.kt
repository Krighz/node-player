package com.nodeplayer.music.update

/**
 * Lightweight Semantic Versioning parser and comparator.
 * Handles formats like "v1.2.0", "1.2.0", "1.2.0-beta1", etc.
 */
object SemVer {

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null
    ) : Comparable<Version> {

        override fun compareTo(other: Version): Int {
            if (this.major != other.major) return this.major.compareTo(other.major)
            if (this.minor != other.minor) return this.minor.compareTo(other.minor)
            if (this.patch != other.patch) return this.patch.compareTo(other.patch)

            // When numerical parts are equal:
            // A version without pre-release is newer than a version with pre-release (e.g. 1.2.0 > 1.2.0-rc1)
            return when {
                this.preRelease == null && other.preRelease == null -> 0
                this.preRelease == null && other.preRelease != null -> 1
                this.preRelease != null && other.preRelease == null -> -1
                else -> (this.preRelease ?: "").compareTo(other.preRelease ?: "")
            }
        }
    }

    fun parse(versionStr: String): Version? {
        val clean = versionStr.trim().removePrefix("v").removePrefix("V")
        if (clean.isBlank()) return null

        val dashParts = clean.split("-", limit = 2)
        val mainPart = dashParts[0]
        val preRelease = if (dashParts.size > 1) dashParts[1] else null

        val numbers = mainPart.split(".")
        val major = numbers.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = numbers.getOrNull(2)?.toIntOrNull() ?: 0

        return Version(major, minor, patch, preRelease)
    }

    fun compare(v1: String, v2: String): Int {
        val parsed1 = parse(v1) ?: return -1
        val parsed2 = parse(v2) ?: return 1
        return parsed1.compareTo(parsed2)
    }

    fun isNewer(candidate: String, current: String): Boolean {
        return compare(candidate, current) > 0
    }
}
