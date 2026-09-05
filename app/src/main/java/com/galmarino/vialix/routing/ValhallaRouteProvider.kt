package com.galmarino.vialix.routing

import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
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
 */
class ValhallaRouteProvider(
    private val endpoint: String,
    private val settings: StateFlow<Settings>,
    private val httpClient: HttpClientProvider,
) : CustomRouteProvider {

    /** Ferrostar's entry point (reroutes, and the app's Home/Work estimates): one route, no alternates. */
    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        getRoutes(userLocation, waypoints, alternates = 0)

    /**
     * The route preview asks for up to [alternates] extra routes so the user can pick between them.
     * The first route in the response is Valhalla's preferred one.
     */
    suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>, alternates: Int): List<Route> {
        val current = settings.value
        val provider =
            WellKnownRouteProvider.Valhalla(
                endpointUrl = endpoint,
                profile = current.routingProfile,
                optionsJson = optionsJson(current, alternates),
            )

        // The adapter owns a Rust handle; release it once the response is parsed.
        return RouteAdapter.fromWellKnownRouteProvider(provider).use { adapter ->
            val response = httpClient.call(adapter.generateRequest(userLocation, waypoints))
            if (!response.isSuccessful) throw InvalidStatusCodeException(response.code)
            val body = response.bodyBytes() ?: throw NoResponseBodyException()
            adapter.parseResponse(body)
        }
    }

    companion object {
        /** Pure so it can be tested: the Valhalla request options for [settings]; `alternates` only when asked for. */
        fun optionsJson(settings: Settings, alternates: Int): String =
            JSONObject().apply {
                put("units", settings.units.valhallaUnits)
                put("language", settings.resolvedLanguageTag())
                if (alternates > 0) put("alternates", alternates)
            }.toString()
    }
}
