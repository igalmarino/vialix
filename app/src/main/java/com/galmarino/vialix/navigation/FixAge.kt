// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import java.time.Duration
import java.time.Instant
import uniffi.ferrostar.UserLocation

/**
 * Older than this and a fix is no longer where the user is: the idle location flow keeps its last
 * value while the app is in the background, and a route requested from it after a long pause, or a
 * Home/Work set to it, would use a position from a different place.
 */
val MAX_ORIGIN_FIX_AGE: Duration = Duration.ofSeconds(60)

/** Whether this fix is recent enough (as of [now]) to stand for the user's current position. A fix from the future (clock skew) counts as fresh. */
fun UserLocation.isFresh(now: Instant, maxAge: Duration = MAX_ORIGIN_FIX_AGE): Boolean = !timestamp.isBefore(now.minus(maxAge))
