package com.galmarino.vialix.navigation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How eagerly the app asks for a new route once the core reports the user off route. The
 * deviation thresholds themselves are in [NavigationControllerConfigs]; these are the knobs on
 * `FerrostarCore` and on the HTTP call that follow.
 *
 * Ferrostar's defaults (5 s, 50 m) are conservative in two ways that made rerouting look broken:
 * the cooldown counts from the *end* of the previous request, and the movement gate measures from
 * where the previous request *started* and is never cleared when that request fails. A slow or
 * failed request therefore blocked the next attempt for the whole timeout plus the cooldown, or
 * until the car had moved 50 m past the point of failure.
 */
object RerouteTuning {
    /** Minimum pause between two reroute requests. The public server asks for about one request per second. */
    val COOLDOWN: Duration = 3.seconds

    /** How far the user must have moved since the previous request before another one is made. */
    const val MIN_MOVEMENT_METERS = 20.0

    /**
     * Call timeout for a reroute request, shorter than the shared client's 15 s: while it is
     * pending the core makes no other attempt, and a stale reroute is worth less than a fresh one
     * from where the car is now.
     */
    val REQUEST_TIMEOUT: Duration = 8.seconds
}
