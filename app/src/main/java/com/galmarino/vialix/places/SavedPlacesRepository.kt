// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.places

import com.galmarino.vialix.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One string in, one string out: the only thing [SavedPlacesRepository] needs from the platform.
 * The app binds it to `SharedPreferences` ([SharedPreferencesKeyValueStore]); tests use a map.
 */
interface KeyValueStore {
    fun read(): String?

    fun write(value: String)
}

/**
 * Home/Work shortcuts and recent destinations, persisted as one JSON blob. Consumers observe
 * [state]; every write goes through the setters so the in-memory value and the stored one cannot
 * diverge.
 *
 * Deliberately not Room: the data is a handful of rows, the project has no annotation processing
 * yet, and the dependency set is kept mirroring Ferrostar's.
 */
class SavedPlacesRepository(private val store: KeyValueStore) {

    private val _state = MutableStateFlow(SavedPlacesCodec.decode(store.read()))
    val state: StateFlow<SavedPlaces> = _state.asStateFlow()

    /** Called whenever guidance starts; keeps the newest [SavedPlaces.MAX_RECENTS]. */
    fun addRecent(destination: Destination) = update { withRecent(destination) }

    /** Long-press on a recent: forget that one place. */
    fun removeRecent(destination: Destination) = update { withoutRecent(destination) }

    fun clearRecents() = update { withoutRecents() }

    fun setFavorite(kind: FavoriteKind, destination: Destination) = update { withFavorite(kind, destination) }

    fun clearFavorite(kind: FavoriteKind) = update { withFavorite(kind, null) }

    private fun update(transform: SavedPlaces.() -> SavedPlaces) {
        _state.update { it.transform() }
        store.write(SavedPlacesCodec.encode(_state.value))
    }
}
