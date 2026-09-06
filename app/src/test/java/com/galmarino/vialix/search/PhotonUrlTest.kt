// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class PhotonUrlTest {

    private val endpoint = "https://photon.komoot.io/api"
    private val berlin = GeographicCoordinate(lat = 52.52, lng = 13.405)

    @Test
    fun `all parameters are set when bias and language are known`() {
        val url = PhotonGeocoder.buildUrl(endpoint, "Alexanderplatz", berlin, "de", 8)

        assertEquals("photon.komoot.io", url.host)
        assertEquals("/api", url.encodedPath)
        assertEquals("Alexanderplatz", url.queryParameter("q"))
        assertEquals("8", url.queryParameter("limit"))
        assertEquals("de", url.queryParameter("lang"))
        assertEquals("52.52", url.queryParameter("lat"))
        assertEquals("13.405", url.queryParameter("lon"))
    }

    @Test
    fun `bias and language are omitted when unknown`() {
        val url = PhotonGeocoder.buildUrl(endpoint, "Alexanderplatz", null, null, 8)

        assertNull(url.queryParameter("lat"))
        assertNull(url.queryParameter("lon"))
        assertNull(url.queryParameter("lang"))
    }

    @Test
    fun `query is percent-encoded once`() {
        val url = PhotonGeocoder.buildUrl(endpoint, "Café & Bar, München", null, null, 8)

        assertEquals("Café & Bar, München", url.queryParameter("q"))
        assertEquals("Caf%C3%A9%20%26%20Bar%2C%20M%C3%BCnchen", url.encodedQuery?.substringAfter("q=")?.substringBefore("&limit"))
    }

    @Test
    fun `endpoint with its own query string keeps it`() {
        val url = PhotonGeocoder.buildUrl("https://photon.example.org/api?api_key=k", "x", null, null, 5)

        assertEquals("k", url.queryParameter("api_key"))
        assertEquals("x", url.queryParameter("q"))
        assertEquals("5", url.queryParameter("limit"))
    }

    @Test
    fun `reverse lookup swaps the api segment for reverse and keeps the key`() {
        val url = PhotonGeocoder.buildReverseUrl("https://photon.example.org/api?api_key=k", berlin, "de")

        assertEquals("/reverse", url.encodedPath)
        assertEquals("k", url.queryParameter("api_key"))
        assertEquals("52.52", url.queryParameter("lat"))
        assertEquals("13.405", url.queryParameter("lon"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals("de", url.queryParameter("lang"))
    }

    @Test
    fun `reverse lookup on a bare host or a trailing slash appends the segment`() {
        assertEquals("/reverse", PhotonGeocoder.buildReverseUrl("https://photon.example.org", berlin, null).encodedPath)
        assertEquals("/photon/reverse", PhotonGeocoder.buildReverseUrl("https://example.org/photon/", berlin, null).encodedPath)
        assertNull(PhotonGeocoder.buildReverseUrl(endpoint, berlin, null).queryParameter("lang"))
    }
}
