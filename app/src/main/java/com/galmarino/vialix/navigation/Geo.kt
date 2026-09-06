package com.galmarino.vialix.navigation

import kotlin.math.asin
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

/** Initial bearing from [a] to [b], clockwise degrees from north in `[0, 360)`. */
fun bearingDegrees(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** The point [distanceMeters] away from this one along [bearingDegrees] (great circle). */
fun GeographicCoordinate.moved(distanceMeters: Double, bearingDegrees: Double): GeographicCoordinate {
    val d = distanceMeters / EARTH_RADIUS_METERS
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(lat)
    val lng1 = Math.toRadians(lng)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
    val lng2 = lng1 + atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    val lngDegrees = ((Math.toDegrees(lng2) + 540.0) % 360.0) - 180.0
    return GeographicCoordinate(lat = Math.toDegrees(lat2), lng = lngDegrees)
}
