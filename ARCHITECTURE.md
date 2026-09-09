# Architecture

Detailed design notes for Vialix. Paths below are relative to the repository root.
See [AGENTS.md](AGENTS.md) for build commands and contribution conventions.

## Build compatibility

The app compiles against Android SDK 37 for Core KTX 1.19.0 and Compose BOM 2026.08.00
(Compose 1.12), with AGP 9.1.1 and Kotlin 2.4.10. Its target SDK remains 36 and minimum SDK 29;
compiling against newer APIs does not opt the app into Android 17 target behaviours. The Gradle
9.7.1 daemon stays on JDK 25 and app bytecode remains Java 17. SDK provisioning in CI, release
and the dev container uses platform 37.0 with build-tools 36.0.0. Lint reports the expected
`OldTargetApi` warning until the separate target-SDK-37 migration is tested.

Ferrostar 0.54.0 and MapLibre Compose 0.13.0 remain a matched pair of published libraries.
The app's AGP / Kotlin / Compose toolchain is upgraded separately and checked with both debug
builds, JVM tests, lint and R8 release builds; it no longer mirrors Ferrostar's own build tools.
The classic Android DSL opt-out is retained during this upgrade. Dependabot keeps these
coordinated dependencies excluded from automatic updates.

## Configuration flow

Build-time config supplies defaults; a small set of settings is adjustable at runtime (see
**Settings** below). `app/build.gradle.kts` reads each key from `local.properties`
(git-ignored), falling back to an env var named by camelCase → `VIALIX_UPPER_SNAKE`
(`valhallaEndpoint` → `VIALIX_VALHALLA_ENDPOINT`), and emits it as a `buildConfigField`. Blank
values are then replaced by the defaults in `NavConfig.kt`, which is the single source of truth for
them. Adding a config key means touching all three places: `build.gradle.kts`, `NavConfig`, and
`local.properties.example`.

Keys: `valhallaEndpoint`, `routingProfile`, `mapStyleUrl`, `mapStyleUrlDark`, `clientId`,
`voiceGuidance`, `geocoderEndpoint`. Note that `override()` only yields strings, so `voiceGuidance` is emitted as a
string and parsed by a boolean `String?.orDefault` overload in `NavConfig`. `mapStyleUrlDark` has
no default: blank stays `null`, meaning the dark map is derived from `mapStyleUrl` (see **Map style**). The default routing
endpoint is the FOSSGIS public Valhalla server — fair use only (~1 req/s per user), and it expects the
`X-Client-Id` header that `ClientIdInterceptor` adds.

`routingProfile` and `voiceGuidance` are **first-launch defaults only**: once the user touches the
matching control in the Settings screen, the value persisted by `NavSettings` wins. Each setter
persists only its own key, so changing one setting does not freeze the others' defaults (units keep
following the locale until the units picker itself is used).

Plain-`http://` endpoints (a self-hosted Valhalla/Photon reached from the emulator as
`http://10.0.2.2:...`) only work in **debug** builds: `app/src/debug/res/xml/network_security_config.xml`
permits cleartext to `10.0.2.2`, `localhost` and `127.0.0.1` and nothing else; release builds keep
the platform default of blocking all cleartext.

## Architecture

**Object graph.** `NavApplication` creates one `AppGraph` (hand-rolled DI, no framework) that owns
the `NavConfig`, the OkHttp client (shared by routing, geocoding and the style download), the
location provider, the `Geocoder`, the `SavedPlacesRepository`, the `MapStyleLoader`, and the lazily
built `FerrostarCore`.
`MainActivity` reaches it via `(application as NavApplication).graph` and passes it to
`NavigationViewModel.Factory`.

**Ferrostar owns navigation state; the app owns pre-navigation state.** `NavigationViewModel`
extends Ferrostar's `DefaultNavigationViewModel`, so route following, step advance, deviation
detection, and rerouting all live in the Rust core. The app adds a separate `ScreenState` (destination pin, the `preview` as a `RoutePreview`
sum type — `navigation/RoutePreview.kt`, pure, unit-tested: `None` / `Fetching` / `Ready(options,
selected)` / `Failed(error)`, so "fetching and failed" or "a selection without routes" cannot be
represented; a snackbar `error` for the failures that have no preview to sit in; pending Home/Work
assignment, the `activeDestination` guidance is heading to, the `arrival` summary, one-shot
`notice`s) for everything that happens *before* `startNavigation` and *after* the trip completes. `navigationUiState` is overridden to inject the app's own location into
Ferrostar's state while idle, so the puck is visible before a route exists; once navigating,
Ferrostar's snapped location wins, but moved ahead by `DisplayLocationPredictor`
(`navigation/DisplayLocation.kt`, pure, unit-tested): Ferrostar's map view animates the puck and the
following camera towards each new fix over 1 s (`rememberDisplayedNavigationLocation`,
`DISPLAY_LOCATION_ANIMATION_DURATION`), so drawn as-is the position trails the car by one to two
seconds. The predictor shifts the fix along its course by (fix age + 1 s) × speed, deriving speed
and course from the previous fix when the provider omits them, capped at 80 m and skipped below
walking pace; it is idempotent per fix, because the idle location flow keeps re-running the
`combine` during guidance. Only the UI sees the shifted location; the core keeps working on real
fixes. The same split shows up on the map: `RoutePreviewLayer`,
`SavedPlacesLayer` and `DroppedPinLayer` render only while `!isNavigating`, because Ferrostar draws
the active route itself.
Because there is no preview UI during guidance, `selectDestination` (and the map long-press
handler) ignore picks while navigating rather than leaving a stale pin behind for afterwards.

