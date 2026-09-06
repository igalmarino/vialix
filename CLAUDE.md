# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Vialix — an Android turn-by-turn navigation app (proof of concept, v0.1) built only on open data
and open-source components: OpenStreetMap, MapLibre, Valhalla routing, and the Ferrostar
navigation core. GPL-3.0-or-later. See `README.md` for the tech-stack rationale and roadmap.

## Commands

```sh
./gradlew assembleDebug                 # build the debug APK
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew lintDebug                     # Android Lint (report: app/build/reports/lint-results-debug.*)
./gradlew assembleRelease               # R8-minified, resource-shrunk (unsigned) release APK
./gradlew testDebugUnitTest --tests "com.galmarino.vialix.NavConfigTest"                 # one class
./gradlew testDebugUnitTest --tests "com.galmarino.vialix.ui.FormattersTest.*coordinates*"  # one test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires an Android SDK with `platforms;android-36` + `build-tools;36.0.0`. The dev container
installs these and exports `ANDROID_HOME=/opt/android-sdk`; outside it, copy
`local.properties.example` to `local.properties` and set `sdk.dir`.

**Which JDK the build runs on.** Gradle 9.2.1 supports Java 25 at most; on JDK 26 the VS Code
Gradle/Java extensions report an incompatible pair and AGP's `JdkImageTransform` fails (`jlink`
error on `core-for-system-modules.jar`). `gradle/gradle-daemon-jvm.properties` therefore pins the
daemon with `toolchainVersion=25`, so `./gradlew` selects a Java 25 toolchain by auto-detection no
matter what `java`/`JAVA_HOME` point at. Building on a machine without a JDK 25 installed fails
with a toolchain-not-found message; install one (in the dev container it is
`/usr/lib/jvm/msopenjdk-current`) rather than deleting the pin.

(The project targets Java 17 bytecode; the README's "JDK 17" is the reference toolchain, and no
JDK 17 is installed in the container.)

There is no instrumented-test source set. CI is `.github/workflows/ci.yml` (JDK 25, SDK 36:
`assembleDebug testDebugUnitTest lintDebug`, then `assembleRelease` to keep R8 honest).

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
detection, and rerouting all live in the Rust core. The app adds a separate `ScreenState`
(destination pin, route preview, fetch/error status, pending Home/Work assignment, the
`activeDestination` guidance is heading to, the `arrival` summary, one-shot `notice`s) for
everything that happens *before* `startNavigation` and *after* the trip completes. `navigationUiState` is overridden to inject the app's own location into
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
(`NavigationControllerConfigs.forProfile` → `TravelMode` DRIVING / CYCLING / WALKING; unknown costings such as `motorcycle` map to DRIVING) and catches
Ferrostar's `UserLocationUnknown`, surfacing it as `RouteError.NoLocationFix` and keeping the
preview so Start can be tapped again.

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
`ScreenState.routeOptions` + `selectedRoute` hold the answer and `routePreview` is the selected
one. The sheet shows a chip per option ("25 min · via A1", `Route.viaName()` in
`navigation/RouteVia.kt`, pure) when there is more than one, `RoutePreviewLayer` draws the others
in grey underneath, and the camera frames all of them. `ValhallaRouteProvider.optionsJson` is pure
and tested.

**Route errors** are shown inline in the preview sheet with a Retry (`retryRoute`) while a
destination is selected; the snackbar only gets errors with no destination to attach to. The
preview also has a car / bicycle / walking switcher that writes `NavSettings.routingProfile`
(the settings collector in the ViewModel re-fetches the preview), and shows the arrival clock time
(`navigation/ArrivalTime.kt`, pure). `ui/RoutingProfiles.kt` is the one list of offered costing
models, shared with the Settings picker.

**Idle location only runs in the foreground.** The ViewModel outlives a backgrounded Activity, so
its 1 Hz `locationUpdates` collection is gated on `hasLocationPermission && isInForeground`;
`NavigationScreen` drives the flag from a `LifecycleStartEffect`. During guidance Ferrostar holds its
own location subscription (and the foreground service), so this only affects the idle puck and
route requests. `KeepScreenOnDisposableEffect` is likewise applied only while navigating.

**Errors shown to the user never carry exception text** (an OkHttp message can include the endpoint
URL, API key and all). Failures are reduced to `RequestFailure` (`Offline` / `ServerError(code)` /
`Other`, pure, unit-tested) inside `RouteError.RequestFailed` and `SearchError.RequestFailed`, and
`ui/RequestFailureText.kt` maps them to strings; the exception itself goes to logcat only.

**Map screen layout.** `NavigationScreen` is a Material3 `BottomSheetScaffold` whose content is
Ferrostar's `DynamicallyOrientingNavigationView` plus, while idle, the status-bar scrim, the
full-width search pill (`TopSearchBar`) and the FAB stack (`MapFabStack`, currently only the
my-location button). The pill's menu icon opens a `ModalNavigationDrawer` wrapping the whole map
screen (`ui/MenuDrawer.kt`: Settings, version, data credits); its edge-swipe gesture is enabled
only while it is open, so a swipe from the edge pans the map rather than opening the drawer. The sheet has three modes: hidden while idle (the map is unobstructed; there
is deliberately no Home/Work/recents drawer, those live on the search screen) and while navigating
(Ferrostar's progress view owns the bottom), `RoutePreviewSheetContent` and `ArrivalSheetContent`
(never draggable; their peek height is their measured content height). The peek height is animated
(`animateDpAsState`) and the content crossfades between modes; the idle chrome uses
`AnimatedVisibility`. The FAB stack sits `FAB_SHEET_GAP` above the sheet's top edge, or above the
navigation bar when the sheet is hidden (`Modifier.offset {}` on `max(peek, bottom inset)`), and
MapLibre's logo / attribution do the same through the view's `ornamentPadding`. Ferrostar's own
idle overlay slot (`withCustomOverlayView`) is not used. Long-presses (map, Home/Work rows, recents)
give `HapticFeedbackType.LongPress`.

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
the rows and dialogs are in `ui/PlaceRows.kt`. Home and Work are always listed; an unset one reads
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
POI patch (`NavConfig.mapStyleUrlFor(dark)` / `derivesNightStyle(dark)`, both pure). The ViewModel
does not know the theme itself: `MainActivity` pushes the resolved dark flag through
`onDarkThemeChanged`, and a collector loads the matching style whenever the flag changes
(`collectLatest`, so a flip mid-download cancels it). The style URL is remote, so the patches are
applied at runtime: `MapStyleLoader.load(url, night)` downloads the style (remembering the last
body, so a theme toggle re-patches without a second download), `PoiLabelStylePatch` (pure,
unit-tested; `text-max-width` 8, `text-letter-spacing` 0 on POI symbol layers) then
`NightStylePatch` edit it, and the ViewModel publishes a `MapStyleState`: `Loading` (the screen
shows an inline style with only a theme-coloured background, `emptyStyleJson(dark)`, so the map is
not loaded twice and does not flash; nothing loads until the Activity has reported the theme),
`Patched(json)` (passed as `BaseStyle.Json`) or `Unavailable(styleUrl)` (download failed, timed out
after 3 s, or the style uses relative URLs: that URL is loaded as-is and the patches are lost; in
the derived dark theme that means the light map). A later theme change leaves the current style up
until the new one is ready, so the map swaps once; MapLibre keeps the camera across the swap but
re-adds every layer.

**Destination search.** `search/` is UI-free: `Geocoder` is the interface (forward `search` and
`reverse`), `PhotonGeocoder` the OkHttp implementation (URL building is pure: `buildUrl` and
`buildReverseUrl`, which swaps Photon's `/api` for `/reverse`; response parsing a pure
`PhotonResponseParser`; all unit-tested), `SearchViewModel` the typeahead. `NavigationViewModel`
uses `reverse` to label long-pressed pins (`resolveAddress`, best effort: the pin keeps saying
"Dropped pin" on failure); because `Destination` is compared by value and the label changes
mid-flight, the route staleness checks compare coordinates, not destinations. The ViewModel is
separate from `NavigationViewModel` and activity-scoped, so keystrokes never recompose the map and
the query survives rotation. Its pipeline is `merge(query.debounce(400), submits).collectLatest {}`:
a newer query cancels the running request on the wire (`PhotonGeocoder` wraps `Call.enqueue` in
`suspendCancellableCoroutine`). Inside `runSearch`, `CancellationException` **must be rethrown**, not
swallowed by `runCatching`, or superseded requests show phantom errors. Picking a result calls
`NavigationViewModel.selectDestination(Destination.of(place))`, the same path as a long-press;
`Destination` carries the optional name/address that `DestinationSheet` shows instead of
"Dropped pin" + coordinates. While the query is shorter than `MIN_QUERY_LENGTH` the screen lists
Home/Work and the recents instead (with a banner while a Home/Work assignment is pending, see
**Saved places**); result rows show the distance from the user via `navigation/Geo.kt`. Voice search (`ui/VoiceSearch.kt`)
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
value cannot diverge. `settings/Settings.kt` is pure and unit-tested; `NavSettings` is Android-only.

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
under a bar follows the same flag), and `viewModel.onDarkThemeChanged` for the basemap. The manifest
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
`rememberClockTimeFormatter` next to it is the arrival clock). The same formatter and Ferrostar's
`LocalizedDurationFormatter` are handed to `RoutePreviewSheetContent`, `ArrivalSheetContent` and (via
`MainActivity`) the search results, so every distance and duration in the app agrees; the app has no
formatting logic of its own, which is why `ferrostar-ui-formatters` is a direct dependency.
Ferrostar's `Route` only has per-step durations; `navigation/Routes.kt` adds `Route.durationSeconds`
for the trip total.

**Rerouting** is configured in `AppGraph`: `deviationHandler` asks for new routes to the remaining
waypoints, `alternativeRouteProcessor` swaps the first one in via `core.replaceRoute`. Thresholds
for step advance and deviation live in `NavigationControllerConfigs` (`driving()` is also the
core's default; `cycling()` and `walking()` are passed per trip, see above).

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
dependency; no `foss` flavor yet) when `GoogleApiAvailability` reports Play Services usable, and
`null` otherwise, in which case the platform `LocationManager` via Ferrostar's
`AndroidLocationProvider` is used. Both implement Ferrostar's `NavigationLocationProviding` and
yield `android.location.Location`, so nothing downstream knows which one is active; one `Location`
logcat line says so. The live provider is wrapped in a `NavigationLocationProvider` alongside a
`SimulatedLocationProvider` that the debug-only "Simulate driving" switch (Settings > Developer)
switches on. `FusedLocationProvider` is Android/GMS-bound and untested like the compass; it emits
the last known fix first (as `AndroidLocationProvider` does, so the puck shows at once), carries
`@SuppressLint("MissingPermission")` because the ViewModel and `startNavigation` only reach it
after `hasLocationPermission` (Lint's `MissingPermission` is an error), and swallows a late
`SecurityException` into `null`. Guidance survives backgrounding through Ferrostar's
`FerrostarForegroundService`, declared in the manifest.

**Domain code is kept UI-free on purpose** — the plan is to move it into a Kotlin Multiplatform
module so the iOS app can share it with Ferrostar's Swift bindings. Prefer keeping navigation and
config logic out of composables.

## Conventions

- Dependency versions live only in `gradle/libs.versions.toml`. AGP, Kotlin, Compose and
  especially `maplibre-compose` intentionally mirror what Ferrostar 0.54.0 is built against —
  bumping `maplibreCompose` off Ferrostar's version breaks the UI modules at runtime.
- `android.newDsl=false` in `gradle.properties` keeps AGP 9 on the classic DSL, matching Ferrostar.
- Ferrostar's Rust bindings are imported as `uniffi.ferrostar.*` (`Route`, `GeographicCoordinate`,
  `Waypoint`, ...). Those types are the app's domain model; don't wrap them without reason.
- Unit tests are plain JUnit 4 on the JVM and cover the pure pieces (see `app/src/test`;
  `SearchViewModel` is driven with a scripted `Geocoder`, `StandardTestDispatcher` +
  `Dispatchers.setMain`). `unitTests.isReturnDefaultValues`
  is on, so `android.util.Log` is a no-op in tests. Pin the locale when testing anything that goes
  through `String.format`, and pass an explicit `Locale` to `SpokenLanguage` rather than relying on
  the ambient default. The `NavigationControllerConfigs` builders call uniffi functions (native) and
  cannot run on the JVM; only the `TravelMode` mapping is tested.
- All user-facing strings go through `res/values/strings.xml` **and every translation**
  (`values-de`, `values-es`, `values-fr`, `values-it`): Lint's `MissingTranslation` is an error and
  CI runs `lintDebug`, so a new string needs all five files (or `translatable="false"`).
  `RouteError` variants are mapped to
  them in `ui/RouteErrorText.kt`, `SearchError` in `SearchScreen.errorText`, both via
  `failureText` for the shared `RequestFailure` cases. The menu credits take the provider names
  from `Attribution.creditFor(endpoint)`.
- Release builds run R8 with resource shrinking (`proguard-rules.pro` plus Ferrostar's consumer
  rules for JNA/uniffi), and `ndk.abiFilters` keeps arm64-v8a, armeabi-v7a and x86_64 only.
- Icons are hand-rolled 24 dp vector drawables in `res/drawable/` (Material Symbols path data),
  not an icons library — keeps the dependency set mirroring Ferrostar's.
- Chrome drawn over the map (search pill, status-bar scrim, FAB stack) and the app's own map
  layers (puck, pin, Home/Work markers, route preview, Ferrostar's arrow puck via `navigationPuckStyle`) key on the
  resolved dark flag (`LocalDarkTheme`), **not** on `MaterialTheme.colorScheme`: `mapChromeColors(dark)`
  in `ui/MapChrome.kt` (white with a 0.5 dp outline over the light basemap, near-black over the dark
  one) and `mapPaint(dark)` in `ui/MapLayers.kt`, both pure and unit-tested. This chrome is the one
  deliberate departure from Material 3 colour roles; everything else uses M3 components with their
  default tokens (`ModalNavigationDrawer` behind the hamburger icon, `BottomSheetDefaults` for the preview/arrival sheet, `ListItem` rows inside
  dialogs, `SegmentedButtonDefaults.Icon` for the profile switcher, `titleSmall` list subheaders
  shared through `SectionHeaderRow`). The colour scheme's
  tones move with the wallpaper under dynamic colour while the basemap is exactly one of two
  styles, so the chrome has to be keyed on the style, and the FAB's active tint is the chrome accent
  (matching the puck) rather than `primary`. `NavigationScreen` reads the paint once and passes it
  down, because MapLibre composes the layers in its own tree. The preview / arrival sheet and
  everything else follow the theme. `formatCoordinates` always uses a decimal point (`Locale.ROOT`).
- There is no navigation library. `MainActivity` overlays `SettingsScreen` and `SearchScreen` on
  top of `NavigationScreen` in a `Box` (inside `AnimatedVisibility`, fade + slide) so the MapLibre
  view is never torn down; each screen's own `BackHandler` closes it.
