package com.galmarino.vialix.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.UserLocationUnknown
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.galmarino.vialix.AppGraph
import com.galmarino.vialix.BuildConfig
import com.galmarino.vialix.NavConfig
import com.galmarino.vialix.RequestFailure
import com.galmarino.vialix.map.MapStyleLoader
import com.galmarino.vialix.map.MapStyleState
import com.galmarino.vialix.places.FavoriteKind
import com.galmarino.vialix.places.SavedPlaces
import com.galmarino.vialix.places.SavedPlacesRepository
import com.galmarino.vialix.routing.ValhallaRouteProvider
import com.galmarino.vialix.search.Geocoder
import com.galmarino.vialix.settings.NavSettings
import com.galmarino.vialix.voice.VoiceGuidance
import uniffi.ferrostar.Route
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
    /** Routes fetched for [destination] (Valhalla's preferred one first, then alternates), shown until navigation starts. */
    val routeOptions: List<Route> = emptyList(),
    /** Index into [routeOptions] of the route the user will start. */
    val selectedRoute: Int = 0,
    val isFetchingRoute: Boolean = false,
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
    /** The route the preview line, the summary and Start refer to. */
    val routePreview: Route?
        get() = routeOptions.getOrNull(selectedRoute)
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
    private val config: NavConfig,
    mapStyleLoader: MapStyleLoader,
) : DefaultNavigationViewModel(core, valhallaExtendedOSRMAnnotationPublisher()) {

    private val hasLocationPermission = MutableStateFlow(false)

    /** True while the map screen is started. Idle GPS polling stops when the app is in the background. */
    private val isInForeground = MutableStateFlow(false)

    private val _location = MutableStateFlow<UserLocation?>(null)

    /** Latest known position, independent of whether we are navigating. */
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    /** Home/Work and recents, straight from the store. */
    val savedPlaces: StateFlow<SavedPlaces> = savedPlacesRepository.state

    /** Whether the app is drawn dark; `null` until the Activity has said (see [onDarkThemeChanged]). */
    private val darkTheme = MutableStateFlow<Boolean?>(null)

    private val _mapStyle = MutableStateFlow<MapStyleState>(MapStyleState.Loading)

    /** The map style with the POI label fix applied, or the decision to load the URL as-is. */
    val mapStyle: StateFlow<MapStyleState> = _mapStyle.asStateFlow()

    /**
     * While idle, Ferrostar's state has no location; inject ours so the puck is visible before a
     * route exists. While navigating, Ferrostar's snapped location wins.
     */
    override val navigationUiState: StateFlow<NavigationUiState> =
        combine(super.navigationUiState, _location) { uiState, location ->
            if (uiState.isNavigating()) uiState else uiState.copy(location = location)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = NavigationUiState.empty(),
        )

    /** Stops the core a few seconds after arrival unless the user taps Done first. */
    private var arrivalJob: Job? = null

    init {
        // Idle position for the puck and route requests. Only while the screen is started:
        // the ViewModel outlives a backgrounded Activity, and 1 Hz GPS in the background would
        // drain the battery for nothing. During guidance Ferrostar holds its own subscription.
        viewModelScope.launch {
            combine(hasLocationPermission, isInForeground) { granted, foreground -> granted && foreground }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (active) {
                        locationProvider.locationUpdates(LOCATION_INTERVAL_MS).map { it.toUserLocation() }
                    } else {
                        emptyFlow()
                    }
                }
                .collect { _location.value = it }
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
                    if (!navigationUiState.value.isNavigating()) selectDestination(destination)
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

        // The basemap for the current theme: patched (and recoloured for the night when no dark
        // style is configured), or give up and let the screen load the URL directly — in the dark
        // theme that URL is then the light style, the only one there is. Nothing loads until the
        // Activity has reported the theme, so the first (and, in the common case, only) style load
        // is the right one. On a later theme change the map keeps its current style until the new
        // one is ready; a flip mid-download cancels the download.
        viewModelScope.launch(Dispatchers.IO) {
            darkTheme.filterNotNull().distinctUntilChanged().collectLatest { dark ->
                val url = config.mapStyleUrlFor(dark)
                val json =
                    withTimeoutOrNull(STYLE_LOAD_TIMEOUT_MS) { mapStyleLoader.load(url, night = config.derivesNightStyle(dark)) }
                _mapStyle.value = if (json != null) MapStyleState.Patched(json) else MapStyleState.Unavailable(url)
            }
        }
    }

    /** The resolved theme (setting + system), pushed by the Activity; picks the basemap style. */
    fun onDarkThemeChanged(dark: Boolean) {
        darkTheme.value = dark
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
        if (navigationUiState.value.isNavigating()) {
            Log.i(TAG, "Ignoring destination picked during guidance")
            return
        }
        // A new pick while the arrival card is still up dismisses it (and stops the core now
        // rather than at the end of the grace period, so Start cannot race a completed session).
        if (_screenState.value.arrival != null) acknowledgeArrival()
        _screenState.update {
            it.copy(destination = destination, routeOptions = emptyList(), selectedRoute = 0, isFetchingRoute = true, error = null)
        }
        // Binding the TTS engine takes a moment; do it while the route is in flight so the first
        // announcement is not lost.
        voiceGuidance.start()
        fetchRoute(destination)
        if (destination.name == null) resolveAddress(destination)
    }

    /** Ask for the same route again after a failure (inline Retry in the preview). */
    fun retryRoute() {
        val destination = _screenState.value.destination ?: return
        selectDestination(destination)
    }

    /** One of the alternatives in the preview was tapped. */
    fun selectRoute(index: Int) {
        _screenState.update { if (index in it.routeOptions.indices) it.copy(selectedRoute = index) else it }
    }

    /** The user dismissed the preview: drop the pin and release the TTS engine bound for it. */
    fun clearDestination() {
        _screenState.update { it.copy(destination = null, routeOptions = emptyList(), isFetchingRoute = false, error = null) }
        if (!navigationUiState.value.isNavigating()) voiceGuidance.shutdown()
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

    /** `false` when there is no fix yet (the caller surfaces [RouteError.NoLocationFix]). */
    fun setFavoriteToCurrentLocation(kind: FavoriteKind): Boolean {
        val here = _location.value ?: return false
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
        if (BuildConfig.DEBUG && current.simulateDriving) {
            locationProvider.enableSimulationOn(route)
        }

        voiceGuidance.start()

        try {
            core.startNavigation(route, NavigationControllerConfigs.forProfile(current.routingProfile))
        } catch (e: UserLocationUnknown) {
            // The fix was lost between the route request and Start. Keep the preview so Start can
            // simply be tapped again once the puck is back.
            Log.w(TAG, "Cannot start navigation without a location", e)
            locationProvider.disableSimulation()
            _screenState.update { it.copy(error = RouteError.NoLocationFix) }
            return
        }

        state.destination?.let(savedPlacesRepository::addRecent)
        // Not clearDestination(): guidance has just begun and the TTS engine must stay bound.
        _screenState.update {
            it.copy(destination = null, routeOptions = emptyList(), isFetchingRoute = false, activeDestination = state.destination)
        }
    }

    /** Ends guidance, whether from the exit button, the arrival card or the arrival grace period. */
    override fun stopNavigation() {
        arrivalJob?.cancel()
        arrivalJob = null
        locationProvider.disableSimulation()
        // Clears any queued announcement via the core's spoken-instruction observer.
        core.stopNavigation()
        voiceGuidance.shutdown()
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
        viewModelScope.launch(Dispatchers.IO) {
            val origin = _location.value
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

            if (routes.isEmpty()) {
                fail(destination, RouteError.NoRouteFound)
            } else {
                Log.i(TAG, "Routes: ${routes.map { "${it.distance.toInt()} m / ${it.steps.size} steps" }}")
                _screenState.update { it.copy(routeOptions = routes, selectedRoute = 0, isFetchingRoute = false) }
            }
        }
    }

    private fun fail(destination: Destination, error: RouteError) {
        _screenState.update {
            if (it.isSelected(destination)) it.copy(isFetchingRoute = false, error = error) else it
        }
    }

    private fun ScreenState.isSelected(destination: Destination) = this.destination?.coordinate == destination.coordinate

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
                graph.config,
                graph.mapStyleLoader,
            ) as T
        }
    }

    private companion object {
        const val TAG = "NavigationViewModel"
        const val LOCATION_INTERVAL_MS = 1000L

        /** Past this, the map loads the plain style URL rather than staying blank any longer. */
        const val STYLE_LOAD_TIMEOUT_MS = 3000L

        /** Long enough for the arrival announcement to finish before the TTS queue is cleared. */
        const val ARRIVAL_GRACE_MS = 5000L

        /** How many alternatives to ask Valhalla for in the preview (the server may return fewer). */
        const val PREVIEW_ALTERNATES = 2
        const val MAX_ROUTE_OPTIONS = 1 + PREVIEW_ALTERNATES
    }
}
