// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.places

import com.galmarino.vialix.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class SavedPlacesCodecTest {

    private fun place(n: Int, name: String? = "Place $n", address: String? = "Street $n") =
        Destination(GeographicCoordinate(lat = 10.0 + n, lng = 20.0 + n), name, address)

    @Test
    fun `encode then decode is the identity`() {
        val places =
            SavedPlaces(
                home = place(1),
                work = place(2, name = null, address = null),
                recents = listOf(place(3), place(4, address = null), place(5, name = null)),
            )
        assertEquals(places, SavedPlacesCodec.decode(SavedPlacesCodec.encode(places)))
    }

    @Test
    fun `unset favourites and no recents survive the round trip`() {
        assertEquals(SavedPlaces(), SavedPlacesCodec.decode(SavedPlacesCodec.encode(SavedPlaces())))
    }

    @Test
    fun `decoding caps the recents at the maximum`() {
        val tooMany = SavedPlaces(recents = (1..SavedPlaces.MAX_RECENTS + 5).map { place(it) })
        // Encoded directly (the value's own withRecent would already have trimmed it).
        val decoded = SavedPlacesCodec.decode(SavedPlacesCodec.encode(tooMany))
        assertEquals(SavedPlaces.MAX_RECENTS, decoded.recents.size)
        assertEquals(tooMany.recents.take(SavedPlaces.MAX_RECENTS), decoded.recents)
    }

    @Test
    fun `an explicit JSON null or empty name reads as no name`() {
        val decoded =
            SavedPlacesCodec.decode(
                """{"version":1,"home":{"lat":1.0,"lng":2.0,"name":null,"address":""},"recents":[]}""",
            )
        val home = decoded.home!!
        assertEquals(GeographicCoordinate(lat = 1.0, lng = 2.0), home.coordinate)
        assertNull(home.name)
        assertNull(home.address)
    }

    @Test
    fun `unknown fields are ignored and a missing recents list is empty`() {
        val decoded = SavedPlacesCodec.decode("""{"version":7,"future":true,"work":{"lat":3.0,"lng":4.0,"extra":1}}""")
        assertEquals(SavedPlaces(work = Destination(GeographicCoordinate(lat = 3.0, lng = 4.0))), decoded)
    }

    @Test
    fun `garbage decodes to nothing rather than throwing`() {
        assertEquals(SavedPlaces(), SavedPlacesCodec.decode("not json"))
        assertEquals(SavedPlaces(), SavedPlacesCodec.decode("[]"))
        assertEquals(SavedPlaces(), SavedPlacesCodec.decode(null))
        assertEquals(SavedPlaces(), SavedPlacesCodec.decode("   "))
    }
}
