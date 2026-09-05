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
}
