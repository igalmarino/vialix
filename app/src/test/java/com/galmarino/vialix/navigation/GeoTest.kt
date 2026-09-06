// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class GeoTest {

    @Test
    fun `distance between two known points`() {
        // Plaza de Mayo to the Obelisco, Buenos Aires: about 1.1 km.
        val plaza = GeographicCoordinate(lat = -34.6083, lng = -58.3712)
        val obelisco = GeographicCoordinate(lat = -34.6037, lng = -58.3816)
        assertEquals(1076.0, distanceMeters(plaza, obelisco), 15.0)
    }

    @Test
    fun `distance to itself is zero`() {
        val point = GeographicCoordinate(lat = 51.5, lng = -0.12)
        assertEquals(0.0, distanceMeters(point, point), 1e-9)
    }

    @Test
    fun `bearing along the cardinal directions`() {
        val here = GeographicCoordinate(lat = 10.0, lng = 20.0)
        assertEquals(0.0, bearingDegrees(here, GeographicCoordinate(lat = 10.1, lng = 20.0)), 1e-6)
        assertEquals(90.0, bearingDegrees(here, GeographicCoordinate(lat = 10.0, lng = 20.1)), 0.02)
        assertEquals(180.0, bearingDegrees(here, GeographicCoordinate(lat = 9.9, lng = 20.0)), 1e-6)
        assertEquals(270.0, bearingDegrees(here, GeographicCoordinate(lat = 10.0, lng = 19.9)), 0.02)
    }

    @Test
    fun `moved point is the requested distance and bearing away`() {
        val here = GeographicCoordinate(lat = -34.6083, lng = -58.3712)
        for (bearing in listOf(0.0, 45.0, 90.0, 200.0, 359.0)) {
            val there = here.moved(250.0, bearing)
            assertEquals(250.0, distanceMeters(here, there), 0.01)
            assertEquals(bearing, bearingDegrees(here, there), 0.01)
        }
    }

    @Test
    fun `moving across the antimeridian wraps the longitude`() {
        val there = GeographicCoordinate(lat = 0.0, lng = 179.9999).moved(100.0, 90.0)
        assertEquals(-179.9992, there.lng, 1e-3)
    }
}
