// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.routing

import android.util.Log
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteAdapter
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WellKnownRouteProvider

/**
 * Valhalla routing that picks up the current [Settings] on every request.
 *
 * Ferrostar's built-in path (`WellKnownRouteProvider.Valhalla` passed to `FerrostarCore`) bakes the
 * profile and the JSON options into a native `RouteAdapter` at construction, and `FerrostarCore`
 * keeps that adapter for life. Implementing [CustomRouteProvider] instead lets the profile,
 * `units` and `language` follow the settings screen without ever rebuilding the core — reroutes
 * included, since the core calls this for those too. The request generation and response parsing
 * are still Ferrostar's own; only the adapter is created per request.
 *
 * Reroutes go through [rerouteHttpClient], which the graph gives a shorter call timeout than the
 * shared client: the core makes no other attempt while a reroute is pending, so a request that hangs
 * costs the whole timeout plus the core's cooldown before the next one.
 */
class ValhallaRouteProvider(
    private val endpoint: String,
    private val settings: StateFlow<Settings>,
    private val httpClient: HttpClientProvider,
    private val rerouteHttpClient: HttpClientProvider = httpClient,
) : CustomRouteProvider {

    /**
     * Ferrostar's entry point, called by the core for reroutes: one route, no alternates. The core
     * only logs a failure under its own tag and drops it, so the outcome is logged here too; the
     * detail (which may include the endpoint) stays in logcat.
     */
    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
        val startedAt = System.nanoTime()
        return try {
            fetch(userLocation, waypoints, alternates = 0, client = rerouteHttpClient).also {
                Log.i(TAG, "Reroute request took ${(System.nanoTime() - startedAt) / 1_000_000} ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reroute request failed", e)
            throw e
        }
    }

    /**
     * The route preview asks for up to [alternates] extra routes so the user can pick between them.
     * The first route in the response is Valhalla's preferred one.
     */
    suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>, alternates: Int): List<Route> =
        fetch(userLocation, waypoints, alternates, client = httpClient)

    private suspend fun fetch(
        userLocation: UserLocation,
        waypoints: List<Waypoint>,
        alternates: Int,
        client: HttpClientProvider,
    ): List<Route> {
        val current = settings.value
        val provider =
            WellKnownRouteProvider.Valhalla(
                endpointUrl = endpoint,
                profile = current.routingProfile,
                optionsJson = optionsJson(current, alternates),
            )

        // The adapter owns a Rust handle; release it once the response is parsed.
        return RouteAdapter.fromWellKnownRouteProvider(provider).use { adapter ->
            val response = client.call(adapter.generateRequest(userLocation, waypoints))
            // Read the body before looking at the status: in Ferrostar's OkHttp wrapper `bodyBytes()`
            // is the only thing that closes the response, and an unread error reply (429/503 from
            // the shared public server) would otherwise keep a pooled connection open.
            val body = response.bodyBytes()
            if (!response.isSuccessful) throw InvalidStatusCodeException(response.code)
            adapter.parseResponse(body ?: throw NoResponseBodyException())
        }
    }

    companion object {
        private const val TAG = "ValhallaRouteProvider"

        /** Pure so it can be tested: the Valhalla request options for [settings]; `alternates` only when asked for. */
        fun optionsJson(settings: Settings, alternates: Int): String = JSONObject().apply {
            put("units", settings.units.valhallaUnits)
            put("language", settings.resolvedLanguageTag())
            if (alternates > 0) put("alternates", alternates)
        }.toString()
    }
}
