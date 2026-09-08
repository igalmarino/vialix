# AGENTS.md

Shared repository instructions for Codex and Claude Code. Codex reads this file directly;
`CLAUDE.md` imports it for Claude Code. Keep common instructions here rather than duplicating them.

## Project

Vialix — an Android turn-by-turn navigation app (proof of concept, v0.1) built only on open data
and open-source components: OpenStreetMap, MapLibre, Valhalla routing, and the Ferrostar
navigation core. GPL-3.0-or-later. See `README.md` for the tech-stack rationale and roadmap.

## Commands

```sh
./gradlew assembleFullDebug             # build the debug APK (full flavour: with the Play Services location client)
./gradlew testFullDebugUnitTest         # JVM unit tests (the flavours share them; run one)
./gradlew lintFullDebug lintFossDebug   # Android Lint (report: app/build/reports/lint-results-fullDebug.*)
./gradlew assembleFullRelease assembleFossRelease   # R8-minified, resource-shrunk release APKs; signed only with keystore.properties / VIALIX_STORE_* env
./gradlew testFullDebugUnitTest --tests "com.galmarino.vialix.NavConfigTest"                 # one class
./gradlew testFullDebugUnitTest --tests "com.galmarino.vialix.ui.FormattersTest.*coordinates*"  # one test
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

Two product flavours in the `distribution` dimension: `full` (default; adds `play-services-location`
as `fullImplementation`) and `foss` (no proprietary code, F-Droid). Each has its own
`location/FusedLocationProvider.kt` under `src/full` and `src/foss` with the same `create()`
signature; nothing else differs. The flavour-less task names (`assembleDebug`, `testDebugUnitTest`)
still exist as aggregates over both flavours and take twice as long.

Requires an Android SDK with `platforms;android-36` + `build-tools;36.0.0`. The dev container
installs these and exports `ANDROID_HOME=/opt/android-sdk`; outside it, copy
`local.properties.example` to `local.properties` and set `sdk.dir`.

**Which JDK the build runs on.** `gradle/gradle-daemon-jvm.properties` pins the daemon with
`toolchainVersion=25`, so `./gradlew` selects a Java 25 toolchain by auto-detection no matter what
`java`/`JAVA_HOME` point at. That is the JDK CI and the dev container use; with the Gradle versions
before 9.7 a JDK 26 made the VS Code Gradle/Java extensions report an incompatible pair and AGP's
`JdkImageTransform` fail (`jlink` error on `core-for-system-modules.jar`), and the pin keeps the
build off whatever happens to be installed. Building on a machine without a JDK 25 installed fails
with a toolchain-not-found message; install one (in the dev container it is
`/usr/lib/jvm/msopenjdk-current`) rather than deleting the pin.

The project targets Java 17 bytecode; the build itself runs on JDK 25.

There is no instrumented-test source set. CI is `.github/workflows/ci.yml` (JDK 25, SDK 36:
wrapper validation, `assembleFullDebug testFullDebugUnitTest lintFullDebug`, the foss debug build
and lint, then both release builds to keep R8 honest; the debug APKs and the R8 `mapping.txt`
files are uploaded as artifacts). Pushing a `v*` tag runs
`.github/workflows/release.yml`, which builds the signed APK from the `VIALIX_*` repository secrets
and attaches it to a GitHub release; the release process (CHANGELOG, `versionCode` + 1, tag) is in
`CONTRIBUTING.md`. Dependabot (`.github/dependabot.yml`) opens version PRs but ignores Ferrostar and
MapLibre Compose, which move together. The debug build type has `applicationIdSuffix ".debug"`.
CI also runs **ktlint** 1.8.0 (`android_studio` style, rules in `.editorconfig`; Composables are
exempt from the function-naming rule) over `app/src`: run `ktlint --format "app/src/**/*.kt"` before
pushing. The `lint {}` block disables `LogNotTimber` and the stale-version checks, and `app/lint.xml` ignores
`ObsoleteSdkInt` for `mipmap-anydpi-v26` (AAPT2 does not resolve the manifest icon from a plain
`mipmap-anydpi` folder), so the report only holds actionable findings; it is currently clean and
the error count must stay at zero. Backups and device transfers exclude the preferences
(`res/xml/data_extraction_rules.xml` + `backup_rules.xml`): the recents are a location history.

## Design notes

Before changing a subsystem, read its relevant sections in [ARCHITECTURE.md](ARCHITECTURE.md),
which covers configuration, navigation, location, map rendering, search, settings and voice.
Update those notes when the behaviour they describe changes.

## Conventions

- Dependency versions live only in `gradle/libs.versions.toml`. AGP, Kotlin, Compose and
  especially `maplibre-compose` intentionally mirror what Ferrostar 0.54.0 is built against —
  bumping `maplibreCompose` off Ferrostar's version breaks the UI modules at runtime. Dependabot
  ignores all of them (`.github/dependabot.yml`); they move by hand, together, when Ferrostar does.
  Newer AndroidX releases can drag Compose 1.12+ in transitively, which needs AGP 9.1+: such a
  Dependabot PR fails `checkFullDebugAarMetadata` and is closed until then.
- `android.newDsl=false` in `gradle.properties` keeps AGP 9 on the classic DSL, matching Ferrostar.
- Ferrostar's Rust bindings are imported as `uniffi.ferrostar.*` (`Route`, `GeographicCoordinate`,
  `Waypoint`, ...). Those types are the app's domain model; don't wrap them without reason.
- Unit tests are plain JUnit 4 on the JVM and cover the pure pieces (see `app/src/test`;
  `SearchViewModel` is driven with a scripted `Geocoder`, `StandardTestDispatcher` +
  `Dispatchers.setMain`). The HTTP layer is tested against OkHttp's `mockwebserver3`
  (`MapStyleLoaderTest`, `PhotonGeocoderTransportTest`, which also covers `ClientIdInterceptor`);
  `ValhallaRouteProvider.getRoutes` cannot be, because Ferrostar's `RouteAdapter` is native.
  `LibertyStyleTest` runs both style patches over a snapshot of the real OpenFreeMap "liberty"
  style (`src/test/resources/openfreemap-liberty.json`; refresh it when the upstream style changes)
  so an upstream change of shape cannot leave the dark map half light unnoticed. `unitTests.isReturnDefaultValues`
  is on, so `android.util.Log` is a no-op in tests. Pin the locale when testing anything that goes
  through `String.format`, and pass an explicit `Locale` to `SpokenLanguage` rather than relying on
  the ambient default. The `NavigationControllerConfigs` builders call uniffi functions (native) and
  cannot run on the JVM; only the `TravelMode` mapping is tested.
- Every Kotlin source starts with the two SPDX header lines (`GPL-3.0-or-later`, see any file).
  Source and privacy links live in the menu drawer (`MenuDrawer.kt`), because a GPL binary carries
  its offer of source with it; `PRIVACY.md` must keep describing exactly what the app sends
  (the location permission rationale string is its summary).
- Accessibility: a Settings row with a switch is `Modifier.toggleable(role = Role.Switch)` with
  `onCheckedChange = null` on the `Switch` (one control per row for TalkBack), picker rows are
  `clickable(role = Role.Button)`; a row managed by long-press also has a visible `ic_more_vert`
  button and an `onLongClickLabel` (`PlaceRows.kt`); section headers carry `heading()`;
  `ReroutingBanner` is an assertive live region; controls in the preview sheet keep a 48 dp target
  (`heightIn` on the segments, `minimumInteractiveComponentSize` on the chips). British spelling in
  the base strings.
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
