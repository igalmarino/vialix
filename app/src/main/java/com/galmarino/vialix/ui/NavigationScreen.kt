// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmarino.vialix.Attribution
import com.galmarino.vialix.NavConfig
import com.galmarino.vialix.R
import com.galmarino.vialix.map.MapStyleController
import com.galmarino.vialix.map.MapStyleState
import com.galmarino.vialix.navigation.Destination
import com.galmarino.vialix.navigation.NavigationViewModel
import com.galmarino.vialix.navigation.Notice
import com.galmarino.vialix.navigation.RouteError
import com.galmarino.vialix.navigation.ScreenState
import com.galmarino.vialix.settings.NavSettings
import com.galmarino.vialix.ui.theme.LocalDarkTheme
import com.stadiamaps.ferrostar.composeui.config.NavigationViewComponentBuilder
import com.stadiamaps.ferrostar.composeui.config.withInstructionsView
import com.stadiamaps.ferrostar.composeui.config.withProgressView
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.composeui.theme.DefaultNavigationUITheme
import com.stadiamaps.ferrostar.composeui.views.components.InstructionsView
import com.stadiamaps.ferrostar.composeui.views.components.TripProgressView
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DurationFormatter
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.compose.style.BaseStyle

private enum class LocationPermission {
    Unknown,
    Granted,

    /** Denied, but the system will still show the request dialog. */
    Denied,

    /** Denied for good ("don't ask again" or repeated denials): only the app's settings page can fix it. */
    DeniedPermanently,
}

/** What the bottom sheet is showing. */
private enum class SheetMode {
    /** Idle (the map is unobstructed) or guidance is running (Ferrostar's progress view takes the bottom). */
    Hidden,

    /** A destination is selected: summary, Start, Cancel. Fixed at its content height. */
    RoutePreview,

    /** Guidance just ended at the destination: trip summary and Done. Fixed at its content height. */
    Arrived,
}

/** Used for the preview and arrival sheets until their content has been measured. */
private val FIXED_SHEET_FALLBACK_HEIGHT = 240.dp

/** How long the sheet takes to grow or shrink when its mode or content changes. */
private const val SHEET_ANIMATION_MS = 250

/** Gap between the my-location button and whatever is below it (the sheet, or the navigation bar). */
private val FAB_SHEET_GAP = 16.dp

/** How long the preview sheet's height must hold still before the camera frames the routes above it. */
private const val SHEET_SETTLE_MS = 100L

/**
 * Shown while the patched style downloads, so the only style the user sees load is the final one.
 * Its background approximates the ground of the style to come (liberty's `#f8f4f0`, or what
 * `NightStylePatch` makes of it), so the first frame does not flash the wrong colour.
 */
internal fun emptyStyleJson(dark: Boolean): String {
    val ground = if (dark) "#1e1b18" else "#f8f4f0"
    return """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"$ground"}}]}"""
}

/** Entry point of the UI: asks for location permission, then shows the map. */
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel,
    config: NavConfig,
    settings: NavSettings,
    mapStyle: MapStyleController,
    onOpenSettings: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoiceQuery: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var permission by rememberSaveable { mutableStateOf(LocationPermission.Unknown) }

    // Idle GPS polling only while the screen is started (see NavigationViewModel.onForegroundChanged).
    LifecycleStartEffect(Unit) {
        viewModel.onForegroundChanged(true)
        onStopOrDispose { viewModel.onForegroundChanged(false) }
    }

    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun grant() {
        permission = LocationPermission.Granted
        viewModel.onLocationPermissionGranted()
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                grant()
            } else {
                // After repeated denials Android answers without showing a dialog; asking again
                // would then be a button that does nothing.
                val canAskAgain =
                    activity != null &&
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                permission = if (canAskAgain) LocationPermission.Denied else LocationPermission.DeniedPermanently
            }
        }

    LaunchedEffect(Unit) {
        if (isGranted()) {
            grant()
        } else if (permission == LocationPermission.Unknown) {
            launcher.launch(requiredPermissions())
        }
    }

    // Back from the system settings page (or wherever) with the permission granted meanwhile.
    LifecycleResumeEffect(Unit) {
        if (permission != LocationPermission.Granted && isGranted()) grant()
        onPauseOrDispose {}
    }

    when (permission) {
        LocationPermission.Denied ->
            PermissionRationale(
                message = stringResource(R.string.permission_rationale),
                actionLabel = stringResource(R.string.permission_retry),
                onAction = { launcher.launch(requiredPermissions()) },
            )

        LocationPermission.DeniedPermanently ->
            PermissionRationale(
                message = stringResource(R.string.permission_denied_permanently),
                actionLabel = stringResource(R.string.permission_open_settings),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )

        else ->
            NavigationMap(
                viewModel = viewModel,
                config = config,
                settings = settings,
                mapStyleController = mapStyle,
                permissionGranted = permission == LocationPermission.Granted,
                onOpenSettings = onOpenSettings,
                onOpenLicenses = onOpenLicenses,
                onOpenSearch = onOpenSearch,
                onVoiceQuery = onVoiceQuery,
            )
    }
}

