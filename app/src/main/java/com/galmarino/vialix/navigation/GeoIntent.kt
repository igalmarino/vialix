// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import java.net.URLDecoder
import uniffi.ferrostar.GeographicCoordinate

/** What another app asked for through a `geo:` (or `google.navigation:`) link. */
sealed interface GeoTarget {
    /** A point, optionally labelled: route there. */
    data class Place(val destination: Destination) : GeoTarget

    /** Free text: search for it. */
    data class Query(val text: String) : GeoTarget
}

/**
 * Parses the URIs a navigation app is offered by other apps (a "Directions" button, a link in a
 * message): `geo:lat,lng`, `geo:lat,lng?z=17`, `geo:0,0?q=lat,lng(Label)`, `geo:0,0?q=Some+Street`
 * and `google.navigation:q=...`. Pure, so the shapes are unit-tested.
 */
object GeoIntent {
    private val coordinatePattern = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\(([^)]*)\))?\s*$""")

    /** `null` when the URI is neither scheme or carries nothing usable. */
    fun parse(uri: String): GeoTarget? {
        val (path, query) =
            when {
                uri.startsWith(GEO_SCHEME, ignoreCase = true) -> uri.substring(GEO_SCHEME.length).split('?', limit = 2).let {
                    it[0] to
                        it.getOrNull(1)
                }

                // The whole tail is a query string: google.navigation:q=...
                uri.startsWith(NAVIGATION_SCHEME, ignoreCase = true) -> "" to uri.substring(NAVIGATION_SCHEME.length)

                else -> return null
            }
        val q = queryParameter(query, "q")?.trim()?.takeIf { it.isNotEmpty() }
        if (q != null) {
            // `q` wins over the path: apps put 0,0 in the path and the real target in q.
            return place(q) ?: GeoTarget.Query(q)
        }
        // "lat,lng;crs=wgs84": parameters after the coordinates are ignored.
        return place(path.substringBefore(';'))
    }

    private fun place(text: String): GeoTarget.Place? {
        val match = coordinatePattern.matchEntire(text) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        // 0,0 is the placeholder apps use when only `q` carries the target, not a destination.
        if (lat == 0.0 && lng == 0.0) return null
        val label = match.groupValues[3].trim().takeIf { it.isNotEmpty() }
        return GeoTarget.Place(Destination(GeographicCoordinate(lat = lat, lng = lng), name = label))
    }

    private fun queryParameter(query: String?, name: String): String? {
        if (query == null) return null
        return query.split('&').firstNotNullOfOrNull { pair ->
            val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
            if (key == name && value != null) URLDecoder.decode(value, "UTF-8") else null
        }
    }

    private const val GEO_SCHEME = "geo:"
    private const val NAVIGATION_SCHEME = "google.navigation:"
}
