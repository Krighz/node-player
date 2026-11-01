package com.nodeplayer.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ListeningTimeSlice(
    val date: String, // "YYYY-MM-DD"
    val seconds: Long
)

class PlaybackStatsTracker(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private var isPlaying = false
    private var lastTickEpochMs = 0L
    private var currentTrackId: Long? = null
    private var currentTrackListenedMs = 0L
    private var currentTrackPlayRecorded = false

    var onPlayCountThresholdReached: ((trackId: Long) -> Unit)? = null
    var onListeningTimeAccumulated: ((date: String, seconds: Long) -> Unit)? = null

    fun onTrackStarted(trackId: Long, currentEpochMs: Long = System.currentTimeMillis()) {
        currentTrackId = trackId
        currentTrackListenedMs = 0L
        currentTrackPlayRecorded = false
        lastTickEpochMs = currentEpochMs
        isPlaying = true
    }

    fun onPlayStateChanged(playing: Boolean, currentEpochMs: Long = System.currentTimeMillis()) {
        if (isPlaying && !playing) {
            // Transitioning from playing to paused -> flush elapsed
            tick(currentEpochMs)
        }
        isPlaying = playing
        lastTickEpochMs = currentEpochMs
    }

    fun onSeek(currentEpochMs: Long = System.currentTimeMillis()) {
        // Seeking does not add or remove listening time
        // Just reset tick reference to now so the seek jump isn't counted
        lastTickEpochMs = currentEpochMs
    }

    /**
     * Ticks the clock with current wall-clock epoch ms.
     * Computes listening delta and handles midnight boundary splits.
     */
    fun tick(currentEpochMs: Long = System.currentTimeMillis()): List<ListeningTimeSlice> {
        if (!isPlaying || currentTrackId == null) {
            lastTickEpochMs = currentEpochMs
            return emptyList()
        }

        val elapsedMs = (currentEpochMs - lastTickEpochMs).coerceAtLeast(0L)
        lastTickEpochMs = currentEpochMs

        if (elapsedMs == 0L) return emptyList()

        currentTrackListenedMs += elapsedMs

        // Check if 30-second play threshold is reached for the track
        if (!currentTrackPlayRecorded && currentTrackListenedMs >= 30_000L) {
            currentTrackPlayRecorded = true
            currentTrackId?.let { onPlayCountThresholdReached?.invoke(it) }
        }

        // Calculate start and end local dates
        val startInstant = Instant.ofEpochMilli(currentEpochMs - elapsedMs)
        val endInstant = Instant.ofEpochMilli(currentEpochMs)

        val startDate = startInstant.atZone(zoneId).toLocalDate()
        val endDate = endInstant.atZone(zoneId).toLocalDate()

        val slices = ArrayList<ListeningTimeSlice>()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        if (startDate == endDate) {
            val sec = elapsedMs / 1000L
            if (sec > 0) {
                slices.add(ListeningTimeSlice(startDate.format(formatter), sec))
            }
        } else {
            // Crosses midnight boundary
            val midnightInstant = endDate.atStartOfDay(zoneId).toInstant()
            val beforeMidnightMs = (midnightInstant.toEpochMilli() - startInstant.toEpochMilli()).coerceAtLeast(0L)
            val afterMidnightMs = (endInstant.toEpochMilli() - midnightInstant.toEpochMilli()).coerceAtLeast(0L)

            val secBefore = beforeMidnightMs / 1000L
            val secAfter = afterMidnightMs / 1000L

            if (secBefore > 0) slices.add(ListeningTimeSlice(startDate.format(formatter), secBefore))
            if (secAfter > 0) slices.add(ListeningTimeSlice(endDate.format(formatter), secAfter))
        }

        for (slice in slices) {
            onListeningTimeAccumulated?.invoke(slice.date, slice.seconds)
        }

        return slices
    }

    /**
     * Current accumulated listening time for active track in milliseconds.
     */
    val currentTrackListenedMsValue: Long
        get() = currentTrackListenedMs

    /**
     * Restores state mid-track (e.g. after service restart).
     */
    fun restoreState(trackId: Long, previouslyListenedMs: Long, playAlreadyRecorded: Boolean) {
        currentTrackId = trackId
        currentTrackListenedMs = previouslyListenedMs
        currentTrackPlayRecorded = playAlreadyRecorded
        lastTickEpochMs = System.currentTimeMillis()
    }
}
