package com.galmarino.vialix.navigation

import uniffi.ferrostar.Route

/** Total travel time in seconds; Ferrostar only exposes it per step. */
val Route.durationSeconds: Double
    get() = steps.sumOf { it.duration }
