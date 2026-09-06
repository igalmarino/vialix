// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

import android.content.Context
import android.util.Log
import com.galmarino.vialix.location.CompassHeadingProvider
import com.galmarino.vialix.location.FusedLocationProvider
import com.galmarino.vialix.map.MapStyleController
import com.galmarino.vialix.map.MapStyleLoader
import com.galmarino.vialix.navigation.NavigationControllerConfigs
import com.galmarino.vialix.navigation.RerouteTuning
import com.galmarino.vialix.places.SavedPlacesRepository
import com.galmarino.vialix.places.SharedPreferencesKeyValueStore
import com.galmarino.vialix.routing.ClientIdInterceptor
import com.galmarino.vialix.routing.ValhallaRouteProvider
import com.galmarino.vialix.search.Geocoder
import com.galmarino.vialix.search.PhotonGeocoder
import com.galmarino.vialix.settings.NavSettings
import com.galmarino.vialix.voice.VoiceGuidance
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AlternativeRouteProcessor
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.isNavigating
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import java.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import uniffi.ferrostar.Route

/**
 * Hand-rolled dependency graph for the app. Small enough that a DI framework would add more
 * ceremony than it removes; swap in Hilt/Koin later if the app grows.
 *
 * Everything here is application-scoped and created lazily on first use.
 */
