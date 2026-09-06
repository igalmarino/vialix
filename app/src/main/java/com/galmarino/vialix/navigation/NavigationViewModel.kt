// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.galmarino.vialix.AppGraph
import com.galmarino.vialix.BuildConfig
import com.galmarino.vialix.RequestFailure
import com.galmarino.vialix.location.CompassHeadingProvider
import com.galmarino.vialix.places.FavoriteKind
import com.galmarino.vialix.places.SavedPlaces
import com.galmarino.vialix.places.SavedPlacesRepository
import com.galmarino.vialix.routing.ValhallaRouteProvider
import com.galmarino.vialix.search.Geocoder
import com.galmarino.vialix.settings.NavSettings
import com.galmarino.vialix.voice.VoiceGuidance
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.UserLocationUnknown
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.isNavigating
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.ferrostar.DeviationKind
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/**
 * Errors surfaced to the user: inline in the route preview while a destination is selected,
 * otherwise as a snackbar. Mapped to strings in the UI layer.
 */
sealed interface RouteError {
    /** We have no position yet, so we cannot ask for a route (or start following one). */
    data object NoLocationFix : RouteError

    /** The server answered but had no route. */
    data object NoRouteFound : RouteError

    /** The request itself failed; [failure] says whether the user can do anything about it. */
    data class RequestFailed(val failure: RequestFailure) : RouteError
}

/** One-shot confirmations shown as a snackbar; the screen calls [NavigationViewModel.dismissNotice] afterwards. */
sealed interface Notice {
    /** A place picked in the search screen was stored as Home/Work. */
    data class FavoriteSaved(val kind: FavoriteKind) : Notice
}

