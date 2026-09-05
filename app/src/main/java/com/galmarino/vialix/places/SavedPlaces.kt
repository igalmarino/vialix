package com.galmarino.vialix.places

import com.galmarino.vialix.navigation.Destination

/** The two pinned shortcuts at the top of the home sheet. */
enum class FavoriteKind { HOME, WORK }

/**
 * Everything the home sheet remembers about the user: the Home/Work shortcuts and the most recent
 * destinations, newest first. A plain value so the bookkeeping (dedupe, cap) is testable without
 * Android; [SavedPlacesRepository] persists it.
 */
data class SavedPlaces(
    val home: Destination? = null,
    val work: Destination? = null,
    val recents: List<Destination> = emptyList(),
) {
    fun favorite(kind: FavoriteKind): Destination? =
        when (kind) {
            FavoriteKind.HOME -> home
            FavoriteKind.WORK -> work
        }

    /** `null` clears the shortcut. */
    fun withFavorite(kind: FavoriteKind, destination: Destination?): SavedPlaces =
        when (kind) {
            FavoriteKind.HOME -> copy(home = destination)
            FavoriteKind.WORK -> copy(work = destination)
        }

    /**
     * Moves [destination] to the front of the recents, dropping any earlier entry for the same
     * coordinate, and trims the list to [MAX_RECENTS].
     */
    fun withRecent(destination: Destination): SavedPlaces {
        val others = recents.filterNot { it.coordinate == destination.coordinate }
        return copy(recents = (listOf(destination) + others).take(MAX_RECENTS))
    }

    /** Drops the recent at [destination]'s coordinate; a no-op when there is none. */
    fun withoutRecent(destination: Destination): SavedPlaces =
        copy(recents = recents.filterNot { it.coordinate == destination.coordinate })

    fun withoutRecents(): SavedPlaces = copy(recents = emptyList())

    companion object {
        const val MAX_RECENTS = 20
    }
}
