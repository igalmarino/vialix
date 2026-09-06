// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonResponseParserTest {

    @Test
    fun `coordinates are longitude first`() {
        val place = parseOne("""{"name":"Brandenburger Tor"}""", "[13.3777, 52.5163]")
        assertEquals(52.5163, place.coordinate.lat, 1e-9)
        assertEquals(13.3777, place.coordinate.lng, 1e-9)
    }

    @Test
    fun `poi keeps its name and gets a full address line`() {
        val place =
            parseOne(
                """{"name":"Hotel Adlon","housenumber":"77","street":"Unter den Linden","postcode":"10117",
                   "city":"Berlin","state":"Berlin","country":"Germany"}""",
            )
        assertEquals("Hotel Adlon", place.name)
        assertEquals("Unter den Linden 77, 10117 Berlin, Germany", place.address)
    }

    @Test
    fun `state is kept when it differs from the city`() {
        val place = parseOne("""{"name":"Marienplatz","city":"München","state":"Bayern","country":"Deutschland"}""")
        assertEquals("München, Bayern, Deutschland", place.address)
    }

    @Test
    fun `address without a name falls back to street and house number`() {
        val place = parseOne("""{"street":"Baker Street","housenumber":"221B","city":"London","country":"United Kingdom"}""")
        assertEquals("Baker Street 221B", place.name)
        assertEquals("London, United Kingdom", place.address)
    }

    @Test
    fun `city without a name is the name and is not repeated in the address`() {
        val place = parseOne("""{"city":"Paris","state":"Île-de-France","country":"France"}""")
        assertEquals("Paris", place.name)
        assertEquals("Île-de-France, France", place.address)
    }

    @Test
    fun `place with only a name has no address`() {
        assertNull(parseOne("""{"name":"Somewhere"}""").address)
    }

    @Test
    fun `features without displayable text or coordinates are skipped`() {
        val json =
            collection(
                feature("""{"osm_key":"place"}""", "[1.0, 2.0]"),
                feature("""{"name":"Short"}""", "[1.0]"),
                feature("""{"name":"Missing"}""", null),
                feature("""{"name":"Kept"}""", "[1.0, 2.0]"),
            )
        assertEquals(listOf("Kept"), PhotonResponseParser.parse(json).map { it.name })
    }

    @Test
    fun `duplicate name and address pairs collapse`() {
        val json =
            collection(
                feature("""{"name":"Berlin","country":"Germany","osm_value":"city"}""", "[13.4, 52.5]"),
                feature("""{"name":"Berlin","country":"Germany","osm_value":"administrative"}""", "[13.41, 52.51]"),
            )
        assertEquals(1, PhotonResponseParser.parse(json).size)
    }

    @Test
    fun `empty collection yields no places`() {
        assertTrue(PhotonResponseParser.parse("""{"type":"FeatureCollection","features":[]}""").isEmpty())
    }

    @Test
    fun `malformed json throws`() {
        assertThrows(JSONException::class.java) { PhotonResponseParser.parse("<html>nope</html>") }
    }

    private fun parseOne(properties: String, coordinates: String = "[13.4, 52.5]"): Place =
        PhotonResponseParser.parse(collection(feature(properties, coordinates))).single()

    private fun feature(properties: String, coordinates: String?): String {
        val geometry = if (coordinates == null) "{\"type\":\"Point\"}" else "{\"type\":\"Point\",\"coordinates\":$coordinates}"
        return """{"type":"Feature","geometry":$geometry,"properties":$properties}"""
    }

    private fun collection(vararg features: String): String = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
