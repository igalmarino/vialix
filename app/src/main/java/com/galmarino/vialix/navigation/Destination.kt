package com.galmarino.vialix.navigation

import com.galmarino.vialix.search.Place
import uniffi.ferrostar.GeographicCoordinate

/**
 * Where the user wants to go: a long-pressed point ([name] and [address] null) or a picked search
 * result. Compared by value, which is what the route-preview code relies on to drop stale replies.
 */
data class Destination(
    val coordinate: GeographicCoordinate,
    val name: String? = null,
    val address: String? = null,
) {
    companion object {
        fun of(place: Place): Destination = Destination(place.coordinate, place.name, place.address)
    }
}
