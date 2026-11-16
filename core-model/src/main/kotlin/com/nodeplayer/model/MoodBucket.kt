package com.nodeplayer.model

/**
 * Time-of-day mood bucket definition per F01.
 * Governs active time slots and matching category keywords from Explore data.
 */
data class MoodBucket(
    val id: String,
    val title: String,
    val subtitle: String,
    val startHour: Int,
    val endHour: Int,
    val categoryKeywords: List<String>
) {
    companion object {
        val DEFAULT_BUCKETS = listOf(
            MoodBucket(
                id = "morning_energy",
                title = "Morning Energy",
                subtitle = "Wake up & commute",
                startHour = 5,
                endHour = 11,
                categoryKeywords = listOf("energy", "workout", "morning", "upbeat", "commute", "pop")
            ),
            MoodBucket(
                id = "deep_focus",
                title = "Deep Focus",
                subtitle = "Work & study flow",
                startHour = 11,
                endHour = 17,
                categoryKeywords = listOf("focus", "instrumental", "ambient", "study", "classical", "electronic")
            ),
            MoodBucket(
                id = "evening_chill",
                title = "Evening Chill",
                subtitle = "Unwind & relax",
                startHour = 17,
                endHour = 22,
                categoryKeywords = listOf("chill", "relax", "evening", "acoustic", "lo-fi", "r&b")
            ),
            MoodBucket(
                id = "late_night",
                title = "Late Night Ambient",
                subtitle = "Deep sleep & calm",
                startHour = 22,
                endHour = 5,
                categoryKeywords = listOf("sleep", "ambient", "night", "calm", "meditation", "drone")
            )
        )
    }
}

/**
 * Resolves the active [MoodBucket] for a given [hour] (0-23) handling midnight wrap-around.
 */
fun bucketFor(hour: Int, buckets: List<MoodBucket>): MoodBucket? = buckets.firstOrNull { b ->
    if (b.startHour <= b.endHour) {
        hour in b.startHour until b.endHour
    } else {
        hour >= b.startHour || hour < b.endHour
    }
}
