# Vialix

Open-source turn-by-turn navigation for Android (iOS planned), built entirely on open data and
open-source components: OpenStreetMap data, MapLibre rendering, Valhalla routing, Photon
geocoding and the Ferrostar navigation core. The name comes from Latin *via*, road.

**Status: proof of concept (v0.1).** Android only. It shows your position on a map, lets you
search for a destination or long-press one, fetches a driving route, and guides you along it with
a manoeuvre banner, spoken turn-by-turn announcements, automatic re-routing when you leave the
route, and a foreground notification so guidance survives backgrounding.

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | Native performance; domain code is UI-free so it can move into a Kotlin Multiplatform module for iOS |
| Navigation core | [Ferrostar](https://github.com/stadiamaps/ferrostar) 0.54 | BSD-3, Rust core with Kotlin **and Swift** bindings: route following, step advance, deviation detection, rerouting, banner UI |
| Map rendering | [MapLibre Compose](https://github.com/maplibre/maplibre-compose) 0.13 (MapLibre Native) | Vector tiles, GPU rendering, the version Ferrostar links against |
| Map tiles | [OpenFreeMap](https://openfreemap.org) "liberty" style | OSM-based, free, no API key |
| Routing | [Valhalla](https://github.com/valhalla/valhalla) via the public FOSSGIS server | Open-source engine; the endpoint is a config value so any Valhalla instance works |
| Geocoding | [Photon](https://github.com/komoot/photon) via komoot's public instance | Open-source OSM geocoder built for search-as-you-type; endpoint configurable, self-hostable |
| Location | Android `LocationManager` (no Google Play Services) | Keeps the app fully FOSS / F-Droid friendly |
| Build | Android Gradle Plugin 9.0, Gradle 9.2, Kotlin 2.3, Java 17 bytecode target built on a JDK 25 toolchain, `minSdk` 29 | Same toolchain Ferrostar is tested with; Gradle 9.2 needs Java ≤ 25 |

## Build and run

Requirements: a JDK 25 toolchain (the Gradle daemon is pinned to it via
`gradle/gradle-daemon-jvm.properties`, since Gradle 9.2 does not support Java 26) and an
Android SDK with platform 36 and build-tools 36.0.0
(`sdkmanager "platforms;android-36" "build-tools;36.0.0"`).

```sh
cp local.properties.example local.properties   # set sdk.dir if ANDROID_HOME is not exported
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the device: grant location, wait for the puck, then either tap the search pill and pick a
result (the search screen lists **Home** / **Work** and your recent destinations until you type;
the mic runs the system speech recogniser) or long-press the map (the pin is labelled with the
nearest address). A route preview slides up with a car / bicycle / walking switcher, the travel
time, distance and arrival time, and a chip per alternative route when Valhalla offers more than
one; tap **Start**. When you get there, an arrival card sums up the trip; **Done** dismisses it.
Home and Work are set from the search screen too: tap an unset one (or long-press a set one) to
search for its address, use your current location or remove it; long-press a recent to remove it,
or **Clear** them all from the header. Once set, Home and Work appear as markers on the map, and a
tap on one previews the route there. The my-location button
cycles free → follow → follow-with-heading; panning the map drops back to free.
Announcements are spoken through the system text-to-speech engine (make sure one is installed
under Settings → Accessibility → Text-to-speech) and can be silenced with the mute button in the
navigation view. The menu icon in the search pill opens **Settings**: guidance language, voice
on/off, routing profile (car / bicycle / walking), units (km / miles), appearance (system /
light / dark; the map switches to a night version of the basemap with it) and the app language
(system default, English, Spanish, German, French or Italian; on Android 13+ this is the same
setting as the system's per-app language). All of it is remembered.
Debug builds add a **Developer** section with a **Simulate driving** switch that replays the route
at 2x, so guidance can be exercised on an emulator or at a desk. Step-advance and off-route
thresholds follow the profile (car / bicycle / walking).

### Configuration

All values are optional and live in `local.properties` (git-ignored) or environment variables
(`VIALIX_VALHALLA_ENDPOINT`, `VIALIX_GEOCODER_ENDPOINT`, ...). See `local.properties.example`.

| Key | Default | Notes |
|---|---|---|
| `valhallaEndpoint` | `https://valhalla1.openstreetmap.de/route` | FOSSGIS demo server. Fair use (about 1 req/s per user); not for production traffic. Swap for [Stadia Maps](https://stadiamaps.com) (`https://api.stadiamaps.com/route/v1?api_key=...`, free tier) or a self-hosted Valhalla. Plain `http://` (e.g. `http://10.0.2.2:8002/route` from the emulator) works in debug builds only |
| `routingProfile` | `auto` | Any Valhalla costing model. First-launch default; the Settings screen overrides it |
| `mapStyleUrl` | `https://tiles.openfreemap.org/styles/liberty` | Any MapLibre style JSON, used in the light theme |
| `mapStyleUrlDark` | *(blank: derived from `mapStyleUrl`)* | Optional ready-made style for the dark theme, used as-is. By default the light style is recoloured into a night palette at runtime, so the dark map keeps the same POIs and roads |
| `clientId` | `com.galmarino.vialix` | Sent as `X-Client-Id`; FOSSGIS asks published apps to identify themselves |
| `voiceGuidance` | `true` | Whether spoken guidance starts out enabled. First-launch default; the Settings screen and the mute button override it |
| `geocoderEndpoint` | `https://photon.komoot.io/api` | komoot's public Photon instance. Fair use, no SLA; the app debounces typing and cancels superseded requests. Swap for a [self-hosted Photon](https://github.com/komoot/photon#installation) |

## Project layout

```
app/src/main/java/com/galmarino/vialix/
├── NavApplication.kt          Application; owns the object graph
├── AppGraph.kt                Wires FerrostarCore, location provider, HTTP client, geocoder, rerouting policy
├── NavConfig.kt               Endpoints / style / client id with defaults and validation
├── MainActivity.kt            Edge-to-edge Compose host
├── RequestFailure.kt          Offline / server error / other, so no exception text reaches the UI
├── Attribution.kt             Provider names for the menu credits, derived from the endpoints
├── navigation/
│   ├── NavigationViewModel.kt         Screen state: destination, route preview, errors, arrival, start/stop
│   ├── Destination.kt                 Coordinate plus optional name/address (long-press or search result)
│   ├── Arrival.kt, ArrivalTime.kt     Trip summary for the arrival card; arrival clock time (pure)
│   ├── RouteVia.kt, Routes.kt         "via A1": the road that tells alternatives apart; trip duration (pure)
│   ├── CameraFollowMode.kt            Free -> follow -> follow-with-heading cycle of the my-location button (pure)
│   ├── Geo.kt                         Haversine distance, for "how far is this result" (pure)
│   └── NavigationControllerConfigs.kt Step-advance and deviation thresholds per travel mode
├── routing/
│   ├── ClientIdInterceptor.kt         X-Client-Id / User-Agent on routing requests
│   └── ValhallaRouteProvider.kt       Valhalla requests that follow the current settings
├── search/
│   ├── Place.kt, Geocoder.kt  Geocoding domain model and interface
│   ├── PhotonGeocoder.kt      Photon HTTP client; PhotonResponseParser.kt parses its GeoJSON (pure)
│   └── SearchViewModel.kt     Debounced typeahead with cancellation of superseded requests
├── settings/
│   ├── Settings.kt            Settings value + DistanceUnits + ThemeMode (pure)
│   ├── NavSettings.kt         SharedPreferences-backed settings store (StateFlow)
│   ├── UiLanguage.kt          The translated interface languages (pure)
│   ├── AppLocales.kt          App language: platform per-app locale (API 33+) or a preference applied by MainActivity
│   └── AppNightMode.kt        Per-app night mode (API 31+) so the launch window follows the theme
├── places/
│   ├── SavedPlaces.kt         Home/Work + recents value with dedupe and the 20-entry cap (pure)
│   ├── SavedPlacesCodec.kt    JSON (de)serialisation (pure)
│   ├── SavedPlacesRepository.kt  StateFlow store over a KeyValueStore
│   └── SharedPreferencesKeyValueStore.kt  The KeyValueStore the app binds it to
├── map/
│   ├── PoiLabelStylePatch.kt  Fixes POI label wrapping in the style JSON (pure)
│   ├── NightStylePatch.kt     Recolours the light style into the night palette (pure)
│   ├── CssColor.kt            CSS colour parsing / HSL, used by the night patch (pure)
│   ├── MapStyleLoader.kt      Downloads a style and applies the patches (light or dark)
│   └── MapStyleState.kt       Loading / Patched / Unavailable, what the map should load
├── voice/
│   ├── VoiceGuidance.kt       Ferrostar AndroidTtsObserver, follows the settings store
│   └── SpokenLanguage.kt      Locale -> Valhalla/TTS language tag
└── ui/
    ├── NavigationScreen.kt    Permissions, Ferrostar's navigation view, the preview / arrival bottom sheet
    ├── TopSearchBar.kt        Full-width search pill (menu / hint / mic) and the status-bar scrim
    ├── MapChrome.kt           Colours of the chrome over the map, per basemap (pure)
    ├── VoiceSearch.kt         Mic button -> system speech recogniser -> search query
    ├── MapControls.kt         My-location FAB stack, CameraFollowMode <-> Ferrostar mapping
    ├── PlaceRows.kt           Recent / Home / Work rows and their manage dialogs (used by search)
    ├── DestinationSheet.kt    Route preview sheet content (name, mode switcher, alternatives, ETA, Retry, Start/Cancel)
    ├── ArrivalSheet.kt        "You've arrived" card with the trip summary
    ├── MenuDrawer.kt          Navigation drawer behind the menu icon: Settings, version, data credits
    ├── MapLayers.kt           Location puck (dot, accuracy circle, heading cone), pin, Home/Work and route preview layers, per-theme paint
    ├── Formatters.kt          Ferrostar distance / clock-time formatters following the units and guidance language
    ├── RoutingProfiles.kt     The offered costing models (car / bicycle / walking), shared by preview and Settings
    ├── RouteErrorText.kt, RequestFailureText.kt  Error enums -> user-facing strings
    ├── search/                Full-screen destination search (Home/Work + recents while empty, distances, retry)
    ├── settings/              Settings screen and its choice dialog
    └── theme/                 Material 3 theme (dynamic colour, static blue palettes below API 31) + LocalDarkTheme
```

## Roadmap

- Alternative map styles (a layers button)
- Route options: avoid tolls/ferries
- Offline maps (PMTiles) and offline routing (self-hosted or on-device Valhalla)
- iOS: Ferrostar Swift bindings + a Kotlin Multiplatform module for the shared domain code
- F-Droid metadata and a signed release pipeline (CI already builds, tests, lints and runs the R8 release build)

## License

GNU General Public License v3.0 or later (GPL-3.0-or-later). See `LICENSE`.

Third-party components keep their own licenses, all of which are GPL-compatible: Ferrostar and
MapLibre (BSD-3-Clause), OkHttp and AndroidX (Apache-2.0). Map data
© [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL); tiles by OpenFreeMap.
