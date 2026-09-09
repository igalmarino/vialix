// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect.CloseSocket
import mockwebserver3.SocketEffect.Stall
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapStyleLoaderTest {

    private val server = MockWebServer()
    private lateinit var loader: MapStyleLoader

    private val style =
        """{"version":8,"sprite":"https://tiles.example.org/sprites/ofm","glyphs":"https://tiles.example.org/fonts/{fontstack}/{range}.pbf",
           "sources":{"openmaptiles":{"type":"vector","url":"https://tiles.example.org/planet"}},
           "layers":[{"id":"background","type":"background","paint":{"background-color":"#f8f4f0"}}]}"""

    @Before
    fun setUp() {
        server.start()
        loader = MapStyleLoader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun load(night: Boolean) = runBlocking { loader.load(server.url("/styles/liberty").toString(), night) }

    @Test
    fun `a downloaded style comes back patched`() {
        server.enqueue(MockResponse.Builder().body(style).build())
        val patched = load(night = false)
        assertTrue(patched is MapStyleState.Patched)
        val background = JSONObject((patched as MapStyleState.Patched).json).getJSONArray("layers").getJSONObject(0)
        assertEquals("#f8f4f0", background.getJSONObject("paint").getString("background-color"))
    }

    @Test
    fun `the night version recolours the same download without a second request`() {
        server.enqueue(MockResponse.Builder().body(style).build())
        load(night = false)
        val night = load(night = true) as MapStyleState.Patched
        assertEquals(1, server.requestCount)
        val background = JSONObject(night.json).getJSONArray("layers").getJSONObject(0).getJSONObject("paint").getString("background-color")
        assertTrue(background, CssColor.parse(background)!!.lightness < 0.4)
    }

    @Test
    fun `a failed download is retryable`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val url = server.url("/styles/liberty").toString()
        assertEquals(MapStyleState.Unavailable(url, downloadFailed = true), load(night = false))
    }

    @Test
    fun `a style that cannot be inlined is loaded as-is and is not a failure`() {
        server.enqueue(MockResponse.Builder().body("""{"version":8,"sprite":"sprites/ofm","sources":{},"layers":[]}""").build())
        val url = server.url("/styles/liberty").toString()
        assertEquals(MapStyleState.Unavailable(url, downloadFailed = false), load(night = false))
    }

    @Test
    fun `a malformed response is not cached and retry can recover`() {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())
        server.enqueue(MockResponse.Builder().body(style).build())
        val url = server.url("/styles/liberty").toString()

        assertEquals(MapStyleState.Unavailable(url, downloadFailed = false), load(night = false))
        assertTrue(load(night = false) is MapStyleState.Patched)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a connection closed while reading is a retryable download failure`() {
        server.enqueue(
            MockResponse.Builder()
                .body(style)
                .onResponseBody(CloseSocket())
                .build(),
        )
        val url = server.url("/styles/liberty").toString()

        assertEquals(MapStyleState.Unavailable(url, downloadFailed = true), load(night = false))
    }

    @Test
    fun `cancelling a stalled download cancels its HTTP call`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(style)
                .onResponseBody(Stall)
                .build(),
        )
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) {
                loader.load(server.url("/styles/liberty").toString(), night = false)
            }
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)

        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a body that is not JSON is loaded as-is and is not a failure`() {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())
        val url = server.url("/styles/liberty").toString()
        assertEquals(MapStyleState.Unavailable(url, downloadFailed = false), load(night = false))
    }
}
