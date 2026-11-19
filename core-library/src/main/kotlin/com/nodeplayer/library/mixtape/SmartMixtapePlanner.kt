package com.nodeplayer.library.mixtape

import com.nodeplayer.model.CanonicalTrackKey
import com.nodeplayer.model.SmartMixtapeConfig
import com.nodeplayer.model.SmartMixtapeEntry
import com.nodeplayer.model.Track
import kotlin.math.roundToInt

object SmartMixtapePlanner {

    data class PlanResult(
        val tracksToDownload: List<Track>,
        val entriesToEvict: List<SmartMixtapeEntry>
    )

    fun createSyncPlan(
        config: SmartMixtapeConfig,
        discoveryTracks: List<Track>,
        heavyRotationTracks: List<Track>,
        existingMixtape: List<SmartMixtapeEntry>,
        manualDownloadKeys: Set<CanonicalTrackKey>
    ): PlanResult {
        if (!config.isEnabled || config.targetSongCount <= 0) {
            return PlanResult(emptyList(), existingMixtape)
        }

        val discoveryQuota = (config.targetSongCount * 0.60f).roundToInt()
        val rotationQuota = config.targetSongCount - discoveryQuota

        val filteredRotation = heavyRotationTracks.filterNot { 
            manualDownloadKeys.contains(CanonicalTrackKey.from(it)) 
        }
        val filteredDiscovery = discoveryTracks.filterNot { 
            manualDownloadKeys.contains(CanonicalTrackKey.from(it)) 
        }

        val candidateMap = LinkedHashMap<CanonicalTrackKey, Track>()
        filteredRotation.take(rotationQuota).forEach { track ->
            candidateMap[CanonicalTrackKey.from(track)] = track
        }
        filteredDiscovery.forEach { track ->
            if (candidateMap.size < config.targetSongCount) {
                candidateMap[CanonicalTrackKey.from(track)] = track
            }
        }
        if (candidateMap.size < config.targetSongCount) {
            filteredRotation.drop(rotationQuota).forEach { track ->
                if (candidateMap.size < config.targetSongCount) {
                    candidateMap[CanonicalTrackKey.from(track)] = track
                }
            }
        }

        val cachedMap = existingMixtape.associateBy { it.canonicalKey }
        val tracksToDownload = candidateMap.values.filterNot { track ->
            cachedMap.containsKey(CanonicalTrackKey.from(track))
        }

        // --- TWO-TIER HARDENED EVICTION ---
        val entriesToEvict = mutableListOf<SmartMixtapeEntry>()
        val (activeCached, staleCached) = existingMixtape.partition { 
            candidateMap.containsKey(it.canonicalKey) 
        }

        val staleLru = staleCached.sortedBy { it.lastPlayedEpochMs }.toMutableList()
        val activeLru = activeCached.sortedBy { it.lastPlayedEpochMs }.toMutableList()

        var projectedCount = existingMixtape.size + tracksToDownload.size
        var projectedBytes = existingMixtape.sumOf { it.fileSizeBytes }
        val estNewBytes = tracksToDownload.size * 3_500_000L

        fun evictNext(): Boolean {
            val target = when {
                staleLru.isNotEmpty() -> staleLru.removeAt(0)
                activeLru.isNotEmpty() -> activeLru.removeAt(0)
                else -> return false
            }
            entriesToEvict.add(target)
            projectedCount--
            projectedBytes -= target.fileSizeBytes
            return true
        }

        while (projectedCount > config.targetSongCount && evictNext()) {}
        while ((projectedBytes + estNewBytes) > config.maxStorageCapBytes && evictNext()) {}

        return PlanResult(
            tracksToDownload = tracksToDownload,
            entriesToEvict = entriesToEvict
        )
    }
}
