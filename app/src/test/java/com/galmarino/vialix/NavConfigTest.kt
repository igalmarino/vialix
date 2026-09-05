package com.galmarino.vialix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavConfigTest {

    @Test
    fun `blank build values fall back to defaults`() {
        val config =
            NavConfig.fromValues(
                valhallaEndpoint = "",
                routingProfile = "  ",
                mapStyleUrl = null,
                mapStyleUrlDark = "",
                clientId = "",
                voiceGuidance = "",
                geocoderEndpoint = null,
            )

        assertEquals(NavConfig.DEFAULT_VALHALLA_ENDPOINT, config.valhallaEndpoint)
        assertEquals(NavConfig.DEFAULT_ROUTING_PROFILE, config.routingProfile)
        assertEquals(NavConfig.DEFAULT_MAP_STYLE_URL, config.mapStyleUrl)
        assertNull(config.mapStyleUrlDark)
        assertEquals(NavConfig.DEFAULT_CLIENT_ID, config.clientId)
        assertEquals(NavConfig.DEFAULT_VOICE_GUIDANCE, config.voiceGuidance)
        assertEquals(NavConfig.DEFAULT_GEOCODER_ENDPOINT, config.geocoderEndpoint)
    }

    @Test
    fun `overrides win over defaults and are trimmed`() {
        val config =
            NavConfig.fromValues(
                valhallaEndpoint = " https://api.stadiamaps.com/route/v1?api_key=k ",
                routingProfile = "bicycle",
                mapStyleUrl = "https://demotiles.maplibre.org/style.json",
                mapStyleUrlDark = " https://tiles.example.org/styles/night ",
                clientId = "example.app",
                voiceGuidance = " FALSE ",
                geocoderEndpoint = " https://photon.example.org/api ",
            )

        assertEquals("https://api.stadiamaps.com/route/v1?api_key=k", config.valhallaEndpoint)
        assertEquals("bicycle", config.routingProfile)
        assertEquals("https://demotiles.maplibre.org/style.json", config.mapStyleUrl)
        assertEquals("https://tiles.example.org/styles/night", config.mapStyleUrlDark)
        assertEquals("example.app", config.clientId)
        assertFalse(config.voiceGuidance)
        assertEquals("https://photon.example.org/api", config.geocoderEndpoint)
    }

    @Test
    fun `unrecognised voice guidance value falls back to the default`() {
        val config = voiceGuidance("yes please")
        assertEquals(NavConfig.DEFAULT_VOICE_GUIDANCE, config.voiceGuidance)
    }

    @Test
    fun `voice guidance is case-insensitive`() {
        assertTrue(voiceGuidance("TRUE").voiceGuidance)
        assertFalse(voiceGuidance("False").voiceGuidance)
    }

    @Test
    fun `self-hosted plain http endpoint is accepted`() {
        val config = NavConfig(valhallaEndpoint = "http://10.0.2.2:8002/route")
        assertEquals("http://10.0.2.2:8002/route", config.valhallaEndpoint)
    }

    @Test
    fun `non-url endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NavConfig(valhallaEndpoint = "valhalla1.openstreetmap.de") }
    }

    @Test
    fun `non-url style is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NavConfig(mapStyleUrl = "liberty") }
    }

    @Test
    fun `non-url dark style is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NavConfig(mapStyleUrlDark = "dark") }
    }

    @Test
    fun `a configured dark style is used as-is`() {
        val config = NavConfig(mapStyleUrl = "https://tiles.example.org/day", mapStyleUrlDark = "https://tiles.example.org/night")
        assertEquals("https://tiles.example.org/day", config.mapStyleUrlFor(dark = false))
        assertEquals("https://tiles.example.org/night", config.mapStyleUrlFor(dark = true))
        assertFalse(config.derivesNightStyle(dark = true))
        assertFalse(config.derivesNightStyle(dark = false))
    }

    @Test
    fun `without a dark style the dark theme derives a night style from the light one`() {
        val config = NavConfig(mapStyleUrl = "https://tiles.example.org/day")
        assertEquals("https://tiles.example.org/day", config.mapStyleUrlFor(dark = false))
        assertEquals("https://tiles.example.org/day", config.mapStyleUrlFor(dark = true))
        assertTrue(config.derivesNightStyle(dark = true))
        assertFalse(config.derivesNightStyle(dark = false))
    }

    @Test
    fun `non-url geocoder endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NavConfig(geocoderEndpoint = "photon.komoot.io") }
    }

    private fun voiceGuidance(value: String?): NavConfig =
        NavConfig.fromValues(
            valhallaEndpoint = null,
            routingProfile = null,
            mapStyleUrl = null,
            mapStyleUrlDark = null,
            clientId = null,
            voiceGuidance = value,
            geocoderEndpoint = null,
        )
}
