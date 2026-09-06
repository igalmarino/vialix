// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.routing

import com.galmarino.vialix.settings.DistanceUnits
import com.galmarino.vialix.settings.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaOptionsTest {

    private val settings = Settings(languageTag = "de-DE", units = DistanceUnits.IMPERIAL)

    @Test
    fun `units and language follow the settings`() {
        val json = JSONObject(ValhallaRouteProvider.optionsJson(settings, alternates = 0))
        assertEquals("miles", json.getString("units"))
        assertEquals("de-DE", json.getString("language"))
        assertFalse(json.has("alternates"))
    }

    @Test
    fun `alternates are requested only for the preview`() {
        assertEquals(2, JSONObject(ValhallaRouteProvider.optionsJson(settings, alternates = 2)).getInt("alternates"))
    }

    @Test
    fun `metric units and the device language when nothing is chosen`() {
        val json = JSONObject(ValhallaRouteProvider.optionsJson(Settings(languageTag = null, units = DistanceUnits.METRIC), alternates = 0))
        assertEquals("kilometers", json.getString("units"))
        // Whatever the device locale resolves to, it is one Valhalla knows.
        assertTrue(json.getString("language") in com.galmarino.vialix.voice.SpokenLanguage.SUPPORTED_TAGS)
    }

    @Test
    fun `nothing but the three options is sent`() {
        val json = JSONObject(ValhallaRouteProvider.optionsJson(settings, alternates = 1))
        assertEquals(setOf("units", "language", "alternates"), json.keys().asSequence().toSet())
    }
}
