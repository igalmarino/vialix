package com.galmarino.vialix.ui

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class FormattersTest {

    private lateinit var previousLocale: Locale

    @Before
    fun pinLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `coordinates use five decimals`() {
        assertEquals("52.52000, 13.40500", formatCoordinates(GeographicCoordinate(lat = 52.52, lng = 13.405)))
    }

    @Test
    fun `coordinates keep a decimal point in comma-decimal locales`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("52.52000, 13.40500", formatCoordinates(GeographicCoordinate(lat = 52.52, lng = 13.405)))
    }

    @Test
    fun `pin geojson is longitude first`() {
        val json = pointFeatureCollectionJson(GeographicCoordinate(lat = 52.52, lng = 13.405))
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[13.405,52.52]},"properties":{}}]}""",
            json,
        )
    }
}
