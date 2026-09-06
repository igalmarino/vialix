package com.galmarino.vialix.navigation

import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/** How the user travels, as far as route following is concerned. */
enum class TravelMode {
    DRIVING,
    CYCLING,
    WALKING;

    companion object {
        /**
         * The mode a Valhalla costing model implies for the three offered profiles (`auto`, `bicycle`,
         * `pedestrian`); anything unknown (`motorcycle`, `truck`, `bus`, ...) is treated as driving.
         */
        fun forProfile(profile: String): TravelMode =
            when (profile.trim().lowercase()) {
                "auto" -> DRIVING
                "pedestrian" -> WALKING
                "bicycle" -> CYCLING
                else -> DRIVING
            }
    }
}

/**
 * Tuning for how the navigation core follows a route. The core takes one of these per trip
 * (`FerrostarCore.startNavigation(route, config)`), so the thresholds follow the routing profile
 * the route was requested with.
 */
object NavigationControllerConfigs {

    fun forProfile(profile: String): NavigationControllerConfig = forMode(TravelMode.forProfile(profile))

    fun forMode(mode: TravelMode): NavigationControllerConfig =
        when (mode) {
            TravelMode.DRIVING -> driving()
            TravelMode.CYCLING -> cycling()
            TravelMode.WALKING -> walking()
        }

    /**
     * Sensible defaults for driving, taken from Ferrostar's reference configuration.
     *
     * - A waypoint counts as reached within 100 m.
     * - Advance to the next step once the user is within 30 m of the manoeuvre and has moved at
     *   least 5 m past it (tolerating 32 m of horizontal GPS error); on the final step, arrive
     *   when within 10 m of the end.
     * - Consider the user off route 30 m from the current step's line, judged only on fixes with a
     *   horizontal accuracy of 40 m or better (`StaticThreshold` takes the accuracy gate first, the
     *   deviation second). Ferrostar's demo uses a 15 m gate and 50 m of deviation; a phone in a car
     *   reports 15-40 m accuracy under trees and between buildings, and with the demo values those
     *   fixes were skipped outright, so a missed turn often never triggered a reroute. The gate is
     *   deliberately wider than the deviation: a noisy fix can now cause a spurious reroute (one
     *   request, and the spoken queue is cleared), which is the lesser evil.
     * - Snap the reported course to the route so the puck does not jitter.
     */
    fun driving(): NavigationControllerConfig =
        NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(100.0),
            stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
            stepAdvanceDistanceToEndOfStep(10u, 32u),
            RouteDeviationTracking.StaticThreshold(minimumHorizontalAccuracy = 40u, maxAcceptableDeviation = 30.0),
            CourseFiltering.SNAP_TO_ROUTE,
        )

    /**
     * Slower and closer to junctions than a car: tighter step advance and deviation thresholds,
     * with the accuracy gate relaxed to 30 m because a handlebar phone rarely does better in town.
     * Starting points; tune on the road.
     */
    fun cycling(): NavigationControllerConfig =
        NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(50.0),
            stepAdvanceDistanceEntryAndExit(20u, 3u, 25u),
            stepAdvanceDistanceToEndOfStep(8u, 25u),
            RouteDeviationTracking.StaticThreshold(minimumHorizontalAccuracy = 30u, maxAcceptableDeviation = 20.0),
            CourseFiltering.SNAP_TO_ROUTE,
        )

    /**
     * Walking pace: manoeuvres are a few metres apart and a pedestrian who crosses the street is
     * not off route, so the deviation threshold stays generous relative to the step thresholds; a
     * phone in a pocket seldom reports better than 20-30 m, hence the 30 m accuracy gate.
     * Starting points; tune on the pavement.
     */
    fun walking(): NavigationControllerConfig =
        NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(25.0),
            stepAdvanceDistanceEntryAndExit(10u, 2u, 20u),
            stepAdvanceDistanceToEndOfStep(5u, 20u),
            RouteDeviationTracking.StaticThreshold(minimumHorizontalAccuracy = 30u, maxAcceptableDeviation = 25.0),
            CourseFiltering.SNAP_TO_ROUTE,
        )
}
