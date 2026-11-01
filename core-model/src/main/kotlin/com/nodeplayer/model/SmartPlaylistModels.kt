package com.nodeplayer.model

enum class SmartRuleField {
    FORMAT,
    LOSSLESS,
    SAMPLE_RATE,
    BIT_DEPTH,
    DATE_ADDED,
    LAST_PLAYED,
    PLAY_COUNT,
    DURATION,
    GENRE,
    YEAR,
    ARTIST,
    ALBUM,
    FOLDER,
    LOUDNESS,
    DR_SCORE,
    HIRES_STATUS,
    FAVORITE
}

enum class SmartRuleOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    GREATER_THAN,
    LESS_THAN,
    IS_TRUE,
    IS_FALSE
}

data class SmartRule(
    val field: SmartRuleField,
    val operator: SmartRuleOperator,
    val value: String
) {
    fun matches(track: Track): Boolean {
        return when (field) {
            SmartRuleField.FORMAT -> matchString(track.mimeType.substringAfterLast("/"), value, operator)
            SmartRuleField.LOSSLESS -> matchBoolean(track.isLossless, operator)
            SmartRuleField.FAVORITE -> matchBoolean(track.isFavorite, operator)
            SmartRuleField.SAMPLE_RATE -> matchNumber(track.sampleRate.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.BIT_DEPTH -> matchNumber(track.bitDepth.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.DATE_ADDED -> matchNumber(track.dateAdded.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.LAST_PLAYED -> matchNumber(track.lastPlayedTimestamp.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.PLAY_COUNT -> matchNumber(track.playCount.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.DURATION -> matchNumber((track.durationMs / 1000).toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.GENRE -> matchString(track.genre ?: "", value, operator)
            SmartRuleField.YEAR -> matchNumber(track.year.toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.ARTIST -> matchString(track.artist, value, operator)
            SmartRuleField.ALBUM -> matchString(track.album, value, operator)
            SmartRuleField.FOLDER -> matchString(track.path.substringBeforeLast("/"), value, operator)
            SmartRuleField.LOUDNESS -> matchNumber((track.loudnessLufs ?: -70f).toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.DR_SCORE -> matchNumber((track.dynamicRangeScore ?: 0).toDouble(), value.toDoubleOrNull() ?: 0.0, operator)
            SmartRuleField.HIRES_STATUS -> matchString(track.hiResStatus.name, value, operator)
        }
    }

    private fun matchString(actual: String, target: String, op: SmartRuleOperator): Boolean {
        return when (op) {
            SmartRuleOperator.EQUALS -> actual.equals(target, ignoreCase = true)
            SmartRuleOperator.NOT_EQUALS -> !actual.equals(target, ignoreCase = true)
            SmartRuleOperator.CONTAINS -> actual.contains(target, ignoreCase = true)
            SmartRuleOperator.NOT_CONTAINS -> !actual.contains(target, ignoreCase = true)
            else -> false
        }
    }

    private fun matchNumber(actual: Double, target: Double, op: SmartRuleOperator): Boolean {
        return when (op) {
            SmartRuleOperator.EQUALS -> actual == target
            SmartRuleOperator.NOT_EQUALS -> actual != target
            SmartRuleOperator.GREATER_THAN -> actual > target
            SmartRuleOperator.LESS_THAN -> actual < target
            else -> false
        }
    }

    private fun matchBoolean(actual: Boolean, op: SmartRuleOperator): Boolean {
        return when (op) {
            SmartRuleOperator.IS_TRUE -> actual
            SmartRuleOperator.IS_FALSE -> !actual
            SmartRuleOperator.EQUALS -> actual == true
            else -> false
        }
    }
}

enum class LogicalOperator { AND, OR }

data class SmartRuleGroup(
    val operator: LogicalOperator = LogicalOperator.AND,
    val rules: List<SmartRule> = emptyList()
) {
    fun matches(track: Track): Boolean {
        if (rules.isEmpty()) return true
        return when (operator) {
            LogicalOperator.AND -> rules.all { it.matches(track) }
            LogicalOperator.OR -> rules.any { it.matches(track) }
        }
    }
}

data class SmartPlaylist(
    val id: Long = 0,
    val name: String,
    val ruleGroup: SmartRuleGroup,
    val sortField: String = "title",
    val sortAscending: Boolean = true,
    val limitCount: Int = 0
) {
    fun filterTracks(allTracks: List<Track>): List<Track> {
        var filtered = allTracks.filter { ruleGroup.matches(it) }
        filtered = when (sortField.lowercase()) {
            "title" -> if (sortAscending) filtered.sortedBy { it.title.lowercase() } else filtered.sortedByDescending { it.title.lowercase() }
            "artist" -> if (sortAscending) filtered.sortedBy { it.artist.lowercase() } else filtered.sortedByDescending { it.artist.lowercase() }
            "dateadded" -> if (sortAscending) filtered.sortedBy { it.dateAdded } else filtered.sortedByDescending { it.dateAdded }
            "playcount" -> if (sortAscending) filtered.sortedBy { it.playCount } else filtered.sortedByDescending { it.playCount }
            "dr" -> if (sortAscending) filtered.sortedBy { it.dynamicRangeScore ?: 0 } else filtered.sortedByDescending { it.dynamicRangeScore ?: 0 }
            else -> filtered
        }
        if (limitCount > 0) {
            filtered = filtered.take(limitCount)
        }
        return filtered
    }

    companion object {
        fun defaultPlaylists(): List<SmartPlaylist> {
            val nowMs = System.currentTimeMillis()
            val thirtyDaysAgoSec = (nowMs - 30L * 24 * 3600 * 1000) / 1000
            val ninetyDaysAgoSec = (nowMs - 90L * 24 * 3600 * 1000) / 1000

            return listOf(
                SmartPlaylist(
                    id = 1L,
                    name = "Recently Added",
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(
                            SmartRule(SmartRuleField.DATE_ADDED, SmartRuleOperator.GREATER_THAN, thirtyDaysAgoSec.toString())
                        )
                    ),
                    sortField = "dateadded",
                    sortAscending = false
                ),
                SmartPlaylist(
                    id = 2L,
                    name = "Lossless Only",
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(
                            SmartRule(SmartRuleField.LOSSLESS, SmartRuleOperator.IS_TRUE, "true")
                        )
                    )
                ),
                SmartPlaylist(
                    id = 3L,
                    name = "Not Played in 3 Months",
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(
                            SmartRule(SmartRuleField.LAST_PLAYED, SmartRuleOperator.LESS_THAN, ninetyDaysAgoSec.toString())
                        )
                    )
                ),
                SmartPlaylist(
                    id = 4L,
                    name = "Long Tracks (>6 min)",
                    ruleGroup = SmartRuleGroup(
                        operator = LogicalOperator.AND,
                        rules = listOf(
                            SmartRule(SmartRuleField.DURATION, SmartRuleOperator.GREATER_THAN, "360")
                        )
                    )
                )
            )
        }
    }
}