private fun requiredPermissions(): Array<String> =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** The system-wide location switch (not the permission). */
private fun isLocationEnabled(context: Context): Boolean = context.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true

/** Whether the foreground-service notification may be posted (always, before Android 13). */
private fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * The map with everything layered on it. Three layers, bottom to top:
 * 1. Ferrostar's navigation view (map, route line, puck; banner and progress view while navigating).
 * 2. While idle: status-bar scrim, search pill and the FAB stack, drawn directly over the map.
 * 3. The bottom sheet (`BottomSheetScaffold`), shown only for the route preview and the arrival card;
 *    the FAB stack sits on top of it.
 *
 * All of it sits inside a `ModalNavigationDrawer` opened by the menu icon (see `MenuDrawerContent`).
 *
 * This function owns what decides the layout (whether guidance is running, which sheet is up and how
 * tall it is) and hands the rest to composables with their own, narrower state reads: [SheetContent],
 * [MapCanvas] (the Ferrostar view, the app's layers, the idle chrome and the FAB) and [MapMessages]
 * (the one-off snackbars). The location updates at 1 Hz, so what recomposes on each fix matters.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun NavigationMap(
    viewModel: NavigationViewModel,
    config: NavConfig,
    settings: NavSettings,
    mapStyleController: MapStyleController,
    permissionGranted: Boolean,
    onOpenSettings: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoiceQuery: (String) -> Unit,
) {
    val uiState by viewModel.navigationUiState.collectAsStateWithLifecycle()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val currentSettings by settings.state.collectAsStateWithLifecycle()
    val isNavigating = uiState.isNavigating()

    // Only guidance justifies keeping the screen awake; browsing the map does not.
    if (isNavigating) KeepScreenOnDisposableEffect()

    // Ferrostar's default banner and progress views format distances for the device locale with
    // no way to inject a formatter, so the same public composables are called here with one that
    // follows the units setting (and the guidance language, which the banner text is in). The
    // preview sheet and the arrival card use the same formatters.
    val distanceFormatter = rememberDistanceFormatter(currentSettings)
    val durationFormatter = rememberDurationFormatter(currentSettings)
    val clockFormatter = rememberClockTimeFormatter(currentSettings)
    val navigationViews = rememberNavigationViews(distanceFormatter)

    // The notification that keeps guidance alive in the background needs a permission on Android
    // 13+. It is asked for when it is about to matter, at Start, rather than stacked behind the
    // location dialog at first launch; guidance starts either way, just without the notification.
    val context = LocalContext.current
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.startNavigation() }
    val startNavigation = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startNavigation()
        }
    }

    val density = LocalDensity.current
    val systemBars = WindowInsets.systemBars.asPaddingValues()

    // ---- Bottom sheet -------------------------------------------------------------------
    val sheetMode =
        when {
            isNavigating -> SheetMode.Hidden
            screenState.arrival != null -> SheetMode.Arrived
            screenState.destination != null -> SheetMode.RoutePreview
            else -> SheetMode.Hidden
        }
    var fixedSheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetPeekHeight =
        when (sheetMode) {
            SheetMode.Hidden -> 0.dp

            // The sheet is never draggable: its peek height is simply its content height.
            SheetMode.RoutePreview, SheetMode.Arrived ->
                if (fixedSheetHeightPx > 0) with(density) { fixedSheetHeightPx.toDp() } else FIXED_SHEET_FALLBACK_HEIGHT
        }
    // The scaffold and the FAB anchor follow this smoothly; the camera padding below uses the
    // target value so the camera options are not rebuilt on every frame of the animation.
    val animatedPeekHeight by animateDpAsState(sheetPeekHeight, tween(SHEET_ANIMATION_MS), label = "sheetPeek")
    // Swiping is disabled, so the sheet stays partially expanded (at the peek height) for good.
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // ---- Map state ----------------------------------------------------------------------
    val mapState = rememberNavigationMapState()
    val cameraOptions = navigationCameraOptions()
    // While browsing, keep the puck centred in the part of the map the preview sheet (if any)
    // leaves visible, and make the heading mode a plain rotating top-down view rather than the
    // tilted guidance camera.
    val browsingCameraOptions =
        remember(cameraOptions, sheetPeekHeight) {
            cameraOptions.copy(
                browsingPadding = PaddingValues(bottom = sheetPeekHeight),
                navigationZoom = cameraOptions.browsingZoom,
                navigationTilt = 0.0,
                navigationPadding = PaddingValues(bottom = sheetPeekHeight),
            )
        }

    // Frame the previewed routes (all alternatives, so each can be seen and picked) above the sheet.
    // Keyed on the routes alone: the sheet re-measures as its content settles and again when the
    // reverse-geocoded address arrives, and re-framing then would undo a pan the user just made. The
    // sheet height is read once it has stopped changing instead.
    val currentPeekHeight by rememberUpdatedState(sheetPeekHeight)
    LaunchedEffect(screenState.routeOptions) {
        if (screenState.routeOptions.isEmpty()) return@LaunchedEffect
        val bounds = screenState.routeOptions.flatMap { it.geometry }.boundingBox() ?: return@LaunchedEffect
        val peek = snapshotFlow { currentPeekHeight }.debounce(SHEET_SETTLE_MS).first()
        mapState.showRouteOverview(
            boundingBox = bounds,
            paddingValues =
                PaddingValues(
                    start = 48.dp,
                    top = systemBars.calculateTopPadding() + 120.dp,
                    end = 48.dp,
                    bottom = peek + 48.dp,
                ),
        )
    }

    MapMessages(
        snackbarHostState = scaffoldState.snackbarHostState,
        error = screenState.error,
        notice = screenState.notice,
        mapStyleController = mapStyleController,
        permissionGranted = permissionGranted,
        onErrorShown = viewModel::dismissError,
        onNoticeShown = viewModel::dismissNotice,
        consumeFirstLaunchHint = settings::consumeFirstLaunchHint,
    )

    val startVoiceSearch = rememberVoiceSearchLauncher(currentSettings.resolvedLanguageTag(), onVoiceQuery)

    // ---- Menu drawer --------------------------------------------------------------------
    // Material's standard pairing for the hamburger icon. Its edge-swipe gesture is only on while
    // it is open (to swipe it closed): closed, a swipe from the edge must pan the map, not open it.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            MenuDrawerContent(
                routingCredit = Attribution.creditFor(config.valhallaEndpoint),
                searchCredit = Attribution.creditFor(config.geocoderEndpoint),
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onOpenLicenses = {
                    scope.launch { drawerState.close() }
                    onOpenLicenses()
                },
            )
        },
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = animatedPeekHeight,
            // Material 3 bottom-sheet tokens (extra-large top corners, surfaceContainerLow, level-1
            // elevation).
            sheetShape = BottomSheetDefaults.ExpandedShape,
            sheetContainerColor = BottomSheetDefaults.ContainerColor,
            sheetShadowElevation = BottomSheetDefaults.Elevation,
            // The handle is part of the content so the measured preview height is the whole sheet.
            sheetDragHandle = null,
            sheetSwipeEnabled = false,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(it) },
            sheetContent = {
                SheetContent(
                    mode = sheetMode,
                    screenState = screenState,
                    routingProfile = currentSettings.routingProfile,
                    distanceFormatter = distanceFormatter,
                    durationFormatter = durationFormatter,
                    clockFormatter = clockFormatter,
                    onProfileSelected = settings::setRoutingProfile,
                    onRouteSelected = viewModel::selectRoute,
                    onRetry = viewModel::retryRoute,
                    onStartNavigation = startNavigation,
                    onCancel = viewModel::clearDestination,
                    onDone = viewModel::acknowledgeArrival,
                    onMeasured = { fixedSheetHeightPx = it },
                )
            },
        ) {
            MapCanvas(
                viewModel = viewModel,
                mapState = mapState,
                mapStyleController = mapStyleController,
                screenState = screenState,
                isNavigating = isNavigating,
                cameraOptions = if (isNavigating) cameraOptions else browsingCameraOptions,
                navigationViews = navigationViews,
                sheetPeekHeight = sheetPeekHeight,
                animatedPeekHeight = animatedPeekHeight,
                onMenuClick = { scope.launch { drawerState.open() } },
                onSearchClick = onOpenSearch,
                onMicClick = startVoiceSearch,
            )
        }
    }
}

