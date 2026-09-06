// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import com.galmarino.vialix.routing.ClientIdInterceptor
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

/** The HTTP half of [PhotonGeocoder]; URL building and response parsing have their own tests. */
class PhotonGeocoderTransportTest {

    private val server = MockWebServer()
    private lateinit var geocoder: PhotonGeocoder

    private val feature =
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[13.405,52.52]},
           "properties":{"name":"Alexanderplatz","city":"Berlin","country":"Germany","osm_id":1,"osm_type":"N"}}]}"""

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(ClientIdInterceptor("com.example.app", "Vialix/test")).build()
        geocoder = PhotonGeocoder(server.url("/api").toString(), client)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a search fetches, identifies the app and parses the answer`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(feature).build())
        val places = geocoder.search("Alexanderplatz", GeographicCoordinate(lat = 52.5, lng = 13.4), "de-DE")

        assertEquals(listOf("Alexanderplatz"), places.map { it.name })
        val request = server.takeRequest()
        assertEquals("/api", request.url.encodedPath)
        assertEquals("Alexanderplatz", request.url.queryParameter("q"))
        assertEquals("de", request.url.queryParameter("lang"))
        assertEquals("com.example.app", request.headers["X-Client-Id"])
        assertEquals("Vialix/test (com.example.app)", request.headers["User-Agent"])
    }

    @Test
    fun `a reverse lookup goes to the reverse endpoint`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(feature).build())
        val place = geocoder.reverse(GeographicCoordinate(lat = 52.52, lng = 13.405), "en-US")
        assertEquals("Alexanderplatz", place?.name)
        assertEquals("/reverse", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `an error status is surfaced as such`() {
        server.enqueue(MockResponse.Builder().code(429).build())
        val e = assertThrows(InvalidStatusCodeException::class.java) { runBlocking { geocoder.search("Berlin", null, null) } }
        assertEquals(429, e.statusCode)
    }

    @Test
    fun `an empty body is an error, not an empty result`() {
        server.enqueue(MockResponse.Builder().body("").build())
        assertThrows(NoResponseBodyException::class.java) { runBlocking { geocoder.search("Berlin", null, null) } }
    }
}