/** UI state owned by this screen, on top of what Ferrostar publishes in [NavigationUiState]. */
data class ScreenState(
    /** Long-pressed point or picked search result. Rendered as a pin and used as the route destination. */
    val destination: Destination? = null,
    /** The routes fetched for [destination] (or the request, or its failure); [RoutePreview.None] without one. */
    val preview: RoutePreview = RoutePreview.None,
    /**
     * A failure with no preview to show it in (Start itself refused, or nothing is selected), shown
     * as a snackbar. Failures of the route request live in [preview] and are shown inline.
     */
    val error: RouteError? = null,
    /** Where guidance is currently going; kept so the arrival card can name it. */
    val activeDestination: Destination? = null,
    /** Set when Ferrostar reports the trip complete, until the user taps Done. */
    val arrival: Arrival? = null,
    val notice: Notice? = null,
    /**
     * Set while the user is searching for an address to store as Home/Work (from the Home/Work row
     * on the search screen): the next picked place is saved there instead of being routed to.
     */
    val favoriteToAssign: FavoriteKind? = null,
) {
    /** Every previewed route, the selected one's index, and the route Start refers to; see [RoutePreview]. */
    val routeOptions: List<Route>
        get() = preview.routeOptions

    val selectedRoute: Int
        get() = preview.selectedIndex

    val routePreview: Route?
        get() = preview.route
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModel(
    private val core: FerrostarCore,
    private val locationProvider: NavigationLocationProvider,
    private val voiceGuidance: VoiceGuidance,
    private val settings: NavSettings,
    private val savedPlacesRepository: SavedPlacesRepository,
    private val geocoder: Geocoder,
    private val routeProvider: ValhallaRouteProvider,
    private val compass: CompassHeadingProvider?,
) : DefaultNavigationViewModel(core, valhallaExtendedOSRMAnnotationPublisher()) {

    private val hasLocationPermission = MutableStateFlow(false)

    /** True while the map screen is started. Idle GPS polling stops when the app is in the background. */
    private val isInForeground = MutableStateFlow(false)

    private val _location = MutableStateFlow<UserLocation?>(null)

    /** Latest known position, independent of whether we are navigating. */
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    /** Where the phone points (true north, whole degrees); `null` while not collected or without a sensor. */
    private val compassHeading = MutableStateFlow<Int?>(null)

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val rerouteAnnouncer = RerouteAnnouncer()

    /** Home/Work and recents, straight from the store. */
    val savedPlaces: StateFlow<SavedPlaces> = savedPlacesRepository.state

    /** Moves the location drawn during guidance ahead of the fix it came from, see its docs. */
    private val displayLocation = DisplayLocationPredictor()

    /**
     * While idle, Ferrostar's state has no location; inject ours so the puck is visible before a
     * route exists. While navigating, Ferrostar's snapped location wins, moved ahead by
     * [DisplayLocationPredictor] so that the view's one-second animation towards it ends where the
     * user is by then rather than where they were. Idempotent per fix, so the idle flow re-running
     * this lambda during guidance does not change the state. The idle location gets the compass as
     * its course when the GPS has none ([withCompassHeading]), so the puck's cone and the heading
     * camera turn with the phone while standing still; [location] itself stays untouched, because
     * a routing origin's course becomes the request's start heading.
     */
    override val navigationUiState: StateFlow<NavigationUiState> =
        combine(super.navigationUiState, _location, compassHeading) { uiState, location, heading ->
            if (uiState.isNavigating()) {
                uiState.copy(location = uiState.location?.let(displayLocation::predict))
            } else {
                uiState.copy(location = location?.withCompassHeading(heading))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS),
            initialValue = NavigationUiState.empty(),
        )

    /** Stops the core a few seconds after arrival unless the user taps Done first. */
    private var arrivalJob: Job? = null

    /** The route request in flight for the preview; a new request cancels it so a slow reply cannot overwrite a newer one. */
    private var routeJob: Job? = null

    init {
        // Position for the puck, route requests and search distances. Only while the screen is
        // started: the ViewModel outlives a backgrounded Activity, and 1 Hz GPS in the background
        // would drain the battery for nothing. During guidance Ferrostar holds its own subscription
        // (the fused provider opens a second GPS request per collector), so the position is taken
        // from its state instead; that also keeps the value current for the moment the trip ends.
        viewModelScope.launch {
            combine(hasLocationPermission, isInForeground, super.navigationUiState.map { it.isNavigating() }) {
                    granted,
                    foreground,
                    navigating,
                ->
                when {
                    navigating -> LocationSource.GUIDANCE
                    granted && foreground -> LocationSource.PROVIDER
                    else -> LocationSource.NONE
                }
            }
                .distinctUntilChanged()
                .flatMapLatest { source ->
                    when (source) {
                        LocationSource.PROVIDER ->
                            locationProvider.locationUpdates(LOCATION_INTERVAL_MS)
                                .map { it.toUserLocation() }
                                // A permission revoked while the process lives ends the fused flow
                                // and makes the platform one throw; neither should take the app down.
                                .catch { e -> Log.w(TAG, "Location updates stopped", e) }

                        LocationSource.GUIDANCE -> super.navigationUiState.map { it.location }.filterNotNull()

                        LocationSource.NONE -> emptyFlow()
                    }
                }
                .collect { _location.value = it }
        }

        // The compass only matters for the idle puck and camera: registered while the screen is
        // started with permission and no guidance running (Ferrostar draws its own puck then).
        if (compass != null) {
            viewModelScope.launch {
                combine(hasLocationPermission, isInForeground, super.navigationUiState.map { it.isNavigating() }) {
                        granted,
                        foreground,
                        navigating,
                    ->
                    granted && foreground && !navigating
                }
                    .distinctUntilChanged()
                    .flatMapLatest { active -> if (active) compass.trueHeadings(_location) else flowOf(null) }
                    .collect { compassHeading.value = it }
            }
        }

        // A route preview is only as good as the options it was fetched with: re-request it when
        // the user changes anything the routing server cares about. Active guidance is left alone.
        viewModelScope.launch {
            settings.state
                .map { Triple(it.resolvedLanguageTag(), it.routingProfile, it.units) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val destination = _screenState.value.destination ?: return@collect
                    if (!core.state.value.isNavigating()) requestRoute(destination)
                }
        }

        // Arrival. `NavigationUiState.isNavigating()` is false for `TripState.Complete`, so the
        // screen already falls back to its idle chrome; what is missing is telling the user, and
        // stopping the core (foreground service, location subscription, TTS). The latter waits a
        // moment because `FerrostarCore.stopNavigation()` clears the spoken-instruction queue and
        // would cut off the "you have arrived" announcement.
        viewModelScope.launch {
            super.navigationUiState
                .map { it.tripState }
                .distinctUntilChanged()
                .collect { tripState ->
                    if (tripState is TripState.Complete) onArrived(tripState)
                }
        }

        // Off route -> say so once. The core suppresses the instruction banner while the user is
        // completely off route and asks for a new route by itself; the screen shows "Rerouting…"
        // from the same state, and this is the spoken counterpart. `OffStepOnRoute` (ahead of the
        // current step but still on the line) keeps its banner and is not announced.
        viewModelScope.launch {
            super.navigationUiState
                .map { state ->
                    val deviation = state.routeDeviation
                    deviation is RouteDeviation.Deviation && deviation.kind is DeviationKind.CompletelyOffRoute
                }
                .distinctUntilChanged()
                .collect { offRoute ->
                    if (rerouteAnnouncer.onDeviation(offRoute, SystemClock.elapsedRealtime())) {
                        voiceGuidance.announceRerouting()
                    }
                }
        }
    }

    /**
     * Guidance keeps its own resources alive through Ferrostar's foreground service; what is ours to
     * release when the screen goes away for good is a TTS engine bound for a preview that never
     * started, and a simulation switched on for it.
     */
    override fun onCleared() {
        routeJob?.cancel()
        if (!core.state.value.isNavigating()) {
            voiceGuidance.shutdown()
            locationProvider.disableSimulation()
        }
        super.onCleared()
    }

    fun onLocationPermissionGranted() {
        hasLocationPermission.value = true
    }

    /** Driven by the screen's lifecycle (started/stopped). */
    fun onForegroundChanged(foreground: Boolean) {
        isInForeground.value = foreground
    }

    /**
     * Drop a pin and immediately request a route to it. Ignored during guidance: Ferrostar owns
     * the map then and there is no preview UI, so accepting it would only leave a stale pin and
     * route behind for when guidance ends.
     */
    fun selectDestination(destination: Destination) {
        if (core.state.value.isNavigating()) {
            Log.i(TAG, "Ignoring destination picked during guidance")
            return
        }
        // A new pick while the arrival card is still up dismisses it (and stops the core now
        // rather than at the end of the grace period, so Start cannot race a completed session).
        if (_screenState.value.arrival != null) acknowledgeArrival()
        _screenState.update { it.copy(destination = destination) }
        // Binding the TTS engine takes a moment; do it while the route is in flight so the first
        // announcement is not lost.
        voiceGuidance.start()
        requestRoute(destination)
        if (destination.name == null) resolveAddress(destination)
    }

    /** (Re)fetch the preview for [destination], which must already be the selected one. */
    private fun requestRoute(destination: Destination) {
        _screenState.update { it.copy(preview = RoutePreview.Fetching, error = null) }
        fetchRoute(destination)
    }

    /** Ask for the same route again after a failure (inline Retry in the preview). */
    fun retryRoute() {
        val destination = _screenState.value.destination ?: return
        selectDestination(destination)
    }

    /** One of the alternatives in the preview was tapped. */
    fun selectRoute(index: Int) {
        _screenState.update { it.copy(preview = it.preview.select(index)) }
    }

    /** The user dismissed the preview: drop the pin and release the TTS engine bound for it. */
    fun clearDestination() {
        routeJob?.cancel()
        _screenState.update { it.copy(destination = null, preview = RoutePreview.None, error = null) }
        if (!core.state.value.isNavigating()) voiceGuidance.shutdown()
    }

    fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        _screenState.update { it.copy(notice = null) }
    }

    /** Done on the arrival card: stop the core now rather than after the grace period. */
    fun acknowledgeArrival() {
        stopNavigation()
        _screenState.update { it.copy(arrival = null) }
    }

    // ---- Home / Work / recents -------------------------------------------------------------

    /** The next place picked in the search screen becomes [kind] instead of a destination. */
    fun beginFavoriteAssignment(kind: FavoriteKind) {
        _screenState.update { it.copy(favoriteToAssign = kind) }
    }

    /** The search screen was closed without a pick. */
    fun cancelFavoriteAssignment() {
        _screenState.update { it.copy(favoriteToAssign = null) }
    }

    /** Search result chosen: either stored as the pending Home/Work, or routed to like a long-press. */
    fun onPlacePicked(destination: Destination) {
        val pending = _screenState.value.favoriteToAssign
        if (pending != null) {
            savedPlacesRepository.setFavorite(pending, destination)
            _screenState.update { it.copy(favoriteToAssign = null, notice = Notice.FavoriteSaved(pending)) }
        } else {
            selectDestination(destination)
        }
    }

    /** `false` when there is no recent fix (the caller surfaces [RouteError.NoLocationFix]). */
    fun setFavoriteToCurrentLocation(kind: FavoriteKind): Boolean {
        val here = freshLocation() ?: return false
        savedPlacesRepository.setFavorite(kind, Destination(here.coordinates))
        return true
    }

    fun clearFavorite(kind: FavoriteKind) {
        savedPlacesRepository.clearFavorite(kind)
    }

    fun removeRecent(destination: Destination) {
        savedPlacesRepository.removeRecent(destination)
    }

    fun clearRecents() {
        savedPlacesRepository.clearRecents()
    }

    /** Start following the previewed route, with the thresholds that suit the profile it was requested for. */
    fun startNavigation() {
        val state = _screenState.value
        val route = state.routePreview ?: return
        val current = settings.state.value

        // Guarded by DEBUG as well as the switch: the switch only exists in debug builds, but a
        // debug install upgraded in place to a release one keeps its preferences.
        val simulate = BuildConfig.DEBUG && current.simulateDriving

        // Without a recent fix the core would anchor the session at the route's first point (it
        // declares `UserLocationUnknown` but does not throw it): keep the preview and say so in a
        // snackbar instead, so Start can simply be tapped again once the puck is back.
        if (!simulate && freshLocation() == null) {
            Log.w(TAG, "Cannot start navigation without a location")
            _screenState.update { it.copy(error = RouteError.NoLocationFix) }
            return
        }

        if (simulate) locationProvider.enableSimulationOn(route)
        routeJob?.cancel()
        voiceGuidance.start()
        displayLocation.reset()

        try {
            core.startNavigation(route, NavigationControllerConfigs.forProfile(current.routingProfile))
        } catch (e: UserLocationUnknown) {
            Log.w(TAG, "Cannot start navigation without a location", e)
            locationProvider.disableSimulation()
            _screenState.update { it.copy(error = RouteError.NoLocationFix) }
            return
        }

        state.destination?.let(savedPlacesRepository::addRecent)
        // Not clearDestination(): guidance has just begun and the TTS engine must stay bound.
        _screenState.update { it.copy(destination = null, preview = RoutePreview.None, activeDestination = state.destination) }
    }

    /** Ends guidance, whether from the exit button, the arrival card or the arrival grace period. */
    override fun stopNavigation() {
        arrivalJob?.cancel()
        arrivalJob = null
        locationProvider.disableSimulation()
        // Clears any queued announcement via the core's spoken-instruction observer.
        core.stopNavigation()
        voiceGuidance.shutdown()
        rerouteAnnouncer.reset()
        _screenState.update { it.copy(activeDestination = null) }
    }

    private fun onArrived(complete: TripState.Complete) {
        if (arrivalJob != null) return
        _screenState.update { it.copy(arrival = Arrival.of(it.activeDestination, complete.summary)) }
        arrivalJob =
            viewModelScope.launch {
                delay(ARRIVAL_GRACE_MS)
                stopNavigation()
            }
    }

    /**
     * Ferrostar's mute button in the navigation view calls this. Instead of flipping the observer
     * directly (what the base class does), write to the settings store: [VoiceGuidance] follows the
     * store, so the button, the settings screen and the persisted value stay in step.
     */
    override fun toggleMute() {
        settings.setVoiceEnabled(!settings.state.value.voiceEnabled)
    }

    private fun fetchRoute(destination: Destination) {
        routeJob?.cancel()
        routeJob =
            viewModelScope.launch(Dispatchers.IO) {
                val origin = freshLocation()
                if (origin == null) {
                    fail(destination, RouteError.NoLocationFix)
                    return@launch
                }

                val routes =
                    try {
                        routeProvider.getRoutes(origin, listOf(destination.asWaypoint()), PREVIEW_ALTERNATES).take(MAX_ROUTE_OPTIONS)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Full detail here only; the user gets a message they can act on.
                        Log.e(TAG, "Route request failed", e)
                        fail(destination, RouteError.RequestFailed(RequestFailure.of(e)))
                        return@launch
                    }

                // The user may have picked a different destination while we were waiting. (Compared by
                // coordinate: the reverse lookup may have relabelled the same pin meanwhile.)
                if (!_screenState.value.isSelected(destination)) return@launch

                Log.i(TAG, "Routes: ${routes.map { "${it.distance.toInt()} m / ${it.steps.size} steps" }}")
                _screenState.update { it.copy(preview = RoutePreview.of(routes)) }
            }
    }

    /** The request for [destination] failed: shown inline in its preview, unless the user has moved on. */
    private fun fail(destination: Destination, error: RouteError) {
        _screenState.update { if (it.isSelected(destination)) it.copy(preview = RoutePreview.Failed(error)) else it }
    }

    private fun ScreenState.isSelected(destination: Destination) = this.destination?.coordinate == destination.coordinate

    /** The last fix, if it is recent enough to be where the user is (see [isFresh]). */
    private fun freshLocation(): UserLocation? = _location.value?.takeIf { it.isFresh(Instant.now()) }

    /** Where the idle position comes from at the moment. */
    private enum class LocationSource { NONE, PROVIDER, GUIDANCE }

    /**
     * Labels a long-pressed point with the nearest address. Best effort: on failure the sheet keeps
     * showing "Dropped pin" and the coordinates.
     */
    private fun resolveAddress(destination: Destination) {
        viewModelScope.launch(Dispatchers.IO) {
            val place =
                try {
                    geocoder.reverse(destination.coordinate, settings.state.value.resolvedLanguageTag())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reverse geocoding failed", e)
                    null
                } ?: return@launch
            _screenState.update {
                val current = it.destination
                if (current != null && current.coordinate == destination.coordinate && current.name == null) {
                    it.copy(destination = current.copy(name = place.name, address = place.address))
                } else {
                    it
                }
            }
        }
    }

    private fun Destination.asWaypoint() = Waypoint(coordinate = coordinate, kind = WaypointKind.BREAK)

    class Factory(private val graph: AppGraph) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                "Unknown ViewModel class ${modelClass.name}"
            }
            return NavigationViewModel(
                graph.ferrostarCore,
                graph.locationProvider,
                graph.voiceGuidance,
                graph.settings,
                graph.savedPlaces,
                graph.geocoder,
                graph.routeProvider,
                graph.compass,
            ) as T
        }
    }

    private companion object {
        const val TAG = "NavigationViewModel"
        const val LOCATION_INTERVAL_MS = 1000L

        /** Keeps the UI state chain alive across a configuration change instead of rebuilding it. */
        const val STOP_TIMEOUT_MS = 5000L

        /** Long enough for the arrival announcement to finish before the TTS queue is cleared. */
        const val ARRIVAL_GRACE_MS = 5000L

        /** How many alternatives to ask Valhalla for in the preview (the server may return fewer). */
        const val PREVIEW_ALTERNATES = 2
        const val MAX_ROUTE_OPTIONS = 1 + PREVIEW_ALTERNATES
    }
}