/**
 * Ferrostar's banner and progress views with the app's distance formatter. Built once per
 * formatter: the builder is a data class compared by its lambdas, so a fresh one on every
 * recomposition would invalidate Ferrostar's whole view at the rate of the GPS fixes.
 */
@Composable
private fun rememberNavigationViews(distanceFormatter: DistanceFormatter): NavigationViewComponentBuilder = remember(distanceFormatter) {
    NavigationViewComponentBuilder.Default()
        .withInstructionsView { instructionsModifier, state ->
            val instruction = state.visualInstruction
            if (instruction == null) {
                // The core hides the banner while the user is completely off route; fill
                // the gap so a slow reroute does not look like nothing.
                if (state.isRerouting()) ReroutingBanner(instructionsModifier)
                return@withInstructionsView
            }
            InstructionsView(
                instructions = instruction,
                distanceToNextManeuver = state.progress?.distanceToNextManeuver,
                modifier = instructionsModifier,
                distanceFormatter = distanceFormatter,
                theme = DefaultNavigationUITheme.instructionRowTheme,
                remainingSteps = state.remainingSteps,
            )
        }
        .withProgressView { progressModifier, state, onTapExit ->
            val progress = state.progress ?: return@withProgressView
            TripProgressView(
                modifier = progressModifier,
                theme = DefaultNavigationUITheme.tripProgressViewTheme,
                distanceFormatter = distanceFormatter,
                progress = progress,
                onTapExit = onTapExit,
            )
        }
}

