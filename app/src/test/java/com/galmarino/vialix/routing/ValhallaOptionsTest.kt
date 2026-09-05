package com.galmarino.vialix.routing

import com.galmarino.vialix.settings.DistanceUnits
import com.galmarino.vialix.settings.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
