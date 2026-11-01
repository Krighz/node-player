package com.nodeplayer.library

import com.nodeplayer.model.Track
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.abs

data class DuplicateGroup(
    val tracks: List<Track>,
    val recommendedTrackId: Long,
    val reason: String
)

object DuplicateFinder {

    /**
     * Compute MD5 hash of up to the first 64KB of a file for fast duplicate verification.
     */
    fun computePartialHash(path: String, maxBytes: Int = 65536): String? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead() || file.length() == 0L) return null
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(maxBytes.coerceAtMost(file.length().toInt()))
            FileInputStream(file).use { input ->
                val read = input.read(buffer)
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds duplicates across [tracks].
     * 1. Exact duplicates: same file size and hash.
     * 2. Metadata duplicates: matching normalized title + artist with duration within 3s.
     */
    fun findDuplicates(
        tracks: List<Track>,
        hashProvider: ((String) -> String?)? = { computePartialHash(it) }
    ): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()
        val processedIds = HashSet<Long>()

        // 1. Exact size & hash duplicates
        val bySize = tracks.filter { it.size > 0 }.groupBy { it.size }
        for ((_, sizeTracks) in bySize) {
            if (sizeTracks.size > 1) {
                if (hashProvider != null) {
                    val byHash = sizeTracks.groupBy { hashProvider(it.path) ?: it.path }
                    for ((_, hashTracks) in byHash) {
                        if (hashTracks.size > 1) {
                            val best = pickBest(hashTracks)
                            groups.add(DuplicateGroup(hashTracks, best.id, "Exact file size & hash match"))
                            processedIds.addAll(hashTracks.map { it.id })
                        }
                    }
                } else {
                    val best = pickBest(sizeTracks)
                    groups.add(DuplicateGroup(sizeTracks, best.id, "Exact file size match"))
                    processedIds.addAll(sizeTracks.map { it.id })
                }
            }
        }

        // 2. Title + Artist match within 3s duration
        val remaining = tracks.filter { it.id !in processedIds }
        val byNormalizedMetadata = remaining.groupBy { normalize(it.title) to normalize(it.artist) }

        for ((key, metaTracks) in byNormalizedMetadata) {
            if (key.first.length < 2) continue
            if (metaTracks.size > 1) {
                val subGroups = ArrayList<MutableList<Track>>()
                for (t in metaTracks) {
                    var placed = false
                    for (sg in subGroups) {
                        if (abs(sg[0].durationMs - t.durationMs) <= 3000L) {
                            sg.add(t)
                            placed = true
                            break
                        }
                    }
                    if (!placed) {
                        subGroups.add(mutableListOf(t))
                    }
                }
                for (sg in subGroups) {
                    if (sg.size > 1) {
                        val best = pickBest(sg)
                        groups.add(DuplicateGroup(sg, best.id, "Matching title & artist (±3s duration)"))
                    }
                }
            }
        }

        return groups
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .trim()
    }

    /**
     * Picks the best track in a duplicate group based on quality:
     * Lossless > Hi-Res > Sample Rate > Bit Depth > File Size.
     */
    fun pickBest(tracks: List<Track>): Track {
        return tracks.maxWithOrNull(
            compareBy<Track> { it.isLossless }
                .thenBy { it.isHiRes }
                .thenBy { it.sampleRate }
                .thenBy { it.bitDepth }
                .thenBy { it.size }
        ) ?: tracks.first()
    }
}