/**
 * The one-off snackbars. Failures of the route request are part of the preview and shown inline
 * in the sheet; the snackbar takes the ones with no preview to sit in (see `ScreenState.error`),
 * the saved-place notices, a failed style download (with Retry), the system location switch being
 * off (with a way to the settings) and the first-launch hint. Error and notice are cleared
 * *before* they are shown, so the same one happening again is a new value for the effect key and
 * gets its own snackbar.
 */
@Composable
private fun MapMessages(
    snackbarHostState: SnackbarHostState,
    error: RouteError?,
    notice: Notice?,
    mapStyleController: MapStyleController,
    permissionGranted: Boolean,
    onErrorShown: () -> Unit,
    onNoticeShown: () -> Unit,
    consumeFirstLaunchHint: () -> Boolean,
) {
    val context = LocalContext.current

    val errorMessage = error?.let { errorText(it) }
    LaunchedEffect(error) {
        if (error != null && errorMessage != null) {
            onErrorShown()
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    val noticeMessage =
        when (notice) {
            is Notice.FavoriteSaved -> stringResource(R.string.favorite_saved, stringResource(notice.kind.labelRes()))
            null -> null
        }
    LaunchedEffect(notice) {
        if (notice != null && noticeMessage != null) {
            onNoticeShown()
            snackbarHostState.showSnackbar(noticeMessage)
        }
    }

    // The style download failed (offline, most likely): the plain URL the map falls back to will not
    // load either, so say so and offer to try again.
    val mapStyle by mapStyleController.state.collectAsStateWithLifecycle()
    val styleUnavailable = (mapStyle as? MapStyleState.Unavailable)?.takeIf { it.downloadFailed }
    val styleUnavailableMessage = stringResource(R.string.map_style_unavailable)
    val retryLabel = stringResource(R.string.retry)
    LaunchedEffect(styleUnavailable) {
        if (styleUnavailable != null) {
            val result = snackbarHostState.showSnackbar(styleUnavailableMessage, actionLabel = retryLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) mapStyleController.retry()
        }
    }

    // Permission granted but location switched off in the system: the map would just stay empty and
    // every route request fail with "no fix", so say what is wrong and offer the system switch.
    var locationEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        locationEnabled = isLocationEnabled(context)
        onPauseOrDispose {}
    }
    val locationOffMessage = stringResource(R.string.location_disabled)
    val turnOnLabel = stringResource(R.string.location_turn_on)
    LaunchedEffect(locationEnabled, permissionGranted) {
        if (permissionGranted && !locationEnabled) {
            val result = snackbarHostState.showSnackbar(
                locationOffMessage,
                actionLabel = turnOnLabel,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    // Only once the permission dialog is out of the way, or the hint is consumed behind it.
    val firstLaunchHint = stringResource(R.string.hint_choose_destination)
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && consumeFirstLaunchHint()) snackbarHostState.showSnackbar(firstLaunchHint)
    }
}

/**
 * The bottom sheet's content: the route preview or the arrival card, crossfading between modes.
 * [onMeasured] reports the content height, which is the sheet's peek height (it is never draggable).
 */
@Composable
private fun SheetContent(
    mode: SheetMode,
    screenState: ScreenState,
    routingProfile: String,
    distanceFormatter: DistanceFormatter,
    durationFormatter: DurationFormatter,
    clockFormatter: DateTimeFormatter,
    onProfileSelected: (String) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onMeasured: (Int) -> Unit,
) {
    // Crossfade stacks the outgoing and incoming content in a Box, so the incoming one is
    // measured (and reported through onSizeChanged) as usual.
    Crossfade(targetState = mode, animationSpec = tween(SHEET_ANIMATION_MS), label = "sheetContent") { current ->
        when (current) {
            SheetMode.Hidden -> Unit

            SheetMode.RoutePreview -> {
                val destination = screenState.destination ?: return@Crossfade
                RoutePreviewSheetContent(
                    destination = destination,
                    preview = screenState.preview,
                    routingProfile = routingProfile,
                    distanceFormatter = distanceFormatter,
                    durationFormatter = durationFormatter,
                    clockFormatter = clockFormatter,
                    onProfileSelected = onProfileSelected,
                    onRouteSelected = onRouteSelected,
                    onRetry = onRetry,
                    onStartNavigation = onStartNavigation,
                    onCancel = onCancel,
                    modifier = Modifier.onSizeChanged { onMeasured(it.height) },
                )
            }

            SheetMode.Arrived -> {
                val arrival = screenState.arrival ?: return@Crossfade
                ArrivalSheetContent(
                    arrival = arrival,
                    distanceFormatter = distanceFormatter,
                    durationFormatter = durationFormatter,
                    onDone = onDone,
                    modifier = Modifier.onSizeChanged { onMeasured(it.height) },
                )
            }
        }
    }
}

/**
 * Ferrostar's map view with the app's layers on it, the idle chrome (scrim + search pill) and the
 * FAB stack anchored above the sheet. Reads the style and the saved places itself, so those do not
 * pass through [NavigationMap].
 */
@Composable
private fun MapCanvas(
    viewModel: NavigationViewModel,
    mapState: NavigationMapState,
    mapStyleController: MapStyleController,
    screenState: ScreenState,
    isNavigating: Boolean,
    cameraOptions: NavigationCameraOptions,
    navigationViews: NavigationViewComponentBuilder,
    sheetPeekHeight: Dp,
    animatedPeekHeight: Dp,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMicClick: (() -> Unit)?,
) {
    val mapStyle by mapStyleController.state.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val bottomInset = systemBars.calculateBottomPadding()
    var mapHeightPx by remember { mutableIntStateOf(0) }
    var fabStackHeightPx by remember { mutableIntStateOf(0) }
    // Read here and passed down: MapLibre composes the layers in its own tree.
    val dark = LocalDarkTheme.current
    val paint = mapPaint(dark)

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { mapHeightPx = it.height }) {
        DynamicallyOrientingNavigationView(
            modifier = Modifier.fillMaxSize(),
            baseStyle =
                when (val style = mapStyle) {
                    is MapStyleState.Patched -> BaseStyle.Json(style.json)
                    MapStyleState.Loading -> BaseStyle.Json(emptyStyleJson(dark))
                    is MapStyleState.Unavailable -> BaseStyle.Uri(style.styleUrl)
                },
            navigationMapState = mapState,
            navigationCameraOptions = cameraOptions,
            viewModel = viewModel,
            locationPuckStyle = remember(paint) { navigationPuckStyle(paint) },
            views = navigationViews,
            // Logo and attribution sit just above the sheet when there is one, otherwise above
            // the navigation bar; Ferrostar moves them above its own progress view while navigating.
            ornamentPadding =
                PaddingValues(
                    start = systemBars.calculateStartPadding(layoutDirection),
                    top = systemBars.calculateTopPadding(),
                    end = systemBars.calculateEndPadding(layoutDirection),
                    bottom = maxOf(sheetPeekHeight, bottomInset),
                ),
            // Ferrostar's arrow puck while navigating; our own dot/cone/circle while browsing.
            showDefaultPuck = isNavigating,
            onTapExit = { viewModel.stopNavigation() },
            onMapLongClick = { position, _ ->
                // During guidance there is no preview to show a new destination in.
                if (isNavigating) {
                    NavigationMapClickResult.Pass
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.selectDestination(Destination(position))
                    NavigationMapClickResult.Consume
                }
            },
        ) { state ->
            if (!state.isNavigating()) {
                RoutePreviewLayer(screenState.routeOptions, screenState.selectedRoute, paint)
                // Below the pin and the puck. A tap picks the place like the search screen does.
                SavedPlacesLayer(savedPlaces, screenState.destination?.coordinate, paint) { kind ->
                    savedPlaces.favorite(kind)?.let(viewModel::selectDestination)
                }
                DroppedPinLayer(screenState.destination?.coordinate, paint)
                LocationPuckLayer(state.location, mapState.cameraState, paint)
            }
        }

        IdleChrome(
            visible = !isNavigating,
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick,
            onMicClick = onMicClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Anchored to the sheet's top edge (or the navigation bar when there is no sheet), and
        // never pushed up under the search pill by a tall preview sheet in landscape.
        val peekPx = with(density) { animatedPeekHeight.roundToPx() }
        val bottomInsetPx = with(density) { bottomInset.roundToPx() }
        val gapPx = with(density) { FAB_SHEET_GAP.roundToPx() }
        val minTopPx = with(density) { (systemBars.calculateTopPadding() + 8.dp + 48.dp + 16.dp).roundToPx() }
        AnimatedVisibility(
            visible = !isNavigating,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .onSizeChanged { fabStackHeightPx = it.height }
                    .offset {
                        val maxVisible = (mapHeightPx - fabStackHeightPx - gapPx - minTopPx).coerceAtLeast(0)
                        IntOffset(0, -(maxOf(peekPx, bottomInsetPx).coerceIn(0, maxVisible) + gapPx))
                    },
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            MyLocationFab(mapState)
        }
    }
}

/** The status-bar scrim and the search pill; they slide away when guidance starts and back when it ends. */
@Composable
private fun IdleChrome(
    visible: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMicClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            StatusBarScrim(modifier = Modifier.align(Alignment.TopCenter))
            TopSearchBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onMicClick = onMicClick,
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Reads the camera mode here, so its flipping on every map gesture recomposes the button and nothing else. */
@Composable
private fun MyLocationFab(mapState: NavigationMapState) {
    val followMode = mapState.cameraMode.toFollowMode()
    MapFabStack(
        followMode = followMode,
        onMyLocationClick = { mapState.cameraMode = followMode.next().toCameraMode() },
    )
}

@Composable
private fun PermissionRationale(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
