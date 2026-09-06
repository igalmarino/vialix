// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

/** A step of [distance] metres taking [duration] seconds (a tenth of the distance unless given). */
internal fun testStep(distance: Double, roadName: String? = null, duration: Double = distance / 10) = RouteStep(
    geometry = emptyList(),
    distance = distance,
    duration = duration,
    roadName = roadName,
    exits = emptyList(),
    instruction = "Continue",
    visualInstructions = emptyList(),
    spokenInstructions = emptyList(),
    annotations = null,
    incidents = emptyList(),
    drivingSide = DrivingSide.RIGHT,
    roundaboutExitNumber = null,
)

internal fun testRoute(vararg steps: RouteStep): Route {
    val origin = GeographicCoordinate(lat = 0.0, lng = 0.0)
    return Route(
        geometry = listOf(origin),
        bbox = BoundingBox(sw = origin, ne = origin),
        distance = steps.sumOf { it.distance },
        waypoints = emptyList(),
        steps = steps.toList(),
    )
}
