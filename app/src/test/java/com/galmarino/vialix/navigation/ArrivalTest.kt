// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.TripSummary

class ArrivalTest {

    private val destination = Destination(GeographicCoordinate(lat = 1.0, lng = 2.0), "Somewhere", "1 Some St")

    @Test
    fun `duration is the wall-clock span between start and end`() {
        val summary =
            TripSummary(
                distanceTraveled = 1234.5,
                snappedDistanceTraveled = 1200.0,
                startedAt = Date(1_000_000L),
                endedAt = Date(1_000_000L + 754_000L),
            )
        val arrival = Arrival.of(destination, summary)
        assertEquals(destination, arrival.destination)
        assertEquals(1234.5, arrival.distanceTraveledMeters, 0.0)
        assertEquals(754.0, arrival.durationSeconds, 0.0)
    }

    @Test
    fun `a missing end time yields a zero duration rather than a negative one`() {
        val summary = TripSummary(distanceTraveled = 10.0, snappedDistanceTraveled = 10.0, startedAt = Date(5_000L), endedAt = null)
        val arrival = Arrival.of(null, summary)
        assertNull(arrival.destination)
        assertEquals(0.0, arrival.durationSeconds, 0.0)
    }
}
