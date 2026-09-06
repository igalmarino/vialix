// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.UserLocation

/** At or above this speed the GPS course is trusted over the compass (which a car body disturbs). */
const val COURSE_TRUSTED_SPEED_MPS = 1.0

/** [degrees] brought into `[0, 360)`. */
fun normalizeDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

/**
 * Heading of the top edge of the screen. The sensor azimuth is relative to the device's natural
 * orientation; when the display is rotated (landscape), the top of the screen points that much
 * further round.
 */
fun screenHeadingDegrees(azimuthDegrees: Double, displayRotationDegrees: Int): Double =
    normalizeDegrees(azimuthDegrees + displayRotationDegrees)

/**
 * The idle location with the compass heading as its course when the GPS course cannot be trusted:
 * missing (a stationary receiver reports none), or reported while (nearly) standing still. A GPS
 * course while moving wins, because it says where the user is *going* and is immune to the car.
 * The result feeds the puck's heading cone and the heading camera mode; it must not be used as a
 * routing origin, since the Valhalla request turns the course into the start `heading`.
 */
fun UserLocation.withCompassHeading(headingDegrees: Int?): UserLocation {
    if (headingDegrees == null) return this
    val moving = speed?.let { it.value >= COURSE_TRUSTED_SPEED_MPS } ?: true
    if (courseOverGround != null && moving) return this
    val degrees = normalizeDegrees(headingDegrees.toDouble()).roundToInt() % 360
    return copy(courseOverGround = CourseOverGround(degrees.toUShort(), null))
}

/**
 * Exponential smoothing of an angle, done on the unit circle so that 359° → 1° settles on 0°
 * rather than swinging through 180°. [alpha] is the weight of each new sample.
 */
class HeadingSmoother(private val alpha: Double = 0.3) {
    private var x = 0.0
    private var y = 0.0
    private var started = false

    fun update(degrees: Double): Double {
        val radians = Math.toRadians(degrees)
        if (started) {
            x += alpha * (cos(radians) - x)
            y += alpha * (sin(radians) - y)
        } else {
            x = cos(radians)
            y = sin(radians)
            started = true
        }
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }
}
