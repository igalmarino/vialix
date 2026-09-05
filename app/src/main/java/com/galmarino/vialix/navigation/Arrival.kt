package com.galmarino.vialix.navigation

import uniffi.ferrostar.TripSummary

/**
 * What the "You've arrived" card shows once Ferrostar reports `TripState.Complete`: where the
 * trip went and what it took. A plain value derived from Ferrostar's [TripSummary] so the
 * arithmetic (and the missing-end-time case) is testable without Android.
 */
data class Arrival(
    /** The destination guidance was started for; null when it is no longer known. */
    val destination: Destination?,
    val distanceTraveledMeters: Double,
    /** Wall-clock time from the start of guidance to arrival, in seconds; 0 when unknown. */
    val durationSeconds: Double,
) {
    companion object {
        fun of(destination: Destination?, summary: TripSummary): Arrival {
            val startedAt = summary.startedAt.time
            val endedAt = summary.endedAt?.time ?: startedAt
            return Arrival(
                destination = destination,
                distanceTraveledMeters = summary.distanceTraveled,
                durationSeconds = ((endedAt - startedAt) / 1000.0).coerceAtLeast(0.0),
            )
        }
    }
}
