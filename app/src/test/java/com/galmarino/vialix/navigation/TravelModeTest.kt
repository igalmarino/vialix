package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TravelModeTest {

    @Test
    fun `the three offered profiles map to their modes`() {
        assertEquals(TravelMode.DRIVING, TravelMode.forProfile("auto"))
        assertEquals(TravelMode.CYCLING, TravelMode.forProfile("bicycle"))
        assertEquals(TravelMode.WALKING, TravelMode.forProfile("pedestrian"))
    }

    @Test
    fun `other Valhalla costings and odd spellings fall back to driving`() {
        assertEquals(TravelMode.DRIVING, TravelMode.forProfile("truck"))
        assertEquals(TravelMode.DRIVING, TravelMode.forProfile("motorcycle"))
        assertEquals(TravelMode.DRIVING, TravelMode.forProfile(""))
        assertEquals(TravelMode.WALKING, TravelMode.forProfile(" Pedestrian "))
    }
}
