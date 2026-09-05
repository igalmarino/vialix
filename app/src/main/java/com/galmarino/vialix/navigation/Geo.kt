package com.galmarino.vialix.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import uniffi.ferrostar.GeographicCoordinate

private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Great-circle distance in metres (haversine). Plenty accurate for "did we move far enough". */
fun distanceMeters(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}