`startNavigation` passes a per-trip `NavigationControllerConfig` chosen from the routing profile
(`NavigationControllerConfigs.forProfile` → `TravelMode` DRIVING / CYCLING / WALKING; unknown costings such as `motorcycle` map to DRIVING). It refuses to
start without a **fresh** fix (`navigation/FixAge.kt`, pure: `UserLocation.isFresh`, 60 s), surfacing
`RouteError.NoLocationFix` and keeping the preview so Start can be tapped again; Ferrostar 0.54's
`startNavigation` declares `UserLocationUnknown` but never throws it (it anchors the session at the
route's first point instead), so the guard is the app's. The same freshness test gates the routing
origin in `fetchRoute` and "Use my current location" for Home/Work, because the idle location flow
keeps its last value while the app is in the background. Route requests hold one `routeJob`: a new
request (Retry, a profile switch, a new pin) cancels the one in flight, so a slow reply cannot land on
top of a newer one; a monotonically increasing request generation and the destination coordinate
guard publication when cancellation loses a race with delivery.
On Android 13+ the `POST_NOTIFICATIONS` permission for the foreground-service notification is
requested when Start is tapped (`NavigationScreen`), not alongside the location dialog at first
launch; guidance starts whatever the answer.

**Arrival.** `NavigationUiState.isNavigating()` is `progress != null`, and `TripState.Complete`
has no progress, so on arrival the screen falls back to its idle chrome by itself; nothing in
Ferrostar stops the core. The ViewModel watches `tripState` for `Complete`, publishes
`ScreenState.arrival` (`navigation/Arrival.kt`, pure, built from Ferrostar's `TripSummary`) which
the screen shows as the `Arrived` sheet mode (`ui/ArrivalSheet.kt`), and calls `stopNavigation()`
after a 5 s grace period (`ARRIVAL_GRACE_MS`) or as soon as Done is tapped (`acknowledgeArrival`).
The grace period exists because `FerrostarCore.stopNavigation()` calls
`spokenInstructionObserver.stopAndClearQueue()` and would cut off the arrival announcement.

**Alternative routes.** The preview asks `ValhallaRouteProvider.getRoutes(..., alternates = 2)`
directly (not `core.getRoutes`, so reroutes still get one route);
`ScreenState.preview` holds the answer (`RoutePreview.of(routes)`: `Ready` with the first selected,
`Failed(NoRouteFound)` for none; `routeOptions`, `selectedRoute` and `routePreview` on `ScreenState`
are derived getters). The sheet shows a chip per option ("25 min · via A1", `Route.viaName()` in
`navigation/RouteVia.kt`, pure) when there is more than one, `RoutePreviewLayer` draws the others
in grey underneath, and the camera frames all of them; that framing effect is keyed on `routeOptions`
only and reads the sheet height once it has held still for 100 ms, because the sheet re-measures when
the address label arrives and re-framing then would undo the user's pan. `ValhallaRouteProvider.optionsJson`
is pure and tested; `getRoutes` reads the response body *before* checking the status, because in
Ferrostar's OkHttp wrapper `bodyBytes()` is the only thing that closes the response.

**Route errors.** A failed request is `RoutePreview.Failed` and is shown inline in the preview
sheet with a Retry (`retryRoute`); `ScreenState.error` is only for failures with no preview to sit
in (Start refused without a fresh fix) and goes to the snackbar. The snackbar effects are keyed on
the error / notice *value* and clear it before showing it, so the same error recurring is a new
key and shows again. A third snackbar, with a *Turn on* action opening the system location
settings, appears while the permission is granted but the system location switch is off
(`isLocationEnabled`, re-checked on every resume). The
preview also has a car / bicycle / walking switcher that writes `NavSettings.routingProfile`
(the settings collector in the ViewModel re-fetches the route through `requestRoute`, which does not
rebind TTS or reverse-geocode again the way `selectDestination` does), and shows the arrival clock time
(`navigation/ArrivalTime.kt`, pure). `ui/RoutingProfiles.kt` is the one list of offered costing
models, shared with the Settings picker.

**Idle location only runs in the foreground.** The ViewModel outlives a backgrounded Activity, so
its 1 Hz `locationUpdates` collection is gated on `hasLocationPermission && isInForeground`;
`NavigationScreen` drives the flag from a `LifecycleStartEffect`. During guidance Ferrostar holds its
own location subscription (and the foreground service), and the fused provider opens a second GPS
request per collector, so the ViewModel's `_location` is then fed from Ferrostar's state
(`LocationSource.GUIDANCE`) instead of the provider; that also keeps it current for the moment the
trip ends. The provider flow has a `.catch`: a permission revoked while the process lives ends the
fused flow (`FusedLocationProvider` closes it) or makes the platform one throw, and neither may
take the app down. Permission is reconciled on every resume in both directions; revocation clears
the cached fix, cancels preview work, stops active guidance and leaves the preview retryable once
permission returns. Asynchronous fused-provider registration failures also close the flow.
`KeepScreenOnDisposableEffect` is applied only while navigating. Decisions in the ViewModel
("are we navigating?") read `core.state.value.isNavigating()`, never the derived
`navigationUiState.value`, which is `WhileSubscribed` and frozen when nothing collects it.
`onCleared()` releases the TTS engine and switches simulation off when no trip is running.

**Errors shown to the user never carry exception text** (an OkHttp message can include the endpoint
URL, API key and all). Failures are reduced to `RequestFailure` (`Offline` / `ServerError(code)` /
`Other`, pure, unit-tested) inside `RouteError.RequestFailed` and `SearchError.RequestFailed`, and
`ui/RequestFailureText.kt` maps them to strings; the exception itself goes to logcat only.

**Map screen layout.** `NavigationScreen` is a Material3 `BottomSheetScaffold` whose content is
Ferrostar's `DynamicallyOrientingNavigationView` plus, while idle, the status-bar scrim, the
full-width search pill (`TopSearchBar`, minimum 56 dp high, with the short “Where to?” prompt) and
the FAB stack (`MapFabStack`, currently only the my-location button). `NavigationMap` owns only what decides the layout (guidance running, sheet mode
and height, camera options) and delegates to composables with narrower state reads, because the
location updates at 1 Hz: `SheetContent` (preview / arrival crossfade), `MapCanvas` (the Ferrostar
view, the app's layers, `IdleChrome`, the FAB; reads the style and saved places itself),
`MyLocationFab` (the only reader of `cameraMode`, which flips on every gesture) and `MapMessages`
(all one-off snackbars). The menu drawer also opens `LicensesScreen` (`ThirdPartyLicenses.kt`, a
hand-maintained list checked by a unit test against every group in the version catalog: a new
dependency cannot ship uncredited). The pill's menu icon opens a `ModalNavigationDrawer` wrapping the whole map
screen (`ui/MenuDrawer.kt`: Settings, version, data credits); its edge-swipe gesture is enabled
only while it is open, so a swipe from the edge pans the map rather than opening the drawer. The sheet has three modes: hidden while idle (the map is unobstructed; there
is deliberately no Home/Work/recents drawer, those live on the search screen) and while navigating
(Ferrostar's progress view owns the bottom), `RoutePreviewSheetContent` and `ArrivalSheetContent`
(never draggable; their peek height is their measured content height). The route preview is capped
at 60% of the map container height: its body scrolls while a Start button at least 56 dp tall stays in a
fixed footer above the navigation bar. Its header close button cancels the preview; there is no
separate Cancel row or drag handle. The labelled profile segments scroll horizontally when screen
width or font scale requires it; the selected route's duration is prominent above the alternative
chips, whose selected state also has a checkmark. The peek height is animated
(`animateDpAsState`) and the content crossfades between modes; the idle chrome uses
`AnimatedVisibility`. The FAB stack sits `FAB_SHEET_GAP` above the sheet's top edge, or above the
navigation bar when the sheet is hidden (`Modifier.offset {}` on `max(peek, bottom inset)`), and
MapLibre's logo / attribution do the same through the view's `ornamentPadding`. Ferrostar's own
idle overlay slot (`withCustomOverlayView`) is not used. Long-presses (map, Home/Work rows, recents)
give `HapticFeedbackType.LongPress`.

During guidance, `SpeedReadout` sits above the trip progress view in its layout slot (portrait
and landscape). It shows the location fix’s speed rounded to whole km/h or mph according to
Settings, using the map chrome colours. Missing, invalid or more than 5-second-old readings
show a dash; a timer expires the readout even when location updates stop. The display predictor
only shifts coordinates, so the readout still uses the measured speed.

**Camera follow modes.** `navigation/CameraFollowMode` (FREE → FOLLOW → HEADING → FREE, pure,
unit-tested) is the my-location button's state; `ui/MapControls.kt` maps it onto Ferrostar's
`NavigationMapState.cameraMode` (`FREE`, `FOLLOW_USER`, `FOLLOW_USER_WITH_BEARING`; `OVERVIEW`
reads as FREE). Ferrostar itself resets the mode to `FREE` on any map gesture, so there is no app
code for "manual pan drops back to free". While idle the view gets `navigationCameraOptions` with
tilt 0, browsing zoom and a bottom padding equal to the sheet peek (0 while idle), so HEADING is a
rotating top-down view centred in the visible part of the map.

**Location puck.** While idle the puck is drawn by `LocationPuckLayer` in `MapLayers.kt` (accuracy
circle, ~60° heading cone only when a course is available, 12 dp dot with a 2.5 dp white ring) from
`NavigationUiState.location`. That course is the GPS course while moving (≥ 1 m/s) and otherwise the
**compass**: `location/CompassHeadingProvider` (rotation-vector sensor, display rotation and
magnetic declination applied, smoothed by `HeadingSmoother`, ≤ 10 Hz, only registered while the
screen is started with permission and no guidance running) feeds `NavigationViewModel.compassHeading`,
and `UserLocation.withCompassHeading` (`navigation/Heading.kt`, pure, unit-tested) merges it into the
idle location injected into `navigationUiState`. Only that injected copy carries the compass:
`NavigationViewModel.location` (routing origin, search distances) keeps the raw fix, because
Ferrostar's Valhalla request sends the origin's course as the start `heading`. Ferrostar's
`TrackingCameraEffect` reads the same course, so the HEADING camera mode turns with the phone too; the view is given `showDefaultPuck = isNavigating` so Ferrostar's own
arrow puck (Ferrostar's `NavigationMapPuckStyle`, built per theme by `navigationPuckStyle(paint)` in
`MapLayers.kt`) takes over during guidance. Custom layers were needed
because neither `NavigationMapPuckStyle` nor MapLibre Compose's `LocationPuck` exposes a heading
cone — their bearing indicator is a dot-sized triangle.

**Saved places.** `places/` is UI-free: `SavedPlaces` (value + dedupe + 20-entry cap + removal),
`SavedPlacesCodec` (org.json) and `SavedPlacesRepository` (`StateFlow` over a one-string
`KeyValueStore`, bound to `SharedPreferences` in `AppGraph`). `startNavigation` adds the destination
to recents. Set Home/Work are drawn on the idle map by `SavedPlacesLayer` (`ui/MapLayers.kt`: teal
circle, `MapPaint.favorite`, with the white `ic_home`/`ic_work` glyph; a tap on the circle layer's
`onClick` calls `selectDestination` with the saved place, and the marker whose coordinate is the
current destination is skipped so the red pin does not sit on top of it). Everything else is managed
on the **search screen** (the map screen has no drawer):
the rows and dialogs are in `ui/PlaceRows.kt`. Home and Work are always listed under a “Saved places”
heading, followed by “Recent”; an empty recents list explains that destinations appear after
navigation starts and omits the Clear action. Saved-place and search-result rows share a 40 dp
icon footprint, prominent names and secondary addresses. An unset Home/Work row reads
"Set location" and a tap (or a long-press on a set one) opens `FavoriteOptionsDialog` (search for
an address / use the current fix / remove). A long-press on a recent removes it and the header's
Clear empties the list. "Search for an address" goes through `ScreenState.favoriteToAssign`
(`beginFavoriteAssignment`, the screen shows a banner and hides the shortcuts) + `onPlacePicked`,
which `MainActivity` calls instead of `selectDestination`; `cancelFavoriteAssignment` runs when
search is closed without a pick, and a successful save posts `Notice.FavoriteSaved` for the map
snackbar. "Use my current location" (`setFavoriteToCurrentLocation`) returns `false` without a fix
and the search screen shows a snackbar. There are no Home/Work ETAs (nothing shows them, and they
would cost public Valhalla requests).

**Map style.** There is one basemap, `NavConfig.mapStyleUrl` (OpenFreeMap "liberty"); the dark
theme is **derived from it at runtime** by `map/NightStylePatch` (pure, unit-tested), because the
ready-made dark styles on the free hosts (OpenFreeMap "dark"/"fiord", VersaTiles "eclipse") are
data-visualisation styles: black roads on a near-black ground, dim labels, no POIs. The patch keeps
hue and alpha and remaps lightness/saturation of every `paint` `*-color` (colour literals inside
expressions and legacy `stops` included) by role: ground (background, fills, non-road lines), road
(`transportation`/`aeroway` lines), casing (id contains `casing`, kept darker than the road fill),
text, halo; raster layers get `raster-brightness-max` 0.35. The bands live in one table in
`NightStylePatch`; tune there. Sprite icons (POI glyphs, arrows) are bitmaps and keep their day
colours; the shield layers have no `paint` and are untouched on purpose. Colour parsing/formatting
is `map/CssColor.kt` (pure, hand-rolled HSL; `android.graphics.Color` is a stub in tests).
`mapStyleUrlDark` is optional: `null` (blank) means "derive"; set, it is loaded as-is with only the
POI patch (`NavConfig.mapStyleUrlFor(dark)` / `derivesNightStyle(dark)`, both pure). `MainActivity` pushes the resolved dark flag through `MapStyleController.onDarkThemeChanged`, and
its collector loads the matching style whenever the flag changes (`collectLatest`, so a flip
mid-download cancels it). The style URL is remote, so the patches are
applied at runtime: `MapStyleLoader.load(url, night)` downloads the style (remembering the last
successfully patched body, so a theme toggle re-patches without a second download while malformed
or interrupted responses remain retryable), `PoiLabelStylePatch` (pure, unit-tested;
`text-max-width` 8, `text-letter-spacing` 0 on POI symbol layers) then
`NightStylePatch` edit it, and `map/MapStyleController` (app-scoped, owned by `AppGraph`; the
ViewModel knows nothing about styles or the theme) publishes a `MapStyleState`: `Loading` (the screen
shows an inline style with only a theme-coloured background, `emptyStyleJson(dark)`, so the map is
not loaded twice and does not flash; nothing loads until the Activity has reported the theme),
`Patched(json)` (passed as `BaseStyle.Json`) or `Unavailable(styleUrl, downloadFailed)` (that URL is
loaded as-is and the patches are lost; in the derived dark theme that means the light map).
`downloadFailed` is `true` for a failed or timed-out (3 s) download, in which case the plain URL will
most likely not load either, so the screen shows a snackbar with Retry (`MapStyleController.retry()` bumps an attempt counter the loader is keyed on); it is `false` for a style that downloaded fine
but uses relative URLs and cannot be inlined. A later theme change leaves the current style up
until the new one is ready, so the map swaps once; MapLibre keeps the camera across the swap but
re-adds every layer.

**Destination search.** `search/` is UI-free: `Geocoder` is the interface (forward `search` and
`reverse`), `PhotonGeocoder` the OkHttp implementation (URL building is pure: `buildUrl` and
`buildReverseUrl`, which swaps Photon's `/api` for `/reverse`; response parsing a pure
`PhotonResponseParser`; all unit-tested), `SearchViewModel` the typeahead. `NavigationViewModel`
uses `reverse` to label long-pressed pins (`resolveAddress`, best effort: the pin keeps saying
"Dropped pin" on failure); the one reverse-lookup job is cancelled when its pin changes or is
dismissed. The ViewModel is
separate from `NavigationViewModel` and activity-scoped, so keystrokes never recompose the map and
the query survives rotation. Its pipeline is `merge(query.debounce(400), submits).collectLatest {}`:
a newer query cancels the running request on the wire (`PhotonGeocoder` wraps `Call.enqueue` in
`suspendCancellableCoroutine`), while the delayed debounce echo of a submitted query is filtered
before `collectLatest` so it cannot restart the same request. Inside `runSearch`,
`CancellationException` **must be rethrown**, not swallowed by `runCatching`, or
superseded requests show phantom errors. A query dropping below `MIN_QUERY_LENGTH` (or `reset()`)
emits on a `clears` flow merged into the same pipeline, so the request in flight is cancelled at
once rather than completing after the 400 ms debounce and repopulating results the user abandoned. Picking a result calls
`NavigationViewModel.selectDestination(Destination.of(place))`, the same path as a long-press;
`Destination` carries the optional name/address that `DestinationSheet` shows instead of
"Dropped pin" + coordinates. While the query is shorter than `MIN_QUERY_LENGTH` the screen lists
Home/Work and the recents instead (with a banner while a Home/Work assignment is pending, see
**Saved places**); result rows show the distance from the user via `navigation/Geo.kt`.
The screen consumes scaffold insets before applying keyboard padding to the entire body, so
lists and the scrollable loading/no-results/error states stay above the keyboard. Empty results
suggest another place name or a fuller address; failures retain the Retry action. Old results
remain visible while a replacement query loads.
Search results are sorted nearest-first by straight-line distance using the latest location when
the response arrives; without a location, Photon's order is preserved. Voice search (`ui/VoiceSearch.kt`)
uses the system `RecognizerIntent` (manifest `<queries>` entry) and feeds the text through
`SearchViewModel.onQueryChanged` + `submit`; the mic is hidden when no recogniser is installed.
`SearchScreen` re-seeds its local `TextFieldValue` only when `state.query` differs from it, so
external changes (voice, `reset()`) show up without re-introducing the cursor jump. Photon only labels in en/de/fr/it (`photonLanguage`); other guidance
languages omit `lang`. Unit tests use the real `org.json` (`testImplementation`) because the one in
the mockable `android.jar` throws `Stub!`.

**Settings.** `settings/NavSettings` is the one runtime store (SharedPreferences behind a
`StateFlow<Settings>`). Only `NavigationViewModel` and the Settings screen take the store itself
(they call its setters); `SearchViewModel`, `ValhallaRouteProvider` and `VoiceGuidance` take the
`StateFlow<Settings>` so they can be unit-tested without Android. The store holds: guidance language (`null` = device locale), voice on/off, routing profile,
units (first-launch default from `DistanceUnits.forLocale`: the phone's "Measurement system"
regional preference, which Android 14+ appends to the default locale as the `ms` unicode extension,
else the region: US/GB/LR/MM → miles), the Appearance `themeMode` (System / Light / Dark), plus the session-only `simulateDriving` (debug builds; shown under Settings > Developer when
`showDeveloperOptions`, never persisted, and additionally gated on `BuildConfig.DEBUG` in
`startNavigation`). It also owns the one-shot `consumeFirstLaunchHint()` flag behind the
first-launch snackbar. Consumers observe `state` rather than reading preferences: `VoiceGuidance` collects it to
mute/unmute and re-point the TTS voice, `NavigationViewModel` collects it to re-fetch a pending
route preview, and `NavigationScreen` reads it for units formatting. Writes only go through the
setters — including Ferrostar's own mute button, whose `toggleMute()` is overridden to write
`voiceEnabled` instead of flipping the observer, so the button, the settings switch and the stored
value cannot diverge. `settings/Settings.kt` is pure and unit-tested; `NavSettings` is Android-only and its `load` only
reads the raw preferences into a `StoredSettings`: the per-key fallbacks and the migrations (a
guidance language Valhalla dropped, a retired routing profile such as `motorcycle`, an unknown enum
name) are `Settings.restore`, pure and tested.

**App language.** `Settings.uiLanguageTag` (`null` = device language) is the interface language,
independent of the guidance language. The offered list is `settings/UiLanguage.kt` (pure:
`SUPPORTED_TAGS` = en, de, es, fr, it; `resolve(locale)` names the translation the device language
gets, for the "System default (…)" label); `UiLanguageTest` checks that every tag except `en` has
a `res/values-<tag>/strings.xml` and vice versa. Where the value lives depends on the API level,
hidden in `settings/AppLocales.kt`: on API 33+ the platform per-app locale (`LocaleManager`) is
the store, so the in-app picker and Settings > Apps > Vialix > Language are one setting
(`NavSettings.syncUiLanguageFromSystem()`, called from `MainActivity.onCreate`, picks up a change
made in the OS screen while the process was alive); on API 29–32 it is the `ui_language`
preference, applied by `MainActivity.attachBaseContext` through `Context.withAppLocale`
(`createConfigurationContext` + `Locale.setDefault`, so "System default" guidance, units and
formatting follow the app language there as the framework makes them on 33+). Changing it recreates
the Activity (the framework does it on 33+; the Settings screen calls `recreate()` below), which
tears down the MapLibre view once — keep `locale` out of `configChanges`, the recreate is what
applies the strings. `generateLocaleConfig = true` in `build.gradle.kts` (with
`res/resources.properties` `unqualifiedResLocale=en`) derives `android:localeConfig` from the
`values-*` folders. No AppCompat: `AppCompatDelegate.setApplicationLocales` would need an
`AppCompatActivity` and theme.

**Theme.** `Settings.resolvesToDark(systemDark)` (pure) is the one answer to "is the app dark?".
`MainActivity` computes it once from the store and `isSystemInDarkTheme()` and fans it out:
`VialixTheme(darkTheme)` (Material 3 with dynamic colour, which also provides `LocalDarkTheme`;
below API 31 it falls back to the static schemes in `ui/theme/Color.kt`, full tonal palettes
generated with material3's HCT solver from the seed `LocationBlue` so that `primaryContainer`,
`inversePrimary`, `secondary` etc. are blue-derived rather than the baseline purple; if the seed
changes, regenerate every tone together, and keep `values(-night)/themes.xml` `windowBackground`
on the new `surface`),
`SystemBarGlyphs` (light glyphs when dark, both bars, no special cases: every surface that can sit
under a bar follows the same flag), and `graph.mapStyle.onDarkThemeChanged` for the basemap. The manifest
lists `uiMode` in `configChanges` so a system or per-app night-mode change does not recreate the
Activity and tear down the MapLibre view; Compose re-reads `LocalConfiguration` instead.
`NavSettings.setThemeMode` also calls `settings/AppNightMode.kt` (`UiModeManager.setApplicationNightMode`,
API 31+, persisted by the system per app; `MODE_NIGHT_CUSTOM` means "follow the system" there) so
the launch window and `values-night` resources match the choice from the next cold start; on API
29–30 the pre-Compose frame follows the system and may flash briefly when the choice differs.
`values(-night)/themes.xml` set `windowBackground` close to the Compose surfaces for the same reason.

**Routing goes through `routing/ValhallaRouteProvider`, a `CustomRouteProvider`, on purpose.**
Ferrostar's `WellKnownRouteProvider.Valhalla` is converted into a native `RouteAdapter` when
`FerrostarCore` is constructed, and the core holds that adapter for life — profile, `units` and
`language` cannot be changed afterwards, and the core cannot be rebuilt because
`DefaultNavigationViewModel` captures it. The custom provider is called on every request (reroutes
included) and builds a fresh adapter from the current `NavSettings`, reusing Ferrostar's request
generation and response parsing. Note Ferrostar's `toJson` helper is `internal`; the options JSON
is built with `org.json`.

**Units in Ferrostar's own views.** `NavigationViewComponentBuilder.Default()` formats distances for
the device locale with no injection point, so `NavigationScreen` supplies `withInstructionsView` /
`withProgressView` that call the same public `InstructionsView` / `TripProgressView` with the
`LocalizedDistanceFormatter` from `rememberDistanceFormatter(settings)` (`ui/Formatters.kt`: units
from `Settings.units`, locale from the *guidance* language so the banner's numbers match its text;
`rememberClockTimeFormatter` next to it is the arrival clock). The same formatter is handed to
`RoutePreviewSheetContent`, `ArrivalSheetContent` and (via `MainActivity`) the search results, so
every distance in the app agrees. **Durations are not Ferrostar's**: its `LocalizedDurationFormatter`
writes English unit letters whatever the locale, and "25 m" next to "12 km" reads as metres, so
`rememberDurationFormatter` builds an `IcuDurationFormatter` (ICU `MeasureFormat`, guidance locale)
over the pure `durationParts` split (rounded to the minute, never "0 min"; unit-tested). The
arrival clock in the preview is read from a `produceState` ticking every 30 s, not from
composition. The `NavigationViewComponentBuilder` is `remember`ed per formatter: it is a data class
compared by its lambdas, and a fresh one per recomposition would invalidate Ferrostar's view on
every fix. Ferrostar's `Route` only has per-step durations; `navigation/Routes.kt` adds
`Route.durationSeconds` for the trip total.

**Rerouting** is configured in `AppGraph`: `deviationHandler` asks for new routes to the remaining
waypoints, `alternativeRouteProcessor` swaps the first one in via `core.replaceRoute` (the session
builder remembers the per-trip config, so the single-argument call keeps the cycling/walking
thresholds). Thresholds for step advance and deviation live in `NavigationControllerConfigs`
(`driving()` is also the core's default; `cycling()` and `walking()` are passed per trip, see
above). `RouteDeviationTracking.StaticThreshold` takes the **accuracy gate first, the deviation
second**: fixes with a worse horizontal accuracy than the gate are skipped and never count as off
route, so the gate is deliberately wider than the deviation (driving 40 m / 30 m, cycling 30 / 20,
walking 30 / 25); Ferrostar's demo values (15 m gate, 50 m) skipped most fixes under trees and
between buildings and a missed turn often never triggered a reroute. The core's own throttles are
set from `navigation/RerouteTuning.kt`: `minimumTimeBeforeRecalculation` 3 s (counted from the
*end* of the previous request) and `minimumMovementBeforeRecalculation` 20 m (from where the
previous request *started*, never cleared on failure), and reroutes go through a second OkHttp
client with an 8 s call timeout (`ValhallaRouteProvider.rerouteHttpClient`, same pool and
interceptors; the preview keeps the 15 s one). The core swallows reroute failures after one
`FerrostarCore` log line, so the provider's `CustomRouteProvider` override logs them too. While
completely off route the core blanks the instruction banner; `ReroutingBanner`
(`ui/ReroutingBanner.kt`, `NavigationUiState.isRerouting()`) fills the slot, and the ViewModel speaks
"Rerouting" once per departure through `VoiceGuidance.announceRerouting()` (guidance language, via
the same observer so mute applies; `navigation/RerouteAnnouncer.kt` is the pure rising-edge + 10 s
gap policy, unit-tested). Because `replaceRoute` stops speech and clears the queue, the processor defers the swap by
`VoiceGuidance.remainingAnnouncementMs()` (≤ 1.5 s; the arithmetic is `voice/AnnouncementTimer`,
pure, volatile field because the writer and the reader are on different threads) when the word is
still being spoken. The deferred swap is one main-thread `pendingRouteSwap` job in `AppGraph`: a newer
reroute supersedes it, and a collector on `core.state` cancels it when the trip ends, so a route
fetched for one trip cannot be swapped into the next. "Simulate driving" cannot exercise any of this: the simulated provider follows the
route exactly; use a real drive or the emulator's Extended controls > Location > Routes with a
track that leaves the planned route.

**External requests.** `geo:` and `google.navigation:` links (manifest intent filters on
`MainActivity`, which is `singleTask` so they reach the running instance through `onNewIntent`) are
parsed by `navigation/GeoIntent.kt` (pure, unit-tested) into a `GeoTarget`: a `Place` (coordinate,
optional label; `q=` wins over the path, `0,0` is a placeholder) goes directly through
`selectDestination`, while a `Query` resets and seeds `SearchViewModel` before opening search.
Both cancel a pending Home/Work assignment first. `MainActivity` parks the target in a
`MutableStateFlow` until the composition takes it and saves the source URI only while it remains
pending. Consumed launch intents are not parsed again after an Activity recreation; `onNewIntent`
still treats every newly delivered link as a new request.

**Voice guidance** is Ferrostar's `AndroidTtsObserver`, wrapped in `voice/VoiceGuidance.kt`, which
adds the two things Ferrostar leaves to the app: the mute preference is persisted to
`NavSettings` (via the `toggleMute()` override in `NavigationViewModel`, which Ferrostar's own mute
button calls), and the TTS voice language follows `Settings.resolvedLanguageTag()` — the same tag
`ValhallaRouteProvider` sends as the `language` routing option, so text and voice agree. Audio
focus, ducking and the mute button UI all come from Ferrostar for free.

The observer **must be attached to `FerrostarCore` before `NavigationViewModel` is constructed** —
`DefaultNavigationViewModel` captures `spokenInstructionObserver?.muteState` once in its
constructor, and if it is null then `NavigationUiState.isMuted` stays null and the navigation view
hides its mute button. That is why `VoiceGuidance` lives in `AppGraph`, not the ViewModel. The TTS
engine is bound on `selectDestination`/`startNavigation` and released in `stopNavigation`.

**Location** is chosen once at startup in `AppGraph`: `location/FusedLocationProvider.create(context)`
returns Google Play's fused location provider (`play-services-location`, the one proprietary
dependency, `full` flavour only; the `foss` flavour's copy of the file always returns `null`) when
`GoogleApiAvailability` reports Play Services usable, and `null` otherwise, in which case the platform `LocationManager` via Ferrostar's
`AndroidLocationProvider` is used. Both implement Ferrostar's `NavigationLocationProviding` and
yield `android.location.Location`, so nothing downstream knows which one is active; one `Location`
logcat line says so. The live provider is wrapped in a `NavigationLocationProvider` alongside a
`SimulatedLocationProvider` that the debug-only "Simulate driving" switch (Settings > Developer)
switches on. `FusedLocationProvider` is Android/GMS-bound and untested like the compass; it emits
the last known fix first (as `AndroidLocationProvider` does, so the puck shows at once), carries
`@SuppressLint("MissingPermission")` because the ViewModel and `startNavigation` only reach it
after `hasLocationPermission` (Lint's `MissingPermission` is an error), and turns a late
`SecurityException` into `null` (last fix) or the end of the flow (updates). The compass listener
sizes its rotation-vector array from the event: `getRotationMatrixFromVector` decides by the
destination length whether the fourth quaternion component is present, so a 3-value sensor given a
4-element array yields a wrong azimuth. Guidance survives backgrounding through Ferrostar's
`FerrostarForegroundService`, declared in the manifest.

**Domain code is kept UI-free on purpose** — the plan is to move it into a Kotlin Multiplatform
module so the iOS app can share it with Ferrostar's Swift bindings. Prefer keeping navigation and
config logic out of composables.

