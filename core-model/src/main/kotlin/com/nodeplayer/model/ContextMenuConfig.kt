package com.nodeplayer.model

/**
 * Unique identifiers for actions in the standardized Track Context Menu.
 */
enum class TrackActionId(val title: String, val iconName: String) {
    PLAY_NEXT("Play next", "QueueMusic"),
    ADD_TO_QUEUE("Add to queue", "PlaylistAdd"),
    ADD_TO_PLAYLIST("Add to playlist", "PlaylistAddCheck"),
    LIKE_SAVE("Save to library", "FavoriteBorder"),
    START_RADIO("Start radio", "Radio"),
    GO_TO_ARTIST("Go to artist", "Person"),
    GO_TO_ALBUM("Go to album", "Album"),
    VIEW_LYRICS("View lyrics", "Lyrics"),
    SHARE("Share", "Share"),
    SONG_INFO("Song info", "Info"),
    NOT_INTERESTED("Not interested / hide", "Block");

    companion object {
        fun fromId(id: String): TrackActionId? = entries.find { it.name.equals(id, ignoreCase = true) }
    }
}

/**
 * Typed configuration model for custom ordering and visibility toggles of context menu items.
 * Meets requirement MENU-03. Pure Kotlin model with zero Android framework dependencies.
 */
data class ContextMenuConfig(
    val orderedTrackActionIds: List<String> = TrackActionId.entries.map { it.name },
    val disabledActionIds: Set<String> = emptySet()
) {
    /**
     * Returns the active list of TrackActionIds in the configured order.
     */
    fun getActiveTrackActions(): List<TrackActionId> {
        return orderedTrackActionIds
            .filterNot { disabledActionIds.contains(it) }
            .mapNotNull { TrackActionId.fromId(it) }
    }

    companion object {
        val Defaults = ContextMenuConfig()
    }
}
