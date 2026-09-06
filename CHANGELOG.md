# Changelog

All notable changes to Vialix. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [semantic versioning](https://semver.org).

## [Unreleased]

### Added
- Open `geo:` links and "Directions" requests from other apps: a coordinate previews the route,
  free text opens the search.
- A snackbar with a *Turn on* action when the system location switch is off.
- A *Retry* when the map style could not be downloaded.
- *Source code* and *Privacy policy* entries in the menu drawer; `PRIVACY.md`.
- A visible "more" button on Home, Work and recent rows (long-press remains a shortcut), offered
  to TalkBack as a custom action.
- Release signing from `keystore.properties` or CI secrets, a tag-driven *Release* workflow, and
  Dependabot.
- A `foss` build flavour without the Google Play services location client.
- An *Open-source licences* screen in the menu drawer.
- A ktlint formatting gate in CI.

### Changed
- Travel times are formatted in the guidance language ("25 min", "1 hr, 25 min") instead of
  "25 m".
- The notification permission is requested when guidance starts, not at first launch.
- Settings switches are one control per row for screen readers; the profile switcher and route
  chips meet the 48 dp touch target.
- The debug build installs as `com.galmarino.vialix.debug`, next to a release build.

### Fixed
- A pooled HTTP connection leaked on every failed routing reply.
- Wrong compass heading on devices whose rotation-vector sensor reports three values.
- A crash when the location permission was revoked while the app was running.
- Two concurrent GPS subscriptions during guidance.
- Route requests from a fix that was hours old after the app had been in the background.
- A slow route reply overwriting a newer one after Retry or a profile switch.
- A re-route fetched for one trip being swapped into the next.
- The preview camera snapping back after the user panned.
- The arrival clock in the preview going stale.
- Identical errors not showing a second snackbar.
- The keyboard staying up when leaving the search with Back.
- A search request still running when the field was cleared could repopulate the results.
- The permission rationale described a Home/Work travel-time request the app does not make.

## [0.1.0] - 2026-09-05

Initial proof of concept: map, search, long-press destinations, route preview with alternatives,
turn-by-turn guidance with spoken instructions and rerouting, Home/Work and recents, settings,
dark map, five interface languages.

[Unreleased]: https://github.com/igalmarino/vialix/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/igalmarino/vialix/releases/tag/v0.1.0
