// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class GeoIntentTest {

    private fun place(lat: Double, lng: Double, name: String? = null) =
        GeoTarget.Place(Destination(GeographicCoordinate(lat = lat, lng = lng), name = name))

    @Test
    fun `a bare coordinate is a place`() {
        assertEquals(place(52.52, 13.405), GeoIntent.parse("geo:52.52,13.405"))
        assertEquals(place(-33.4489, -70.6693), GeoIntent.parse("geo:-33.4489,-70.6693?z=15"))
        assertEquals(place(48.0, 2.0), GeoIntent.parse("GEO:48,2;crs=wgs84"))
    }

    @Test
    fun `q with a coordinate and a label is a named place`() {
        assertEquals(place(52.52, 13.405, "Alexanderplatz"), GeoIntent.parse("geo:0,0?q=52.52,13.405(Alexanderplatz)"))
        assertEquals(place(52.52, 13.405, "Two Words"), GeoIntent.parse("geo:0,0?q=52.52%2C13.405+(Two+Words)"))
    }

    @Test
    fun `q wins over the path`() {
        assertEquals(place(1.0, 2.0), GeoIntent.parse("geo:52.52,13.405?q=1,2"))
    }

    @Test
    fun `free text in q is a search`() {
        assertEquals(GeoTarget.Query("Rue de Rivoli, Paris"), GeoIntent.parse("geo:0,0?q=Rue+de+Rivoli%2C+Paris"))
        assertEquals(GeoTarget.Query("Central Park"), GeoIntent.parse("geo:0,0?z=12&q=Central%20Park"))
    }

    @Test
    fun `google navigation links work the same way`() {
        assertEquals(place(52.52, 13.405), GeoIntent.parse("google.navigation:q=52.52,13.405"))
        assertEquals(GeoTarget.Query("Berlin Hbf"), GeoIntent.parse("google.navigation:q=Berlin+Hbf&mode=d"))
    }

    @Test
    fun `nothing usable is null`() {
        assertNull(GeoIntent.parse("geo:0,0"))
        assertNull(GeoIntent.parse("geo:0,0?q="))
        assertNull(GeoIntent.parse("geo:91,0"))
        assertNull(GeoIntent.parse("geo:0,181"))
        assertNull(GeoIntent.parse("geo:abc"))
        assertNull(GeoIntent.parse("https://example.com/?q=1,2"))
        assertNull(GeoIntent.parse(""))
    }
}
