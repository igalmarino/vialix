// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import uniffi.ferrostar.UserLocation

/**
 * Where to draw the user *now*, given the last fix the core has seen.
 *
 * Ferrostar's map view does not draw a fix where it is: `rememberDisplayedNavigationLocation`
 * animates the puck (and the camera following it) from its current position towards each new fix
 * over one second, linearly. The drawn position therefore only reaches a fix about a second after
 * it arrived, and the fix was already a few hundred milliseconds old by then, so on the road the
 * puck trails the car by one to two seconds of travel. The core is left alone (step advance,
 * deviation and arrival keep working on real fixes); only the location handed to the UI is moved
 * ahead along the course by the distance covered in that time, so the animation lands where the
 * user actually is. On the route the view re-projects the result onto the route line, which takes
 * care of corners.
 *
 * Speed and course come from the fix when the provider supplies them, else from the previous fix.
 * Nothing is done below walking pace (the course is noise when stationary), and the shift is
 * capped so a bad speed reading cannot throw the puck down the road.
 */
class DisplayLocationPredictor(
    private val lookaheadSeconds: Double = DISPLAY_ANIMATION_SECONDS,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastFix: UserLocation? = null
    private var lastPrediction: UserLocation? = null

    /** Idempotent for the same [fix], so re-emissions of the same UI state do not move the puck. */
    fun predict(fix: UserLocation): UserLocation {
        if (fix == lastFix) return lastPrediction ?: fix
        val prediction = predictDisplayLocation(fix, lastFix, nowEpochMillis(), lookaheadSeconds)
        lastFix = fix
        lastPrediction = prediction
        return prediction
    }

    /** Forget the previous fix; call between trips so it is not used to derive speed or course. */
    fun reset() {
        lastFix = null
        lastPrediction = null
    }
}

/** Ferrostar's `DISPLAY_LOCATION_ANIMATION_DURATION`: how long the view takes to reach a new fix. */
const val DISPLAY_ANIMATION_SECONDS = 1.0

/** Below this the course is not trustworthy, so the fix is shown where it is. */
const val MIN_PREDICTION_SPEED_MPS = 0.7

/** How stale a fix may be counted as; older ones are treated as this old. */
const val MAX_FIX_AGE_SECONDS = 2.0

/** Upper bound on the shift (about 130 km/h for 2 s): a wild speed reading stays a small error. */
const val MAX_PREDICTION_METERS = 80.0

/** Fix pairs closer together than this in time give no usable speed or course. */
private const val MIN_DERIVATION_INTERVAL_SECONDS = 0.2

/** ... and further apart than this are not consecutive readings of the same movement. */
private const val MAX_DERIVATION_INTERVAL_SECONDS = 5.0

/** Below this distance between two fixes the derived bearing is noise. */
private const val MIN_DERIVATION_DISTANCE_METERS = 3.0

/**
 * [fix] moved along its course by the distance travelled in `(age of the fix) + [lookaheadSeconds]`.
 * Unchanged when there is no usable speed or course, or when the user is (nearly) stationary.
 */
fun predictDisplayLocation(
    fix: UserLocation,
    previousFix: UserLocation?,
    nowEpochMillis: Long,
    lookaheadSeconds: Double = DISPLAY_ANIMATION_SECONDS,
): UserLocation {
    val speed = fix.speed?.value ?: derivedSpeed(previousFix, fix) ?: return fix
    if (speed < MIN_PREDICTION_SPEED_MPS) return fix
    val course = fix.courseOverGround?.degrees?.toDouble() ?: derivedBearing(previousFix, fix) ?: return fix
    val ageSeconds = ((nowEpochMillis - fix.timestamp.toEpochMilli()) / 1000.0).coerceIn(0.0, MAX_FIX_AGE_SECONDS)
    val distance = (speed * (ageSeconds + lookaheadSeconds)).coerceIn(0.0, MAX_PREDICTION_METERS)
    return fix.copy(coordinates = fix.coordinates.moved(distance, course))
}

private fun intervalSeconds(previous: UserLocation, fix: UserLocation): Double? {
    val seconds = (fix.timestamp.toEpochMilli() - previous.timestamp.toEpochMilli()) / 1000.0
    return seconds.takeIf { it in MIN_DERIVATION_INTERVAL_SECONDS..MAX_DERIVATION_INTERVAL_SECONDS }
}

private fun derivedSpeed(previous: UserLocation?, fix: UserLocation): Double? {
    if (previous == null) return null
    val seconds = intervalSeconds(previous, fix) ?: return null
    return distanceMeters(previous.coordinates, fix.coordinates) / seconds
}

private fun derivedBearing(previous: UserLocation?, fix: UserLocation): Double? {
    if (previous == null || intervalSeconds(previous, fix) == null) return null
    if (distanceMeters(previous.coordinates, fix.coordinates) < MIN_DERIVATION_DISTANCE_METERS) return null
    return bearingDegrees(previous.coordinates, fix.coordinates)
}