class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    val config: NavConfig = NavConfig.fromBuildConfig()

    /** Runtime settings; [config] only supplies their first-launch defaults. */
    val settings: NavSettings by lazy { NavSettings(appContext, config) }

    /** Home/Work shortcuts and recent destinations shown in the home sheet. */
    val savedPlaces: SavedPlacesRepository by lazy {
        SavedPlacesRepository(SharedPreferencesKeyValueStore(appContext, PLACES_PREFS, PLACES_KEY))
    }

    /** For the few collectors that must outlive any screen (settings -> TTS, the reroute swap). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A reroute waiting for the "Rerouting" announcement to finish before it is swapped in. Main thread only. */
    private var pendingRouteSwap: Job? = null

    /**
     * Live positions come from Google Play's fused location provider when Play Services is on the
     * device ([FusedLocationProvider.create] is `null` otherwise) and from the platform
     * `LocationManager` via Ferrostar's [AndroidLocationProvider] on GMS-free ROMs. The simulated
     * provider is only switched on from the debug-only "Simulate driving" switch in Settings >
     * Developer.
     */
    val locationProvider: NavigationLocationProvider by lazy {
        NavigationLocationProvider(
            liveProviding = FusedLocationProvider.create(appContext) ?: AndroidLocationProvider(appContext),
            simulatedProvider = SimulatedLocationProvider(warpFactor = 2u),
        )
    }

    /** Compass for the idle puck and the heading camera while standing still; `null` without the sensor. */
    val compass: CompassHeadingProvider? by lazy { CompassHeadingProvider.create(appContext) }

    /** Shared by routing and geocoding: one connection pool, one timeout, one set of identifying headers. */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(15))
            .addInterceptor(ClientIdInterceptor(config.clientId, "Vialix/${BuildConfig.VERSION_NAME}"))
            .build()
    }

    private val httpClient: HttpClientProvider by lazy { okHttpClient.toOkHttpClientProvider() }

    /** Same pool and headers, shorter timeout: see [RerouteTuning.REQUEST_TIMEOUT]. */
    private val rerouteHttpClient: HttpClientProvider by lazy {
        okHttpClient.newBuilder().callTimeout(RerouteTuning.REQUEST_TIMEOUT.toJavaDuration()).build().toOkHttpClientProvider()
    }

    /** Forward geocoding for the destination search. */
    val geocoder: Geocoder by lazy { PhotonGeocoder(config.geocoderEndpoint, okHttpClient) }

    /** The basemap for the current theme, patched before MapLibre sees it; the Activity pushes the theme in. */
    val mapStyle: MapStyleController by lazy { MapStyleController(config, MapStyleLoader(okHttpClient), appScope) }

    private val foregroundServiceManager: ForegroundServiceManager by lazy {
        FerrostarForegroundServiceManager(appContext, DefaultForegroundNotificationBuilder(appContext))
    }

    /**
     * Speaks the manoeuvre announcements that come back with every route. Built here rather than
     * in the ViewModel because Ferrostar's `DefaultNavigationViewModel` captures the observer's
     * mute flow once, in its constructor: attach it later and `NavigationUiState.isMuted` stays
     * null and Ferrostar hides its mute button.
     */
    val voiceGuidance: VoiceGuidance by lazy { VoiceGuidance(appContext, settings.state, appScope) }

    /**
     * Routing. Reads profile, units and language from `settings` on every request; see the class
     * docs for why Ferrostar's built-in Valhalla provider cannot do that. The ViewModel calls it
     * directly for the route preview (with alternates); the core calls it for reroutes.
     */
    val routeProvider: ValhallaRouteProvider by lazy {
        ValhallaRouteProvider(config.valhallaEndpoint, settings.state, httpClient, rerouteHttpClient)
    }

    /** The navigation engine: route requests, route following, deviation detection, rerouting. */
    val ferrostarCore: FerrostarCore by lazy {
        FerrostarCore(
            customRouteProvider = routeProvider,
            httpClient = httpClient,
            locationProvider = locationProvider,
            navigationControllerConfig = NavigationControllerConfigs.driving(),
            foregroundServiceManager = foregroundServiceManager,
        ).apply {
            spokenInstructionObserver = voiceGuidance.observer

            // How soon the core may ask again after a reroute request, successful or not. The
            // cooldown runs from the end of the previous request and the movement gate from the
            // spot where it was started (never cleared on failure), so Ferrostar's 5 s / 50 m
            // defaults left the user without a route for a long time after one slow request.
            minimumTimeBeforeRecalculation = RerouteTuning.COOLDOWN
            minimumMovementBeforeRecalculation = RerouteTuning.MIN_MOVEMENT_METERS

            // Off route -> ask the server for a fresh route to the remaining waypoints ...
            deviationHandler = RouteDeviationHandler { _, _, remainingWaypoints ->
                CorrectiveAction.GetNewRoutes(remainingWaypoints)
            }
            // ... and swap it in as soon as it arrives. `replaceRoute` stops speech and clears the
            // queue, so if the "Rerouting" announcement is still being spoken the swap waits for it
            // (at most ~1.5 s, and only when the server answered faster than the word was said).
            alternativeRouteProcessor = AlternativeRouteProcessor { core, routes ->
                val route = routes.firstOrNull()
                if (route == null) {
                    Log.w(TAG, "Reroute returned no routes; staying on the current one")
                } else {
                    Log.i(TAG, "Rerouted: ${route.distance.toInt()} m, ${route.steps.size} steps")
                    scheduleRouteSwap(core, route)
                }
            }

            // A swap still waiting when the trip ends must not land in the next trip.
            appScope.launch {
                state.map { it.isNavigating() }.distinctUntilChanged().collect { navigating ->
                    if (!navigating) cancelRouteSwap()
                }
            }
        }
    }

    /**
     * Swaps [route] in once the current announcement is done. Runs on the main thread, like the trip
     * watcher above and any earlier swap, so a newer reroute cleanly supersedes one still waiting.
     */
    private fun scheduleRouteSwap(core: FerrostarCore, route: Route) {
        appScope.launch {
            cancelRouteSwap()
            pendingRouteSwap = launch {
                val wait = voiceGuidance.remainingAnnouncementMs()
                if (wait > 0) delay(wait)
                if (core.state.value.isNavigating()) core.replaceRoute(route)
            }
        }
    }

    private fun cancelRouteSwap() {
        pendingRouteSwap?.cancel()
        pendingRouteSwap = null
    }

    private companion object {
        const val TAG = "AppGraph"
        const val PLACES_PREFS = "places"
        const val PLACES_KEY = "saved_places"
    }
}
