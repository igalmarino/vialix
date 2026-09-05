package com.galmarino.vialix

import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionTest {

    @Test
    fun `the default servers are credited by name`() {
        assertEquals("Valhalla (FOSSGIS)", Attribution.creditFor(NavConfig.DEFAULT_VALHALLA_ENDPOINT))
        assertEquals("Photon (komoot)", Attribution.creditFor(NavConfig.DEFAULT_GEOCODER_ENDPOINT))
    }

    @Test
    fun `a hosted provider is credited even with a key in the URL`() {
        assertEquals("Stadia Maps", Attribution.creditFor("https://api.stadiamaps.com/route/v1?api_key=secret"))
    }

    @Test
    fun `an unknown host is credited by host name without port or path`() {
        assertEquals("10.0.2.2", Attribution.creditFor("http://10.0.2.2:8002/route"))
        assertEquals("routing.example.org", Attribution.creditFor("https://Routing.Example.org/valhalla/route"))
    }
}
